package net.runelite.client.plugins.microbot.netosalvaging;

import lombok.Getter;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.shared.session.NetoBreakManager;
import net.runelite.client.plugins.microbot.shared.session.NetoRuntimeDisable;
import net.runelite.client.plugins.microbot.shared.session.NetoWorldHopManager;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import javax.inject.Inject;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

public class NetoSalvagingScript extends Script {

    @Inject
    private NetoSalvagingConfig config;
    @Inject
    private NetoBreakManager breakManager;
    @Inject
    private NetoWorldHopManager worldHopManager;
    @Inject
    private NetoRuntimeDisable runtimeDisable;

    @Getter
    private Instant startTime;

    enum State {
        BANKING,
        SALVAGING
    }

    private State state = State.BANKING;

    public boolean run() {
        startTime = Instant.now();
        breakManager.configure(config, "Neto Salvaging");
        worldHopManager.configure(config, "Neto Salvaging");
        worldHopManager.reset();
        runtimeDisable.configure(config, "Neto Salvaging");
        runtimeDisable.reset();
        state = State.BANKING;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run() || !Microbot.isLoggedIn())
                    return;
                if (runtimeDisable.updateRuntime(NetoSalvagingPlugin.class))
                    return;
                if (breakManager.updateBreakState())
                    return;

                switch (state) {
                    case BANKING:
                        handleBanking();
                        break;
                    case SALVAGING:
                        handleSalvaging();
                        break;
                }
            } catch (Exception ex) {
                Microbot.log("Error in NetoSalvagingScript: " + ex.getMessage());
            }
        }, 0, 300, TimeUnit.MILLISECONDS);
        return true;
    }

    private void handleBanking() {
        Microbot.status = "Banking - Opening Bank";
        if (!Rs2Bank.isOpen()) {
            if (!Rs2Bank.openBank()) {
                return;
            }
        }

        // 1. Deposit all items
        Microbot.status = "Banking - Depositing Items";
        Rs2Bank.depositAll();
        sleepGaussian(600, 200);

        // 2. Withdraw-all the first item that matches the name " salvage" ("Small
        // salvage", "Large salvage", etc)
        // If there are no more "salvage" items in the bank, stop the plugin and logout.
        Microbot.status = "Banking - Withdrawing Salvage";
        Rs2ItemModel salvageItem = Rs2Bank.get(item -> item.getName().toLowerCase().contains("salvage"));
        if (salvageItem != null) {
            Rs2Bank.withdrawAll(salvageItem.getId());
            sleepUntil(() -> Rs2Inventory.hasItem(salvageItem.getId()), 3000);
        } else {
            Microbot.status = "No salvage found, logging out";
            Rs2Bank.closeBank();
            sleepUntil(() -> !Rs2Bank.isOpen(), 3000);
            sleepGaussian(600, 100);
            Rs2Player.logout();
            shutdown();
            return;
        }

        // 3. Close the bank
        Microbot.status = "Banking - Closing Bank";
        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 3000);
        sleepGaussian(300, 100);

        // Try start break or hop world at safe point
        if (!breakManager.tryStartBreakAtSafePoint()) {
            worldHopManager.tryHopIfDue(this::isRunning);
        }

        state = State.SALVAGING;
    }

    private void handleSalvaging() {
        if (Rs2Bank.isOpen()) {
            Rs2Bank.closeBank();
            sleepUntil(() -> !Rs2Bank.isOpen(), 3000);
            return;
        }

        // Check if we still have salvage items in inventory
        boolean hasSalvage = Rs2Inventory.contains(item -> item.getName().toLowerCase().contains("salvage"));
        if (!hasSalvage) {
            state = State.BANKING;
            return;
        }

        // 1. Use the interaction "Sort-salvage" with the object "Salvaging station"
        Microbot.status = "Salvaging - Interacting with Station";
        boolean success = Rs2GameObject.interact("Salvaging station", "Sort-salvage");

        if (success) {
            // 2. Wait for the inventory to not have any " salvage" items
            Microbot.status = "Salvaging - Processing Salvage";
            sleepUntil(() -> !Rs2Inventory.contains(item -> item.getName().toLowerCase().contains("salvage")), 60000);
        } else {
            sleepGaussian(1000, 300); // Wait a bit before retrying if interaction failed
        }
    }
}
