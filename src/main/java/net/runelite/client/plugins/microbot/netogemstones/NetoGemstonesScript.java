package net.runelite.client.plugins.microbot.netogemstones;

import net.runelite.api.GameObject;
import net.runelite.api.ItemID;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.http.api.worlds.WorldRegion;

import java.util.concurrent.TimeUnit;

public class NetoGemstonesScript extends Script {

    public static String VERSION = "1.0.0";
    private static final int GEM_ROCK = 11380;
    private static final int GEM_ROCK_2 = 11381;
    private static final int BANK_DEPOSIT_CHEST = 10530;

    private NetoGemstonesState state = NetoGemstonesState.MINING;

    public boolean run(NetoGemstonesConfig config) {

        // SETUP
        Microbot.enableAutoRunOn = false;
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyMiningSetup();
        Rs2Antiban.setActivity(Activity.MINING_GEMSTONES);
        Rs2Camera.setZoom(200);
        Rs2Camera.setPitch(512);
        Rs2Camera.setYaw(1024);
        sleepGaussian(700, 200);

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;

                switch (state) {
                    case MINING:
                        doMining(config);
                        break;
                    case BANKING:
                        doBanking();
                        break;
                }

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    public void doMining(NetoGemstonesConfig config) {
        if (Rs2Inventory.isFull()) {
            state = NetoGemstonesState.BANKING;
            return;
        }
        GameObject gemRock = Rs2GameObject.getGameObject("Gem rocks");
        if (gemRock != null) {
            if (Rs2GameObject.interact(gemRock, "Mine")) {
                Rs2Player.waitForXpDrop(Skill.MINING);
                if (config.hopOnPlayerDetect()) {
                    Rs2Player.hopIfPlayerDetected(1, 0, config.distanceToHop(), config.worldRegion() == WorldRegion.UNITED_STATES_OF_AMERICA ? null : config.worldRegion());
                }
            }
        }
    }

    public void doBanking() {
        if (!Rs2Inventory.isFull()) {
            state = NetoGemstonesState.MINING;
            return;
        }

        GameObject depositChest = Rs2GameObject.getGameObject(BANK_DEPOSIT_CHEST);
        if (depositChest != null) {
            if (Rs2GameObject.interact(depositChest, "Deposit")) {
                if (sleepUntil(() -> Rs2Widget.hasWidget("Deposit Box"))) {
                    if (Rs2Inventory.hasItem("Open gem bag")) {
                        Rs2Bank.emptyGemBag();
                        sleepGaussian(600, 200);
                    }
                    Rs2Widget.clickWidget("Deposit inventory");
                    sleepGaussian(900, 300);
                    Rs2Bank.closeBank();
                }
            }
        }
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }

    private enum NetoGemstonesState {
        MINING,
        BANKING
    }
}
