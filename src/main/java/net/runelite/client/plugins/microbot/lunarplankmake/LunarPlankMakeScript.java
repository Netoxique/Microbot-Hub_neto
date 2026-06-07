package net.runelite.client.plugins.microbot.lunarplankmake;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.lunarplankmake.enums.Logs;
import net.runelite.client.plugins.microbot.shared.session.NetoBreakManager;
import net.runelite.client.plugins.microbot.shared.session.NetoRuntimeDisable;
import net.runelite.client.plugins.microbot.shared.session.NetoWorldHopManager;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2RunePouch;
import net.runelite.client.plugins.microbot.util.inventory.RunePouchType;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;
import net.runelite.client.util.QuantityFormatter;

import javax.inject.Inject;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class LunarPlankMakeScript extends Script {

    public static String combinedMessage = "";
    public static long plankMade = 0;
    private int profitPerPlank = 0;
    private long startTime;
    private LunarPlankMakeConfig activeConfig;
    private boolean useSetDelay;
    private int setDelay;
    private boolean useRandomDelay;
    private int maxRandomDelay;

    private boolean useVouchers;
    private boolean lazyMode;
    private boolean inventoryStarted = false;

    @Inject
    private NetoBreakManager breakManager;
    @Inject
    private NetoWorldHopManager worldHopManager;
    @Inject
    private NetoRuntimeDisable runtimeDisable;

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

        refreshProfitPerPlank(config);

        useSetDelay = config.useSetDelay();
        setDelay = config.setDelay();
        useRandomDelay = config.useRandomDelay();
        maxRandomDelay = config.maxRandomDelay();
        useVouchers = config.useSawmillVouchers();
        lazyMode = config.lazyMode();

        worldHopManager.configure(config, "Lunar Plank Make");
        breakManager.configure(config, "Lunar Plank Make");
        runtimeDisable.configure(config, "Lunar Plank Make");
        worldHopManager.reset();
        breakManager.reset();
        runtimeDisable.reset();

        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyCookingSetup();
        Rs2Antiban.setActivity(Activity.CASTING_PLANK_MAKE);
        Rs2AntibanSettings.simulateMistakes = false;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;
                if (runtimeDisable.updateRuntime(LunarPlankMakePlugin.class)) return;
                if (breakManager.updateBreakState()) return;
                if (!Microbot.isLoggedIn()) return;

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
        int logId = config.ITEM().getLogItemId();
        if (!Rs2Inventory.hasItem(logId)) {
            if (inventoryStarted) {
                worldHopManager.recordCompletedTrip();
                inventoryStarted = false;
                if (worldHopManager.tryHopIfDue(this::isRunning).isAttempted()) {
                    return;
                }
                if (breakManager.tryStartBreakAtSafePoint()) {
                    return;
                }
            }
            currentState = State.BANKING;
            return;
        }

        int plankId = config.ITEM().getPlankItemId();
        int initialPlankCount = Rs2Inventory.count(plankId);

        if (lazyMode) {
            int initialLogQuantity = Rs2Inventory.count(logId);
            Rs2Magic.cast(MagicAction.PLANK_MAKE);
            addDelay();
            Rs2Inventory.interact(logId);
            inventoryStarted = true;
            if (waitUntilNoLogsRemaining(config, initialLogQuantity)) {
                int plankMadeThisBatch = Rs2Inventory.count(plankId) - initialPlankCount;
                plankMade += plankMadeThisBatch;
                addDelay();
            } else {
                Microbot.log("Lazy mode: timed out waiting for logs to finish converting.");
                currentState = State.WAITING;
            }
            return;
        }

        Rs2Magic.cast(MagicAction.PLANK_MAKE);
        addDelay();
        Rs2Inventory.interact(logId);
        inventoryStarted = true;

        if (waitForInventoryChange(plankId, initialPlankCount)) {
            int plankMadeThisAction = Rs2Inventory.count(plankId) - initialPlankCount;
            plankMade += plankMadeThisAction;
            addDelay();
        } else {
            Microbot.log("Failed to detect plank creation.");
            currentState = State.WAITING;
        }
    }

    private boolean waitUntilNoLogsRemaining(LunarPlankMakeConfig config, int initialLogQuantity) {
        if (initialLogQuantity <= 0) {
            return true;
        }
        int logId = config.ITEM().getLogItemId();
        long start = System.currentTimeMillis();
        long timeoutMs = initialLogQuantity * 4000L;
        if (timeoutMs < 60000L) {
            timeoutMs = 60000L;
        }
        while (Rs2Inventory.hasItem(logId)) {
            if (System.currentTimeMillis() - start > timeoutMs) {
                return false;
            }
            sleep(50);
        }
        return true;
    }

    private boolean waitForInventoryChange(int plankItemId, int initialCount) {
        long start = System.currentTimeMillis();
        while (Rs2Inventory.count(plankItemId) == initialCount) {
            if (System.currentTimeMillis() - start > 3000) {
                return false;
            }
            sleep(10);
        }
        return true;
    }

    private void bank(LunarPlankMakeConfig config) {
        if (!Rs2Bank.openBank()) return;

        int plankId = config.ITEM().getPlankItemId();
        int logId = config.ITEM().getLogItemId();

        Rs2Bank.depositAll(plankId);
        sleepUntilOnClientThread(() -> !Rs2Inventory.hasItem(plankId));

        boolean hasVoucher = false;

        if (useVouchers) {
            if (Rs2Inventory.contains("Sawmill voucher")) {
                hasVoucher = true;
            } else if (Rs2Bank.hasItem("Sawmill voucher")) {
                Rs2Bank.withdrawAll("Sawmill voucher");
                sleepUntilOnClientThread(() -> Rs2Inventory.contains("Sawmill voucher"));
                hasVoucher = true;
            }
        }

        int logsToWithdraw = hasVoucher ? 12 : 28;

        int logsInInventory = Rs2Inventory.count(logId);
        if (logsInInventory >= logsToWithdraw) {
            Rs2Bank.closeBank();
            currentState = State.PLANKING;
        inventoryStarted = false;
            calculateProfitAndDisplay(config);
            return;
        }

        if (!Rs2Bank.hasItem(logId)) {
            Microbot.showMessage("No more " + config.ITEM().getName() + " to plank.");
            shutdown();
            return;
        }

        int need = logsToWithdraw - logsInInventory;
        Rs2Bank.withdrawX(logId, need);
        sleepUntilOnClientThread(() -> Rs2Inventory.count(logId) >= logsToWithdraw);

        Rs2Bank.closeBank();
        currentState = State.PLANKING;
        inventoryStarted = false;
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


    private void prep() {
        if (!Rs2Bank.openBank()) {
            return;
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
        inventoryStarted = false;
    }

    private void waitUntilReady() {
        sleep(500);
        currentState = State.PLANKING;
        inventoryStarted = false;
    }

    private void refreshProfitPerPlank(LunarPlankMakeConfig config) {
        Logs item = config.ITEM();
        int plankPrice = gePrice(item.getFinished());
        int logPrice = gePrice(item.getName());
        int astral = gePrice("Astral rune");
        int nature = gePrice("Nature rune");
        int runeGp = 2 * astral + nature;
        if (config.includeEarthRuneCost()) {
            int earth = gePrice("Earth rune");
            runeGp += 15 * earth;
        }
        int voucherPerPlank = 0;
        if (config.useSawmillVouchers()) {
            int voucherPrice = gePrice("Sawmill voucher");
            voucherPerPlank = voucherPrice / 24;
        }
        int planksPerLog = config.useSawmillVouchers() ? 2 : 1;
        int logCostPerPlank = logPrice / planksPerLog;
        int coinFeePerPlank = item.getPlankMakeCoinFee() / planksPerLog;
        int runeCostPerPlank = runeGp / planksPerLog;
        profitPerPlank = plankPrice - logCostPerPlank - coinFeePerPlank - runeCostPerPlank - voucherPerPlank;
    }

    private static int gePrice(String itemName) {
        try {
            return Microbot.getItemManager().search(itemName).get(0).getPrice();
        } catch (Exception e) {
            return 0;
        }
    }

    private void calculateProfitAndDisplay(LunarPlankMakeConfig config) {
        refreshProfitPerPlank(config);
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
            sleep(Rs2Random.between(0, maxRandomDelay));
        }
    }

    @Override
    public void shutdown() {
        super.shutdown();
        plankMade = 0;
        combinedMessage = "";
        currentState = State.PLANKING;
        inventoryStarted = false;
    }
}
