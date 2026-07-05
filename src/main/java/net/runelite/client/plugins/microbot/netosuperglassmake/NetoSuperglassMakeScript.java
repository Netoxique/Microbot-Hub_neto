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
import net.runelite.api.TileObject;
import net.runelite.api.ObjectComposition;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.api.MenuAction;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;

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
                        Rs2Camera.setPitch(1960 / 8);
                        Rs2Camera.setYaw(1295 / 8);
                        Rs2Camera.setZoom(2627);
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
    }

    private void glassblowing() {
        Rs2Tab.switchToMagicTab();
        castSuperglassMake();
        Rs2Bank.preHover();
//        sleep(600 * 2, 600 * 4);
        sleepGaussian(1500, 150);
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

    private NewMenuEntry getObjectMenuEntry(TileObject object, String action) {
        if (object == null) return null;
        try {
            ObjectComposition objComp = Rs2GameObject.convertToObjectComposition(object);
            if (objComp == null) return null;

            int param0;
            int param1;
            if (object instanceof GameObject) {
                GameObject obj = (GameObject) object;
                if (obj.sizeX() > 1) {
                    param0 = obj.getLocalLocation().getSceneX() - obj.sizeX() / 2;
                } else {
                    param0 = obj.getLocalLocation().getSceneX();
                }
                if (obj.sizeY() > 1) {
                    param1 = obj.getLocalLocation().getSceneY() - obj.sizeY() / 2;
                } else {
                    param1 = obj.getLocalLocation().getSceneY();
                }
            } else {
                param0 = object.getLocalLocation().getSceneX();
                param1 = object.getLocalLocation().getSceneY();
            }

            int index = 0;
            String[] actions;
            if (objComp.getImpostorIds() != null && objComp.getImpostor() != null) {
                actions = objComp.getImpostor().getActions();
            } else {
                actions = objComp.getActions();
            }

            if (actions != null) {
                for (int i = 0; i < actions.length; i++) {
                    if (actions[i] == null) continue;
                    if (action.equalsIgnoreCase(Rs2UiHelper.stripColTags(actions[i]))) {
                        index = i;
                        break;
                    }
                }
            }

            MenuAction menuAction = MenuAction.GAME_OBJECT_FIRST_OPTION;
            if (index == 1) {
                menuAction = MenuAction.GAME_OBJECT_SECOND_OPTION;
            } else if (index == 2) {
                menuAction = MenuAction.GAME_OBJECT_THIRD_OPTION;
            } else if (index == 3) {
                menuAction = MenuAction.GAME_OBJECT_FOURTH_OPTION;
            } else if (index == 4) {
                menuAction = MenuAction.GAME_OBJECT_FIFTH_OPTION;
            }

            int worldViewId = net.runelite.api.WorldView.TOPLEVEL;
            try {
                if (object.getWorldView() != null && !object.getWorldView().isTopLevel()) {
                    net.runelite.api.WorldView worldView = Microbot.getClientThread().invoke(() -> Microbot.getClient().getLocalPlayer().getWorldView());
                    if (worldView == null) {
                        worldViewId = Microbot.getClient().getTopLevelWorldView().getId();
                    } else {
                        worldViewId = worldView.getId();
                    }
                }
            } catch (Exception e) {
                // Ignore and use TOPLEVEL
            }

            return new NewMenuEntry()
                    .param0(param0)
                    .param1(param1)
                    .opcode(menuAction.getId())
                    .identifier(object.getId())
                    .itemId(-1)
                    .option(action)
                    .target(objComp.getName())
                    .gameObject(object)
                    .worldViewId(worldViewId);
        } catch (Exception e) {
            return null;
        }
    }

    private NewMenuEntry getHoveredBankEntry() {
        GameObject bank = Rs2GameObject.findBank();
        if (bank != null) {
            Rectangle bounds = Rs2UiHelper.getObjectClickbox(bank);
            if (bounds != null && !isDefaultRectangle(bounds) && Rs2UiHelper.isMouseWithinRectangle(bounds)) {
                return getObjectMenuEntry(bank, "Bank");
            }
        }

        GameObject bankChest = Rs2GameObject.getGameObject("bank chest");
        if (bankChest != null) {
            Rectangle bounds = Rs2UiHelper.getObjectClickbox(bankChest);
            if (bounds != null && !isDefaultRectangle(bounds) && Rs2UiHelper.isMouseWithinRectangle(bounds)) {
                return getObjectMenuEntry(bankChest, "Bank");
            }
        }

        WallObject grandExchangeBooth = Rs2GameObject.findGrandExchangeBooth();
        if (grandExchangeBooth != null) {
            Rectangle bounds = Rs2UiHelper.getObjectClickbox(grandExchangeBooth);
            if (bounds != null && !isDefaultRectangle(bounds) && Rs2UiHelper.isMouseWithinRectangle(bounds)) {
                return getObjectMenuEntry(grandExchangeBooth, "Bank");
            }
        }

        return null;
    }

    private boolean openBank() {
        if (Rs2Bank.isOpen()) return true;
        NewMenuEntry entry = getHoveredBankEntry();
        if (entry != null) {
            java.awt.Point currentPos = Microbot.getMouse().getMousePosition();
            while (!Rs2Bank.isOpen()) {
                Microbot.getMouse().click(new Point(currentPos.x, currentPos.y), entry);
                sleepGaussian(105, 22);
            }
            return Rs2Bank.isOpen();
        }
        if (isHoveringBank()) {
            java.awt.Point currentPos = Microbot.getMouse().getMousePosition();
            while (!Rs2Bank.isOpen()) {
                Microbot.getMouse().click(new Point(currentPos.x, currentPos.y));
                sleepGaussian(105, 22);
            }
            return Rs2Bank.isOpen();
        }
        return Rs2Bank.openBank();
    }

    private void castSuperglassMake() {
        MagicAction spell = MagicAction.SUPERGLASS_MAKE;
        net.runelite.api.widgets.Widget widget = Rs2Widget.getWidget(spell.getWidgetId());
        if (widget == null) return;

        Microbot.doInvoke(new NewMenuEntry()
                .option("Cast")
                .param0(-1)
                .param1(spell.getWidgetId())
                .opcode(MenuAction.CC_OP.getId())
                .identifier(1)
                .itemId(-1)
                .target(spell.getName()),
                widget.getBounds()
        );
    }
}
