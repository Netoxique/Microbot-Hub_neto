package net.runelite.client.plugins.microbot.netogeseller;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import java.awt.event.KeyEvent;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.api.events.GrandExchangeOfferChanged;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
public class NetoGeSellerScript extends Script {

    private enum State {
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
        private final int threshold;
        private final SellMode mode;

        public SellItemConfig(String name, int threshold, SellMode mode) {
            this.name = name;
            this.threshold = threshold;
            this.mode = mode;
        }

        public String getName() { return name; }
        public int getThreshold() { return threshold; }
        public SellMode getMode() { return mode; }
    }

    private Map<String, SellItemConfig> itemsToSellMap = new HashMap<>();
    private boolean hasUnsoldBankItems = false;
    private volatile int totalProfit = 0;

    private static class SlotState {
        int itemId;
        int quantitySold;
        int spent;

        SlotState(int itemId, int quantitySold, int spent) {
            this.itemId = itemId;
            this.quantitySold = quantitySold;
            this.spent = spent;
        }
    }

    private final SlotState[] slotStates = new SlotState[8];

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
        this.currentState = State.BANKING;
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

    private Map<String, SellItemConfig> parseItemsToSell(String configStr) {
        Map<String, SellItemConfig> map = new HashMap<>();
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
                map.put(name.toLowerCase(), new SellItemConfig(name, threshold, mode));
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

        for (Map.Entry<String, SellItemConfig> entry : itemsToSellMap.entrySet()) {
            String itemNameLower = entry.getKey();
            SellItemConfig itemConfig = entry.getValue();

            int bankQty = getBankQuantity(itemNameLower);
            int invQty = getInventoryQuantity(itemNameLower);
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
                    String exactName = getExactItemName(itemNameLower);
                    if (exactName != null) {
                        Microbot.status = "Withdrawing " + toWithdraw + " " + exactName;
                        if (toWithdraw == bankQty) {
                            Rs2Bank.withdrawAll(exactName, true);
                        } else {
                            Rs2Bank.withdrawX(exactName, toWithdraw);
                        }
                        sleep(600, 1000);
                    }
                }
            }
        }

        // Check if there are still more items to withdraw later
        for (Map.Entry<String, SellItemConfig> entry : itemsToSellMap.entrySet()) {
            String itemNameLower = entry.getKey();
            SellItemConfig itemConfig = entry.getValue();
            int bankQty = getBankQuantity(itemNameLower);
            int invQty = getInventoryQuantity(itemNameLower);
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

        boolean foundItemToSell = false;
        for (Rs2ItemModel item : Rs2Inventory.all(Rs2ItemModel::isTradeable)) {
            String nameLower = item.getName().toLowerCase();
            log.info("Considering tradeable inventory item '{}', quantity={}, configuredForSale={}", item.getName(), item.getQuantity(), itemsToSellMap.containsKey(nameLower));
            if (itemsToSellMap.containsKey(nameLower)) {
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
                    log.info("Attempting to sell '{}' with hotkey '{}'.", item.getName(), config.hotkey());
                    boolean sold = sellItemWithHotkey(item.getName(), quantityToSell, config.hotkey());
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
                    if (itemName != null && itemsToSellMap.containsKey(itemName.toLowerCase())) {
                        log.info("Active offer found for configured item: '{}' (State: {})", itemName, state);
                        return true;
                    }
                }
            }
            return false;
        }).orElse(false);
    }

    private boolean sellItemWithHotkey(String itemName, int quantity, String hotkey) {
        log.info("sellItemWithHotkey started. itemName='{}', quantity={}, hotkey='{}'", itemName, quantity, hotkey);
        if (!Rs2Inventory.hasItem(itemName, true)) {
            log.warn("sellItemWithHotkey returning false: inventory does not contain '{}'.", itemName);
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
        boolean offerInteraction = Rs2Inventory.interact(itemName, "Offer", true);
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

    private int getBankQuantity(String itemNameLower) {
        return Rs2Bank.bankItems().stream()
                .filter(item -> item.getName().toLowerCase().equals(itemNameLower))
                .mapToInt(Rs2ItemModel::getQuantity)
                .sum();
    }

    private int getInventoryQuantity(String itemNameLower) {
        return Rs2Inventory.all().stream()
                .filter(item -> item.getName().toLowerCase().equals(itemNameLower))
                .mapToInt(Rs2ItemModel::getQuantity)
                .sum();
    }

    private String getExactItemNameFromBank(String itemNameLower) {
        return Rs2Bank.bankItems().stream()
                .filter(item -> item.getName().toLowerCase().equals(itemNameLower))
                .map(Rs2ItemModel::getName)
                .findFirst()
                .orElse(null);
    }

    private String getExactItemNameFromInventory(String itemNameLower) {
        return Rs2Inventory.all().stream()
                .filter(item -> item.getName().toLowerCase().equals(itemNameLower))
                .map(Rs2ItemModel::getName)
                .findFirst()
                .orElse(null);
    }

    private String getExactItemName(String itemNameLower) {
        String name = getExactItemNameFromInventory(itemNameLower);
        if (name != null) return name;
        return getExactItemNameFromBank(itemNameLower);
    }

    private void initializeSlotStates() {
        Microbot.getClientThread().runOnClientThreadOptional(() -> {
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            for (int i = 0; i < 8; i++) {
                if (offers != null && i < offers.length) {
                    GrandExchangeOffer offer = offers[i];
                    if (offer != null && offer.getState() != GrandExchangeOfferState.EMPTY) {
                        slotStates[i] = new SlotState(offer.getItemId(), offer.getQuantitySold(), offer.getSpent());
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
        if (itemName == null || !itemsToSellMap.containsKey(itemName.toLowerCase())) {
            return;
        }

        SlotState prev = slotStates[slot];
        if (prev == null || prev.itemId != itemId) {
            prev = new SlotState(itemId, offer.getQuantitySold(), offer.getSpent());
            slotStates[slot] = prev;
            return;
        }

        int newQuantitySold = offer.getQuantitySold();
        int newSpent = offer.getSpent();

        int deltaQuantity = newQuantitySold - prev.quantitySold;
        int deltaSpent = newSpent - prev.spent;

        if (deltaSpent > 0 || deltaQuantity > 0) {
            if (deltaSpent > 0) {
                totalProfit += deltaSpent;
            }
            prev.quantitySold = newQuantitySold;
            prev.spent = newSpent;
        }
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
