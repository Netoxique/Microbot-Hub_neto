package net.runelite.client.plugins.microbot.netoalching;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.netoalching.enums.NetoAlchingState;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.api.MenuAction;
import net.runelite.api.Point;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;
import java.awt.Rectangle;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
public class NetoAlchingScript extends Script {

    @Getter
    private NetoAlchingState state = NetoAlchingState.PREP;

    @Getter
    private int totalProfit = 0;

    private NetoAlchingConfig config;
    private List<String> alchItemsList = Collections.emptyList();
    private boolean itemsRemainingInBank = false;
    private boolean isFinished = false;

    public static final List<String> FIRE_STAVES = List.of(
            "Twinflame staff",
            "Fire battlestaff",
            "Mystic fire staff",
            "Lava battlestaff",
            "Mystic lava staff",
            "Smoke battlestaff",
            "Mystic smoke staff",
            "Steam battlestaff",
            "Mystic steam staff",
            "Staff of fire"
    );

    public boolean run(NetoAlchingConfig config) {
        this.config = config;
        this.state = NetoAlchingState.PREP;
        this.totalProfit = 0;
        this.itemsRemainingInBank = false;
        this.isFinished = false;

        // Parse configured items list
        this.alchItemsList = parseAlchItems(config.alchItems());

        Microbot.enableAutoRunOn = false;
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyGeneralBasicSetup();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (isFinished) return;

                // Magic level check for High Alchemy
                if (Rs2Player.getRealSkillLevel(Skill.MAGIC) < 55) {
                    Microbot.showMessage("Magic level 55 is required for High Alchemy.");
                    finishScript();
                    return;
                }

                if (alchItemsList.isEmpty()) {
                    Microbot.showMessage("Please configure items to alch.");
                    finishScript();
                    return;
                }

                switch (state) {
                    case PREP:
                        handlePrep();
                        break;
                    case WITHDRAWING:
                        handleWithdrawing();
                        break;
                    case ALCHING:
                        handleAlching();
                        break;
                }

            } catch (Exception ex) {
                log.error("Error in NetoAlchingScript: ", ex);
            }
        }, 0, 800, TimeUnit.MILLISECONDS);
        return true;
    }

    private void handlePrep() {
        Microbot.status = "Preparing: Checking staff & runes";

        if (!Rs2Bank.isOpen()) {
            Rs2Bank.openBank();
            sleepUntil(Rs2Bank::isOpen, 5000);
            return;
        }

        Rs2Bank.depositAll();
        sleepUntil(Rs2Inventory::isEmpty, 5000);

        // Verify Nature Runes are present either in inventory or in bank
        boolean hasNatureRunes = Rs2Inventory.hasItem(ItemID.NATURERUNE) || Rs2Bank.hasItem(ItemID.NATURERUNE);
        if (!hasNatureRunes) {
            Microbot.showMessage("No Nature runes found in bank or inventory.");
            finishScript();
            return;
        }

        // Check if a valid fire staff is equipped
        boolean isStaffEquipped = isWearingFireStaff();
        if (!isStaffEquipped) {
            // Find if we have a staff in inventory or bank
            String staffToEquip = findAvailableFireStaff();
            if (staffToEquip == null) {
                Microbot.showMessage("No valid fire staff found in inventory or bank.");
                finishScript();
                return;
            }

            if (Rs2Inventory.hasItem(staffToEquip)) {
                Microbot.status = "Equipping fire staff from inventory";
                Rs2Bank.wearItem(staffToEquip);
                sleepUntil(this::isWearingFireStaff, 2000);
            } else if (Rs2Bank.hasItem(staffToEquip)) {
                Microbot.status = "Withdrawing and equipping fire staff";
                Rs2Bank.withdrawAndEquip(staffToEquip);
                sleepUntil(this::isWearingFireStaff, 2000);
            }

            // Re-verify if it successfully equipped
            if (!isWearingFireStaff()) {
                log.warn("Failed to equip fire staff, retrying next loop.");
                return;
            }
        }

        // Withdraw all Nature runes
        if (Rs2Bank.hasItem(ItemID.NATURERUNE)) {
            Microbot.status = "Withdrawing nature runes";
            Rs2Bank.withdrawAll(ItemID.NATURERUNE);
            sleepUntil(() -> Rs2Inventory.hasItem(ItemID.NATURERUNE), 2000);
        }

        // Transition to WITHDRAWING state
        state = NetoAlchingState.WITHDRAWING;
    }

    private void handleWithdrawing() {
        Microbot.status = "Withdrawing items to alch";

        if (!Rs2Bank.isOpen()) {
            Rs2Bank.openBank();
            sleepUntil(Rs2Bank::isOpen, 5000);
            return;
        }

        // Enable withdraw as notes
        Rs2Widget.clickWidget(InterfaceID.Bankmain.NOTE);
        sleep(600, 1000);

        int targetSlots = Rs2Inventory.hasItem(ItemID.COINS) ? 28 : 27;

        // Loop and withdraw configured items
        for (String itemName : alchItemsList) {
            int occupied = 28 - Rs2Inventory.emptySlotCount();
            if (occupied >= targetSlots) {
                break;
            }

            if (Rs2Bank.hasItem(itemName, true)) {
                Microbot.status = "Withdrawing: " + itemName;
                Rs2Bank.withdrawAll(itemName, true);
                sleepUntil(() -> Rs2Inventory.hasItem(itemName, true), 2000);
            }
        }

        // Check if there are still more items to alch in the bank
        itemsRemainingInBank = false;
        for (String itemName : alchItemsList) {
            if (Rs2Bank.hasItem(itemName, true)) {
                itemsRemainingInBank = true;
                break;
            }
        }

        // Transition to ALCHING
        state = NetoAlchingState.ALCHING;
    }

    private void handleAlching() {
        Microbot.status = "Alching items";

        if (Rs2Bank.isOpen()) {
            Rs2Bank.closeBank();
            sleepUntil(() -> !Rs2Bank.isOpen(), 3000);
            return;
        }

        // Check if we have nature runes in inventory
        if (!Rs2Inventory.hasItem(ItemID.NATURERUNE)) {
            Microbot.showMessage("Out of Nature runes.");
            finishScript();
            return;
        }

        // Find the first alchable item present in our inventory
        Rs2ItemModel alchItem = alchItemsList.stream()
                .filter(itemName -> Rs2Inventory.hasItem(itemName, true))
                .map(itemName -> Rs2Inventory.get(itemName, true))
                .findFirst()
                .orElse(null);

        if (alchItem != null) {
            Microbot.status = "Alching " + alchItem.getName();
            int haPrice = alchItem.getHaPrice();
            if (haPrice == 0) {
                int unnotedId = alchItem.isNoted() ? alchItem.getUnNotedId() : alchItem.getId();
                net.runelite.api.ItemComposition unnotedComp = Microbot.getClientThread().runOnClientThreadOptional(() ->
                        Microbot.getClient().getItemDefinition(unnotedId)).orElse(null);
                if (unnotedComp != null) {
                    haPrice = unnotedComp.getHaPrice();
                    if (haPrice == 0) {
                        haPrice = (int) (unnotedComp.getPrice() * 0.6);
                    }
                }
            }

            // Perform anti-ban slot move (similar to AIO Magic)
            if (Rs2AntibanSettings.naturalMouse) {
                int inventorySlot = 12; // High alchemy coordinates match slot 12
                if (alchItem.getSlot() != inventorySlot) {
                    Rs2Inventory.moveItemToSlot(alchItem, inventorySlot);
                    return;
                }
            }

            performHoverOptimizedAlch(alchItem);
            boolean xpGained = Rs2Player.waitForXpDrop(Skill.MAGIC, 10000, false);
            if (xpGained) {
                totalProfit += haPrice;
            }
        } else {
            // Inventory has no more alchable items
            if (itemsRemainingInBank) {
                state = NetoAlchingState.WITHDRAWING;
            } else {
                finishScript();
            }
        }
    }

    private void performHoverOptimizedAlch(Rs2ItemModel item) {
        // Switch to magic tab if not already active
        if (Rs2Tab.getCurrentTab() != InterfaceTab.MAGIC) {
            Rs2Tab.switchToMagicTab();
            sleepUntil(() -> Rs2Tab.getCurrentTab() == InterfaceTab.MAGIC, 5000);
            sleep(150, 300);
        }

        // Handle sub-menu check (like Rs2Magic setup)
        Widget backWidget = Rs2Widget.getWidget(218, 4);
        if (backWidget != null && backWidget.getActions() != null && Rs2Widget.isWidgetVisible(218, 4) &&
                Arrays.stream(backWidget.getActions()).anyMatch(x -> x.equalsIgnoreCase("back"))) {
            Rs2Widget.clickWidget(backWidget);
            sleep(150, 300);
        }

        // Locate High Alchemy spell widget
        MagicAction magicSpell = MagicAction.HIGH_LEVEL_ALCHEMY;
        Widget spellWidget = Rs2Widget.getWidget(magicSpell.getWidgetId());
        if (spellWidget == null) return;

        // Verify requirements / can cast
        if (!Rs2Magic.canCast(magicSpell)) {
            log.warn("Cannot cast High Alchemy");
            return;
        }

        // Click on High Alchemy spell
        NewMenuEntry spellEntry = new NewMenuEntry()
                .option("Cast")
                .param0(-1)
                .param1(magicSpell.getWidgetId())
                .opcode(MenuAction.WIDGET_TARGET.getId())
                .identifier(1)
                .itemId(-1)
                .target(magicSpell.getName());

        Rectangle spellBounds = spellWidget.getBounds();
        Point spellClickPoint;
        if (Rs2UiHelper.isMouseWithinRectangle(spellBounds)) {
            java.awt.Point mousePos = Microbot.getMouse().getMousePosition();
            spellClickPoint = new Point(mousePos.x, mousePos.y);
        } else {
            spellClickPoint = Rs2UiHelper.getClickingPoint(spellBounds, true);
        }

        Microbot.status = "Casting High Alchemy";
        Microbot.getMouse().click(spellClickPoint, spellEntry);

        if (!Microbot.getClient().isClientThread()) {
            sleep(Rs2Random.logNormalBounded(50, 100));
        }

        // Wait for inventory tab to open (the game switches automatically when targeting)
        sleepUntil(() -> Microbot.getClientThread().runOnClientThreadOptional(() -> Rs2Tab.getCurrentTab() == InterfaceTab.INVENTORY).orElse(false), 5000);
        sleep(300, 600);

        // Click on the item to alch
        Widget inventoryWidget = Rs2Widget.getWidget(ComponentID.INVENTORY_CONTAINER);
        if (inventoryWidget == null || inventoryWidget.getChildren() == null) return;

        Widget itemWidget = Arrays.stream(inventoryWidget.getChildren())
                .filter(w -> w != null && w.getIndex() == item.getSlot())
                .findFirst()
                .orElse(null);

        Rectangle itemBounds = itemWidget != null ? itemWidget.getBounds() : null;
        if (itemBounds == null) return;

        NewMenuEntry itemEntry = new NewMenuEntry()
                .option("Cast")
                .param0(item.getSlot())
                .param1(ComponentID.INVENTORY_CONTAINER)
                .opcode(MenuAction.WIDGET_TARGET_ON_WIDGET.getId())
                .identifier(1)
                .itemId(item.getId())
                .target(item.getName());

        Point itemClickPoint;
        if (Rs2UiHelper.isMouseWithinRectangle(itemBounds)) {
            java.awt.Point mousePos = Microbot.getMouse().getMousePosition();
            itemClickPoint = new Point(mousePos.x, mousePos.y);
        } else {
            itemClickPoint = Rs2UiHelper.getClickingPoint(itemBounds, true);
        }

        Microbot.status = "Alching " + item.getName();
        Microbot.getMouse().click(itemClickPoint, itemEntry);

        if (!Microbot.getClient().isClientThread()) {
            sleep(Rs2Random.logNormalBounded(50, 100));
        }
    }

    private boolean isWearingFireStaff() {
        for (String staff : FIRE_STAVES) {
            if (Rs2Equipment.isWearing(staff)) {
                return true;
            }
        }
        return false;
    }

    private String findAvailableFireStaff() {
        // First check inventory
        for (String staff : FIRE_STAVES) {
            if (Rs2Inventory.hasItem(staff)) {
                return staff;
            }
        }
        // Then check bank
        for (String staff : FIRE_STAVES) {
            if (Rs2Bank.hasItem(staff)) {
                return staff;
            }
        }
        return null;
    }

    private List<String> parseAlchItems(String items) {
        if (items == null || items.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(items.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .collect(Collectors.toList());
    }

    public static String formatProfit(int profit) {
        if (profit >= 1_000_000) {
            return new java.text.DecimalFormat("#.##").format(profit / 1_000_000.0) + "M";
        } else if (profit >= 10_000) {
            return new java.text.DecimalFormat("#.##").format(profit / 1_000.0) + "K";
        } else {
            return String.valueOf(profit);
        }
    }

    private void finishScript() {
        if (isFinished) return;
        isFinished = true;

        Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Microbot.getClient().addChatMessage(
                    net.runelite.api.ChatMessageType.GAMEMESSAGE,
                    "",
                    "<col=ff0000>Neto Alching: Finished alching. Total profit: " + formatProfit(totalProfit) + "</col>",
                    ""
            );
            return null;
        });

        if (config.sellToGeAtShutdown()) {
            boolean started = Microbot.startPlugin("net.runelite.client.plugins.microbot.netogeseller.NetoGeSellerPlugin");
            if (!started) {
                System.err.println("Neto Alching: Failed to start Neto GE Seller plugin at shutdown.");
                log.error("Failed to start Neto GE Seller plugin at shutdown.");
            }
        }

        // Disable/stop the plugin
        Microbot.stopPlugin(Microbot.getPluginManager()
                .getPlugins().stream()
                .filter(p -> p instanceof NetoAlchingPlugin)
                .findFirst().orElse(null));
    }

    @Override
    public void shutdown() {
        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
    }
}
