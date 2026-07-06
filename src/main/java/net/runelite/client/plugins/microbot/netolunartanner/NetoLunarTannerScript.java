package net.runelite.client.plugins.microbot.netolunartanner;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.netolunartanner.enums.Hides;
import net.runelite.client.plugins.microbot.shared.session.NetoBreakManager;
import net.runelite.client.plugins.microbot.shared.session.NetoRuntimeDisable;
import net.runelite.client.plugins.microbot.shared.session.NetoWorldHopManager;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;
import net.runelite.client.util.QuantityFormatter;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.api.MenuAction;
import net.runelite.api.Point;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import java.awt.Rectangle;
import java.util.Arrays;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemID;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
public class NetoLunarTannerScript extends Script {

    public static String combinedMessage = "";
    public static long hidesTanned = 0;
    public static long totalProfit = 0;
    private long startTime;
    private List<Hides> priorityList = new ArrayList<>();
    private Hides activeHide;
    private final Map<Hides, Integer> profitMap = new HashMap<>();
    private int castsThisInventory = 0;

    @Inject
    private NetoLunarTannerPlugin plugin;
    @Inject
    private NetoBreakManager breakManager;
    @Inject
    private NetoWorldHopManager worldHopManager;
    @Inject
    private NetoRuntimeDisable runtimeDisable;

    // State management
    private enum State {
        PREP,
        TANNING,
        BANKING
    }

    private State currentState = State.PREP;

    public boolean run(NetoLunarTannerConfig config) {
        startTime = System.currentTimeMillis();
        hidesTanned = 0;
        totalProfit = 0;
        activeHide = null;
        currentState = State.PREP;
        castsThisInventory = 0;

        List<Hides> parsed = parsePriorityList(config.hidePriority());
        if (parsed == null) {
            Microbot.showMessage("Invalid Hide Priority input: \"" + config.hidePriority() + "\". Valid options are: green, blue, red, black.");
            shutdown();
            return false;
        }
        priorityList = parsed;

        breakManager.configure(config, "Neto Lunar Tanner");
        worldHopManager.configure(config, "Neto Lunar Tanner");
        runtimeDisable.configure(config, "Neto Lunar Tanner");

        breakManager.reset();
        worldHopManager.reset();
        runtimeDisable.reset();

        if (plugin.isBreakHandlerEnabled()) {
            BreakHandlerScript.setLockState(true);
        }

        Rs2Antiban.setActivityIntensity(ActivityIntensity.LOW);

        Rs2Camera.setZoom(5660);
        Microbot.getClientThread().invokeLater(() -> {
            Microbot.getClient().setCameraPitchTarget(2630);
            Microbot.getClient().setCameraYawTarget(1560);
        });

        // Cache the profit for each hide type using ItemManager
        profitMap.clear();
        for (Hides hide : Hides.values()) {
            try {
                int unprocessedItemPrice = Microbot.getItemManager().search(hide.getName()).get(0).getPrice();
                int processedItemPrice = Microbot.getItemManager().search(hide.getFinished()).get(0).getPrice();
                profitMap.put(hide, processedItemPrice - unprocessedItemPrice);
            } catch (Exception e) {
                // Fallback to 0 if lookup fails
                profitMap.put(hide, 0);
            }
        }

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                // Check runtime limits
                if (runtimeDisable.updateRuntime(NetoLunarTannerPlugin.class)) return;

                // Check if break is currently active
                if (breakManager.updateBreakState()) return;

                if (!super.run() || !Microbot.isLoggedIn()) return;

                if (currentState == State.PREP) {
                    handlePrep();
                    return;
                }

                if (currentState == State.TANNING) {
                    // Wait for inventory to register hides (resolves race condition after closing bank)
                    if (!hasHidesToTan()) {
                        sleepUntil(this::hasHidesToTan, 2000);
                    }

                    if (hasHidesToTan()) {
                        // Find activeHide if null or not in inventory
                        if (activeHide == null || !Rs2Inventory.hasItem(activeHide.getName(), true)) {
                            for (Hides hide : priorityList) {
                                if (Rs2Inventory.hasItem(hide.getName(), true)) {
                                    activeHide = hide;
                                    break;
                                }
                            }
                        }

                        if (activeHide != null && Rs2Inventory.hasItem(activeHide.getName(), true)) {
                            int initialHideCount = Rs2Inventory.count(activeHide.getFinished());
                            castTanLeatherHoverOptimized();

                            // Wait for the inventory count to change indicating hides have been tanned
                            while (Rs2Inventory.count(activeHide.getFinished()) == initialHideCount) {
                                // Check the inventory count periodically without sleeping
                            }

                            int hidesTannedThisAction = Rs2Inventory.count(activeHide.getFinished()) - initialHideCount;
                            hidesTanned += hidesTannedThisAction;

                            int profitPerHide = profitMap.getOrDefault(activeHide, 0);
                            totalProfit += (long) profitPerHide * hidesTannedThisAction;

                            calculateProfitAndDisplay();

                            castsThisInventory++;

                            // Check if we just finished tanning an inventory of hides
                            if (castsThisInventory >= 5 || !Rs2Inventory.hasItem(activeHide.getName(), true)) {
                                if (pauseForBreakAfterTanning()) {
                                    activeHide = null;
                                    return;
                                }

                                if (worldHopManager.tryHopIfDue(this::isRunning).isAttempted()) {
                                    activeHide = null;
                                    return;
                                }

                                // Tanned all hides, transition to banking
                                activeHide = null;
                                bank();
                            }
                        }
                    } else {
                        // No hides in inventory even after waiting -> bank for more
                        activeHide = null;
                        bank();
                    }
                }
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 50, TimeUnit.MILLISECONDS);
        return true;
    }

    private void castTanLeatherHoverOptimized() {
        MagicAction magicSpell = MagicAction.TAN_LEATHER;

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

        // Locate Tan Leather spell widget
        Widget spellWidget = Rs2Widget.getWidget(magicSpell.getWidgetId());
        if (spellWidget == null) return;

        // Verify requirements / can cast
        if (!Rs2Magic.canCast(magicSpell)) {
            return;
        }

        // Click on Tan Leather spell
        MenuAction menuAction;
        if (magicSpell.getName().toLowerCase().contains("teleport") ||
                magicSpell.getName().toLowerCase().contains("bones to") ||
                (magicSpell.getActions() != null && Arrays.stream(magicSpell.getActions()).anyMatch(x -> x != null && x.equalsIgnoreCase("cast")))) {
            menuAction = MenuAction.CC_OP;
        } else {
            menuAction = MenuAction.WIDGET_TARGET;
        }

        NewMenuEntry spellEntry = new NewMenuEntry()
                .option("Cast")
                .param0(-1)
                .param1(magicSpell.getWidgetId())
                .opcode(menuAction.getId())
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

        Microbot.status = "Casting Tan Leather";
        Microbot.getMouse().click(spellClickPoint, spellEntry);

        if (!Microbot.getClient().isClientThread()) {
            sleep(Rs2Random.logNormalBounded(50, 100));
        }
    }

    private boolean pauseForBreakAfterTanning() {
        if (breakManager.tryStartBreakAtSafePoint()) {
            return true;
        }

        if (!plugin.isBreakHandlerEnabled()) {
            return false;
        }

        BreakHandlerScript.setLockState(false);
        if (BreakHandlerScript.isBreakActive() || BreakHandlerScript.breakIn <= 0) {
            return true;
        }

        BreakHandlerScript.setLockState(true);
        return false;
    }

    // Parse comma-separated list of priority hides
    private List<Hides> parsePriorityList(String priorityStr) {
        List<Hides> list = new ArrayList<>();
        if (priorityStr == null || priorityStr.trim().isEmpty()) {
            return null;
        }
        String[] parts = priorityStr.split(",");
        for (String part : parts) {
            String trimmed = part.trim().toLowerCase();
            if (trimmed.isEmpty()) continue;
            Hides hide = null;
            if (trimmed.contains("green")) {
                hide = Hides.GREEN_DRAGONHIDE;
            } else if (trimmed.contains("blue")) {
                hide = Hides.BLUE_DRAGONHIDE;
            } else if (trimmed.contains("red")) {
                hide = Hides.RED_DRAGONHIDE;
            } else if (trimmed.contains("black")) {
                hide = Hides.BLACK_DRAGONHIDE;
            }

            if (hide == null) {
                // Invalid token input
                return null;
            }
            if (!list.contains(hide)) {
                list.add(hide);
            }
        }
        return list.isEmpty() ? null : list;
    }

    // Calculate the profit and display it
    private void calculateProfitAndDisplay() {
        double elapsedHours = (System.currentTimeMillis() - startTime) / 3600000.0;
        int hidesPerHour = (int) (hidesTanned / elapsedHours);
        int profitPerHour = (int) (totalProfit / elapsedHours);

        // Format the message
        combinedMessage = "Hides: " +
                QuantityFormatter.quantityToRSDecimalStack((int) hidesTanned) + " (" +
                QuantityFormatter.quantityToRSDecimalStack(hidesPerHour) + "/hr) | " +
                "Profit: " + QuantityFormatter.quantityToRSDecimalStack((int) totalProfit) + " (" +
                QuantityFormatter.quantityToRSDecimalStack(profitPerHour) + "/hr)";
    }

    // Bank the finished hide and withdraw the next prioritized hide
    private void bank() {
        if (currentState != State.BANKING) {
            currentState = State.BANKING;
            if (!Rs2Bank.openBank()) {
                currentState = State.TANNING;
                return;
            }

            // Deposit all finished leather for all hides
            for (Hides hide : Hides.values()) {
                if (Rs2Inventory.hasItem(hide.getFinished())) {
                    Rs2Bank.depositAll(hide.getFinished());
                    sleepUntilOnClientThread(() -> !Rs2Inventory.hasItem(hide.getFinished()));
                }
            }

            // Find the highest priority hide available in the bank
            Hides selectedHide = null;
            for (Hides hide : priorityList) {
                if (Rs2Bank.hasItem(hide.getName())) {
                    selectedHide = hide;
                    break;
                }
            }

            if (selectedHide != null) {
                Rs2Bank.withdrawAll(selectedHide.getName());
//                Hides finalSelectedHide = selectedHide;
//                sleepUntilOnClientThread(() -> Rs2Inventory.hasItem(finalSelectedHide.getName()));
                activeHide = selectedHide;
            } else {
                Microbot.showMessage("No more hides from the priority list found to tan.");
                shutdown();
                return;
            }

            Rs2Bank.closeBank();
            sleepGaussian(105, 22);
            currentState = State.TANNING;
            castsThisInventory = 0; // Reset cast counter for the new inventory
            calculateProfitAndDisplay();
        }
    }

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

    private boolean isWearingFireStaff() {
        for (String staff : FIRE_STAVES) {
            if (Rs2Equipment.isWearing(staff)) {
                return true;
            }
        }
        return false;
    }

    private String findAvailableFireStaff() {
        for (String staff : FIRE_STAVES) {
            if (Rs2Inventory.hasItem(staff)) {
                return staff;
            }
        }
        for (String staff : FIRE_STAVES) {
            if (Rs2Bank.hasItem(staff)) {
                return staff;
            }
        }
        return null;
    }

    private void handlePrep() {
        Microbot.status = "Prep: Opening bank";
        if (!Rs2Bank.isOpen()) {
            Rs2Bank.openBank();
            sleepUntil(Rs2Bank::isOpen, 5000);
            if (!Rs2Bank.isOpen()) return;
        }

        // 2. Deposit all inventory items, if there's still any in inventory
        if (!Rs2Inventory.isEmpty()) {
            Microbot.status = "Prep: Depositing inventory";
            Rs2Bank.depositAll();
            sleepUntil(Rs2Inventory::isEmpty, 5000);
            if (!Rs2Inventory.isEmpty()) return;
        }

        // 3. Withdraw and equip a valid fire staff
        boolean isStaffEquipped = isWearingFireStaff();
        if (!isStaffEquipped) {
            String staffToEquip = findAvailableFireStaff();
            if (staffToEquip == null) {
                Microbot.showMessage("No valid fire staff found in inventory or bank.");
                shutdown();
                return;
            }

            if (Rs2Inventory.hasItem(staffToEquip)) {
                Microbot.status = "Prep: Equipping fire staff from inventory";
                Rs2Bank.wearItem(staffToEquip);
                sleepUntil(this::isWearingFireStaff, 3000);
            } else if (Rs2Bank.hasItem(staffToEquip)) {
                Microbot.status = "Prep: Withdrawing and equipping fire staff";
                Rs2Bank.withdrawAndEquip(staffToEquip);
                sleepUntil(this::isWearingFireStaff, 3000);
            }

            if (!isWearingFireStaff()) {
                log.warn("Failed to equip fire staff, retrying next loop.");
                return;
            }

            Microbot.status = "Prep: Sleeping 1200 ms after equipping staff";
            sleep(1200);
        }

        // 4. Deposit all inventory items, if there's still any
        if (!Rs2Inventory.isEmpty()) {
            Microbot.status = "Prep: Depositing remaining inventory";
            Rs2Bank.depositAll();
            sleepUntil(Rs2Inventory::isEmpty, 5000);
            if (!Rs2Inventory.isEmpty()) return;
        }

        // 5. Withdraw-all Astral runes, Nature runes, Coins
        boolean hasAstral = Rs2Inventory.hasItem(ItemID.ASTRAL_RUNE) || Rs2Bank.hasItem(ItemID.ASTRAL_RUNE);
        boolean hasNature = Rs2Inventory.hasItem(ItemID.NATURE_RUNE) || Rs2Bank.hasItem(ItemID.NATURE_RUNE);
        boolean hasCoins = Rs2Inventory.hasItem(ItemID.COINS_995) || Rs2Bank.hasItem(ItemID.COINS_995);

        if (!hasAstral || !hasNature || !hasCoins) {
            Microbot.showMessage("Missing required items (Astral runes, Nature runes, or Coins) in bank/inventory.");
            shutdown();
            return;
        }

        // Withdraw Astral runes
        if (!Rs2Inventory.hasItem(ItemID.ASTRAL_RUNE)) {
            Microbot.status = "Prep: Withdrawing Astral runes";
            Rs2Bank.withdrawAll(ItemID.ASTRAL_RUNE);
            sleepUntil(() -> Rs2Inventory.hasItem(ItemID.ASTRAL_RUNE), 3000);
            if (!Rs2Inventory.hasItem(ItemID.ASTRAL_RUNE)) return;
        }

        // Withdraw Nature runes
        if (!Rs2Inventory.hasItem(ItemID.NATURE_RUNE)) {
            Microbot.status = "Prep: Withdrawing Nature runes";
            Rs2Bank.withdrawAll(ItemID.NATURE_RUNE);
            sleepUntil(() -> Rs2Inventory.hasItem(ItemID.NATURE_RUNE), 3000);
            if (!Rs2Inventory.hasItem(ItemID.NATURE_RUNE)) return;
        }

        // Withdraw Coins
        if (!Rs2Inventory.hasItem(ItemID.COINS_995)) {
            Microbot.status = "Prep: Withdrawing Coins";
            Rs2Bank.withdrawAll(ItemID.COINS_995);
            sleepUntil(() -> Rs2Inventory.hasItem(ItemID.COINS_995), 3000);
            if (!Rs2Inventory.hasItem(ItemID.COINS_995)) return;
        }

        log.info("Prep state completed successfully. Transitioning to TANNING.");
        currentState = State.TANNING;
        castsThisInventory = 0; // Reset cast counter
    }

    private boolean hasHidesToTan() {
        for (Hides hide : priorityList) {
            if (Rs2Inventory.hasItem(hide.getName(), true)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void shutdown() {
        if (plugin.isBreakHandlerEnabled()) {
            BreakHandlerScript.setLockState(false);
        }
        breakManager.reset();
        worldHopManager.reset();
        runtimeDisable.reset();
        super.shutdown();
        hidesTanned = 0; // Reset the count of tanned hides
        totalProfit = 0; // Reset total profit
        combinedMessage = ""; // Reset the combined message
        currentState = State.PREP; // Reset the current state
        castsThisInventory = 0; // Reset cast counter
        activeHide = null;
    }
}
