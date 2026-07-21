package net.runelite.client.plugins.microbot.netogeseller;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.MenuAction;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeSlots;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spellbook;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.api.ItemID;
import net.runelite.api.coords.WorldPoint;
import java.awt.event.KeyEvent;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.api.events.GrandExchangeOfferChanged;

import javax.inject.Inject;
import java.awt.Rectangle;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
public class NetoGeSellerScript extends Script {

    private enum State {
        TELEPORTING,
        BANKING,
        SELLING,
        WAITING_FOR_SELL
    }

    @Inject
    private NetoGeSellerConfig config;

    @Inject
    private NetoGeSellerPlugin plugin;

    private State currentState = State.BANKING;
    private boolean firstTimeOpeningBank = true;
    public enum SellMode {
        SELL_ALL,
        SELL_EXCESS,
        SELL_ALL_IF_OVER
    }

    public static class SellItemConfig {
        private final String name;
        private final Integer itemId;
        private final int threshold;
        private final SellMode mode;
        private final boolean sellHighest;

        public SellItemConfig(String name, Integer itemId, int threshold, SellMode mode, boolean sellHighest) {
            this.name = name;
            this.itemId = itemId;
            this.threshold = threshold;
            this.mode = mode;
            this.sellHighest = sellHighest;
        }

        public String getName() { return name; }
        public Integer getItemId() { return itemId; }
        public boolean isItemIdSelector() { return itemId != null; }
        public int getThreshold() { return threshold; }
        public SellMode getMode() { return mode; }
        public boolean isSellHighest() { return sellHighest; }
    }

    private Map<String, SellItemConfig> itemsToSellMap = new LinkedHashMap<>();
    private boolean hasUnsoldBankItems = false;
    private volatile int totalProfit = 0;

    private static class SlotState {
        int itemId;
        int totalQuantity;
        int price;
        int quantitySold;
        int spent;
        GrandExchangeOfferState state;
        long createdAtMillis;

        SlotState(int itemId, int totalQuantity, int price, int quantitySold, int spent, GrandExchangeOfferState state, long createdAtMillis) {
            this.itemId = itemId;
            this.totalQuantity = totalQuantity;
            this.price = price;
            this.quantitySold = quantitySold;
            this.spent = spent;
            this.state = state;
            this.createdAtMillis = createdAtMillis;
        }
    }

    private static class RepostCandidate {
        final int slotIndex;
        final GrandExchangeSlots slot;
        final int itemId;
        final String itemName;
        final GrandExchangeOfferState state;

        RepostCandidate(int slotIndex, GrandExchangeSlots slot, int itemId, String itemName, GrandExchangeOfferState state) {
            this.slotIndex = slotIndex;
            this.slot = slot;
            this.itemId = itemId;
            this.itemName = itemName;
            this.state = state;
        }
    }

    private final SlotState[] slotStates = new SlotState[8];
    private static final int GE_COLLECT_BUTTON = 30474246;

    public int getTotalProfit() {
        return totalProfit;
    }

    public static String formatProfit(int profit) {
        if (profit >= 1_000_000) {
            return new java.text.DecimalFormat("#.##").format(profit / 1_000_000.0) + "M GP";
        } else if (profit >= 10_000) {
            return new java.text.DecimalFormat("#.##").format(profit / 1_000.0) + "K GP";
        } else {
            return String.format("%,d GP", profit);
        }
    }

    public boolean run() {
        this.currentState = State.TELEPORTING;
        this.firstTimeOpeningBank = true;
        this.itemsToSellMap = parseItemsToSell(config.itemsToSell());
        this.hasUnsoldBankItems = true;
        this.totalProfit = 0;
        initializeSlotStates();

        Microbot.enableAutoRunOn = false;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                switch (currentState) {
                    case TELEPORTING:
                        handleTeleporting();
                        break;
                    case BANKING:
                        handleBanking();
                        break;
                    case SELLING:
                        handleSelling();
                        break;
                    case WAITING_FOR_SELL:
                        handleWaitingForSell();
                        break;
                }

            } catch (Exception ex) {
                Microbot.log("Error in Neto GE Seller: " + ex.getMessage());
                log.error("Neto GE Seller error: ", ex);
            }
        }, 0, 200, TimeUnit.MILLISECONDS);

        return true;
    }

    Map<String, SellItemConfig> parseItemsToSell(String configStr) {
        Map<String, SellItemConfig> map = new LinkedHashMap<>();
        if (configStr == null || configStr.trim().isEmpty()) {
            return map;
        }
        String[] items = configStr.split(",");
        for (String item : items) {
            item = item.trim();
            if (item.isEmpty()) continue;

            String name = item;
            int threshold = 0;
            SellMode mode = SellMode.SELL_ALL;
            boolean sellHighest = false;

            if (item.startsWith("+")) {
                sellHighest = true;
                item = item.substring(1).trim();
                name = item;
            }

            String delimiter = null;
            if (item.contains(":")) {
                delimiter = ":";
            } else if (item.contains(";")) {
                delimiter = ";";
            }

            if (delimiter != null) {
                String[] parts = item.split(delimiter);
                name = parts[0].trim();
                if (parts.length > 1) {
                    String valStr = parts[1].trim();
                    if (valStr.startsWith(">")) {
                        mode = SellMode.SELL_ALL_IF_OVER;
                        valStr = valStr.substring(1).trim();
                    } else {
                        mode = SellMode.SELL_EXCESS;
                    }
                    try {
                        threshold = Integer.parseInt(valStr);
                    } catch (NumberFormatException e) {
                        Microbot.log("Invalid quantity for item: " + name);
                    }
                }
            }

            if (!name.isEmpty()) {
                Integer itemId = null;
                if (name.matches("\\d+")) {
                    try {
                        itemId = Integer.valueOf(name);
                        if (itemId <= 0) {
                            Microbot.log("Invalid item ID in Neto GE Seller list: " + name);
                            continue;
                        }
                    } catch (NumberFormatException e) {
                        Microbot.log("Invalid item ID in Neto GE Seller list: " + name);
                        continue;
                    }
                }
                String key = itemId == null ? "name:" + name.toLowerCase() : "id:" + itemId;
                map.put(key, new SellItemConfig(name, itemId, threshold, mode, sellHighest));
            }
        }
        return map;
    }

    private void handleBanking() {
        Microbot.status = "Opening Bank";
        if (!Rs2Bank.openBank()) {
            return;
        }
        sleepUntil(Rs2Bank::isOpen);

        if (firstTimeOpeningBank) {
            Microbot.status = "Depositing inventory items";
            Rs2Bank.depositAll();
            sleepUntil(Rs2Inventory::isEmpty);
            firstTimeOpeningBank = false;
        }

        Rs2Widget.clickWidget(InterfaceID.Bankmain.NOTE);
        sleep(600, 1000);
        hasUnsoldBankItems = false;

        for (SellItemConfig itemConfig : itemsToSellMap.values()) {

            int bankQty = getBankQuantity(itemConfig);
            int invQty = getInventoryQuantity(itemConfig);
            int totalQty = bankQty + invQty;
            
            int excess = 0;
            if (itemConfig.getMode() == SellMode.SELL_ALL) {
                excess = totalQty;
            } else if (itemConfig.getMode() == SellMode.SELL_EXCESS) {
                excess = totalQty - itemConfig.getThreshold();
            } else if (itemConfig.getMode() == SellMode.SELL_ALL_IF_OVER) {
                if (totalQty >= itemConfig.getThreshold()) {
                    excess = totalQty;
                } else {
                    excess = 0;
                }
            }

            if (excess > 0) {
                if (Rs2Inventory.isFull()) {
                    Microbot.status = "Inventory full of items to sell";
                    hasUnsoldBankItems = true;
                    break;
                }

                int toWithdraw = Math.min(excess - invQty, bankQty);
                if (toWithdraw > 0) {
                    Rs2ItemModel bankItem = getMatchingBankItem(itemConfig);
                    if (bankItem != null) {
                        Microbot.status = "Withdrawing " + toWithdraw + " " + bankItem.getName();
                        if (toWithdraw == bankQty) {
                            Rs2Bank.withdrawAll(bankItem.getId());
                        } else {
                            Rs2Bank.withdrawX(bankItem.getId(), toWithdraw);
                        }
                        sleep(600, 1000);
                    }
                }
            }
        }

        // Check if there are still more items to withdraw later
        for (SellItemConfig itemConfig : itemsToSellMap.values()) {
            int bankQty = getBankQuantity(itemConfig);
            int invQty = getInventoryQuantity(itemConfig);
            int totalQty = bankQty + invQty;
            
            int excess = 0;
            if (itemConfig.getMode() == SellMode.SELL_ALL) {
                excess = totalQty;
            } else if (itemConfig.getMode() == SellMode.SELL_EXCESS) {
                excess = totalQty - itemConfig.getThreshold();
            } else if (itemConfig.getMode() == SellMode.SELL_ALL_IF_OVER) {
                if (totalQty >= itemConfig.getThreshold()) {
                    excess = totalQty;
                } else {
                    excess = 0;
                }
            }
            
            if (excess > invQty) {
                hasUnsoldBankItems = true;
                break;
            }
        }

        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen());
        currentState = State.SELLING;
    }

    private void handleSelling() {
        log.info("Neto GE Seller SELLING state started. hasUnsoldBankItems={}, inventorySize={}", hasUnsoldBankItems, Rs2Inventory.size());
        Microbot.status = "Opening Grand Exchange";
        if (!Rs2GrandExchange.openExchange()) {
            log.warn("Failed to open Grand Exchange from SELLING state.");
            return;
        }
        log.info("Grand Exchange open interaction succeeded. Waiting for GE interface.");
        boolean geOpen = sleepUntil(Rs2GrandExchange::isOpen);
        log.info("Grand Exchange open wait result={}, isOpen={}", geOpen, Rs2GrandExchange.isOpen());
        if (handleTimedOutOffer()) {
            return;
        }

        boolean foundItemToSell = false;
        for (Rs2ItemModel item : Rs2Inventory.all(Rs2ItemModel::isTradeable)) {
            SellItemConfig itemConfig = findMatchingConfig(item);
            log.info("Considering tradeable inventory item '{}' (id={}), quantity={}, configuredForSale={}",
                    item.getName(), item.getId(), item.getQuantity(), itemConfig != null);
            if (itemConfig != null) {
                int quantityToSell = item.getQuantity();
                if (quantityToSell <= 0) {
                    log.warn("Skipping configured item '{}' because quantity is {}.", item.getName(), quantityToSell);
                    continue;
                }

                foundItemToSell = true;
                int availableSlots = Rs2GrandExchange.getAvailableSlotsCount();
                log.info("Matched configured sell item '{}'. quantityToSell={}, availableSlots={}", item.getName(), quantityToSell, availableSlots);

                if (availableSlots > 0) {
                    Microbot.status = "Selling " + quantityToSell + " " + item.getName();
                    String sellHotkey = itemConfig.isSellHighest() ? config.highValueHotkey() : config.hotkey();
                    log.info("Attempting to sell '{}' with hotkey '{}'.", item.getName(), sellHotkey);
                    boolean sold = sellItemWithHotkey(item, quantityToSell, sellHotkey);
                    log.info("sellItemWithHotkey result for '{}': {}", item.getName(), sold);
//                    if (sold) {
//                        sleep(1000, 1500);
//                    }
                } else {
                    if (Rs2GrandExchange.hasSoldOffer()) {
                        Microbot.status = "Collecting sold offers to Bank";
                        log.info("No available GE slots, but sold offers exist. Collecting to bank.");
                        Rs2GrandExchange.collectAllToBank();
                        boolean soldOffersCleared = sleepUntil(() -> !Rs2GrandExchange.hasSoldOffer(), 5000);
                        log.info("Sold-offer collection wait result={}, hasSoldOffer={}", soldOffersCleared, Rs2GrandExchange.hasSoldOffer());
                        sleep(800, 1200);
                    } else {
                        if (Rs2Inventory.size() < 28 && hasUnsoldBankItems) {
                            Microbot.status = "Slots full. Returning to bank to withdraw more items";
                            log.info("GE slots full, no sold offers, inventory has space, and bank still has unsold items. Returning to BANKING.");
                            Rs2GrandExchange.closeExchange();
                            boolean geClosed = sleepUntil(() -> !Rs2GrandExchange.isOpen());
                            log.info("GE close wait result={}, isOpen={}", geClosed, Rs2GrandExchange.isOpen());
                            currentState = State.BANKING;
                            return;
                        } else {
                            Microbot.status = "Slots full. Waiting for sales...";
                            log.info("GE slots full and no sold offers. Waiting for sales. inventorySize={}, hasUnsoldBankItems={}", Rs2Inventory.size(), hasUnsoldBankItems);
                            sleep(2000);
                        }
                    }
                }
                break;
            }
        }

        if (!foundItemToSell) {
            if (hasUnsoldBankItems) {
                Microbot.status = "No items in inventory. Returning to bank";
                log.info("No configured sell item found in inventory, but bank has unsold items. Returning to BANKING.");
                Rs2GrandExchange.closeExchange();
                boolean geClosed = sleepUntil(() -> !Rs2GrandExchange.isOpen());
                log.info("GE close wait result={}, isOpen={}", geClosed, Rs2GrandExchange.isOpen());
                currentState = State.BANKING;
            } else {
                log.info("No configured sell item found in inventory and no unsold bank items remain. Transitioning to WAITING_FOR_SELL.");
                currentState = State.WAITING_FOR_SELL;
            }
        }
    }

    private void handleWaitingForSell() {
        Microbot.status = "Waiting for all offers to sell...";
        log.info("handleWaitingForSell: Checking for active/completed offers of configured items.");
        if (!Rs2GrandExchange.openExchange()) {
            log.warn("handleWaitingForSell: Failed to open Grand Exchange.");
            return;
        }
        sleepUntil(Rs2GrandExchange::isOpen);

        if (handleTimedOutOffer()) {
            return;
        }

        if (Rs2GrandExchange.hasSoldOffer()) {
            log.info("handleWaitingForSell: Sold offer(s) detected. Collecting all to bank.");
            Rs2GrandExchange.collectAllToBank();
            sleepUntil(() -> !Rs2GrandExchange.hasSoldOffer(), 5000);
            sleep(800, 1200);
        }

        if (!hasActiveOffers()) {
            Microbot.status = "All offers completed. Stopping plugin.";
            log.info("handleWaitingForSell: No active offers for configured items. Closing Grand Exchange and stopping plugin.");
            Rs2GrandExchange.closeExchange();
            sleepUntil(() -> !Rs2GrandExchange.isOpen());
            Microbot.stopPlugin(this.plugin);
        }
    }

    private boolean handleTimedOutOffer() {
        RepostCandidate candidate = findTimedOutOffer();
        if (candidate == null) {
            return false;
        }

        if (candidate.state == GrandExchangeOfferState.SELLING) {
            Microbot.status = "Reposting timed out " + candidate.itemName;
            log.info("Offer for '{}' in slot {} timed out after {} seconds. Aborting offer.",
                    candidate.itemName, candidate.slot, getOfferTimeoutSeconds());

            if (!abortOfferSlot(candidate.slot)) {
                log.warn("Failed to abort timed out offer '{}' in slot {}.", candidate.itemName, candidate.slot);
                return false;
            }

            boolean cancelled = sleepUntil(() -> isSlotState(candidate.slotIndex, candidate.itemId, GrandExchangeOfferState.CANCELLED_SELL), 5000);
            log.info("Timed out offer abort wait result={}, item='{}', slot={}", cancelled, candidate.itemName, candidate.slot);
            if (!cancelled) {
                return true;
            }
        }

        Microbot.status = "Collecting timed out " + candidate.itemName;
        log.info("Collecting timed out offer '{}' to inventory from the overview collect button.", candidate.itemName);
        boolean collected = collectToInventoryFromOverview(candidate.slotIndex);
        log.info("Timed out offer collect result={}, item='{}', slot={}", collected, candidate.itemName, candidate.slot);
        if (!collected) {
            return true;
        }

        slotStates[candidate.slotIndex] = null;
        currentState = State.SELLING;
        sleep(600, 1000);
        return true;
    }

    private RepostCandidate findTimedOutOffer() {
        long now = System.currentTimeMillis();
        long timeoutMillis = getOfferTimeoutSeconds() * 1000L;
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            if (offers == null) return null;

            int maxSlots = Math.min(offers.length, slotStates.length);
            for (int i = 0; i < maxSlots; i++) {
                GrandExchangeOffer offer = offers[i];
                if (offer == null || offer.getItemId() <= 0) {
                    continue;
                }

                GrandExchangeOfferState state = offer.getState();
                if (state != GrandExchangeOfferState.SELLING && state != GrandExchangeOfferState.CANCELLED_SELL) {
                    continue;
                }

                String itemName = Microbot.getItemManager().getItemComposition(offer.getItemId()).getName();
                if (findMatchingConfig(offer.getItemId(), itemName) == null) {
                    continue;
                }

                SlotState tracked = slotStates[i];
                if (tracked == null || isDifferentOffer(tracked, offer)) {
                    slotStates[i] = createSlotState(offer, now);
                    continue;
                }

                applyOfferDelta(tracked, offer);
                if (now - tracked.createdAtMillis >= timeoutMillis) {
                    GrandExchangeSlots slot = GrandExchangeSlots.values()[i];
                    return new RepostCandidate(i, slot, offer.getItemId(), itemName, state);
                }
            }

            return null;
        }).orElse(null);
    }

    private boolean abortOfferSlot(GrandExchangeSlots slot) {
        if (slot == null) {
            return false;
        }
        if (Rs2GrandExchange.isOfferScreenOpen()) {
            Rs2GrandExchange.backToOverview();
        }
        Widget parent = getOfferSlotWidget(slot);
        if (parent == null) {
            return false;
        }

        NewMenuEntry menuEntry = new NewMenuEntry()
                .option("Abort offer")
                .target("")
                .identifier(2)
                .type(MenuAction.CC_OP)
                .param0(2)
                .param1(parent.getId())
                .itemId(-1)
                .forceLeftClick(false);
        Rectangle bounds = parent.getBounds() != null && Rs2UiHelper.isRectangleWithinCanvas(parent.getBounds())
                ? parent.getBounds()
                : Rs2UiHelper.getDefaultRectangle();
        Microbot.doInvoke(menuEntry, bounds);
        sleep(250, 750);
        return true;
    }

    private boolean collectToInventoryFromOverview(int slotIndex) {
        if (Rs2GrandExchange.isOfferScreenOpen()) {
            Rs2GrandExchange.backToOverview();
        }
        if (!Rs2GrandExchange.isOpen()) {
            Rs2GrandExchange.openExchange();
            sleepUntil(Rs2GrandExchange::isOpen, 5000);
        }

        Widget collectButton = Rs2Widget.getWidget(GE_COLLECT_BUTTON);
        if (collectButton == null) {
            log.warn("Grand Exchange collect button was not found.");
            return false;
        }

        NewMenuEntry entry = new NewMenuEntry()
                .option("Collect to inventory")
                .target("")
                .identifier(1)
                .type(MenuAction.CC_OP)
                .param0(0)
                .param1(collectButton.getId())
                .itemId(-1)
                .forceLeftClick(false);
        Rectangle bounds = collectButton.getBounds() != null && Rs2UiHelper.isRectangleWithinCanvas(collectButton.getBounds())
                ? collectButton.getBounds()
                : Rs2UiHelper.getDefaultRectangle();
        Microbot.doInvoke(entry, bounds);
        sleep(600, 1000);
        return sleepUntil(() -> isSlotEmpty(slotIndex), 5000);
    }

    private Widget getOfferSlotWidget(GrandExchangeSlots slot) {
        try {
            java.lang.reflect.Method m = Class.forName("net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeWidget")
                    .getDeclaredMethod("getSlot", GrandExchangeSlots.class);
            m.setAccessible(true);
            return (Widget) m.invoke(null, slot);
        } catch (Exception e) {
            log.error("Error retrieving Grand Exchange slot widget: ", e);
            return null;
        }
    }

    private boolean isSlotState(int slotIndex, int itemId, GrandExchangeOfferState expectedState) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            if (offers == null || slotIndex < 0 || slotIndex >= offers.length) {
                return false;
            }
            GrandExchangeOffer offer = offers[slotIndex];
            return offer != null && offer.getItemId() == itemId && offer.getState() == expectedState;
        }).orElse(false);
    }

    private boolean isSlotEmpty(int slotIndex) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            if (offers == null || slotIndex < 0 || slotIndex >= offers.length) {
                return false;
            }
            GrandExchangeOffer offer = offers[slotIndex];
            return offer == null || offer.getState() == GrandExchangeOfferState.EMPTY;
        }).orElse(false);
    }

    private int getOfferTimeoutSeconds() {
        return Math.max(1, config.offerTimeoutSeconds());
    }

    private SlotState createSlotState(GrandExchangeOffer offer, long createdAtMillis) {
        return new SlotState(
                offer.getItemId(),
                offer.getTotalQuantity(),
                offer.getPrice(),
                offer.getQuantitySold(),
                offer.getSpent(),
                offer.getState(),
                createdAtMillis
        );
    }

    private boolean isDifferentOffer(SlotState tracked, GrandExchangeOffer offer) {
        return tracked.itemId != offer.getItemId()
                || tracked.totalQuantity != offer.getTotalQuantity()
                || tracked.price != offer.getPrice();
    }

    private void applyOfferDelta(SlotState tracked, GrandExchangeOffer offer) {
        int newQuantitySold = offer.getQuantitySold();
        int newSpent = offer.getSpent();

        int deltaQuantity = newQuantitySold - tracked.quantitySold;
        int deltaSpent = newSpent - tracked.spent;

        if (deltaSpent > 0 || deltaQuantity > 0) {
            if (deltaSpent > 0) {
                totalProfit += deltaSpent;
            }
            tracked.quantitySold = newQuantitySold;
            tracked.spent = newSpent;
        }
        tracked.state = offer.getState();
    }

    private boolean hasActiveOffers() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            if (offers == null) return false;
            for (GrandExchangeOffer offer : offers) {
                GrandExchangeOfferState state = offer.getState();
                if (state == GrandExchangeOfferState.SELLING || state == GrandExchangeOfferState.BUYING) {
                    int itemId = offer.getItemId();
                    if (itemId <= 0) continue;
                    String itemName = Microbot.getItemManager().getItemComposition(itemId).getName();
                    if (findMatchingConfig(itemId, itemName) != null) {
                        log.info("Active offer found for configured item: '{}' (State: {})", itemName, state);
                        return true;
                    }
                }
            }
            return false;
        }).orElse(false);
    }

    private boolean sellItemWithHotkey(Rs2ItemModel item, int quantity, String hotkey) {
        String itemName = item.getName();
        log.info("sellItemWithHotkey started. itemName='{}', quantity={}, hotkey='{}'", itemName, quantity, hotkey);
        if (!Rs2Inventory.hasItem(item.getId())) {
            log.warn("sellItemWithHotkey returning false: inventory does not contain '{}' (id={}).", itemName, item.getId());
            return false;
        }
        int availableSlots = Rs2GrandExchange.getAvailableSlotsCount();
        if (availableSlots == 0) {
            log.warn("sellItemWithHotkey returning false: no GE slots available.");
            return false;
        }
        if (quantity <= 0) {
            log.warn("sellItemWithHotkey returning false: invalid quantity {} for '{}'.", quantity, itemName);
            return false;
        }

        log.info("Interacting with '{}' using Offer action.", itemName);
        boolean offerInteraction = Rs2Inventory.interact(item.getId(), "Offer");
        log.info("Offer interaction result for '{}': {}", itemName, offerInteraction);
        if (!offerInteraction) {
            log.warn("sellItemWithHotkey returning false: Offer interaction failed for '{}'.", itemName);
            return false;
        }

        log.info("Waiting for GE offer screen to open for '{}'.", itemName);
        boolean offerScreenOpened = sleepUntil(() -> Rs2Widget.hasWidget("Enter Price"), 5000);
        log.info("Offer screen wait result={}, hasEnterPrice={}", offerScreenOpened, Rs2Widget.hasWidget("Enter Price"));
        if (!offerScreenOpened) {
            log.warn("sellItemWithHotkey returning false: offer screen did not open (Enter Price button not found) for '{}'.", itemName);
            return false;
        }
        sleep(300, 500);

        int currentOfferQty = Microbot.getVarbitValue(VarbitID.GE_NEWOFFER_QUANTITY);
        log.info("Current GE offer quantity varbit={}, targetQuantity={}", currentOfferQty, quantity);
        if (quantity != currentOfferQty) {
            log.info("GE offer quantity differs from target. Setting quantity to {}.", quantity);
            if (!setQuantity(quantity)) {
                log.warn("sellItemWithHotkey returning false: failed to set quantity for '{}'.", itemName);
                return false;
            }
        }

        Widget pricePerItemButtonX = Rs2Widget.findWidget("Enter Price");
        if (pricePerItemButtonX == null) {
            pricePerItemButtonX = Rs2Widget.findWidget("Enter price");
        }
        if (pricePerItemButtonX == null) {
            log.warn("sellItemWithHotkey returning false: price-per-item X button (Enter Price) was not found.");
            return false;
        }

        log.info("Clicking price-per-item X button (Enter Price) for '{}'.", itemName);
        Microbot.getMouse().click(pricePerItemButtonX.getBounds());
        boolean priceChatboxOpened = sleepUntil(() -> Rs2Widget.getWidget(InterfaceID.Chatbox.MES_TEXT2) != null, 3000);
        log.info("Price chatbox prompt wait result={}", priceChatboxOpened);
        if (!priceChatboxOpened) {
            log.warn("sellItemWithHotkey returning false: price chatbox prompt did not appear.");
            return false;
        }
        sleep(600, 1000);

        if (hotkey == null || hotkey.isEmpty()) {
            hotkey = "n";
        }

        log.info("Pressing insta-sell hotkey sequence '{}'.", hotkey);
        for (char c : hotkey.toCharArray()) {
            int vk = getVirtualKeyCode(c);
            if (vk != KeyEvent.VK_UNDEFINED) {
                Rs2Keyboard.keyPress(vk);
            } else {
                Rs2Keyboard.keyPress(c);
            }
            sleep(100, 200);
        }
        sleepGaussian(250, 25);

        log.info("Submitting insta-sell hotkey value with Enter.");
        Rs2Keyboard.enter();
        sleepGaussian(250, 25);

        Widget confirmButton = getConfirmButton();
        if (confirmButton == null) {
            log.warn("sellItemWithHotkey returning false: confirm button was not found.");
            return false;
        }

        log.info("Clicking GE confirm button for '{}'.", itemName);
        Rs2Widget.clickWidget(confirmButton);

        boolean offerScreenClosed = sleepUntil(() -> !Rs2Widget.hasWidget("Enter Price"), 5000);
        log.info("sellItemWithHotkey final result for '{}': offerScreenClosed={}, hasEnterPrice={}", itemName, offerScreenClosed, Rs2Widget.hasWidget("Enter Price"));
        return offerScreenClosed;
    }

    private int getVirtualKeyCode(char c) {
        if (c >= 'a' && c <= 'z') {
            return KeyEvent.VK_A + (c - 'a');
        }
        if (c >= 'A' && c <= 'Z') {
            return KeyEvent.VK_A + (c - 'A');
        }
        if (c >= '0' && c <= '9') {
            return KeyEvent.VK_0 + (c - '0');
        }
        switch (c) {
            case ' ':
                return KeyEvent.VK_SPACE;
            case '\n':
            case '\r':
                return KeyEvent.VK_ENTER;
            case '\t':
                return KeyEvent.VK_TAB;
            case '\b':
                return KeyEvent.VK_BACK_SPACE;
            default:
                return KeyEvent.VK_UNDEFINED;
        }
    }

    private boolean setQuantity(int quantity) {
        int tries = 0;
        int targetQuantity = quantity;
        while (targetQuantity != Microbot.getVarbitValue(VarbitID.GE_NEWOFFER_QUANTITY)) {
            int currentQuantity = Microbot.getVarbitValue(VarbitID.GE_NEWOFFER_QUANTITY);
            log.info("setQuantity attempt {}. currentQuantity={}, targetQuantity={}", tries + 1, currentQuantity, targetQuantity);
            Widget quantityButtonX = getQuantityButton_X();
            if (quantityButtonX == null) {
                log.warn("setQuantity attempt {} could not find quantity X button.", tries + 1);
                tries++;
                continue;
            }
            log.info("Clicking quantity X button.");
            Microbot.getMouse().click(quantityButtonX.getBounds());
            boolean quantityChatboxOpened = sleepUntil(() -> Rs2Widget.getWidget(InterfaceID.Chatbox.MES_TEXT2) != null, 3000);
            log.info("Quantity chatbox prompt wait result={}", quantityChatboxOpened);
            sleep(600, 1000);
            log.info("Setting GE offer quantity chatbox value to {}.", targetQuantity);
            Rs2GrandExchange.setChatboxValue(targetQuantity);
            sleep(500, 750);
            Rs2Keyboard.enter();
            sleep(1000);
            tries++;
            if (tries > 3) {
                log.error("Failed to set quantity after 3 tries, breaking out.");
                Rs2GrandExchange.closeExchange();
                break;
            }
        }
        boolean success = tries <= 3;
        log.info("setQuantity completed. success={}, tries={}, finalQuantity={}, targetQuantity={}", success, tries, Microbot.getVarbitValue(VarbitID.GE_NEWOFFER_QUANTITY), targetQuantity);
        return success;
    }

    private Widget getPricePerItemButton_X() {
        try {
            java.lang.reflect.Method m = Class.forName("net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeWidget")
                    .getDeclaredMethod("getPricePerItemButton_X");
            m.setAccessible(true);
            return (Widget) m.invoke(null);
        } catch (Exception e) {
            log.error("Error retrieving price button X: ", e);
            return null;
        }
    }

    private Widget getQuantityButton_X() {
        try {
            java.lang.reflect.Method m = Class.forName("net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeWidget")
                    .getDeclaredMethod("getQuantityButton_X");
            m.setAccessible(true);
            return (Widget) m.invoke(null);
        } catch (Exception e) {
            log.error("Error retrieving quantity button X: ", e);
            return null;
        }
    }

    private Widget getConfirmButton() {
        try {
            java.lang.reflect.Method m = Class.forName("net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeWidget")
                    .getDeclaredMethod("getConfirm");
            m.setAccessible(true);
            return (Widget) m.invoke(null);
        } catch (Exception e) {
            log.error("Error retrieving confirm button: ", e);
            return null;
        }
    }

    private int getBankQuantity(SellItemConfig itemConfig) {
        return Rs2Bank.bankItems().stream()
                .filter(item -> matches(itemConfig, item))
                .mapToInt(Rs2ItemModel::getQuantity)
                .sum();
    }

    private int getInventoryQuantity(SellItemConfig itemConfig) {
        return Rs2Inventory.all().stream()
                .filter(item -> matches(itemConfig, item))
                .mapToInt(Rs2ItemModel::getQuantity)
                .sum();
    }

    private Rs2ItemModel getMatchingBankItem(SellItemConfig itemConfig) {
        return Rs2Bank.bankItems().stream()
                .filter(item -> matches(itemConfig, item))
                .findFirst()
                .orElse(null);
    }

    private SellItemConfig findMatchingConfig(Rs2ItemModel item) {
        return itemsToSellMap.values().stream()
                .filter(config -> matches(config, item))
                .findFirst()
                .orElse(null);
    }

    private SellItemConfig findMatchingConfig(int itemId, String itemName) {
        return itemsToSellMap.values().stream()
                .filter(config -> matches(config, itemId, itemName))
                .findFirst()
                .orElse(null);
    }

    private boolean matches(SellItemConfig config, Rs2ItemModel item) {
        return matches(config, item.getId(), item.getName());
    }

    private boolean matches(SellItemConfig config, int actualItemId, String actualItemName) {
        if (!config.isItemIdSelector()) {
            return actualItemName != null && actualItemName.equalsIgnoreCase(config.getName());
        }

        int linkedNoteId = getLinkedNoteId(actualItemId);
        return matchesConfiguredId(config.getItemId(), actualItemId, linkedNoteId);
    }

    static boolean matchesConfiguredId(int configuredItemId, int actualItemId, int actualLinkedNoteId) {
        return configuredItemId == actualItemId || configuredItemId == actualLinkedNoteId;
    }

    private int getLinkedNoteId(int itemId) {
        try {
            return Microbot.getItemManager().getItemComposition(itemId).getLinkedNoteId();
        } catch (RuntimeException e) {
            log.debug("Unable to resolve linked note ID for item {}", itemId, e);
            return -1;
        }
    }

    private void initializeSlotStates() {
        Microbot.getClientThread().runOnClientThreadOptional(() -> {
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            long now = System.currentTimeMillis();
            for (int i = 0; i < 8; i++) {
                if (offers != null && i < offers.length) {
                    GrandExchangeOffer offer = offers[i];
                    if (offer != null && offer.getState() != GrandExchangeOfferState.EMPTY) {
                        slotStates[i] = createSlotState(offer, now);
                    } else {
                        slotStates[i] = null;
                    }
                } else {
                    slotStates[i] = null;
                }
            }
            return null;
        });
    }

    @Subscribe
    public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event) {
        GrandExchangeOffer offer = event.getOffer();
        int slot = event.getSlot();
        if (slot < 0 || slot >= 8) return;

        if (offer.getState() == GrandExchangeOfferState.EMPTY) {
            slotStates[slot] = null;
            return;
        }

        boolean isSelling = offer.getState() == GrandExchangeOfferState.SELLING || 
                            offer.getState() == GrandExchangeOfferState.SOLD || 
                            offer.getState() == GrandExchangeOfferState.CANCELLED_SELL;
        if (!isSelling) {
            return;
        }

        int itemId = offer.getItemId();
        if (itemId <= 0) return;

        String itemName = Microbot.getItemManager().getItemComposition(itemId).getName();
        if (findMatchingConfig(itemId, itemName) == null) {
            return;
        }

        SlotState prev = slotStates[slot];
        if (prev == null || isDifferentOffer(prev, offer)) {
            prev = createSlotState(offer, System.currentTimeMillis());
            slotStates[slot] = prev;
            return;
        }

        applyOfferDelta(prev, offer);
    }

    private static final int[] RING_OF_WEALTH_IDS = {
        ItemID.RING_OF_WEALTH_1,
        ItemID.RING_OF_WEALTH_2,
        ItemID.RING_OF_WEALTH_3,
        ItemID.RING_OF_WEALTH_4,
        ItemID.RING_OF_WEALTH_5
    };

    private static final java.util.List<String> FIRE_STAVES = java.util.List.of(
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

    private boolean isAtGrandExchange() {
        WorldPoint gePoint = BankLocation.GRAND_EXCHANGE.getWorldPoint();
        WorldPoint playerPoint = Rs2Player.getWorldLocation();
        if (playerPoint == null) return false;
        return playerPoint.distanceTo(gePoint) <= 15;
    }

    private boolean isWearingFireStaff() {
        for (String staff : FIRE_STAVES) {
            if (Rs2Equipment.isWearing(staff)) {
                return true;
            }
        }
        return false;
    }

    private void handleTeleporting() {
        if (isAtGrandExchange()) {
            log.info("Already at Grand Exchange. Proceeding to banking.");
            currentState = State.BANKING;
            return;
        }

        if (!Rs2Bank.isOpen()) {
            Microbot.status = "Opening Bank for Teleport Prep";
            if (Rs2Bank.openBank()) {
                sleepUntil(Rs2Bank::isOpen, 5000);
            }
            return;
        }

        // 1. Standard Spellbook Teleport
        if (Rs2Magic.isSpellbook(Rs2Spellbook.MODERN)) {
            boolean hasLaw = Rs2Inventory.hasItem(ItemID.LAW_RUNE) || Rs2Bank.hasItem(ItemID.LAW_RUNE);
            boolean hasAir = Rs2Inventory.itemQuantity(ItemID.AIR_RUNE) >= 3 || Rs2Bank.hasItem(ItemID.AIR_RUNE);
            boolean isFireStaffEquipped = isWearingFireStaff();
            boolean hasFire = isFireStaffEquipped || Rs2Inventory.hasItem(ItemID.FIRE_RUNE) || Rs2Bank.hasItem(ItemID.FIRE_RUNE);

            if (hasLaw && hasAir && hasFire) {
                Microbot.status = "Withdrawing runes for Varrock Teleport";
                if (!Rs2Inventory.hasItem(ItemID.LAW_RUNE)) {
                    Rs2Bank.withdrawOne(ItemID.LAW_RUNE);
                    sleep(600, 1000);
                }
                int airInInv = Rs2Inventory.itemQuantity(ItemID.AIR_RUNE);
                if (airInInv < 3) {
                    Rs2Bank.withdrawX(ItemID.AIR_RUNE, 3 - airInInv);
                    sleep(600, 1000);
                }
                if (!isFireStaffEquipped && !Rs2Inventory.hasItem(ItemID.FIRE_RUNE)) {
                    Rs2Bank.withdrawOne(ItemID.FIRE_RUNE);
                    sleep(600, 1000);
                }

                if (Rs2Inventory.hasItem(ItemID.LAW_RUNE) && Rs2Inventory.itemQuantity(ItemID.AIR_RUNE) >= 3 && (isFireStaffEquipped || Rs2Inventory.hasItem(ItemID.FIRE_RUNE))) {
                    Rs2Bank.closeBank();
                    sleepUntil(() -> !Rs2Bank.isOpen(), 3000);
                    Microbot.status = "Casting Varrock Teleport to G.E";
                    boolean castSuccess = Rs2Magic.cast(MagicAction.VARROCK_TELEPORT, "Grand Exchange", 2);
                    if (castSuccess) {
                        sleepUntil(this::isAtGrandExchange, 6000);
                        if (isAtGrandExchange()) {
                            currentState = State.BANKING;
                            return;
                        }
                    }
                }
            }
        }

        // 2. Varrock Teleport Tablet
        if (Rs2Inventory.hasItem(ItemID.VARROCK_TELEPORT) || Rs2Bank.hasItem(ItemID.VARROCK_TELEPORT)) {
            Microbot.status = "Using Varrock Teleport Tablet";
            if (!Rs2Inventory.hasItem(ItemID.VARROCK_TELEPORT)) {
                Rs2Bank.withdrawOne(ItemID.VARROCK_TELEPORT);
                sleep(600, 1000);
            }
            if (Rs2Inventory.hasItem(ItemID.VARROCK_TELEPORT)) {
                Rs2Bank.closeBank();
                sleepUntil(() -> !Rs2Bank.isOpen(), 3000);
                boolean used = Rs2Inventory.interact(ItemID.VARROCK_TELEPORT, "Break");
                if (used) {
                    sleepUntil(this::isAtGrandExchange, 6000);
                    if (isAtGrandExchange()) {
                        currentState = State.BANKING;
                        return;
                    }
                }
            }
        }

        // 3. Ring of Wealth Teleport (1-5 charges, least charges first)
        int targetRingId = -1;
        for (int id : RING_OF_WEALTH_IDS) {
            if (Rs2Inventory.hasItem(id)) {
                targetRingId = id;
                break;
            }
        }
        if (targetRingId == -1) {
            for (int id : RING_OF_WEALTH_IDS) {
                if (Rs2Bank.hasItem(id)) {
                    targetRingId = id;
                    break;
                }
            }
        }

        if (targetRingId != -1) {
            Microbot.status = "Using Ring of Wealth Teleport";
            if (!Rs2Inventory.hasItem(targetRingId)) {
                Rs2Bank.withdrawOne(targetRingId);
                sleep(600, 1000);
            }
            if (Rs2Inventory.hasItem(targetRingId)) {
                Rs2Bank.closeBank();
                sleepUntil(() -> !Rs2Bank.isOpen(), 3000);
                boolean used = Rs2Inventory.interact(targetRingId, "Grand Exchange");
                if (used) {
                    sleepUntil(this::isAtGrandExchange, 6000);
                    if (isAtGrandExchange()) {
                        currentState = State.BANKING;
                        return;
                    }
                }
            }
        }

        log.warn("All G.E teleport options unavailable. Falling back to default banking.");
        currentState = State.BANKING;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        String message = "Neto GE Seller shutdown. Total Profit: " + formatProfit(totalProfit);
        Microbot.getClientThread().invoke(() -> {
            Microbot.getClient().addChatMessage(ChatMessageType.GAMEMESSAGE, "", "<col=ff0000>" + message + "</col>", null);
        });
        log.info(message);
    }
}
