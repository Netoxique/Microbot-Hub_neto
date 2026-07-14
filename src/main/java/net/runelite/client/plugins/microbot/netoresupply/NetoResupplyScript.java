package net.runelite.client.plugins.microbot.netoresupply;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.ItemComposition;
import net.runelite.api.MenuAction;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeAction;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeRequest;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeSlots;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.grandexchange.models.GrandExchangeOfferDetails;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.http.api.item.ItemPrice;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
public class NetoResupplyScript extends Script {
    private enum State {
        WALKING_TO_GE,
        BANKING,
        OPENING_EXCHANGE,
        MANAGING_OFFERS,
        VERIFYING,
        FINISHED
    }

    private NetoResupplyConfig config;
    private NetoResupplyPlugin plugin;
    private State state;
    private final List<ResupplyItem> items = new ArrayList<>();
    private final List<String> problems = new ArrayList<>();
    private final Map<GrandExchangeSlots, TrackedOffer> trackedOffers = new LinkedHashMap<>();
    private boolean completionReported;

    public boolean run(NetoResupplyConfig config, NetoResupplyPlugin plugin) {
        this.config = config;
        this.plugin = plugin;
        this.state = State.WALKING_TO_GE;
        this.completionReported = false;
        this.items.clear();
        this.problems.clear();
        this.trackedOffers.clear();
        parseConfiguredLists();
        Microbot.enableAutoRunOn = false;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run() || !isRunning()) return;
                switch (state) {
                    case WALKING_TO_GE:
                        walkToGrandExchange();
                        break;
                    case BANKING:
                        inspectBank();
                        break;
                    case OPENING_EXCHANGE:
                        openExchange();
                        break;
                    case MANAGING_OFFERS:
                        manageOffers();
                        break;
                    case VERIFYING:
                        verifyBank();
                        break;
                    case FINISHED:
                        finishRun();
                        break;
                }
            } catch (Exception ex) {
                String message = "Unexpected error: " + safeMessage(ex);
                log.error("Neto Resupply error", ex);
                recordProblem(message);
                moveTo(State.VERIFYING);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private void parseConfiguredLists() {
        List<String> enabledLists = new ArrayList<>();
        if (config.farmingEnabled()) enabledLists.add(config.farmingList());
        if (config.magicEnabled()) enabledLists.add(config.magicList());
        if (config.runecraftEnabled()) enabledLists.add(config.runecraftList());
        if (config.constructionEnabled()) enabledLists.add(config.constructionList());
        if (config.herbloreEnabled()) enabledLists.add(config.herbloreList());
        if (config.craftingEnabled()) enabledLists.add(config.craftingList());
        if (config.fletchingEnabled()) enabledLists.add(config.fletchingList());
        if (config.huntingEnabled()) enabledLists.add(config.huntingList());
        if (config.smithingEnabled()) enabledLists.add(config.smithingList());
        if (config.cookingEnabled()) enabledLists.add(config.cookingList());
        if (config.teleportsEnabled()) enabledLists.add(config.teleportsList());

        NetoResupplyListParser.ParseResult parsed = NetoResupplyListParser.parse(enabledLists);
        problems.addAll(parsed.getErrors());
        for (NetoResupplyListParser.RequestedItem requested : parsed.getItems().values()) {
            items.add(new ResupplyItem(requested.getName(), requested.getMinQuantity(), requested.getMaxQuantity(), requested.isMinMax()));
        }
        for (String problem : problems) log.warn(problem);
    }

    private void walkToGrandExchange() {
        if (items.isEmpty()) {
            Microbot.status = "Neto Resupply: no valid enabled items";
            moveTo(State.FINISHED);
            return;
        }
        Microbot.status = "Neto Resupply: walking to Grand Exchange";
        if (Rs2GrandExchange.walkToGrandExchange()) moveTo(State.BANKING);
    }

    private void inspectBank() {
        Microbot.status = "Neto Resupply: checking bank supplies";
        if (!Rs2Bank.isOpen() && !Rs2Bank.openBank()) return;
        if (!sleepUntil(Rs2Bank::isOpen, 5000)) return;

        if (!Rs2Inventory.isEmpty()) {
            Rs2Bank.depositAll();
            sleepUntil(Rs2Inventory::isEmpty, 5000);
        }

        Map<String, Integer> bankQuantities = readBankQuantities();
        List<ResupplyItem> validItems = new ArrayList<>();
        for (ResupplyItem item : items) {
            ItemIdentity identity = resolveTradeableItem(item.configuredName);
            if (identity == null) {
                recordProblem("Unknown or untradeable item: " + item.configuredName);
                continue;
            }
            item.name = identity.name;
            item.itemId = identity.itemId;
            item.initialOwned = bankQuantities.getOrDefault(identity.name.toLowerCase(Locale.ROOT), 0);
            item.initialDeficit = NetoResupplyListParser.deficit(item.minQuantity, item.maxQuantity, item.minMax, item.initialOwned);
            item.remaining = item.initialDeficit;
            validItems.add(item);
        }
        items.clear();
        items.addAll(validItems);
        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 3000);

        moveTo(hasPendingItems() ? State.OPENING_EXCHANGE : State.VERIFYING);
    }

    private Map<String, Integer> readBankQuantities() {
        Map<String, Integer> quantities = new LinkedHashMap<>();
        for (Rs2ItemModel bankItem : Rs2Bank.bankItems()) {
            if (bankItem.getName() == null) continue;
            String key = bankItem.getName().toLowerCase(Locale.ROOT);
            quantities.merge(key, bankItem.getQuantity(), NetoResupplyScript::safeAdd);
        }
        return quantities;
    }

    private ItemIdentity resolveTradeableItem(String configuredName) {
        List<ItemPrice> matches = Microbot.getItemManager().search(configuredName);
        if (matches == null) return null;
        for (ItemPrice match : matches) {
            if (!configuredName.equalsIgnoreCase(match.getName())) continue;
            ItemComposition composition = Microbot.getClientThread().runOnClientThreadOptional(
                    () -> Microbot.getItemManager().getItemComposition(match.getId())).orElse(null);
            if (composition != null && composition.isTradeable()) {
                return new ItemIdentity(composition.getId(), composition.getName());
            }
        }
        return null;
    }

    private void openExchange() {
        Microbot.status = "Neto Resupply: opening Grand Exchange";
        if (!Rs2GrandExchange.isOpen() && !Rs2GrandExchange.openExchange()) return;
        if (sleepUntil(Rs2GrandExchange::isOpen, 5000)) moveTo(State.MANAGING_OFFERS);
    }

    private void manageOffers() {
        refreshTrackedOffers();

        ResupplyItem pendingItem = nextPendingItem();
        GrandExchangeSlots availableSlot = getAvailableUntrackedSlot();
        if (pendingItem != null && availableSlot != null) {
            submitOffer(pendingItem, availableSlot);
            return;
        }

        if (hasCollectableOffers()) {
            collectFromOverview();
            return;
        }

        List<TrackedOffer> expiredOffers = getExpiredOffers();
        if (!expiredOffers.isEmpty()) {
            abortExpiredOffers(expiredOffers);
            return;
        }

        if (pendingItem == null && trackedOffers.isEmpty()) {
            moveTo(State.VERIFYING);
            return;
        }

        if (pendingItem != null) {
            Microbot.status = trackedOffers.isEmpty()
                    ? "Neto Resupply: waiting for a free GE slot"
                    : "Neto Resupply: all GE slots are busy";
        } else {
            Microbot.status = "Neto Resupply: waiting for " + trackedOffers.size() + " active offer(s)";
        }
    }

    private void submitOffer(ResupplyItem item, GrandExchangeSlots slot) {
        item.remaining = remainingFor(item);
        if (item.remaining == 0 || isItemTracked(item)) return;

        int markup = safeAdd(Math.max(0, config.initialMarkup()), safeMultiply(item.reposts, Math.max(0, config.retryMarkupStep())));
        Microbot.status = "Neto Resupply: posting " + item.remaining + " " + item.name;
        GrandExchangeRequest request = GrandExchangeRequest.builder()
                .slot(slot)
                .action(GrandExchangeAction.BUY)
                .itemName(item.name)
                // Match the working Auto Buyer flow: the GE helper uses this flag when locating
                // and clicking the item in the chatbox search results. Exact matching can fail on
                // the result widget text and leave the script sitting after typing the item name.
                .exact(false)
                .quantity(item.remaining)
                .percent(markup)
                .closeAfterCompletion(false)
                .toBank(true)
                .build();

        if (!Rs2GrandExchange.processOffer(request)) {
            failOrRetry(item, "Could not submit offer (check coins, item limits, and GE access)");
            return;
        }
        trackedOffers.put(slot, new TrackedOffer(slot, item, System.currentTimeMillis()));
    }

    private GrandExchangeSlots getAvailableUntrackedSlot() {
        for (GrandExchangeSlots slot : Rs2GrandExchange.getAvailableSlots()) {
            if (!trackedOffers.containsKey(slot)) return slot;
        }
        return null;
    }

    private void refreshTrackedOffers() {
        for (TrackedOffer tracked : trackedOffers.values()) {
            GrandExchangeOfferDetails details = Rs2GrandExchange.getOfferDetails(tracked.slot);
            if (details == null || details.getItemId() != tracked.item.itemId) continue;
            tracked.lastState = details.getState();
            accountPurchasedQuantity(tracked, details.getQuantitySold());
        }
    }

    private boolean hasCollectableOffers() {
        if (Rs2GrandExchange.hasBoughtOffer()) return true;
        return trackedOffers.values().stream().anyMatch(tracked -> isTerminal(tracked.lastState));
    }

    private void collectFromOverview() {
        refreshTrackedOffers();
        List<TrackedOffer> terminalOffers = new ArrayList<>();
        for (TrackedOffer tracked : trackedOffers.values()) {
            if (isTerminal(tracked.lastState)) terminalOffers.add(tracked);
        }

        Microbot.status = "Neto Resupply: collecting completed offers to bank";
        if (!Rs2GrandExchange.collectAllToBank()) return;
        sleepUntil(() -> terminalOffers.stream().allMatch(tracked -> Rs2GrandExchange.getOfferDetails(tracked.slot) == null), 5000);

        for (TrackedOffer tracked : terminalOffers) {
            if (Rs2GrandExchange.getOfferDetails(tracked.slot) != null) continue;
            trackedOffers.remove(tracked.slot);
            tracked.item.remaining = remainingFor(tracked.item);
            if (tracked.item.remaining > 0) {
                String reason = tracked.lastState == GrandExchangeOfferState.CANCELLED_BUY
                        ? "Offer was cancelled"
                        : "Completed offer left an unresolved remainder";
                failOrRetry(tracked.item, reason);
            }
        }
    }

    private List<TrackedOffer> getExpiredOffers() {
        long now = System.currentTimeMillis();
        List<TrackedOffer> expired = new ArrayList<>();
        for (TrackedOffer tracked : trackedOffers.values()) {
            if (tracked.lastState == GrandExchangeOfferState.BUYING && now - tracked.submittedAt >= timeoutMillis()) {
                expired.add(tracked);
            }
        }
        return expired;
    }

    private void abortExpiredOffers(List<TrackedOffer> expiredOffers) {
        Microbot.status = "Neto Resupply: aborting " + expiredOffers.size() + " expired offer(s)";
        List<TrackedOffer> abortedOffers = new ArrayList<>();
        for (TrackedOffer tracked : expiredOffers) {
            if (abortOfferSlot(tracked.slot)) {
                abortedOffers.add(tracked);
            } else {
                deferTrackedOffer(tracked, "Timed-out offer could not be aborted");
            }
        }
        if (abortedOffers.isEmpty()) return;

        sleepUntil(() -> abortedOffers.stream().allMatch(tracked -> {
            GrandExchangeOfferDetails details = Rs2GrandExchange.getOfferDetails(tracked.slot);
            return details != null && details.getState() == GrandExchangeOfferState.CANCELLED_BUY;
        }), 5000);
        refreshTrackedOffers();

        List<TrackedOffer> cancelledOffers = new ArrayList<>();
        for (TrackedOffer tracked : abortedOffers) {
            if (tracked.lastState == GrandExchangeOfferState.CANCELLED_BUY) {
                cancelledOffers.add(tracked);
            } else {
                deferTrackedOffer(tracked, "Timed-out offer did not cancel");
            }
        }
        if (cancelledOffers.isEmpty()) return;

        Microbot.status = "Neto Resupply: collecting expired offers to bank";
        if (!Rs2GrandExchange.collectAllToBank()) return;
        sleepUntil(() -> cancelledOffers.stream().allMatch(tracked -> Rs2GrandExchange.getOfferDetails(tracked.slot) == null), 5000);

        for (TrackedOffer tracked : cancelledOffers) {
            if (Rs2GrandExchange.getOfferDetails(tracked.slot) != null) continue;
            trackedOffers.remove(tracked.slot);
            tracked.item.remaining = remainingFor(tracked.item);
            if (tracked.item.remaining > 0) failOrRetry(tracked.item, "Offer timed out");
        }
    }

    private boolean abortOfferSlot(GrandExchangeSlots slot) {
        if (slot == null) return false;
        if (Rs2GrandExchange.isOfferScreenOpen()) Rs2GrandExchange.backToOverview();
        Widget slotWidget;
        try {
            java.lang.reflect.Method method = Class.forName(
                            "net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeWidget")
                    .getDeclaredMethod("getSlot", GrandExchangeSlots.class);
            method.setAccessible(true);
            slotWidget = (Widget) method.invoke(null, slot);
        } catch (Exception ex) {
            log.error("Unable to locate GE slot widget for {}", slot, ex);
            return false;
        }
        if (slotWidget == null) return false;

        NewMenuEntry menuEntry = new NewMenuEntry()
                .option("Abort offer")
                .target("")
                .identifier(2)
                .type(MenuAction.CC_OP)
                .param0(2)
                .param1(slotWidget.getId())
                .itemId(-1)
                .forceLeftClick(false);
        Rectangle bounds = slotWidget.getBounds() != null && Rs2UiHelper.isRectangleWithinCanvas(slotWidget.getBounds())
                ? slotWidget.getBounds()
                : Rs2UiHelper.getDefaultRectangle();
        Microbot.doInvoke(menuEntry, bounds);
        return true;
    }

    private void accountPurchasedQuantity(TrackedOffer tracked, int offerQuantity) {
        if (offerQuantity <= tracked.accountedQuantity) return;
        int delta = offerQuantity - tracked.accountedQuantity;
        tracked.item.acquired = safeAdd(tracked.item.acquired, delta);
        tracked.item.remaining = remainingFor(tracked.item);
        tracked.accountedQuantity = offerQuantity;
    }

    private void failOrRetry(ResupplyItem item, String reason) {
        if (item.reposts >= Math.max(0, config.maximumReposts())) {
            item.unresolvedReason = reason + " after " + item.reposts + " reposts";
            recordProblem(item.name + ": " + item.unresolvedReason);
            return;
        }
        item.reposts++;
    }

    private void deferTrackedOffer(TrackedOffer tracked, String reason) {
        recordProblem(tracked.item.name + ": " + reason + "; will retry after another timeout");
        tracked.submittedAt = System.currentTimeMillis();
    }

    private ResupplyItem nextPendingItem() {
        for (ResupplyItem item : items) {
            item.remaining = remainingFor(item);
            if (item.remaining > 0 && item.unresolvedReason == null && !isItemTracked(item)) return item;
        }
        return null;
    }

    private boolean hasPendingItems() {
        return nextPendingItem() != null;
    }

    private boolean isItemTracked(ResupplyItem item) {
        return trackedOffers.values().stream().anyMatch(tracked -> tracked.item == item);
    }

    private int remainingFor(ResupplyItem item) {
        if (item.initialDeficit > 0) {
            return Math.max(0, item.initialDeficit - item.acquired);
        }
        return 0;
    }

    private static boolean isTerminal(GrandExchangeOfferState state) {
        return state == GrandExchangeOfferState.BOUGHT || state == GrandExchangeOfferState.CANCELLED_BUY;
    }

    private void verifyBank() {
        Microbot.status = "Neto Resupply: verifying bank quantities";
        if (Rs2GrandExchange.isOpen()) {
            Rs2GrandExchange.closeExchange();
            sleepUntil(() -> !Rs2GrandExchange.isOpen(), 3000);
        }
        if (!Rs2Bank.isOpen() && !Rs2Bank.openBank()) return;
        if (!sleepUntil(Rs2Bank::isOpen, 5000)) return;

        Map<String, Integer> bankQuantities = readBankQuantities();
        for (ResupplyItem item : items) {
            item.finalOwned = bankQuantities.getOrDefault(item.name.toLowerCase(Locale.ROOT), 0);
            if (item.initialDeficit > 0) {
                item.remaining = Math.max(0, item.maxQuantity - item.finalOwned);
            } else {
                item.remaining = 0;
            }
            if (item.remaining > 0 && item.unresolvedReason == null) {
                item.unresolvedReason = "Bank is still short by " + item.remaining;
                recordProblem(item.name + ": " + item.unresolvedReason);
            }
        }
        Rs2Bank.closeBank();
        moveTo(State.FINISHED);
    }

    private void finishRun() {
        if (completionReported) return;
        completionReported = true;
        long fulfilled = items.stream().filter(item -> item.remaining == 0).count();
        long unresolved = items.size() - fulfilled;
        String message = "Neto Resupply finished: " + fulfilled + " fulfilled, " + unresolved + " unresolved, " + problems.size() + " warning(s).";
        log.info(message);
        sendChat(message);
        Microbot.status = message;
        Microbot.stopPlugin(plugin);
    }

    private void recordProblem(String message) {
        if (message == null || message.trim().isEmpty()) return;
        problems.add(message);
        log.warn(message);
    }

    private void sendChat(String message) {
        Microbot.getClientThread().invoke(() -> Microbot.getClient().addChatMessage(
                ChatMessageType.GAMEMESSAGE, "", "<col=ff981f>" + message + "</col>", null));
    }

    private void moveTo(State nextState) {
        state = nextState;
    }

    private long timeoutMillis() {
        return Math.max(1, config.offerTimeoutSeconds()) * 1000L;
    }

    private static int safeAdd(int left, int right) {
        long sum = (long) left + right;
        return sum > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
    }

    private static int safeMultiply(int left, int right) {
        long product = (long) left * right;
        return product > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) product;
    }

    private static String safeMessage(Exception ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    private static final class ItemIdentity {
        private final int itemId;
        private final String name;

        private ItemIdentity(int itemId, String name) {
            this.itemId = itemId;
            this.name = name;
        }
    }

    private static final class ResupplyItem {
        private final String configuredName;
        private final int minQuantity;
        private final int maxQuantity;
        private final boolean minMax;
        private String name;
        private int itemId;
        private int initialOwned;
        private int initialDeficit;
        private int acquired;
        private int finalOwned;
        private int remaining;
        private int reposts;
        private String unresolvedReason;

        private ResupplyItem(String configuredName, int minQuantity, int maxQuantity, boolean minMax) {
            this.configuredName = configuredName;
            this.name = configuredName;
            this.minQuantity = minQuantity;
            this.maxQuantity = maxQuantity;
            this.minMax = minMax;
        }
    }

    private static final class TrackedOffer {
        private final GrandExchangeSlots slot;
        private final ResupplyItem item;
        private long submittedAt;
        private int accountedQuantity;
        private final int repostAttempt;
        private GrandExchangeOfferState lastState = GrandExchangeOfferState.BUYING;

        private TrackedOffer(GrandExchangeSlots slot, ResupplyItem item, long submittedAt) {
            this.slot = slot;
            this.item = item;
            this.submittedAt = submittedAt;
            this.repostAttempt = item.reposts;
        }
    }
}
