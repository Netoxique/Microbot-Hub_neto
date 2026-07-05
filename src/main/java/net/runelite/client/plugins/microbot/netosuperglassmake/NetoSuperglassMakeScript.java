package net.runelite.client.plugins.microbot.netosuperglassmake;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.Notifier;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.api.GameObject;
import net.runelite.api.WallObject;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import javax.inject.Inject;
import java.awt.Rectangle;
import net.runelite.api.Point;
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
        }, 0, 30, TimeUnit.MILLISECONDS);
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
            openBank();
        }

        Rs2Bank.depositAll("Molten Glass");

        sleepUntil(() -> !Rs2Inventory.contains("Molten Glass"), 3000); // Wait for inventory to empty

        if (currentItem == NetoSuperglassMakeInfo.items.GiantSeaweed) {
            if (Rs2Bank.count("Giant seaweed") < 3 || Rs2Bank.count("Bucket of sand") < 18) {
                notifier.notify("Out of materials");
                while (super.isRunning()) {
                    sleep(1000);
                }
            }

            withdrawSeaweed("Giant seaweed", 3);
            Rs2Bank.withdrawX("Bucket of sand", 18);
//            sleepUntil(() -> Rs2Inventory.count("Giant seaweed") >= 3 && Rs2Inventory.count("Bucket of sand") >= 18, 3000);
        } else {
            if (Rs2Bank.count("Seaweed") < 13 || Rs2Bank.count("Bucket of sand") < 13) {
                notifier.notify("Out of materials");
                while (super.isRunning()) {
                    sleep(1000);
                }
            }

            Rs2Bank.withdrawX("Bucket of sand", 13);
            Rs2Bank.withdrawX(401, 13);
//            sleepUntil(() -> Rs2Inventory.count("Seaweed") >= 13 && Rs2Inventory.count("Bucket of sand") >= 13, 3000);
        }

        sleepGaussian(105, 22);
        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 1200);
    }

    private void glassblowing() {
        Rs2Tab.switchToMagicTab();
        Rs2Magic.cast(MagicAction.SUPERGLASS_MAKE);
        Rs2Bank.preHover();
        sleep(600 * 2, 600 * 4);
    }

    private void picking() {
        if (!config.pickUpGlass()) {
            return;
        }
        while (!Rs2Bank.isOpen()) {
            openBank();
            sleep(60, 200);
        }
        Rs2Bank.depositAll("Molten Glass");
        sleepUntil(() -> !Rs2Inventory.contains("Molten Glass"), 3000);
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
            openBank();
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

    private void withdrawSeaweed(String name, int amount) {
        for (int i = 0; i < amount; i++) {
            Rs2ItemModel seaweed = Rs2Bank.findBankItem(name);
            if (seaweed == null) break;

            Rectangle bounds = Rs2Bank.itemBounds(seaweed);
            if (bounds == null) break;

            Point mousePos = Microbot.getClient().getMouseCanvasPosition();
            if (bounds.contains(mousePos.getX(), mousePos.getY())) {
                java.awt.Point currentPos = Microbot.getMouse().getMousePosition();
                Microbot.getMouse().click(new Point(currentPos.x, currentPos.y));
            } else {
                Rs2Bank.withdrawOne(seaweed.getId());
            }
            sleepGaussian(105, 22);
        }
    }

    private boolean isHoveringBank() {
        GameObject bank = Rs2GameObject.findBank();
        if (bank != null) {
            Rectangle bounds = Rs2UiHelper.getObjectClickbox(bank);
            if (bounds != null && !isDefaultRectangle(bounds) && Rs2UiHelper.isMouseWithinRectangle(bounds)) {
                return true;
            }
        }

        GameObject bankChest = Rs2GameObject.getGameObject("bank chest");
        if (bankChest != null) {
            Rectangle bounds = Rs2UiHelper.getObjectClickbox(bankChest);
            if (bounds != null && !isDefaultRectangle(bounds) && Rs2UiHelper.isMouseWithinRectangle(bounds)) {
                return true;
            }
        }

        WallObject grandExchangeBooth = Rs2GameObject.findGrandExchangeBooth();
        if (grandExchangeBooth != null) {
            Rectangle bounds = Rs2UiHelper.getObjectClickbox(grandExchangeBooth);
            if (bounds != null && !isDefaultRectangle(bounds) && Rs2UiHelper.isMouseWithinRectangle(bounds)) {
                return true;
            }
        }

        Rs2NpcModel banker = Rs2Npc.getBankerNPC();
        if (banker != null) {
            Rectangle bounds = Rs2UiHelper.getActorClickbox(banker);
            if (bounds != null && !isDefaultRectangle(bounds) && Rs2UiHelper.isMouseWithinRectangle(bounds)) {
                return true;
            }
        }

        return false;
    }

    private boolean isDefaultRectangle(Rectangle rect) {
        return rect.width >= Microbot.getClient().getCanvasWidth() && rect.height >= Microbot.getClient().getCanvasHeight();
    }

    private boolean openBank() {
        if (Rs2Bank.isOpen()) return true;
        if (isHoveringBank() && !Rs2Bank.isOpen()) {
            java.awt.Point currentPos = Microbot.getMouse().getMousePosition();
            Microbot.getMouse().click(new Point(currentPos.x, currentPos.y));
            sleepUntil(Rs2Bank::isOpen, 5000);
            return Rs2Bank.isOpen();
        }
        return Rs2Bank.openBank();
    }
}
