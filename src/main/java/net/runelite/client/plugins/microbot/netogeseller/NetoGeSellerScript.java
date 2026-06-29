package net.runelite.client.plugins.microbot.netogeseller;

import lombok.extern.slf4j.Slf4j;
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

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
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
    private Map<String, Integer> itemsToSellMap = new HashMap<>();
    private boolean hasUnsoldBankItems = false;

    public boolean run() {
        this.currentState = State.BANKING;
        this.firstTimeOpeningBank = true;
        this.itemsToSellMap = parseItemsToSell(config.itemsToSell());
        this.hasUnsoldBankItems = true;

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
        }, 0, 1000, TimeUnit.MILLISECONDS);

        return true;
    }

    private Map<String, Integer> parseItemsToSell(String configStr) {
        Map<String, Integer> map = new HashMap<>();
        if (configStr == null || configStr.trim().isEmpty()) {
            return map;
        }
        String[] items = configStr.split(",");
        for (String item : items) {
            item = item.trim();
            if (item.isEmpty()) continue;

            String name = item;
            int keep = 0;

            if (item.contains(":")) {
                String[] parts = item.split(":");
                name = parts[0].trim();
                if (parts.length > 1) {
                    try {
                        keep = Integer.parseInt(parts[1].trim());
                    } catch (NumberFormatException e) {
                        Microbot.log("Invalid keep quantity for item: " + name);
                    }
                }
            } else if (item.contains(";")) {
                String[] parts = item.split(";");
                name = parts[0].trim();
                if (parts.length > 1) {
                    try {
                        keep = Integer.parseInt(parts[1].trim());
                    } catch (NumberFormatException e) {
                        Microbot.log("Invalid keep quantity for item: " + name);
                    }
                }
            }

            if (!name.isEmpty()) {
                map.put(name.toLowerCase(), keep);
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

        Rs2Bank.setWithdrawAsNote();
        hasUnsoldBankItems = false;

        for (Map.Entry<String, Integer> entry : itemsToSellMap.entrySet()) {
            String itemNameLower = entry.getKey();
            int keepThreshold = entry.getValue();

            int bankQty = getBankQuantity(itemNameLower);
            int invQty = getInventoryQuantity(itemNameLower);
            int totalQty = bankQty + invQty;
            int excess = totalQty - keepThreshold;

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
                        Rs2Bank.withdrawX(exactName, toWithdraw);
                        sleep(600, 1000);
                    }
                }
            }
        }

        // Check if there are still more items to withdraw later
        for (Map.Entry<String, Integer> entry : itemsToSellMap.entrySet()) {
            String itemNameLower = entry.getKey();
            int keepThreshold = entry.getValue();
            int bankQty = getBankQuantity(itemNameLower);
            int invQty = getInventoryQuantity(itemNameLower);
            int excess = (bankQty + invQty) - keepThreshold;
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
        Microbot.status = "Opening Grand Exchange";
        if (!Rs2GrandExchange.openExchange()) {
            return;
        }
        sleepUntil(Rs2GrandExchange::isOpen);

        boolean foundItemToSell = false;
        for (Rs2ItemModel item : Rs2Inventory.all(Rs2ItemModel::isTradeable)) {
            String nameLower = item.getName().toLowerCase();
            if (itemsToSellMap.containsKey(nameLower)) {
                int quantityToSell = item.getQuantity();
                if (quantityToSell <= 0) continue;

                foundItemToSell = true;

                if (Rs2GrandExchange.getAvailableSlotsCount() > 0) {
                    Microbot.status = "Selling " + quantityToSell + " " + item.getName();
                    if (sellItemWithHotkey(item.getName(), quantityToSell, config.hotkey())) {
                        sleep(1000, 1500);
                    }
                } else {
                    if (Rs2GrandExchange.hasSoldOffer()) {
                        Microbot.status = "Collecting sold offers to Bank";
                        Rs2GrandExchange.collectAllToBank();
                        sleepUntil(() -> !Rs2GrandExchange.hasSoldOffer(), 5000);
                        sleep(800, 1200);
                    } else {
                        if (Rs2Inventory.size() < 28 && hasUnsoldBankItems) {
                            Microbot.status = "Slots full. Returning to bank to withdraw more items";
                            Rs2GrandExchange.closeExchange();
                            sleepUntil(() -> !Rs2GrandExchange.isOpen());
                            currentState = State.BANKING;
                            return;
                        } else {
                            Microbot.status = "Slots full. Waiting for sales...";
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
                Rs2GrandExchange.closeExchange();
                sleepUntil(() -> !Rs2GrandExchange.isOpen());
                currentState = State.BANKING;
            } else {
                currentState = State.WAITING_FOR_SELL;
            }
        }
    }

    private void handleWaitingForSell() {
        Microbot.status = "Waiting for all offers to sell...";
        if (!Rs2GrandExchange.openExchange()) {
            return;
        }
        sleepUntil(Rs2GrandExchange::isOpen);

        if (Rs2GrandExchange.hasSoldOffer()) {
            Rs2GrandExchange.collectAllToBank();
            sleepUntil(() -> !Rs2GrandExchange.hasSoldOffer(), 5000);
            sleep(800, 1200);
        }

        if (!hasActiveOffers()) {
            Microbot.status = "All offers completed. Stopping plugin.";
            Rs2GrandExchange.closeExchange();
            sleepUntil(() -> !Rs2GrandExchange.isOpen());
            shutdown();
            Microbot.stopPlugin(this.plugin);
        }
    }

    private boolean hasActiveOffers() {
        GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
        if (offers == null) return false;
        for (GrandExchangeOffer offer : offers) {
            GrandExchangeOfferState state = offer.getState();
            if (state == GrandExchangeOfferState.SELLING || state == GrandExchangeOfferState.BUYING) {
                return true;
            }
        }
        return false;
    }

    private boolean sellItemWithHotkey(String itemName, int quantity, String hotkey) {
        if (!Rs2Inventory.hasItem(itemName, true)) return false;
        if (Rs2GrandExchange.getAvailableSlotsCount() == 0) return false;
        if (quantity <= 0) return false;

        if (!Rs2Inventory.interact(itemName, "Offer", true)) return false;

        if (!sleepUntil(Rs2GrandExchange::isOfferScreenOpen, 5000)) return false;
        sleep(300, 500);

        int currentOfferQty = Microbot.getVarbitValue(VarbitID.GE_NEWOFFER_QUANTITY);
        if (quantity != currentOfferQty) {
            if (!setQuantity(quantity)) {
                return false;
            }
        }

        Widget pricePerItemButtonX = getPricePerItemButton_X();
        if (pricePerItemButtonX == null) {
            return false;
        }

        Microbot.getMouse().click(pricePerItemButtonX.getBounds());
        if (!sleepUntil(() -> Rs2Widget.getWidget(InterfaceID.Chatbox.MES_TEXT2) != null, 3000)) return false;
        sleep(600, 1000);

        if (hotkey == null || hotkey.isEmpty()) {
            hotkey = "n";
        }
        
        Rs2Keyboard.keyPress(hotkey.charAt(0));
        sleep(600, 1000);

        Rs2Keyboard.enter();
        sleep(1000, 1500);

        Widget confirmButton = getConfirmButton();
        if (confirmButton == null) {
            return false;
        }

        Rs2Widget.clickWidget(confirmButton);
        sleepUntil(() -> Rs2Widget.hasWidget("Your offer is much"), 2000);
        if (Rs2Widget.hasWidget("Your offer is much")) {
            Rs2Widget.clickWidget("Yes");
        }

        return sleepUntil(() -> !Rs2GrandExchange.isOfferScreenOpen(), 5000);
    }

    private boolean setQuantity(int quantity) {
        int tries = 0;
        int targetQuantity = quantity;
        while (targetQuantity != Microbot.getVarbitValue(VarbitID.GE_NEWOFFER_QUANTITY)) {
            Widget quantityButtonX = getQuantityButton_X();
            if (quantityButtonX == null) {
                tries++;
                continue;
            }
            Microbot.getMouse().click(quantityButtonX.getBounds());
            sleepUntil(() -> Rs2Widget.getWidget(InterfaceID.Chatbox.MES_TEXT2) != null, 3000);
            sleep(600, 1000);
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
        return tries <= 3;
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

    @Override
    public void shutdown() {
        super.shutdown();
    }
}
