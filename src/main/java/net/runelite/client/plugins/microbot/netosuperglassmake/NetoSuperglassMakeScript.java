package net.runelite.client.plugins.microbot.netosuperglassmake;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.Notifier;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.netosuperglassmake.NetoSuperglassMakeInfo.states.*;


@Slf4j
public class NetoSuperglassMakeScript extends Script {
    @Inject
    private Notifier notifier;

    private NetoSuperglassMakeConfig config;

    private NetoSuperglassMakeInfo.items currentItem;

    private boolean oneTimeSpellBookCheck = false;

    public boolean run(NetoSuperglassMakeConfig config) {
        this.config = config;
        oneTimeSpellBookCheck = false;
        Rs2Antiban.antibanSetupTemplates.applyUniversalAntibanSetup();
        currentItem = config.ITEM();
        Microbot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                switch (NetoSuperglassMakeInfo.botStatus) {
                    case Starting:
                        NetoSuperglassMakeInfo.botStatus = NetoSuperglassMakeInfo.states.Prep;
                        break;
                    case Prep:
                        prep();
                        NetoSuperglassMakeInfo.botStatus = NetoSuperglassMakeInfo.states.Banking;
                        break;
                    case Banking:
                        banking();
                        NetoSuperglassMakeInfo.botStatus = NetoSuperglassMakeInfo.states.Glassblowing;
                        break;
                    case Glassblowing:
                        glassblowing();
                        if (config.pickUpGlass()) {
                            NetoSuperglassMakeInfo.botStatus = NetoSuperglassMakeInfo.states.Picking;
                        } else {
                            NetoSuperglassMakeInfo.botStatus = NetoSuperglassMakeInfo.states.Banking;
                        }
                        break;
                    case Picking:
                        picking();
                        NetoSuperglassMakeInfo.botStatus = NetoSuperglassMakeInfo.states.Banking;
                        break;

                }

            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
    }

    private void takeBreak() {
        if (Rs2Random.nextInt(0, 20, 1, true) == 30) {
            sleep(1000, 20000);
        }
    }

    private void banking() {
        takeBreak();

        while (!Rs2Bank.isOpen()) {
            Rs2Bank.openBank();
            sleep(100, 200);
        }
        Rs2Bank.depositAll("Molten Glass");
        sleepUntil(() -> !Rs2Inventory.contains("Molten Glass"), 100);
        if (currentItem == NetoSuperglassMakeInfo.items.GiantSeaweed) {
            if (Rs2Bank.count("Giant seaweed") < 3 || Rs2Bank.count("Bucket of sand") < 3) {
                notifier.notify("Out of materials");
                while (super.isRunning()) {
                    sleep(1000);
                }
            }

            for (int i = 0; i < 3; i++) {
                Rs2Bank.withdrawOne("Giant seaweed");
            }

            Rs2Bank.withdrawX("Bucket of sand", 18);
        } else {
            if (Rs2Bank.count("Seaweed") < 3 || Rs2Bank.count("Bucket of sand") < 3) {
                notifier.notify("Out of materials");
                while (super.isRunning()) {
                    sleep(1000);
                }
            }

            Rs2Bank.withdrawX("Bucket of sand", 13);
            Rs2Bank.withdrawX(401, 13);


        }

        sleep(60, 100);
        Rs2Bank.closeBank();
        while (Rs2Bank.isOpen()) {
            sleep(40, 100);
        }


    }

    private void glassblowing() {
        superglassmake();
        sleep(60, 100);
        sleepUntil(() -> Rs2Inventory.contains("Molten Glass"), 100);
    }

    private void superglassmake() {
        if (!oneTimeSpellBookCheck) {
            Rs2Magic.oneTimeSpellBookCheck();
            oneTimeSpellBookCheck = true;
        }
        if (Rs2Magic.quickCast(MagicAction.SUPERGLASS_MAKE)) {
            Rs2Bank.preHover();
            sleep(600 * 2, 600 * 4);
        }

    }


    private void picking() {
        if (!config.pickUpGlass()) {
            return;
        }
        while (!Rs2Bank.isOpen()) {
            Rs2Bank.openBank();
            sleep(60, 200);
        }
        Rs2Bank.depositAll("Molten Glass");
        sleepUntil(() -> !Rs2Inventory.contains("Molten Glass"), 100);
        if (Rs2GroundItem.exists("Molten Glass", 1)) {
            sleep(60, 100);
            Rs2Bank.closeBank();
            while (Rs2GroundItem.exists("Molten Glass", 1)) {
                Rs2GroundItem.loot("Molten Glass", 1);
                sleep(60, 100);
            }
        }

    }

    private void prep() {
        while (!Rs2Bank.isOpen()) {
            Rs2Bank.openBank();
            sleep(100, 300);
        }

        Rs2Bank.depositAll();
        sleepUntil(() -> Rs2Inventory.isEmpty(), 2000);

        if (Rs2Bank.hasItem("Astral rune")) {
            Rs2Bank.withdrawAll("Astral rune");
            sleepUntil(() -> Rs2Inventory.contains("Astral rune"), 2000);
        }

        boolean isWearingSmokeStaff = Rs2Equipment.isWearing(item -> {
            String name = item.getName().toLowerCase();
            return name.contains("smoke") && (name.contains("staff") || name.contains("battlestaff"));
        });

        if (!isWearingSmokeStaff) {
            Rs2ItemModel smokeStaff = Rs2Bank.bankItems().stream()
                    .filter(item -> {
                        String name = item.getName().toLowerCase();
                        return name.contains("smoke") && (name.contains("staff") || name.contains("battlestaff"));
                    })
                    .findFirst()
                    .orElse(null);

            if (smokeStaff != null) {
                Rs2Bank.withdrawAndEquip(smokeStaff.getId());
                sleepUntil(() -> Rs2Equipment.isWearing(smokeStaff.getId()), 2000);
            } else {
                Microbot.log("No Smoke staff found in bank!");
            }
        }
    }
}
