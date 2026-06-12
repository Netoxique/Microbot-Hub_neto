package net.runelite.client.plugins.microbot.netosawmillplanks;

import net.runelite.api.ItemID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.shared.session.NetoBreakManager;
import net.runelite.client.plugins.microbot.shared.session.NetoRuntimeDisable;
import net.runelite.client.plugins.microbot.shared.session.NetoWorldHopManager;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;

import javax.inject.Inject;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import lombok.Getter;

public class NetoSawmillPlanksScript extends Script {

    public static final String LOG_BASKET = "Log basket"; // Item name
    public static final int SAWMILL_OPERATOR_ID = 3101;
    private static final WorldPoint EARTH_ALTAR_TELEPORT = new WorldPoint(3288, 3467, 0);

    @Inject
    private NetoSawmillPlanksConfig config;
    @Inject
    private NetoBreakManager breakManager;
    @Inject
    private NetoWorldHopManager worldHopManager;
    @Inject
    private NetoRuntimeDisable runtimeDisable;

    @Getter
    private int logsLeft = 0;
    @Getter
    private Instant startTime;

    State state = State.BANKING;
    boolean hasPerformedInitialCleanup = false;

    public boolean run() {
        startTime = Instant.now();
        breakManager.configure(config, "Neto Sawmill Planks");
        worldHopManager.configure(config, "Neto Sawmill Planks");
        worldHopManager.reset();
        runtimeDisable.configure(config, "Neto Sawmill Planks");
        runtimeDisable.reset();
        hasPerformedInitialCleanup = false;
        state = State.BANKING;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;
                if (runtimeDisable.updateRuntime(NetoSawmillPlanksPlugin.class)) return;
                if (breakManager.updateBreakState()) return;

                switch (state) {
                    case BANKING:
                        Rs2Antiban.setActivityIntensity(ActivityIntensity.HIGH);
                        handleBanking();
                        break;
                    case WALKING_TO_SAWMILL:
                        Rs2Antiban.setActivityIntensity(ActivityIntensity.LOW);
                        handleWalking();
                        break;
                    case BUYING:
                        Rs2Antiban.setActivityIntensity(ActivityIntensity.LOW);
                        handleBuying();
                        break;
                }
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }

    private void logWalk(WorldPoint dst) {
        WorldPoint myLocation = Rs2Player.getWorldLocation();
        if (myLocation == null) return;

        var future = scheduledExecutorService.submit(() -> Rs2Walker.walkTo(dst));

        while (!future.isDone()) {
            if (Rs2Player.getWorldLocation().distanceTo(dst) <= 7) {
                Rs2Walker.setTarget(null);
                future.cancel(true);
                break;
            }
            sleep(50);
        }
    }

    private boolean npc_interact(int id, String action) {
        return Microbot.getRs2NpcCache().query().withId(id).interact(action);
    }

    private void handleBanking() {
        // 1. Bank Open/Proximity
        if (!Rs2Bank.isOpen()) {
            BankLocation nearestBank = Rs2Bank.getNearestBank();
            if (Rs2Bank.isNearBank(nearestBank, 15)) {
                Rs2Bank.openBank();
                sleepUntil(Rs2Bank::isOpen);
            } else {
                Rs2Bank.walkToBankAndUseBank(nearestBank);
            }
            return;
        }

        logsLeft = Rs2Bank.count(ItemID.MAHOGANY_LOGS);

        // Force Overlay refresh (not working)
        if (logsLeft == 0 && Rs2Bank.hasItem(ItemID.MAHOGANY_LOGS)) {
            sleep(100);
            logsLeft = Rs2Bank.count(ItemID.MAHOGANY_LOGS);
        }

        // Log out if logs empty
        if (logsLeft == 0) {
            Rs2Bank.closeBank();
            sleepUntil(() -> !Rs2Bank.isOpen());
            sleepGaussian(600, 50); // 300 to 500 ms
            Rs2Player.logout();
            shutdown();
            return;
        }

        // Stamina Check
        if (Rs2Player.getRunEnergy() < 10) {
            if (Rs2Bank.hasItem("Stamina potion")) {
                Rs2Bank.withdrawItem(false, "Stamina potion");
                sleepUntil(() -> Rs2Inventory.hasItem("Stamina potion"), 3000);
                sleepGaussian(400, 50); // 300 to 500 ms
                Rs2Inventory.interact("Stamina potion", "Drink");
            }
        }

        // Required Items Check
        if (!Rs2Equipment.isWearing("Ring of the elements")) {
            if (Rs2Inventory.hasItem("Ring of the elements")) {
                Rs2Inventory.interact("Ring of the elements", "Wear");
            } else {
                Rs2Bank.withdrawAndEquip("Ring of the elements");
            }
            sleepGaussian(400, 50); // 300 to 500 ms
        }
        if (!Rs2Inventory.hasItem("Coins")) {
            Rs2Bank.withdrawAll("Coins");
            sleepGaussian(400, 50); // 300 to 500 ms
        }
        if (!Rs2Inventory.hasItem("Plank sack")) {
            Rs2Bank.withdrawItem("Plank sack");
            sleepGaussian(400, 50); // 300 to 500 ms
        }
        if (!Rs2Inventory.hasItem(LOG_BASKET)) {
            Rs2Bank.withdrawItem(LOG_BASKET);
            sleepGaussian(400, 50); // 300 to 500 ms
        }

        // Cleanup once per run
        if (!hasPerformedInitialCleanup) {
            sleepUntil(() -> Rs2Inventory.hasItem(LOG_BASKET), 3000);
            Rs2Inventory.interact(LOG_BASKET, "Empty to bank");
            sleepGaussian(500, 50);
            hasPerformedInitialCleanup = true;
        }

        // Deposit anything that's not allowed
        Rs2Bank.depositAllExcept(
                "Plank sack",
                "Log basket",
                "Coins",
                "Ring of dueling",
                "Ring of the elements",
                "Crafting cape",
                "Farming cape",
                "Sailors' amulet"
        );

        // Make sure no planks in inventory
        sleepUntil(() -> !Rs2Inventory.hasItem("Mahogany plank"), 5000);

        // Deposit Excess
        if (Rs2Inventory.hasItem("Plank sack")) {
            Rs2Inventory.interact("Plank sack", "Empty");
            sleepUntil(() -> Rs2Inventory.hasItem("Mahogany plank"), 5000);
            sleepGaussian(150, 25); // 100 to 200 ms
        }

        if (Rs2Inventory.hasItem("Mahogany plank")) {
            Rs2Bank.depositAll("Mahogany plank");
            sleepUntil(() -> !Rs2Inventory.hasItem("Mahogany plank"), 5000);
            sleepGaussian(400, 50); // 300 to 500 ms
        }

        // Teleport Items Check
        boolean hasTeleport = Rs2Equipment.isWearing("Crafting cape") || Rs2Equipment.isWearing("Farming cape") || 
                             Rs2Equipment.isWearing("Sailors' amulet") || Rs2Equipment.isWearing("Ring of dueling") ||
                             Rs2Inventory.hasItem("Crafting cape") || Rs2Inventory.hasItem("Farming cape") || 
                             Rs2Inventory.hasItem("Sailors' amulet") || Rs2Inventory.hasItem("Ring of dueling");
        
        if (!hasTeleport) {
            if (Rs2Bank.hasItem("Crafting cape")) Rs2Bank.withdrawAndEquip("Crafting cape");
            else if (Rs2Bank.hasItem("Farming cape")) Rs2Bank.withdrawAndEquip("Farming cape");
            else if (Rs2Bank.hasItem("Sailors' amulet")) Rs2Bank.withdrawAndEquip("Sailors' amulet");
            else Rs2Bank.withdrawItem(true, "Ring of dueling");
            sleepGaussian(400, 50); // 300 to 500 ms
        }
        
        if (Rs2Inventory.hasItem("Crafting cape")) { Rs2Inventory.interact("Crafting cape", "Wear"); return; }
        if (Rs2Inventory.hasItem("Farming cape")) { Rs2Inventory.interact("Farming cape", "Wear"); return; }
        if (Rs2Inventory.hasItem("Sailors' amulet")) { Rs2Inventory.interact("Sailors' amulet", "Wear"); return; }

        // Withdraw Logs
        if (!Rs2Inventory.isFull()) {
            Rs2Bank.withdrawAll("Mahogany logs");
            sleepGaussian(400, 50); // 300 to 500 ms
        }

        // Close Bank
        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen());

        // Fill Basket
        sleepUntil(() -> Rs2Inventory.hasItem("Mahogany logs"), 3000);
        sleepGaussian(150, 25); // 100 to 200 ms
        Rs2Inventory.interact(LOG_BASKET, "Fill");
        sleepGaussian(400, 50); // 300 to 500 ms

        // Open Bank
        Rs2Bank.openBank();
        sleepUntil(Rs2Bank::isOpen);

        // Withdraw logs again
        sleepGaussian(400, 50); // 300 to 500 ms
        Rs2Bank.withdrawAll("Mahogany logs");

        // Close Bank
        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen());
        sleepGaussian(150, 25); // 100 to 200 ms

        if (!breakManager.tryStartBreakAtSafePoint()) {
            worldHopManager.tryHopIfDue(this::isRunning);
        }

        state = State.WALKING_TO_SAWMILL;
    }

    private void handleWalking() {
        // Teleport to Earth Altar
        if (Rs2Equipment.isWearing("Ring of the elements")) {
            Rs2Equipment.interact("Ring of the elements", "Earth Altar");
            sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(EARTH_ALTAR_TELEPORT) <= 10, 10000);
        }

        // Walk to sawmill
        WorldPoint sawmillPoint = new WorldPoint(3302, 3491, 0);
        logWalk(sawmillPoint);
        state = State.BUYING;
    }

    private void handleBuying() {
        // Sawmill Interaction
        sleepUntil(() -> Microbot.getRs2NpcCache().query().withId(SAWMILL_OPERATOR_ID).nearest() != null, 5000);
        npc_interact(SAWMILL_OPERATOR_ID, "Buy-Plank");
        sleepUntil(() -> Rs2Widget.findWidget("Mahogany - 1,500gp") != null, 5000);
        sleepGaussian(150, 25); // 100 to 200 ms

        // Buy First Batch
        Rs2Widget.clickWidget("Mahogany - 1,500gp");
        sleepUntil(() -> !Rs2Inventory.hasItem("Mahogany logs"), 5000);
        sleepGaussian(150, 25); // 100 to 200 ms

        // Empty Basket
        Rs2Inventory.interact(LOG_BASKET, "Empty");
        sleepUntil(() -> Rs2Inventory.hasItem("Mahogany logs"), 5000);
        sleepGaussian(150, 25); // 100 to 200 ms

        // Second Sawmill Interaction
        npc_interact(SAWMILL_OPERATOR_ID, "Buy-Plank");
        sleepUntil(() -> Rs2Widget.findWidget("Mahogany - 1,500gp") != null, 5000);
        sleepGaussian(150, 25); // 100 to 200 ms

        // Buy Second Batch
        Rs2Widget.clickWidget("Mahogany - 1,500gp");
        sleepUntil(() -> !Rs2Inventory.hasItem("Mahogany logs"), 5000);
        sleepGaussian(400, 50); // 300 to 500 ms

        teleportToBank();

        // Transition
        state = State.BANKING;
    }

    private void teleportToBank() {
        if (Rs2Equipment.isWearing("Crafting cape")) {
            Rs2Equipment.interact("Crafting cape", "Teleport");
        } else if (Rs2Inventory.hasItem("Crafting cape")) {
            Rs2Inventory.interact("Crafting cape", "Teleport");
        } else if (Rs2Equipment.isWearing("Farming cape")) {
            Rs2Equipment.interact("Farming cape", "Teleport");
        } else if (Rs2Inventory.hasItem("Farming cape")) {
            Rs2Inventory.interact("Farming cape", "Teleport");
        } else if (Rs2Equipment.isWearing("Sailors' amulet")) {
            Rs2Equipment.interact("Sailors' amulet", "Deepfin Point");
        } else if (Rs2Inventory.hasItem("Sailors' amulet")) {
            Rs2Inventory.interact("Sailors' amulet", "Deepfin Point");
        } else if (Rs2Equipment.isWearing("Ring of dueling")) {
            Rs2Equipment.interact("Ring of dueling", "Castle Wars");
        } else if (Rs2Inventory.hasItem("Ring of dueling")) {
            Rs2Inventory.interact("Ring of dueling", "Castle Wars");
        }
        sleepUntil(Rs2Player::isAnimating, 2000);
        sleepUntil(() -> !Rs2Player.isAnimating(), 8000);
    }

    @Override
    public void shutdown() {
        breakManager.reset();
        super.shutdown();
    }
}
