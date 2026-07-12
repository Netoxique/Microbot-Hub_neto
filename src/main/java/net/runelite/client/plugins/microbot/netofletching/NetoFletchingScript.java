package net.runelite.client.plugins.microbot.netofletching;


import lombok.Getter;
import lombok.Setter;
import net.runelite.api.Point;
import net.runelite.api.Skill;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.netofletching.enums.NetoFletchingItem;
import net.runelite.client.plugins.microbot.netofletching.enums.NetoFletchingMaterial;
import net.runelite.client.plugins.microbot.netofletching.enums.NetoFletchingMode;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.concurrent.TimeUnit;

@Getter
class NetoProgressiveFletchingModel {
    @Setter
    private NetoFletchingItem fletchingItem;
    @Setter
    private NetoFletchingMaterial fletchingMaterial;
}

public class NetoFletchingScript extends Script {

    // The fletching interface widget group ID
    private static final int FLETCHING_WIDGET_GROUP_ID = 17694736;

    private static final String FLETCHING_KNIFE = "fletching knife";
    private static final String KNIFE = "knife";

    NetoProgressiveFletchingModel model = new NetoProgressiveFletchingModel();

    String primaryItemToFletch = "";
    String secondaryItemToFletch = "";

    NetoFletchingMode fletchingMode;

    public void run(NetoFletchingConfig config) {
        fletchingMode = config.fletchingMode();
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyFletchingSetup();
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn())
                    return;
                if (!super.run()) return;

                if ((fletchingMode == NetoFletchingMode.PROGRESSIVE || fletchingMode == NetoFletchingMode.PROGRESSIVE_STRUNG)
                        && model.getFletchingItem() == null) {
                    calculateItemToFletch();
                }


                if (!configChecks(config)) return;

                if (Rs2AntibanSettings.actionCooldownActive)
                    return;

//                if (config.Afk() && Random.random(1, 100) == 2)
//                    sleep(1000, 60000);

                boolean hasRequirementsToFletch;
                boolean hasRequirementsToBank;
                primaryItemToFletch = usesKnife() ? getPreferredKnife() : fletchingMode.getItemName();

                if (fletchingMode == NetoFletchingMode.PROGRESSIVE) {
                    secondaryItemToFletch = model.getFletchingMaterial().getLogItemName();
                    hasRequirementsToFletch = Rs2Inventory.hasItem(primaryItemToFletch)
                            && Rs2Inventory.hasItemAmount(secondaryItemToFletch, model.getFletchingItem().getAmountRequired());
                    hasRequirementsToBank = !Rs2Inventory.hasItem(primaryItemToFletch)
                            || !Rs2Inventory.hasItemAmount(secondaryItemToFletch, model.getFletchingItem().getAmountRequired());
                } else if (fletchingMode == NetoFletchingMode.PROGRESSIVE_STRUNG) {
                    secondaryItemToFletch = model.getFletchingMaterial().getName() + " "
                            + model.getFletchingItem().getContainsInventoryName() + " (u)";
                    hasRequirementsToFletch = Rs2Inventory.hasItem(primaryItemToFletch) && Rs2Inventory.hasItem(secondaryItemToFletch);
                    hasRequirementsToBank = !Rs2Inventory.hasItem(primaryItemToFletch) || !Rs2Inventory.hasItem(secondaryItemToFletch);
                } else {
                    secondaryItemToFletch = fletchingMode == NetoFletchingMode.STRUNG
                            ? config.fletchingMaterial().getName() + " " + config.fletchingItem().getContainsInventoryName() + " (u)"
                            : config.fletchingMaterial().getLogItemName();
                    hasRequirementsToFletch = Rs2Inventory.hasItem(primaryItemToFletch)
                            && Rs2Inventory.hasItemAmount(secondaryItemToFletch, config.fletchingItem().getAmountRequired());
                    hasRequirementsToBank = !Rs2Inventory.hasItem(primaryItemToFletch)
                            || !Rs2Inventory.hasItemAmount(secondaryItemToFletch, config.fletchingItem().getAmountRequired());
                }

                if (hasRequirementsToFletch) {
                    fletch(config);
                }
                if (hasRequirementsToBank) {
                    bankItems(config);
                }

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
    }

    private void bankItems(NetoFletchingConfig config) {
        if (!Rs2Bank.isOpen()) {
            if (!Rs2Bank.openBank()) {
                return; // Bank didn't open, retry next iteration
            }
        }

        // Deposit items based on the fletching mode
        switch (fletchingMode) {
            case STRUNG:
                Rs2Bank.depositAll();
                break;
            case PROGRESSIVE:
                Rs2Bank.depositAll(model.getFletchingItem().getContainsInventoryName());
                calculateItemToFletch();
                secondaryItemToFletch = model.getFletchingMaterial().getLogItemName();
                break;
            case PROGRESSIVE_STRUNG:
                Rs2Bank.depositAll();
                calculateItemToFletch();
                secondaryItemToFletch = model.getFletchingMaterial().getName() + " "
                        + model.getFletchingItem().getContainsInventoryName() + " (u)";
                break;
            default:
                Rs2Bank.depositAll(config.fletchingItem().getContainsInventoryName());
                Rs2Inventory.waitForInventoryChanges(5000);
                break;
        }

        // Check if the primary item is available
        boolean hasPrimaryInBank = usesKnife() ? bankHasAnyKnife() : Rs2Bank.hasItem(primaryItemToFletch);
        boolean hasPrimaryInInventory = usesKnife() ? hasAnyKnife() : Rs2Inventory.hasItem(primaryItemToFletch);
        if (!hasPrimaryInBank && !hasPrimaryInInventory) {
            Rs2Bank.closeBank();
            Microbot.status = "[Shutting down] - Reason: " + primaryItemToFletch + " not found in the bank.";
            Microbot.showMessage(Microbot.status);
            shutdown();
            return;
        }

        // Ensure the inventory isn't full without the primary item
        if (!hasPrimaryInInventory) {
            Rs2Bank.depositAll();
        }

        // Withdraw the primary item if not already in the inventory
        if (!hasPrimaryInInventory) {
            Rs2Bank.withdrawX(primaryItemToFletch, fletchingMode.getAmount(), true);
        }

        // Check if the secondary item is available
        if (!Rs2Bank.hasItem(secondaryItemToFletch)) {
            if (fletchingMode == NetoFletchingMode.UNSTRUNG_STRUNG && Rs2Bank.hasBankItem("bow string")) {
                Rs2Bank.depositAll();
                fletchingMode = NetoFletchingMode.STRUNG;
                return;
            }
            Rs2Bank.closeBank();
            Microbot.status = "[Shutting down] - Reason: " + secondaryItemToFletch + " not found in the bank.";
            Microbot.showMessage(Microbot.status);
            shutdown();
            return;
        }

        // Withdraw the secondary item if not already in the inventory
        if (!Rs2Inventory.hasItem(secondaryItemToFletch)) {
            if (fletchingMode == NetoFletchingMode.STRUNG) {
                Rs2Bank.withdrawDeficit(secondaryItemToFletch, fletchingMode.getAmount());
            } else {
                Rs2Bank.withdrawAll(secondaryItemToFletch);
            }
        }
        if (Rs2AntibanSettings.naturalMouse) {
            // Testing if completing the mouse movement before the final item check improves the overall flow.
            // This should allow time for the inventory to update while the mouse is moving.
            // Enhances the bot's behavior to appear more natural and less automated.
            Widget closeButton = Rs2Widget.getWidget(786434).getChild(11);
            Point closePoint = Rs2UiHelper.getClickingPoint(closeButton != null ? closeButton.getBounds() : null, true);
            Rs2Random.waitEx(200, 100);
            Microbot.naturalMouse.moveTo(closePoint.getX(), closePoint.getY());
        }

        // Final check to ensure both items are in the inventory
        boolean hasPrimaryFinal = usesKnife() ? hasAnyKnife() : Rs2Inventory.hasItem(primaryItemToFletch);
        if (!hasPrimaryFinal || !Rs2Inventory.hasItem(secondaryItemToFletch)) {
            Microbot.log("waiting for inventory changes.");
            Rs2Inventory.waitForInventoryChanges(5000);
        }

        Rs2Random.waitEx(200, 100);
        Rs2Bank.closeBank();
    }


    private void fletch(NetoFletchingConfig config) {
        String itemToUse = usesKnife() ? getKnifeInInventory() : primaryItemToFletch;
        Rs2Inventory.combineClosest(itemToUse, secondaryItemToFletch);
        sleepUntil(() -> Rs2Widget.getWidget(FLETCHING_WIDGET_GROUP_ID) != null, 5000);
        char option;
        if (fletchingMode == NetoFletchingMode.PROGRESSIVE || fletchingMode == NetoFletchingMode.PROGRESSIVE_STRUNG) {

            option = model.getFletchingItem().getOption(model.getFletchingMaterial(), fletchingMode);
            Rs2Keyboard.keyPress(option);
        } else {
            option = config.fletchingItem().getOption(config.fletchingMaterial(), fletchingMode);
            Rs2Keyboard.keyPress(option);
        }

        Rs2Bank.preHover();

        sleepUntil(() -> !Rs2Inventory.hasItem(secondaryItemToFletch), 60000);
        Rs2Antiban.actionCooldown();
        Rs2Antiban.takeMicroBreakByChance();
    }

    private boolean configChecks(NetoFletchingConfig config) {
        if (config.fletchingMaterial() == NetoFletchingMaterial.REDWOOD && config.fletchingItem() != NetoFletchingItem.SHIELD) {
            Microbot.getNotifier().notify("[Wrong Configuration] You can only make shields with redwood logs.");
            shutdown();
            return false;
        }
        return true;
    }

    private String getPreferredKnife() {
        if (Rs2Inventory.hasItem(FLETCHING_KNIFE) || Rs2Bank.hasItem(FLETCHING_KNIFE)) {
            return FLETCHING_KNIFE;
        }
        return KNIFE;
    }

    private boolean hasAnyKnife() {
        return Rs2Inventory.hasItem(FLETCHING_KNIFE) || Rs2Inventory.hasItem(KNIFE);
    }

    private String getKnifeInInventory() {
        if (Rs2Inventory.hasItem(FLETCHING_KNIFE)) {
            return FLETCHING_KNIFE;
        }
        return KNIFE;
    }

    private boolean bankHasAnyKnife() {
        return Rs2Bank.hasItem(FLETCHING_KNIFE) || Rs2Bank.hasItem(KNIFE);
    }

    private boolean usesKnife() {
        return fletchingMode == NetoFletchingMode.UNSTRUNG
                || fletchingMode == NetoFletchingMode.UNSTRUNG_STRUNG
                || fletchingMode == NetoFletchingMode.PROGRESSIVE;
    }

    public void calculateItemToFletch() {
        int level = Microbot.getClient().getRealSkillLevel(Skill.FLETCHING);
        NetoFletchingItem item = null;
        NetoFletchingMaterial material = null;



        if (fletchingMode == NetoFletchingMode.PROGRESSIVE_STRUNG && level < 5) {
            Microbot.showMessage("Can't String Bows Below Level 5");
            shutdown();
            return;
        }
        if (level < 5) {
            item = NetoFletchingItem.ARROW_SHAFT;
            material = NetoFletchingMaterial.LOG;
        } else if (level < 10) {
            item = NetoFletchingItem.SHORT;
            material = (fletchingMode == NetoFletchingMode.PROGRESSIVE) ? NetoFletchingMaterial.LOG : NetoFletchingMaterial.WOOD;
        } else if (level < 20) {
            item = NetoFletchingItem.LONG;
            material = (fletchingMode == NetoFletchingMode.PROGRESSIVE) ? NetoFletchingMaterial.LOG : NetoFletchingMaterial.WOOD;
        } else if (level < 25) {
            item = NetoFletchingItem.SHORT;
            material = NetoFletchingMaterial.OAK;
        } else if (level < 35) {
            item = NetoFletchingItem.LONG;
            material = NetoFletchingMaterial.OAK;
        } else if (level < 40) {
            item = NetoFletchingItem.SHORT;
            material = NetoFletchingMaterial.WILLOW;
        } else if (level < 50) {
            item = NetoFletchingItem.LONG;
            material = NetoFletchingMaterial.WILLOW;
        } else if (level < 55) {
            item = NetoFletchingItem.SHORT;
            material = NetoFletchingMaterial.MAPLE;
        } else if (level < 65) {
            item = NetoFletchingItem.LONG;
            material = NetoFletchingMaterial.MAPLE;
        } else if (level < 70) {
            item = NetoFletchingItem.SHORT;
            material = NetoFletchingMaterial.YEW;
        } else if (level < 80) {
            item = NetoFletchingItem.LONG;
            material = NetoFletchingMaterial.YEW;
        } else if (level < 85) {
            item = NetoFletchingItem.SHORT;
            material = NetoFletchingMaterial.MAGIC;
        } else {
            item = NetoFletchingItem.LONG;
            material = NetoFletchingMaterial.MAGIC;
        }

        model.setFletchingItem(item);
        model.setFletchingMaterial(material);
    }


    @Override
    public void shutdown() {

        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
    }
}
