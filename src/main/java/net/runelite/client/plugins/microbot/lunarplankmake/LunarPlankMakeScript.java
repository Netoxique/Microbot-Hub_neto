package net.runelite.client.plugins.microbot.lunarplankmake;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.math.Random;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;
import net.runelite.client.util.QuantityFormatter;

import java.util.concurrent.TimeUnit;

public class LunarPlankMakeScript extends Script {

    public static String combinedMessage = "";
    public static long plankMade = 0;
    private int profitPerPlank = 0;
    private long startTime;
    private boolean useSetDelay;
    private int setDelay;
    private boolean useRandomDelay;
    private int maxRandomDelay;

    // State management
    private enum State {
        PLANKING,
        BANKING,
        PREP,
        WAITING
    }

    private static final String[] EARTH_STAFF_NAMES = {
            "staff of earth",
            "earth battlestaff",
            "mystic earth staff",
            "mud staff",
            "mystic mud staff",
            "lava battlestaff",
            "mystic lava staff",
            "dust battlestaff",
            "mystic dust staff"
    };

    private static final Map<Runes, Integer> RUNES_PER_CAST = Map.of(
            Runes.NATURE, 1,
            Runes.ASTRAL, 2
    );

    private static final Map<Runes, Integer> REQUIRED_RUNES = Map.of(
            Runes.NATURE, 16000,
            Runes.ASTRAL, 16000
    );

    private State currentState = State.PLANKING;

    public boolean run(LunarPlankMakeConfig config) {
        activeConfig = config;
        startTime = System.currentTimeMillis();
        int unprocessedItemPrice = Microbot.getItemManager().search(config.ITEM().getName()).get(0).getPrice();
        int processedItemPrice = Microbot.getItemManager().search(config.ITEM().getFinished()).get(0).getPrice();
        profitPerPlank = processedItemPrice - unprocessedItemPrice;

        useSetDelay = config.useSetDelay();
        setDelay = config.setDelay();
        useRandomDelay = config.useRandomDelay();
        maxRandomDelay = config.maxRandomDelay();

        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyCookingSetup();
        Rs2Antiban.setActivity(Activity.CASTING_PLANK_MAKE);
        Rs2AntibanSettings.simulateMistakes = false;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run() || !isRunning() || !Microbot.isLoggedIn()) return;
                switch (currentState) {
                    case PLANKING:
                        plankItems(config);
                        break;
                    case BANKING:
                        bank(config);
                        break;
                    case PREP:
                        prep();
                        break;
                    case WAITING:
                        waitUntilReady();
                        break;
                }
            } catch (Exception ex) {
                Microbot.log("Exception in LunarPlankMakeScript: " + ex.getMessage());
            }
        }, 0, 50, TimeUnit.MILLISECONDS);
        return true;
    }

    private void plankItems(LunarPlankMakeConfig config) {
        if (Rs2Inventory.hasItem(config.ITEM().getName(), true)) {
            if (!hasRunesForPlankMake()) {
                currentState = State.PREP;
                return;
            }
            int initialPlankCount = Rs2Inventory.count(config.ITEM().getFinished());
            Rs2Magic.cast(MagicAction.PLANK_MAKE);
            addDelay();
            Rs2Inventory.interact(config.ITEM().getName());

            // Wait for the inventory count to change indicating Planks have been made
            boolean inventoryChanged = waitForInventoryChange(config.ITEM().getFinished(), initialPlankCount);

            if (!isRunning()) {
                return;
            }

            if (inventoryChanged) {
                int plankMadeThisAction = Rs2Inventory.count(config.ITEM().getFinished()) - initialPlankCount;
                plankMade += plankMadeThisAction;
                addDelay();
            } else {
                Microbot.log("Failed to detect plank creation.");
                currentState = State.WAITING;
            }
        } else {
            currentState = State.BANKING;
        }
    }

    private boolean waitForInventoryChange(String itemName, int initialCount) {
        long start = System.currentTimeMillis();
        while (Rs2Inventory.count(itemName) == initialCount) {
            if (!isRunning()) {
                return false;
            }
            if (System.currentTimeMillis() - start > 3000) { // 3-second timeout
                return false;
            }
            sleep(10);
        }
        return true;
    }

    private void bank(LunarPlankMakeConfig config) {
        if (!Rs2Bank.openBank()) return;

        Rs2Bank.depositAll(config.ITEM().getFinished());
        sleepUntilOnClientThread(() -> !Rs2Inventory.hasItem(config.ITEM().getFinished()));

        currentState = State.PREP;
        calculateProfitAndDisplay(config);
    }

    private void prep() {
        if (!Rs2Bank.isOpen() && !Rs2Bank.openBank()) {
            return;
        }

        Rs2Bank.setWithdrawAsItem();

        if (!isPrepared() && !Rs2Inventory.isEmpty()) {
            Rs2Bank.depositAll();
            sleepUntilOnClientThread(Rs2Inventory::isEmpty);
        }

        if (!ensureEarthStaffEquipped()) {
            return;
        }

        if (!ensureRuneSupplies()) {
            return;
        }

        if (!ensureCoinsAvailable()) {
            return;
        }

        if (!withdrawLogsForPlanking()) {
            return;
        }

        Rs2Bank.closeBank();
        currentState = State.PLANKING;
    }

    private boolean isPrepared() {
        return Rs2Equipment.isWearing(EARTH_STAFF_NAMES)
                && hasRunesForPlankMake()
                && Rs2Inventory.hasItem("coins");
    }

    private boolean hasRunesForPlankMake() {
        int natureRequired = RUNES_PER_CAST.getOrDefault(Runes.NATURE, 0);
        int astralRequired = RUNES_PER_CAST.getOrDefault(Runes.ASTRAL, 0);

        int natureAvailable = Rs2Inventory.count("nature rune");
        int astralAvailable = Rs2Inventory.count("astral rune");

        if (Rs2Inventory.hasRunePouch()) {
            natureAvailable += Rs2RunePouch.getQuantity(Runes.NATURE);
            astralAvailable += Rs2RunePouch.getQuantity(Runes.ASTRAL);
        }

        return natureAvailable >= natureRequired && astralAvailable >= astralRequired;
    }

    private boolean ensureEarthStaffEquipped() {
        if (Rs2Equipment.isWearing(EARTH_STAFF_NAMES)) {
            return true;
        }

        if (Rs2Inventory.wield(EARTH_STAFF_NAMES)) {
            sleepUntilOnClientThread(() -> Rs2Equipment.isWearing(EARTH_STAFF_NAMES));
            if (Rs2Equipment.isWearing(EARTH_STAFF_NAMES)) {
                return true;
            }
        }

        for (String staff : EARTH_STAFF_NAMES) {
            if (Rs2Bank.hasItem(staff)) {
                if (Rs2Bank.withdrawX(staff, 1)) {
                    sleepUntilOnClientThread(() -> Rs2Inventory.hasItem(staff));
                    if (Rs2Inventory.wield(staff)) {
                        sleepUntilOnClientThread(() -> Rs2Equipment.isWearing(EARTH_STAFF_NAMES));
                        return Rs2Equipment.isWearing(EARTH_STAFF_NAMES);
                    }
                    return false;
                }
            }
        }

        Microbot.showMessage("No earth staff available to equip.");
        shutdown();
        return false;
    }

    private boolean ensureRuneSupplies() {
        if (!Rs2Inventory.hasRunePouch()) {
            for (RunePouchType type : RunePouchType.values()) {
                if (Rs2Bank.hasItem(type.getItemId())) {
                    if (Rs2Bank.withdrawX(type.getItemId(), 1)) {
                        sleepUntilOnClientThread(() -> Rs2Inventory.hasItem(type.getItemId()));
                        break;
                    }
                }
            }

            if (!Rs2Inventory.hasRunePouch()) {
                return withdrawRunesToInventory();
            }
        }

        if (hasRunesForPlankMake()) {
            return true;
        }

        if (Rs2RunePouch.load(REQUIRED_RUNES)) {
            sleepUntilOnClientThread(this::hasRunesForPlankMake);
            if (hasRunesForPlankMake()) {
                return true;
            }
        }

        return withdrawRunesToInventory();
    }

    private boolean withdrawRunesToInventory() {
        if (!withdrawAllAndWait("astral rune")) {
            Microbot.showMessage("No astral runes available.");
            shutdown();
            return false;
        }

        if (!withdrawAllAndWait("nature rune")) {
            Microbot.showMessage("No nature runes available.");
            shutdown();
            return false;
        }

        if (!hasRunesForPlankMake()) {
            Microbot.showMessage("Not enough runes available to cast Plank Make.");
            shutdown();
            return false;
        }

        return true;
    }

    private boolean withdrawAllAndWait(String itemName) {
        if (Rs2Inventory.hasItem(itemName)) {
            return true;
        }

        if (!Rs2Bank.hasItem(itemName)) {
            return false;
        }

        if (!Rs2Bank.withdrawAll(itemName)) {
            return false;
        }

        sleepUntilOnClientThread(() -> Rs2Inventory.hasItem(itemName));
        return Rs2Inventory.hasItem(itemName);
    }

    private boolean ensureCoinsAvailable() {
        if (Rs2Inventory.hasItem("coins")) {
            return true;
        }

        if (!Rs2Bank.hasItem("coins")) {
            Microbot.showMessage("No coins available in bank.");
            shutdown();
            return false;
        }

        if (!Rs2Bank.withdrawAll("coins")) {
            return false;
        }

        sleepUntilOnClientThread(() -> Rs2Inventory.hasItem("coins"));
        return Rs2Inventory.hasItem("coins");
    }

    private boolean withdrawLogsForPlanking() {
        if (activeConfig == null) {
            Microbot.log("No active configuration available for withdrawing logs.");
            return false;
        }

        String logName = activeConfig.ITEM().getName();

        if (Rs2Inventory.hasItem(logName)) {
            return true;
        }

        if (!Rs2Bank.hasItem(logName)) {
            Microbot.showMessage("No more " + logName + " to plank.");
            shutdown();
            return false;
        }

        if (!Rs2Bank.withdrawAll(logName)) {
            return false;
        }

        sleepUntilOnClientThread(() -> Rs2Inventory.hasItem(logName));
        return Rs2Inventory.hasItem(logName);
    }

    private void waitUntilReady() {
        sleep(500); // Short sleep before retrying
        currentState = State.PLANKING;
    }

    private void calculateProfitAndDisplay(LunarPlankMakeConfig config) {
        double elapsedHours = (System.currentTimeMillis() - startTime) / 3600000.0;
        int plankPerHour = (int) (plankMade / elapsedHours);
        int totalProfit = profitPerPlank * (int) plankMade;
        int profitPerHour = profitPerPlank * plankPerHour;

        combinedMessage = config.ITEM().getFinished() + ": " +
                QuantityFormatter.quantityToRSDecimalStack((int) plankMade) + " (" +
                QuantityFormatter.quantityToRSDecimalStack(plankPerHour) + "/hr) | " +
                "Profit: " + QuantityFormatter.quantityToRSDecimalStack(totalProfit) + " (" +
                QuantityFormatter.quantityToRSDecimalStack(profitPerHour) + "/hr)";
    }

    private void addDelay() {
        if (useSetDelay) {
            sleep(setDelay);
        } else if (useRandomDelay) {
            sleep(Random.random(0, maxRandomDelay));
        }
    }

    @Override
    public void shutdown() {
        super.shutdown();
        plankMade = 0; // Reset the count of planks made
        combinedMessage = ""; // Reset the combined message
        currentState = State.PLANKING; // Reset the current state
        activeConfig = null;
    }
}
