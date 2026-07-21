package net.runelite.client.plugins.microbot.netosailingsalv.features.salvaging;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.boat.Rs2BoatCache;
import net.runelite.client.plugins.microbot.api.player.models.Rs2PlayerModel;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.netosailingsalv.NetoSailingSalvConfig;
import net.runelite.client.plugins.microbot.netosailingsalv.NetoSailingSalvPlugin;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.inventory.Rs2RunePouch;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
@Singleton
public class SalvagingScript {

    private static final int DEFAULT_MINIMUM_ALCH_VALUE = 2000;
    private static final int PREP_TIMEOUT_MS = 10000;
    private static final int BOAT_TELEPORT_TIMEOUT_MS = 15000;
    private static final int STARTUP_SCENE_GRACE_MS = 2000;
    private static final List<String> CONTAINERS = List.of("Gem sack", "Gem bag", "Herb sack", "Seed box");
    /** Kept in the same priority order as Neto Alching; plugins compile into isolated source sets. */
    private static final List<String> FIRE_STAVES = List.of(
            "Twinflame staff", "Fire battlestaff", "Mystic fire staff", "Lava battlestaff",
            "Mystic lava staff", "Smoke battlestaff", "Mystic smoke staff", "Steam battlestaff",
            "Mystic steam staff", "Staff of fire");
    private static final Set<String> PROTECTED_ITEM_NAMES = Set.of(
            "gem sack", "gem bag", "herb sack", "seed box", "coins", "platinum tokens",
            "rune pouch", "divine rune pouch", "law rune", "mud rune", "nature rune", "earth rune", "water rune");
    private static final Map<Runes, Integer> BOAT_AND_ALCH_RUNES = Map.of(
            Runes.LAW, 16000,
            Runes.MUD, 16000,
            Runes.NATURE, 16000);

    /** First decimal number in the occupied widget text (often just {@code X}; still works if the client shows {@code X / N}). */
    private static final Pattern CARGO_HOLD_FIRST_NUMBER = Pattern.compile("(\\d+)");

    private static final int SIZE_SALVAGEABLE_AREA = 15;
    private static final int MIN_INVENTORY_FULL = 24;
    private static final int SALVAGE_TIMEOUT = 20000;
    private static final int DEPLOY_TIMEOUT = 5000;
    private static final int WAIT_TIME = 5000;
    private static final int WAIT_TIME_MAX = 10000;
    private static final int CARGO_HOLD_UI_TIMEOUT_MS = 8000;
    private static final int CARGO_HOLD_WITHDRAW_FAIL_THRESHOLD = 5;
    private static final int CARGO_HOLD_WITHDRAW_NO_GAIN_THRESHOLD = 5;
    /** Wait for inventory to reflect the withdraw after the salvage slot is clicked. */
    private static final int CARGO_HOLD_WITHDRAW_INVENTORY_TIMEOUT_MS = 12000;
    /** Pause after clicking the salvage slot so the client can apply the withdraw before inventory polling or Escape. */
    private static final int CARGO_HOLD_POST_WITHDRAW_CLICK_MIN_MS = 2200;
    private static final int CARGO_HOLD_POST_WITHDRAW_CLICK_MAX_MS = 5000;
    /** After salvage appears in inventory, wait again before Escape so the hold does not close mid-pipeline. */
    private static final int CARGO_HOLD_BEFORE_CLOSE_AFTER_WITHDRAW_MIN_MS = 800;
    private static final int CARGO_HOLD_BEFORE_CLOSE_AFTER_WITHDRAW_MAX_MS = 2200;
    /** Periodically re-open the hold and count ITEMS widgets so &quot;full&quot; stays accurate without item-container tracking. */
    private static final int CARGO_HOLD_WIDGET_RESYNC_MIN_MS = 4500;
    /** After Deposit inventory, occupied text can lag; retry read while the panel stays open before Escape. */
    private static final int CARGO_HOLD_POST_DEPOSIT_READ_ATTEMPTS = 6;
    private static final int CARGO_HOLD_POST_DEPOSIT_READ_GAP_MIN_MS = 120;
    private static final int CARGO_HOLD_POST_DEPOSIT_READ_GAP_MAX_MS = 320;
    /** Radius for {@link Rs2GameObject} scans and name fallbacks around the player (boat nested view, port, etc.). */
    private static final int NEARBY_TILE_OBJECT_SCAN_RADIUS = 32;
    /**
     * In-game message when deposit fails because every slot is taken ({@code Text.standardize} comparison, so extra
     * punctuation or wording after this phrase still matches).
     */
    private static final String CARGO_HOLD_FULL_MESSAGE_CONTAINS = "the cargo hold is full";
    private final Rs2TileObjectCache tileObjectCache;
    @SuppressWarnings("unused")
    private final Rs2BoatCache boatCache;
    private final EventBus eventBus;
    private final PluginManager pluginManager;
    private final Provider<NetoSailingSalvPlugin> pluginProvider;

    private volatile SalvagingState state = SalvagingState.PREP;
    private boolean alchingAvailable = true;
    private boolean prepComplete;
    private WorldPoint locationBeforeBoatTeleport;
    private String equippedBankTeleport;
    private long activationStartedAt;

    /**
     * Shipwreck lists rebuilt each {@link GameTick} on the client thread by scanning {@link Client#getTopLevelWorldView()}
     * (the sea layer). Plugin Hub &quot;Sailing&quot; uses game object spawn/despawn events instead; both target the same
     * {@link GameObject} ids. At-sea wrecks live on the top-level sea scene; boat facilities (e.g. cargo hold) live in the
     * boarded boat&apos;s nested {@link WorldView}. {@link Rs2GameObject} / tile cache can miss one or the other depending
     * on context; shipwrecks use an explicit top-level scene walk, cargo hold uses the local player world view scene walk.
     */
    private final Map<String, Rs2TileObjectModel> activeWreckByKey = new HashMap<>();
    private final Map<String, Rs2TileObjectModel> inactiveWreckByKey = new HashMap<>();
    private volatile List<Rs2TileObjectModel> activeWreckSnapshot = List.of();
    private volatile List<Rs2TileObjectModel> inactiveWreckSnapshot = List.of();

    /** Max cargo slots for this boat tier ({@link CargoHoldObjectIds#ID_TO_CAPACITY}). */
    private int cargoHoldCapacity = -1;
    /** Occupied slots: parsed from {@link CargoHoldInterfaceWidgets} occupied text (usually just {@code X}) when the hold is open; else ITEMS grid count. */
    private volatile int cargoHoldCount = -1;
    /** Salvage stacks in the hold from the same grid (item name contains &quot;salvage&quot;; one slot = one stack). */
    private volatile int cargoHoldSalvageStackCount = -1;
    private volatile boolean cargoHoldProcessing = false;
    private int lastCargoHoldObjectId = -1;
    private int cargoHoldWithdrawFailures = 0;
    /** Consecutive withdraw clicks that did not change inventory (separate from open failures). */
    private int cargoHoldWithdrawNoGainStreak = 0;
    private long lastCargoHoldInitAttemptMs;
    private long lastCargoHoldInitHintLogMs;
    private long lastCargoHoldWidgetResyncMs;

    @Inject
    public SalvagingScript(Rs2TileObjectCache tileObjectCache, Rs2BoatCache boatCache, EventBus eventBus,
                           PluginManager pluginManager, Provider<NetoSailingSalvPlugin> pluginProvider) {
        this.tileObjectCache = tileObjectCache;
        this.boatCache = boatCache;
        this.eventBus = eventBus;
        this.pluginManager = pluginManager;
        this.pluginProvider = pluginProvider;
    }

    public void register() {
        resetForActivation();
        eventBus.register(this);
    }

    public void unregister() {
        eventBus.unregister(this);
        resetForActivation();
    }

    private void resetForActivation() {
        state = SalvagingState.PREP;
        prepComplete = false;
        alchingAvailable = true;
        locationBeforeBoatTeleport = null;
        equippedBankTeleport = null;
        activationStartedAt = System.currentTimeMillis();
        resetCargoHoldState();
    }

    public SalvagingState getState() {
        return state;
    }

    public boolean isAlchingAvailable() {
        return alchingAvailable;
    }

    /**
     * Handles activation while the player is already aboard their boat. Scene/tile caches can trail plugin startup by
     * one game tick, so normal PREP is held briefly before deciding that the boat facilities are absent.
     *
     * @return true when this script tick is handled (waiting, started salvaging, or stopped the plugin)
     */
    private boolean handleAlreadyOnBoatActivation() {
        boolean stationVisible = findSalvagingStation() != null;
        boolean hookVisible = hasValidSalvagingHook();
        if (stationVisible && hookVisible) {
            String carriedTeleport = findCarriedBankTeleport();
            if (carriedTeleport == null) {
                stopPluginWithChat("A valid bank teleport is required before starting salvaging on your boat.");
                return true;
            }
            equippedBankTeleport = carriedTeleport;
            prepComplete = true;
            state = SalvagingState.SALVAGING;
            Microbot.status = "Salvaging prepared (already on boat)";
            log.info("Boat facilities and bank teleport '{}' detected; skipping PREP", carriedTeleport);
            return true;
        }
        return System.currentTimeMillis() - activationStartedAt < STARTUP_SCENE_GRACE_MS;
    }

    private String findCarriedBankTeleport() {
        List<Rs2ItemModel> carried = new ArrayList<>(Rs2Equipment.items());
        carried.addAll(Rs2Inventory.all());
        for (Rs2ItemModel item : carried) {
            String name = item.getName();
            if (name == null) {
                continue;
            }
            if (isValidBankTeleportName(name)) {
                return name;
            }
        }
        return null;
    }

    static boolean isValidBankTeleportName(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase();
        return lower.equals("crafting cape")
                || lower.equals("crafting cape(t)")
                || lower.equals("sailors' amulet")
                || lower.matches("skills necklace\\(\\d+\\)")
                || lower.matches("ring of dueling\\(\\d+\\)");
    }

    private void runPrep() {
        Microbot.status = "Salvaging PREP: opening closest bank";
        if (!Rs2Bank.isOpen()) {
            Rs2Bank.walkToBankAndUseBank();
            sleepUntil(Rs2Bank::isOpen, PREP_TIMEOUT_MS);
            return;
        }

        if (!Rs2Bank.depositAll()) {
            failPrep("Could not deposit inventory during salvaging preparation.");
            return;
        }
        sleepUntil(Rs2Inventory::isEmpty, PREP_TIMEOUT_MS);

        equippedBankTeleport = equipBestBankTeleport();
        if (equippedBankTeleport == null) {
            failPrep("No supported bank teleport was found.");
            return;
        }

        String staff = findFirstAvailableItem(FIRE_STAVES);
        if (staff == null) {
            disableAlching("No fire staff found; alching is disabled for this activation.");
        } else if (!Rs2Equipment.isWearing(staff) && !Rs2Bank.withdrawAndEquip(staff)) {
            disableAlching("Could not equip a fire staff; alching is disabled for this activation.");
        }

        boolean hasPouch = Rs2Bank.hasRunePouch();
        if (hasPouch) {
            if (!Rs2Bank.withdrawRunePouch() || !sleepUntil(Rs2Inventory::hasRunePouch, PREP_TIMEOUT_MS)) {
                failPrep("Could not withdraw the rune pouch.");
                return;
            }
            if (Rs2Bank.count(ItemID.LAWRUNE) <= 0 || Rs2Bank.count(ItemID.MUDRUNE) <= 0) {
                failPrep("Law and Mud runes are required to prepare the rune pouch.");
                return;
            }
            boolean hasNatureRunes = Rs2Bank.count(ItemID.NATURERUNE) > 0;
            if (!hasNatureRunes) {
                disableAlching("No Nature runes found; alching is disabled for this activation.");
            }
            Map<Runes, Integer> loadout = hasNatureRunes
                    ? BOAT_AND_ALCH_RUNES
                    : Map.of(Runes.LAW, 16000, Runes.MUD, 16000);
            if (!Rs2RunePouch.load(loadout)) {
                failPrep("Could not fill the rune pouch with the required boat teleport runes.");
                return;
            }
            Rs2Bank.depositAllExcept("Rune pouch", "Divine rune pouch");
        } else {
            if (!withdrawRequiredLooseRune(ItemID.LAWRUNE, "Law rune")
                    || !withdrawRequiredLooseRune(ItemID.EARTHRUNE, "Earth rune")
                    || !withdrawRequiredLooseRune(ItemID.WATERRUNE, "Water rune")) {
                failPrep("Two Law, Earth, and Water runes are required when no rune pouch is available.");
                return;
            }
            if (Rs2Bank.count(ItemID.NATURERUNE) <= 0) {
                disableAlching("No Nature runes found; alching is disabled for this activation.");
            } else if (alchingAvailable && !Rs2Bank.withdrawAll(ItemID.NATURERUNE)) {
                disableAlching("Could not withdraw Nature runes; alching is disabled for this activation.");
            }
            Rs2Bank.depositAllExcept("Law rune", "Earth rune", "Water rune", "Nature rune");
        }

        if (!Rs2Bank.withdrawX(ItemID.COINS, 100000)
                || !sleepUntil(() -> Rs2Inventory.count(ItemID.COINS) >= 100000, PREP_TIMEOUT_MS)) {
            failPrep("Could not withdraw 100,000 Coins.");
            return;
        }

        for (String container : CONTAINERS) {
            if (Rs2Bank.hasItem(container) && !Rs2Bank.withdrawOne(container)) {
                failPrep("Could not withdraw " + container + ".");
                return;
            }
        }
        if (!Rs2Bank.emptyContainers()) {
            Widget emptyContainers = Rs2Widget.findWidget("Empty containers");
            if (emptyContainers == null) {
                failPrep("Could not find the bank Empty containers action.");
                return;
            }
            Rs2Widget.clickWidget(emptyContainers);
        }

        Rs2Bank.closeBank();
        if (!sleepUntil(() -> !Rs2Bank.isOpen(), PREP_TIMEOUT_MS)) {
            failPrep("Could not close the bank after preparation.");
            return;
        }

        locationBeforeBoatTeleport = Rs2Player.getWorldLocation();
        if (!castTeleportToBoat()) {
            failPrep("Could not cast Teleport to Boat.");
            return;
        }
        boolean moved = sleepUntil(() -> {
            WorldPoint now = Rs2Player.getWorldLocation();
            return now != null && locationBeforeBoatTeleport != null && now.distanceTo(locationBeforeBoatTeleport) > 10;
        }, BOAT_TELEPORT_TIMEOUT_MS);
        boolean validBoatSetup = moved && sleepUntil(
                () -> findSalvagingStation() != null && hasValidSalvagingHook(), BOAT_TELEPORT_TIMEOUT_MS);
        if (!validBoatSetup) {
            Microbot.log("Boat setup validation failed; returning to a bank and stopping the plugin.");
            teleportBackToBank();
            stopPlugin();
            return;
        }

        prepComplete = true;
        state = SalvagingState.SALVAGING;
        Microbot.status = "Salvaging prepared";
    }

    private boolean withdrawRequiredLooseRune(int itemId, String name) {
        return Rs2Bank.count(itemId) >= 2 && Rs2Bank.withdrawX(itemId, 2)
                && sleepUntil(() -> Rs2Inventory.count(itemId) >= 2, PREP_TIMEOUT_MS);
    }

    private String equipBestBankTeleport() {
        String selected = findFirstAvailableItem(List.of("Crafting cape", "Crafting cape(t)"));
        if (selected == null) {
            selected = findFirstAvailableItem(List.of("Sailors' amulet"));
        }
        if (selected == null) {
            selected = findLowestChargeAvailableItem("Skills necklace");
        }
        if (selected == null) {
            selected = findLowestChargeAvailableItem("Ring of dueling");
        }
        if (selected == null) {
            return null;
        }
        if (Rs2Equipment.isWearing(selected)) {
            return selected;
        }
        if (!Rs2Bank.withdrawAndEquip(selected)) {
            return null;
        }
        final String equipped = selected;
        return sleepUntil(() -> Rs2Equipment.isWearing(equipped), PREP_TIMEOUT_MS) ? selected : null;
    }

    private String findFirstAvailableItem(List<String> names) {
        for (String name : names) {
            Rs2ItemModel equipped = Rs2Equipment.get(item -> item.getName() != null && item.getName().equalsIgnoreCase(name));
            if (equipped != null) {
                return equipped.getName();
            }
            if (Rs2Bank.hasItem(name)) {
                Rs2ItemModel item = Rs2Bank.getBankItem(name);
                return item == null ? name : item.getName();
            }
        }
        return null;
    }

    private String findLowestChargeAvailableItem(String baseName) {
        List<Rs2ItemModel> candidates = new ArrayList<>(Rs2Bank.bankItems());
        candidates.addAll(Rs2Equipment.items());
        return candidates.stream()
                .filter(item -> item.getName() != null && item.getName().startsWith(baseName + "("))
                .min(Comparator.comparingInt(item -> chargeFromName(item.getName())))
                .map(Rs2ItemModel::getName)
                .orElse(null);
    }

    private static int chargeFromName(String name) {
        Matcher matcher = Pattern.compile("\\((\\d+)\\)$").matcher(name);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : Integer.MAX_VALUE;
    }

    private boolean castTeleportToBoat() {
        Rs2Tab.switchToMagicTab();
        sleep(300, 600);
        Widget spell = Rs2Widget.findWidget("Teleport to Boat");
        if (spell == null || spell.isHidden()) {
            return false;
        }
        Rs2Widget.clickWidget(spell);
        return true;
    }

    private boolean hasValidSalvagingHook() {
        Set<String> validHooks = Set.of(
                "dragon salvaging hook", "rune salvaging hook", "adamant salvaging hook");
        return tileObjectCache.query().fromWorldView()
                .where(obj -> obj.getName() != null && validHooks.contains(obj.getName().toLowerCase()))
                .nearestOnClientThread() != null;
    }

    private void teleportBackToBank() {
        if (equippedBankTeleport == null) {
            return;
        }
        String[][] actions;
        if (equippedBankTeleport.startsWith("Crafting cape")) {
            actions = new String[][]{{"Teleport"}, {"Crafting Guild"}};
        } else if (equippedBankTeleport.startsWith("Sailors' amulet")) {
            actions = new String[][]{{"Teleport"}};
        } else if (equippedBankTeleport.startsWith("Skills necklace")) {
            actions = new String[][]{{"Cooking Guild"}, {"Teleport"}};
        } else {
            actions = new String[][]{{"Castle Wars"}, {"Teleport"}};
        }
        WorldPoint before = Rs2Player.getWorldLocation();
        for (String[] action : actions) {
            if (Rs2Equipment.interact(equippedBankTeleport, action[0])) {
                if (sleepUntil(() -> {
                    WorldPoint now = Rs2Player.getWorldLocation();
                    return now != null && before != null && now.distanceTo(before) > 10;
                }, BOAT_TELEPORT_TIMEOUT_MS)) {
                    Rs2Bank.walkToBankAndUseBank();
                    return;
                }
            }
        }
    }

    private void failPrep(String reason) {
        Microbot.log(reason);
        stopPlugin();
    }

    private void stopPluginWithChat(String reason) {
        state = SalvagingState.STOPPED;
        Microbot.getClientThread().invoke(() -> Microbot.getClient().addChatMessage(
                ChatMessageType.GAMEMESSAGE, "", "<col=ff0000>" + reason + "</col>", null));
        stopPlugin();
    }

    private void stopPlugin() {
        state = SalvagingState.STOPPED;
        Microbot.getClientThread().invoke(() -> {
            NetoSailingSalvPlugin plugin = pluginProvider.get();
            pluginManager.setPluginEnabled(plugin, false);
            try {
                pluginManager.stopPlugin(plugin);
            } catch (Exception ex) {
                log.error("Failed to stop Neto Sailing Salv plugin", ex);
            }
        });
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        rebuildShipwreckMapsFromTopLevelScene();
        activeWreckSnapshot = List.copyOf(activeWreckByKey.values());
        inactiveWreckSnapshot = List.copyOf(inactiveWreckByKey.values());
    }

    /**
     * Full top-level scene pass (sea layer), same objects the client draws for distant water tiles.
     */
    private void rebuildShipwreckMapsFromTopLevelScene() {
        activeWreckByKey.clear();
        inactiveWreckByKey.clear();
        Client client = Microbot.getClient();
        if (client == null) {
            return;
        }
        WorldView wv = client.getTopLevelWorldView();
        if (wv == null) {
            return;
        }
        Scene scene = wv.getScene();
        if (scene == null) {
            return;
        }
        Tile[][][] tiles = scene.getTiles();
        if (tiles == null) {
            return;
        }
        int plane = wv.getPlane();
        if (plane < 0) {
            return;
        }
        if (plane >= tiles.length) {
            return;
        }
        Tile[][] planeTiles = tiles[plane];
        if (planeTiles == null) {
            return;
        }
        int maxX = Math.min(Constants.SCENE_SIZE, planeTiles.length);
        for (int x = 0; x < maxX; x++) {
            Tile[] column = planeTiles[x];
            if (column == null) {
                continue;
            }
            int maxY = Math.min(Constants.SCENE_SIZE, column.length);
            for (int y = 0; y < maxY; y++) {
                Tile tile = column[y];
                if (tile == null) {
                    continue;
                }
                GameObject[] gameObjects = tile.getGameObjects();
                if (gameObjects != null) {
                    for (GameObject go : gameObjects) {
                        if (go == null) {
                            continue;
                        }
                        if (!go.getSceneMinLocation().equals(tile.getSceneLocation())) {
                            continue;
                        }
                        considerShipwreckTileObjectForRebuild(go);
                    }
                }
                DecorativeObject dec = tile.getDecorativeObject();
                if (dec != null) {
                    considerShipwreckTileObjectForRebuild(dec);
                }
            }
        }
    }

    private void considerShipwreckTileObjectForRebuild(TileObject obj) {
        int id = obj.getId();
        if (SalvageObjectIds.ACTIVE_SHIPWRECK_IDS.contains(id)) {
            activeWreckByKey.put(dedupeKey(obj), new Rs2TileObjectModel(obj));
            return;
        }
        if (SalvageObjectIds.INACTIVE_SHIPWRECK_IDS.contains(id)) {
            inactiveWreckByKey.put(dedupeKey(obj), new Rs2TileObjectModel(obj));
        }
    }

    private static String dedupeKey(TileObject o) {
        WorldPoint p = o.getWorldLocation();
        return o.getId() + ":" + p.getX() + ":" + p.getY() + ":" + p.getPlane();
    }

    private static List<Rs2TileObjectModel> mergeDistinctTileObjectLists(List<Rs2TileObjectModel> a, List<Rs2TileObjectModel> b) {
        Map<String, Rs2TileObjectModel> byKey = new LinkedHashMap<>();
        for (Rs2TileObjectModel m : a) {
            byKey.put(tileObjectDedupeKey(m), m);
        }
        for (Rs2TileObjectModel m : b) {
            String key = tileObjectDedupeKey(m);
            if (!byKey.containsKey(key)) {
                byKey.put(key, m);
            }
        }
        return List.copyOf(byKey.values());
    }

    private static String tileObjectDedupeKey(Rs2TileObjectModel m) {
        return dedupeKey(m);
    }

    public List<Rs2TileObjectModel> getActiveWrecks() {
        return activeWreckSnapshot;
    }

    public List<Rs2TileObjectModel> getInactiveWrecks() {
        return inactiveWreckSnapshot;
    }

    public void run(NetoSailingSalvConfig config) {
        try {
            if (!prepComplete) {
                state = SalvagingState.PREP;
                if (handleAlreadyOnBoatActivation()) {
                    return;
                }
                runPrep();
                return;
            }

            state = SalvagingState.SALVAGING;
            var player = new Rs2PlayerModel();

            if (cargoHoldCapacity == -1) {
                initCargoHold();
            }

            if (isPlayerAnimating(player)) {
                log.info("Currently salvaging, waiting...");
                sleep(WAIT_TIME, WAIT_TIME_MAX);
                return;
            }

            if (handleCargoHoldMode(config, player)) {
                return;
            }

            if (tryRunIdleInventoryCleanup(config)) {
                return;
            }

            if (isInventoryFull()) {
                if (cargoHoldProcessing && hasSalvageItems()) {
                    log.info("Inventory full during cargo-hold processing; processing salvage at station before more withdraws");
                } else {
                    log.info("Inventory full, handling before salvaging");
                }
                handleFullInventory(config, player);
                return;
            }

            var nearbyWreck = findNearestWreck(player.getWorldLocation());
            if (nearbyWreck == null) {
                log.info("No shipwreck found nearby");
                sleep(WAIT_TIME);
                return;
            }

            deploySalvagingHook(player);

        } catch (Exception ex) {
            log.error("Error in salvaging script", ex);
        }
    }

    private void resetCargoHoldState() {
        cargoHoldCapacity = -1;
        cargoHoldCount = -1;
        cargoHoldSalvageStackCount = -1;
        cargoHoldProcessing = false;
        lastCargoHoldObjectId = -1;
        cargoHoldWithdrawFailures = 0;
        cargoHoldWithdrawNoGainStreak = 0;
        lastCargoHoldInitAttemptMs = 0;
        lastCargoHoldInitHintLogMs = 0;
        lastCargoHoldWidgetResyncMs = 0;
    }

    /**
     * @return true if this tick is fully handled and {@link #run(NetoSailingSalvConfig)} should return.
     */
    private boolean handleCargoHoldMode(NetoSailingSalvConfig config, Rs2PlayerModel player) {
        syncCargoHoldIfObjectVariantChanged();
        if (cargoHoldCapacity == -1) {
            initCargoHold();
            if (cargoHoldCapacity == -1) {
                logCargoHoldInitThrottled(
                        "Cargo hold not initialized yet; salvaging continues. Stand on your boat near the hold.");
                return false;
            }
        }

        refreshCargoHoldCountsIfPanelOpen();

        if (!cargoHoldProcessing) {
            if (hasNearbySalvageableWreck(player.getWorldLocation()) || hasSalvageItems()) {
                if (!willDepositSalvageToCargoHoldImminently()) {
                    maybeResyncCargoHoldCountsFromOpenUi();
                }
            }
        }

        if (cargoHoldProcessing || shouldProcessCargoHold()) {
            if (!cargoHoldProcessing && shouldProcessCargoHold()) {
                cargoHoldProcessing = true;
                log.info("Cargo hold processing phase started (full or near capacity)");
            }
            if (cargoHoldSalvageStackCount == 0) {
                cargoHoldProcessing = false;
                cargoHoldWithdrawFailures = 0;
                cargoHoldWithdrawNoGainStreak = 0;
                log.info("No salvage left in cargo hold, resuming normal salvaging");
                return false;
            }
            if (hasSalvageItems() && canDepositSalvageToCargoHold()
                    && !suppressSalvageDepositDuringCargoHoldProcessing()) {
                depositToCargoHold();
                return true;
            }
            if (isInventoryFull()) {
                handleFullInventory(config, player);
                return true;
            }
            boolean fillingInventoryFromHold = !isInventoryFull()
                    && (cargoHoldSalvageStackCount > 0
                    || (cargoHoldSalvageStackCount < 0 && cargoHoldCount > 0));
            if (!fillingInventoryFromHold && !hasNearbySalvageableWreck(player.getWorldLocation())) {
                return false;
            }
            processCargoHoldWithdrawStep();
            return true;
        }

        return false;
    }

    private void syncCargoHoldIfObjectVariantChanged() {
        if (cargoHoldCapacity < 0) {
            return;
        }
        Rs2TileObjectModel hold = findCargoHold();
        if (hold == null) {
            return;
        }
        int id = hold.getId();
        if (lastCargoHoldObjectId < 0) {
            lastCargoHoldObjectId = id;
            return;
        }
        if (id == lastCargoHoldObjectId) {
            return;
        }
        lastCargoHoldObjectId = id;
        Integer cap = CargoHoldObjectIds.ID_TO_CAPACITY.get(id);
        if (cap != null) {
            cargoHoldCapacity = cap;
        }
        clampCargoHoldCount();
        clampCargoHoldSalvageStackCount();
    }

    private void initCargoHold() {
        if (cargoHoldCapacity != -1) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastCargoHoldInitAttemptMs < 2500) {
            return;
        }
        lastCargoHoldInitAttemptMs = now;

        Rs2TileObjectModel hold = findCargoHold();
        if (hold == null) {
            logCargoHoldInitThrottled("Cargo hold: no cargo hold object in this scene (stand on your boat).");
            return;
        }
        Integer capObj = CargoHoldObjectIds.ID_TO_CAPACITY.get(hold.getId());
        if (capObj == null) {
            logCargoHoldInitThrottled(
                    "Cargo hold: object id " + hold.getId() + " not mapped to capacity; add it to CargoHoldObjectIds if this is a new boat tier or variant.");
            return;
        }
        int cap = capObj;
        if (!openCargoHoldInterfaceForWithdraw()) {
            logCargoHoldInitThrottled(
                    "Cargo hold: could not open interface for initialization; stand on your boat and use Open on the hold.");
            return;
        }
        sleep(Rs2Random.between(280, 650));
        cargoHoldCapacity = cap;
        if (!readOccupiedCountFromOpenHoldInterface()) {
            cargoHoldCapacity = -1;
            cargoHoldCount = -1;
            cargoHoldSalvageStackCount = -1;
            logCargoHoldInitThrottled(
                    "Cargo hold: could not read hold contents after opening; check client/game updates.");
            closeCargoHoldInterface();
            return;
        }
        closeCargoHoldInterface();
        lastCargoHoldObjectId = hold.getId();
        lastCargoHoldInitHintLogMs = 0;
        lastCargoHoldWidgetResyncMs = System.currentTimeMillis();
        log.info(
                "Cargo hold initialized: capacity={} slots, occupied={}, salvage stacks={}; deposits use in-UI Deposit inventory.",
                cargoHoldCapacity, cargoHoldCount, cargoHoldSalvageStackCount);
    }

    private void logCargoHoldInitThrottled(String message) {
        long t = System.currentTimeMillis();
        if (t - lastCargoHoldInitHintLogMs < 15000) {
            return;
        }
        lastCargoHoldInitHintLogMs = t;
        log.info(message);
    }

    /**
     * Resolves the cargo hold on the client thread: tile cache merge, explicit walk of the local player&apos;s
     * {@link WorldView} scene (same approach as Plugin Hub {@code BoatTracker} + {@code CargoHoldTier} ids),
     * {@link Rs2GameObject} radius scan, then name match, then nearest to the player.
     */
    private Rs2TileObjectModel findCargoHold() {
        return Microbot.getClientThread().invoke(this::findCargoHoldOnClientThread);
    }

    private Rs2TileObjectModel findCargoHoldOnClientThread() {
        List<Rs2TileObjectModel> fromWorldView = tileObjectCache.query()
                .fromWorldView()
                .where(this::isCargoHoldTileObject)
                .toList();
        List<Rs2TileObjectModel> fromDefaultScene = tileObjectCache.query()
                .where(this::isCargoHoldTileObject)
                .toList();
        List<Rs2TileObjectModel> merged = mergeDistinctTileObjectLists(fromWorldView, fromDefaultScene);
        merged = mergeDistinctTileObjectLists(merged, scanCargoHoldObjectsFromLocalPlayerWorldViewScene());
        merged = mergeDistinctTileObjectLists(merged, scanCargoHoldObjectsFromScene());
        if (merged.isEmpty()) {
            WorldPoint anchor = Rs2Player.getWorldLocation();
            if (anchor != null) {
                try {
                    TileObject named = Rs2GameObject.getTileObject("Cargo hold", anchor, NEARBY_TILE_OBJECT_SCAN_RADIUS);
                    if (named != null) {
                        merged = List.of(new Rs2TileObjectModel(named));
                    }
                } catch (RuntimeException ex) {
                    log.debug("Cargo hold: Rs2GameObject.getTileObject name fallback failed (known issue on some sea scenes)", ex);
                }
            }
        }
        if (merged.isEmpty()) {
            return null;
        }
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null) {
            return merged.get(0);
        }
        return merged.stream()
                .min(Comparator.comparingInt(o -> player.distanceTo(o.getWorldLocation())))
                .orElse(null);
    }

    private List<Rs2TileObjectModel> scanCargoHoldObjectsFromScene() {
        WorldPoint anchor = Rs2Player.getWorldLocation();
        if (anchor == null) {
            return List.of();
        }
        try {
            List<?> raw = Rs2GameObject.getAll(o -> CargoHoldObjectIds.ALL_IDS.contains(o.getId()), anchor, NEARBY_TILE_OBJECT_SCAN_RADIUS);
            List<Rs2TileObjectModel> out = new ArrayList<>();
            for (Object o : raw) {
                if (o instanceof TileObject) {
                    out.add(new Rs2TileObjectModel((TileObject) o));
                }
            }
            return out;
        } catch (RuntimeException ex) {
            log.debug("Cargo hold: Rs2GameObject.getAll scene scan failed", ex);
            return List.of();
        }
    }

    /**
     * Full scene pass on the local player&apos;s {@link WorldView} (the boat interior when boarded), matching Plugin Hub
     * {@code BoatTracker} / {@code CargoHoldTier.fromGameObjectId} behaviour.
     */
    private List<Rs2TileObjectModel> scanCargoHoldObjectsFromLocalPlayerWorldViewScene() {
        Client client = Microbot.getClient();
        if (client == null) {
            return List.of();
        }
        Player lp = client.getLocalPlayer();
        if (lp == null) {
            return List.of();
        }
        WorldView wv = lp.getWorldView();
        if (wv == null) {
            return List.of();
        }
        return collectTileObjectsFromWorldViewScene(wv, this::isCargoHoldTileObject);
    }

    /**
     * Walks one {@link WorldView}&apos;s scene (e.g. local player / boat interior) and collects {@link TileObject}s that
     * match {@code predicate}. Same tile rules as {@link #rebuildShipwreckMapsFromTopLevelScene()} for game objects.
     */
    private List<Rs2TileObjectModel> collectTileObjectsFromWorldViewScene(
            WorldView wv,
            Predicate<Rs2TileObjectModel> predicate) {
        Scene scene = wv.getScene();
        if (scene == null) {
            return List.of();
        }
        Tile[][][] tiles = scene.getTiles();
        if (tiles == null) {
            return List.of();
        }
        int plane = wv.getPlane();
        if (plane < 0) {
            return List.of();
        }
        if (plane >= tiles.length) {
            return List.of();
        }
        Tile[][] planeTiles = tiles[plane];
        if (planeTiles == null) {
            return List.of();
        }
        List<Rs2TileObjectModel> out = new ArrayList<>();
        int maxX = Math.min(Constants.SCENE_SIZE, planeTiles.length);
        for (int x = 0; x < maxX; x++) {
            Tile[] column = planeTiles[x];
            if (column == null) {
                continue;
            }
            int maxY = Math.min(Constants.SCENE_SIZE, column.length);
            for (int y = 0; y < maxY; y++) {
                Tile tile = column[y];
                if (tile == null) {
                    continue;
                }
                GameObject[] gameObjects = tile.getGameObjects();
                if (gameObjects != null) {
                    for (GameObject go : gameObjects) {
                        if (go == null) {
                            continue;
                        }
                        if (!go.getSceneMinLocation().equals(tile.getSceneLocation())) {
                            continue;
                        }
                        maybeAddTileObjectIf(out, go, predicate);
                    }
                }
                DecorativeObject dec = tile.getDecorativeObject();
                if (dec != null) {
                    maybeAddTileObjectIf(out, dec, predicate);
                }
            }
        }
        return out;
    }

    private static void maybeAddTileObjectIf(
            List<Rs2TileObjectModel> out,
            TileObject obj,
            Predicate<Rs2TileObjectModel> predicate) {
        Rs2TileObjectModel model = new Rs2TileObjectModel(obj);
        if (!predicate.test(model)) {
            return;
        }
        out.add(model);
    }

    private boolean isCargoHoldTileObject(Rs2TileObjectModel obj) {
        if (CargoHoldObjectIds.ALL_IDS.contains(obj.getId())) {
            return true;
        }
        String name = obj.getName();
        if (name == null) {
            return false;
        }
        return name.toLowerCase().contains("cargo hold");
    }

    private boolean shouldProcessCargoHold() {
        if (cargoHoldCapacity < 0 || cargoHoldCount < 0) {
            return false;
        }
        int freeSlots = cargoHoldCapacity - cargoHoldCount;
        return freeSlots == 0 || freeSlots < Rs2Inventory.emptySlotCount();
    }

    /**
     * True when the hold is initialized and has reported spare capacity and the player is carrying salvage.
     * Intentionally does <em>not</em> use {@link #shouldProcessCargoHold()}  Ethat check compares hold free slots to
     * empty inventory slots, which stays true while the inventory is &quot;full&quot; (24+ items) but still has
     * several empty spaces, and would block in-UI deposit even though the client may still allow it.
     */
    private boolean canDepositSalvageToCargoHold() {
        if (cargoHoldCapacity < 0) {
            return false;
        }
        if (cargoHoldCount < 0) {
            return false;
        }
        int free = cargoHoldCapacity - cargoHoldCount;
        if (free <= 0) {
            return false;
        }
        return Rs2Inventory.count("salvage") > 0;
    }

    private void depositToCargoHold() {
        if (!openCargoHoldInterfaceForWithdraw()) {
            log.info("Cargo hold: could not open interface for deposit");
            return;
        }
        sleep(Rs2Random.between(200, 480));
        if (!readOccupiedCountAfterDepositWhileHoldOpen()) {
            log.warn("Cargo hold: could not read hold grid from UI before deposit");
            closeCargoHoldInterface();
            return;
        }
        lastCargoHoldWidgetResyncMs = System.currentTimeMillis();
        int salvageBefore = Rs2Inventory.count("salvage");
        if (salvageBefore <= 0) {
            closeCargoHoldInterface();
            return;
        }
        if (!canDepositSalvageToCargoHold()) {
            if (cargoHoldCapacity > 0) {
                int free = cargoHoldCapacity - cargoHoldCount;
                if (free <= 0) {
                    cargoHoldProcessing = true;
                    log.info(
                            "Cargo hold has no free slots after UI read ({} / {}); switching to processing phase",
                            cargoHoldCount,
                            cargoHoldCapacity);
                }
            }
            closeCargoHoldInterface();
            return;
        }
        AtomicBoolean clicked = new AtomicBoolean(false);
        Microbot.getClientThread().invoke(() -> clicked.set(clickDepositInventoryInOpenCargoHold()));
        if (!clicked.get()) {
            log.warn("Cargo hold: Deposit inventory control not found in interface");
            closeCargoHoldInterface();
            return;
        }
        sleep(Rs2Random.between(280, 620));
        sleepUntil(() -> Rs2Inventory.count("salvage") < salvageBefore, SALVAGE_TIMEOUT);
        boolean readOk = readOccupiedCountAfterDepositWhileHoldOpen();
        lastCargoHoldWidgetResyncMs = System.currentTimeMillis();
        if (!readOk) {
            log.info("Cargo hold: could not refresh counts after deposit from UI");
        }
        if (!shouldLeaveCargoHoldOpenAfterDeposit()) {
            closeCargoHoldInterface();
        }
    }

    /**
     * When the cargo-hold pipeline will continue on the next script iteration (withdraw another stack, or deposit again
     * with the UI already open), closing here forces an immediate re-open in {@link #processCargoHoldWithdrawStep()} or
     * {@link #openCargoHoldInterfaceForWithdraw()}. Leave the panel open instead.
     */
    private boolean shouldLeaveCargoHoldOpenAfterDeposit() {
        if (cargoHoldSalvageStackCount <= 0) {
            return false;
        }
        if (cargoHoldProcessing) {
            return true;
        }
        return shouldProcessCargoHold();
    }

    /**
     * Opens the cargo hold panel (Open on world object). Used for withdraw and for in-UI deposit.
     */
    private boolean openCargoHoldInterfaceForWithdraw() {
        if (Rs2Widget.isWidgetVisible(InterfaceID.SailingBoatCargohold.UNIVERSE)) {
            return true;
        }
        Rs2TileObjectModel hold = findCargoHold();
        if (hold == null) {
            return false;
        }
        hold.click("Open");
        return sleepUntil(() -> Rs2Widget.isWidgetVisible(InterfaceID.SailingBoatCargohold.UNIVERSE), CARGO_HOLD_UI_TIMEOUT_MS);
    }

    /**
     * Reads occupied + salvage counts while the cargo-hold interface is already open. Call
     * {@link #openCargoHoldInterfaceForWithdraw()} (and a short sleep) first when the panel was not open.
     *
     * @return false if the read failed
     */
    private boolean readOccupiedCountFromOpenHoldInterface() {
        int[] grid = Microbot.getClientThread().invoke(this::countOccupiedAndSalvageStacksInOpenHoldInterface);
        if (grid == null) {
            return false;
        }
        applyCargoHoldCountsFromItemGrid(grid);
        return true;
    }

    /**
     * Reads occupied/salvage counts while the hold panel stays open: used before clicking Deposit inventory (sync state,
     * avoid depositing into a full hold) and after a deposit (header line can lag). Settles then retries across ticks.
     */
    private boolean readOccupiedCountAfterDepositWhileHoldOpen() {
        sleep(Rs2Random.between(180, 420));
        for (int attempt = 0; attempt < CARGO_HOLD_POST_DEPOSIT_READ_ATTEMPTS; attempt++) {
            if (readOccupiedCountFromOpenHoldInterface()) {
                return true;
            }
            sleep(Rs2Random.between(CARGO_HOLD_POST_DEPOSIT_READ_GAP_MIN_MS, CARGO_HOLD_POST_DEPOSIT_READ_GAP_MAX_MS));
        }
        return false;
    }

    /**
     * Re-reads occupied/salvage counts whenever the cargo-hold panel is already open (no throttle). Must run before
     * deposit/withdraw decisions: throttled {@link #maybeResyncCargoHoldCountsFromOpenUi()}, skipped resync while
     * {@link #cargoHoldProcessing}, and deposit-imminent skips left stale counts and repeated deposit attempts into a
     * full hold.
     */
    private void refreshCargoHoldCountsIfPanelOpen() {
        boolean visible = Microbot.getClientThread().runOnClientThreadOptional(
                () -> Rs2Widget.isWidgetVisible(InterfaceID.SailingBoatCargohold.UNIVERSE)).orElse(false);
        if (!visible) {
            return;
        }
        if (readOccupiedCountFromOpenHoldInterface()) {
            lastCargoHoldWidgetResyncMs = System.currentTimeMillis();
        }
    }

    /**
     * Re-opens the hold on a throttle and re-counts widgets when the panel was closed. When the panel is open,
     * {@link #refreshCargoHoldCountsIfPanelOpen()} already refreshed this tick.
     */
    private void maybeResyncCargoHoldCountsFromOpenUi() {
        boolean wasVisible = Microbot.getClientThread().runOnClientThreadOptional(
                () -> Rs2Widget.isWidgetVisible(InterfaceID.SailingBoatCargohold.UNIVERSE)).orElse(false);
        if (wasVisible) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastCargoHoldWidgetResyncMs < CARGO_HOLD_WIDGET_RESYNC_MIN_MS) {
            return;
        }
        if (!openCargoHoldInterfaceForWithdraw()) {
            return;
        }
        sleep(Rs2Random.between(180, 420));
        if (!readOccupiedCountFromOpenHoldInterface()) {
            return;
        }
        lastCargoHoldWidgetResyncMs = now;
        closeCargoHoldInterface();
    }

    private boolean clickDepositInventoryInOpenCargoHold() {
        Client client = Microbot.getClient();
        if (client == null) {
            return false;
        }
        Widget universe = client.getWidget(InterfaceID.SailingBoatCargohold.UNIVERSE);
        if (universe == null || universe.isHidden()) {
            return false;
        }
        Widget deposit = client.getWidget(InterfaceID.SailingBoatCargohold.DEPOSITALL_INVENTORY);
        if (deposit != null && !deposit.isHidden()) {
            Rs2Widget.clickWidget(deposit);
            return true;
        }
        Widget target = findDepositInventoryWidget(universe);
        if (target == null) {
            return false;
        }
        Rs2Widget.clickWidget(target);
        return true;
    }

    private static Widget findDepositInventoryWidget(Widget w) {
        if (w == null) {
            return null;
        }
        String[] actions = w.getActions();
        if (actions != null) {
            for (String a : actions) {
                if (a == null) {
                    continue;
                }
                String lower = a.toLowerCase();
                if (lower.contains("deposit") && lower.contains("inventory")) {
                    return w;
                }
            }
        }
        String text = w.getText();
        if (text != null) {
            String lower = text.toLowerCase().replace("<br>", " ");
            if (lower.contains("deposit") && lower.contains("inventory")) {
                return w;
            }
        }
        Widget[] children = w.getChildren();
        if (children == null) {
            return null;
        }
        for (Widget c : children) {
            Widget found = findDepositInventoryWidget(c);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * Applies {@code grid[0]} = occupied slots, {@code grid[1]} = salvage stack count from the ITEMS grid.
     * {@link #cargoHoldCapacity} is unchanged here (set from {@link CargoHoldObjectIds} at init).
     */
    private void applyCargoHoldCountsFromItemGrid(int[] grid) {
        if (grid == null) {
            return;
        }
        if (grid.length < 2) {
            return;
        }
        int occ = Math.max(0, grid[0]);
        int sal = Math.max(0, grid[1]);
        if (cargoHoldCapacity > 0) {
            cargoHoldCount = Math.min(occ, cargoHoldCapacity);
            cargoHoldSalvageStackCount = Math.min(sal, cargoHoldCount);
            return;
        }
        cargoHoldCount = occ;
        cargoHoldSalvageStackCount = Math.min(sal, occ);
    }

    private void closeCargoHoldInterface() {
        if (!Rs2Widget.isWidgetVisible(InterfaceID.SailingBoatCargohold.UNIVERSE)) {
            return;
        }
        sleep(Rs2Random.between(280, 620));
        Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
        sleep(Rs2Random.between(280, 520));
    }

    /**
     * {@code [0]} = occupied slots, {@code [1]} = salvage-named stacks in the ITEMS grid.
     * Occupied comes from {@link CargoHoldInterfaceWidgets} (occupied-only text child {@code 943, 4}) when parseable; else the ITEMS grid walk.
     * Requires the cargo-hold panel to already be open. Client thread only.
     */
    private int[] countOccupiedAndSalvageStacksInOpenHoldInterface() {
        try {
            Client client = Microbot.getClient();
            if (client == null) {
                return null;
            }
            Widget universe = client.getWidget(InterfaceID.SailingBoatCargohold.UNIVERSE);
            if (universe == null || universe.isHidden()) {
                return null;
            }
            Widget items = client.getWidget(InterfaceID.SailingBoatCargohold.ITEMS);
            if (items == null || items.isHidden()) {
                return null;
            }
            int salvageStacks = countSalvageItemSlotsInHoldRecursive(client, items);
            Integer occupiedFromLine = parseOccupiedSlotsFromCargoHoldTextLine(client);
            int occupied;
            if (occupiedFromLine != null) {
                occupied = occupiedFromLine;
            } else {
                occupied = countNonEmptyItemSlotsRecursive(items);
            }
            return new int[] { occupied, salvageStacks };
        } catch (RuntimeException ex) {
            log.debug("Cargo hold: interface read failed", ex);
            return null;
        }
    }

    /**
     * First number in the occupied-slot widget text (typically just occupied {@code X}; same regex if a {@code X / N} string appears).
     */
    private static Integer parseOccupiedSlotsFromCargoHoldTextLine(Client client) {
        Widget w = client.getWidget(
                CargoHoldInterfaceWidgets.CARGO_HOLD_OCCUPIED_TEXT_GROUP,
                CargoHoldInterfaceWidgets.CARGO_HOLD_OCCUPIED_TEXT_CHILD);
        if (w == null) {
            return null;
        }
        if (w.isHidden()) {
            return null;
        }
        String t = w.getText();
        if (t == null) {
            return null;
        }
        if (t.isEmpty()) {
            return null;
        }
        String plain = Text.removeTags(t).replace("<br>", " ").trim();
        Matcher m = CARGO_HOLD_FIRST_NUMBER.matcher(plain);
        if (!m.find()) {
            return null;
        }
        return Integer.parseInt(m.group(1));
    }

    private static int countSalvageItemSlotsInHoldRecursive(Client client, Widget w) {
        int count = 0;
        if (w.getItemId() > 0) {
            var def = client.getItemDefinition(w.getItemId());
            if (def != null) {
                String name = def.getName();
                if (name != null) {
                    if (name.toLowerCase().contains("salvage")) {
                        count++;
                    }
                }
            }
        }
        Widget[] children = w.getChildren();
        if (children == null) {
            return count;
        }
        for (Widget c : children) {
            if (c == null) {
                continue;
            }
            count += countSalvageItemSlotsInHoldRecursive(client, c);
        }
        return count;
    }

    private static int countNonEmptyItemSlotsRecursive(Widget w) {
        int count = 0;
        if (w.getItemId() > 0) {
            count++;
        }
        Widget[] children = w.getChildren();
        if (children == null) {
            return count;
        }
        for (Widget c : children) {
            if (c == null) {
                continue;
            }
            count += countNonEmptyItemSlotsRecursive(c);
        }
        return count;
    }

    private static int countSalvageItemStacksInInventory() {
        int n = 0;
        for (Rs2ItemModel item : Rs2Inventory.all()) {
            String name = item.getName();
            if (name == null) {
                continue;
            }
            if (name.toLowerCase().contains("salvage")) {
                n++;
            }
        }
        return n;
    }

    private void processCargoHoldWithdrawStep() {
        boolean holdWasAlreadyOpen = Rs2Widget.isWidgetVisible(InterfaceID.SailingBoatCargohold.UNIVERSE);
        if (!openCargoHoldInterfaceForWithdraw()) {
            cargoHoldWithdrawFailures++;
            if (cargoHoldWithdrawFailures >= CARGO_HOLD_WITHDRAW_FAIL_THRESHOLD) {
                log.warn("Cargo hold: interface failed to open repeatedly; exiting processing mode");
                cargoHoldProcessing = false;
                cargoHoldWithdrawFailures = 0;
            }
            return;
        }
        cargoHoldWithdrawFailures = 0;

        if (!holdWasAlreadyOpen) {
            sleep(Rs2Random.between(280, 650));
        }
        sleep(Rs2Random.between(120, 320));

        int trackedSalvageBeforeRead = cargoHoldSalvageStackCount;
        readOccupiedCountFromOpenHoldInterface();
        if (cargoHoldSalvageStackCount == 0) {
            if (trackedSalvageBeforeRead > 0) {
                sleep(Rs2Random.between(450, 900));
                readOccupiedCountFromOpenHoldInterface();
            }
        }
        if (cargoHoldSalvageStackCount == 0) {
            closeCargoHoldInterface();
            return;
        }

        int salvageBefore = Rs2Inventory.count("salvage");
        AtomicBoolean invoked = new AtomicBoolean(false);
        Microbot.getClientThread().invoke(() -> invoked.set(invokeWithdrawOneSalvageStackFromCargoHoldUi()));
        if (!invoked.get()) {
            log.info("Cargo hold: no salvage stack in hold UI; re-reading occupied count from open interface");
            readOccupiedCountFromOpenHoldInterface();
            closeCargoHoldInterface();
            if (cargoHoldSalvageStackCount == 0) {
                cargoHoldProcessing = false;
            }
            return;
        }

        // Only after the salvage slot click  Elong waits belong here, not before the click.
        sleep(Rs2Random.between(CARGO_HOLD_POST_WITHDRAW_CLICK_MIN_MS, CARGO_HOLD_POST_WITHDRAW_CLICK_MAX_MS));
        boolean gainedInventory = sleepUntil(
                () -> Rs2Inventory.count("salvage") != salvageBefore, CARGO_HOLD_WITHDRAW_INVENTORY_TIMEOUT_MS);
        if (!gainedInventory) {
            sleep(Rs2Random.between(650, 1400));
            gainedInventory = sleepUntil(() -> Rs2Inventory.count("salvage") != salvageBefore, 7000);
        }
        if (!gainedInventory) {
            cargoHoldWithdrawNoGainStreak++;
            log.info("Cargo hold: withdraw not reflected in inventory yet; leaving hold open for retry (avoid closing before click applies)");
            if (cargoHoldWithdrawNoGainStreak >= CARGO_HOLD_WITHDRAW_NO_GAIN_THRESHOLD) {
                log.warn("Cargo hold: withdraw inventory never updated; closing interface and exiting processing mode");
                closeCargoHoldInterface();
                cargoHoldProcessing = false;
                cargoHoldWithdrawNoGainStreak = 0;
            }
            return;
        }
        cargoHoldWithdrawNoGainStreak = 0;

        int gained = Rs2Inventory.count("salvage") - salvageBefore;
        int countBeforeUiRead = cargoHoldCount;
        int salvageCountBeforeUiRead = cargoHoldSalvageStackCount;
        readOccupiedCountFromOpenHoldInterface();
        if (gained > 0) {
            if (cargoHoldCount == countBeforeUiRead) {
                cargoHoldCount = Math.max(0, cargoHoldCount - 1);
                clampCargoHoldCount();
            }
            if (cargoHoldSalvageStackCount == salvageCountBeforeUiRead && cargoHoldSalvageStackCount > 0) {
                cargoHoldSalvageStackCount = Math.max(0, cargoHoldSalvageStackCount - 1);
            }
            clampCargoHoldSalvageStackCount();
        }

        Rs2Antiban.actionCooldown();
        if (isInventoryFull()) {
            sleep(Rs2Random.between(CARGO_HOLD_BEFORE_CLOSE_AFTER_WITHDRAW_MIN_MS, CARGO_HOLD_BEFORE_CLOSE_AFTER_WITHDRAW_MAX_MS));
            if (Rs2Random.dicePercentage(18)) {
                Rs2Antiban.takeMicroBreakByChance();
            }
            closeCargoHoldInterface();
            return;
        }
        if (cargoHoldSalvageStackCount == 0) {
            closeCargoHoldInterface();
            return;
        }
        sleep(Rs2Random.between(180, 480));
    }

    /**
     * Left-clicks the salvage stack widget in the open cargo-hold item grid ({@code Rs2Widget.clickWidget}).
     * Real widget click (same pattern as other hub plugins), not a synthesized menu entry.
     */
    private boolean invokeWithdrawOneSalvageStackFromCargoHoldUi() {
        Client client = Microbot.getClient();
        if (client == null) {
            return false;
        }
        Widget salvageSlot = findFirstSalvageStackWidget(client);
        if (salvageSlot == null) {
            return false;
        }
        Rs2Widget.clickWidget(salvageSlot);
        return true;
    }

    private static Widget findFirstSalvageStackWidget(Client client) {
        Widget items = client.getWidget(InterfaceID.SailingBoatCargohold.ITEMS);
        if (items == null) {
            return null;
        }
        return findSalvageInTree(client, items);
    }

    private static Widget findSalvageInTree(Client client, Widget w) {
        if (w == null) {
            return null;
        }
        if (w.getItemId() > 0) {
            var def = client.getItemDefinition(w.getItemId());
            if (def != null && def.getName().toLowerCase().contains("salvage")) {
                return w;
            }
        }
        Widget[] children = w.getChildren();
        if (children == null) {
            return null;
        }
        for (Widget c : children) {
            Widget found = findSalvageInTree(client, c);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private void clampCargoHoldCount() {
        if (cargoHoldCapacity < 0) {
            return;
        }
        if (cargoHoldCount < 0) {
            cargoHoldCount = 0;
        }
        if (cargoHoldCount > cargoHoldCapacity) {
            cargoHoldCount = cargoHoldCapacity;
        }
    }

    private void clampCargoHoldSalvageStackCount() {
        if (cargoHoldSalvageStackCount < 0) {
            return;
        }
        cargoHoldSalvageStackCount = Math.max(0, cargoHoldSalvageStackCount);
        if (cargoHoldCount >= 0) {
            cargoHoldSalvageStackCount = Math.min(cargoHoldSalvageStackCount, cargoHoldCount);
        }
    }

    private boolean isPlayerAnimating(Rs2PlayerModel player) {
        return player.getAnimation() != -1;
    }

    /** Stable threshold so cargo-hold processing does not alternate ticks between &quot;full&quot; and not full. */
    private boolean isInventoryFull() {
        return Rs2Inventory.count() >= MIN_INVENTORY_FULL;
    }

    /**
     * While {@link #cargoHoldProcessing} and the hold still has salvage stacks ({@link #cargoHoldSalvageStackCount}
     * &gt; 0), do not {@link #depositToCargoHold()}. Non-salvage items may still occupy slots; deposits resume when
     * salvage stacks in the hold reach 0.
     */
    private boolean suppressSalvageDepositDuringCargoHoldProcessing() {
        return cargoHoldProcessing && cargoHoldSalvageStackCount > 0;
    }

    /**
     * When true, this tick will open the hold for {@link #depositToCargoHold()} (from cargo-hold mode or
     * {@link #handleFullInventory}). Skipping {@link #maybeResyncCargoHoldCountsFromOpenUi()} avoids an extra
     * open→read→close before that deposit, which already refreshes counts after deposit.
     */
    private boolean willDepositSalvageToCargoHoldImminently() {
        return hasSalvageItems()
                && canDepositSalvageToCargoHold()
                && !suppressSalvageDepositDuringCargoHoldProcessing();
    }

    private boolean hasSalvageItems() {
        return Rs2Inventory.count("salvage") > 0;
    }

    private Rs2TileObjectModel findNearestWreck(WorldPoint playerLocation) {
        var activeWrecks = getActiveWrecks();

        if (activeWrecks.isEmpty()) {
            log.info("No active shipwrecks found");
            sleep(WAIT_TIME);
            return null;
        }

        return activeWrecks.stream()
                .filter(wreck -> isWithinSalvageArea(playerLocation, wreck))
                .min(Comparator.comparingInt(wreck -> playerLocation.distanceTo(wreck.getWorldLocation())))
                .orElse(null);
    }

    private boolean isWithinSalvageArea(WorldPoint playerLocation, Rs2TileObjectModel wreck) {
        return playerLocation.distanceTo(wreck.getWorldLocation()) <= SIZE_SALVAGEABLE_AREA;
    }

    /**
     * True if any active shipwreck is within hook range. Used to avoid opening the cargo hold for withdraw processing
     * while idle with no wreck nearby (which would spam open/close every script tick).
     */
    private boolean hasNearbySalvageableWreck(WorldPoint playerLocation) {
        for (Rs2TileObjectModel wreck : getActiveWrecks()) {
            if (isWithinSalvageArea(playerLocation, wreck)) {
                return true;
            }
        }
        return false;
    }

    /**
     * After cargo-hold mass processing, inventory can sit below the &quot;full&quot; threshold while still holding
     * drop/alch/casket loot. Runs one {@link #clearInventoryViaAlchDropAndCaskets} pass when there is no salvage to
     * protect and configured cleanup would change the inventory.
     *
     * @return true if a cleanup pass was executed (caller should return for this tick).
     */
    private boolean tryRunIdleInventoryCleanup(NetoSailingSalvConfig config) {
        if (hasSalvageItems()) {
            return false;
        }
        if (!inventoryCleanupConfigured(config)) {
            return false;
        }
        if (!inventoryHasCleanupWork(config)) {
            return false;
        }
        log.info("Inventory cleanup (drop/alch/caskets) before salvaging");
        clearInventoryViaAlchDropAndCaskets(config);
        return true;
    }

    private boolean inventoryCleanupConfigured(NetoSailingSalvConfig config) {
        return true;
    }

    private boolean inventoryHasCleanupWork(NetoSailingSalvConfig config) {
        int minimum = minimumAlchValue(config);
        for (Rs2ItemModel item : Rs2Inventory.all()) {
            if (!isProtected(item) && item.isTradeable()) {
                if (!alchingAvailable || item.getHaPrice() < minimum || (!item.isStackable() && item.getHaPrice() >= minimum)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void handleFullInventory(NetoSailingSalvConfig config, Rs2PlayerModel player) {
        if (hasSalvageItems() && !isPlayerAnimating(player)) {
            if (canDepositSalvageToCargoHold() && !suppressSalvageDepositDuringCargoHoldProcessing()) {
                depositToCargoHold();
                return;
            }
            depositSalvageOrDrop(config);
            return;
        }
        clearInventoryViaAlchDropAndCaskets(config);
    }

    private void clearInventoryViaAlchDropAndCaskets(NetoSailingSalvConfig config) {
        state = SalvagingState.FILLING;
        fillContainers();
        state = SalvagingState.ALCHING;
        alchItems(config);
        state = SalvagingState.FILLING;
        fillContainers();
        state = SalvagingState.DROPPING;
        dropJunk(config);
        state = SalvagingState.SALVAGING;
    }

    private void depositSalvageOrDrop(NetoSailingSalvConfig config) {
        var salvagingStation = findSalvagingStation();

        if (salvagingStation != null) {
            depositAtStation(salvagingStation);
        } else {
            log.info("No salvaging station found, dropping junk items");
            dropJunk(config);
        }
    }

    /**
     * Resolves a salvaging station like {@link #findCargoHold()}: tile cache, explicit local {@link WorldView} scene walk
     * (on-board station), then {@link Rs2GameObject} radius scan (e.g. port), nearest to the player.
     */
    private Rs2TileObjectModel findSalvagingStation() {
        return Microbot.getClientThread().invoke(this::findSalvagingStationOnClientThread);
    }

    private Rs2TileObjectModel findSalvagingStationOnClientThread() {
        List<Rs2TileObjectModel> fromWorldView = tileObjectCache.query()
                .fromWorldView()
                .where(this::isSalvagingStationTileObject)
                .toList();
        List<Rs2TileObjectModel> fromDefaultScene = tileObjectCache.query()
                .where(this::isSalvagingStationTileObject)
                .toList();
        List<Rs2TileObjectModel> merged = mergeDistinctTileObjectLists(fromWorldView, fromDefaultScene);
        Client client = Microbot.getClient();
        if (client != null) {
            Player lp = client.getLocalPlayer();
            if (lp != null) {
                WorldView wv = lp.getWorldView();
                if (wv != null) {
                    merged = mergeDistinctTileObjectLists(
                            merged,
                            collectTileObjectsFromWorldViewScene(wv, this::isSalvagingStationTileObject));
                }
            }
        }
        merged = mergeDistinctTileObjectLists(merged, scanSalvagingStationsFromRs2GameObject());
        if (merged.isEmpty()) {
            return null;
        }
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null) {
            return merged.get(0);
        }
        return merged.stream()
                .min(Comparator.comparingInt(o -> player.distanceTo(o.getWorldLocation())))
                .orElse(null);
    }

    private List<Rs2TileObjectModel> scanSalvagingStationsFromRs2GameObject() {
        WorldPoint anchor = Rs2Player.getWorldLocation();
        if (anchor == null) {
            return List.of();
        }
        try {
            List<?> raw = Rs2GameObject.getAll(
                    o -> SalvagingStationObjectIds.ALL_IDS.contains(o.getId()),
                    anchor,
                    NEARBY_TILE_OBJECT_SCAN_RADIUS);
            List<Rs2TileObjectModel> out = new ArrayList<>();
            for (Object o : raw) {
                if (o instanceof TileObject) {
                    out.add(new Rs2TileObjectModel((TileObject) o));
                }
            }
            return out;
        } catch (RuntimeException ex) {
            log.debug("Salvaging station: Rs2GameObject.getAll scan failed", ex);
            return List.of();
        }
    }

    /**
     * Boat and port salvaging stations are identified by object ID ({@code ObjectID1.SAILING_SALVAGING_STATION_*})
     * because composition objects often do not expose the exact menu name &quot;Salvaging station&quot; via
     * {@link Rs2TileObjectModel#getName()}.
     */
    private boolean isSalvagingStationTileObject(Rs2TileObjectModel obj) {
        if (SalvagingStationObjectIds.ALL_IDS.contains(obj.getId())) {
            return true;
        }
        String name = obj.getName();
        if (name == null) {
            return false;
        }
        return name.equalsIgnoreCase("salvaging station");
    }

    private void depositAtStation(Rs2TileObjectModel station) {
        station.click();
        sleepUntil(() -> !hasSalvageItems(), SALVAGE_TIMEOUT);
    }

    private void deploySalvagingHook(Rs2PlayerModel player) {
        var hook = tileObjectCache.query()
                .fromWorldView()
                .where(obj -> obj.getName() != null && obj.getName().toLowerCase().contains("salvaging hook"))
                .nearestOnClientThread();

        if (hook != null) {
            hook.click("Deploy");
            sleepUntil(() -> isPlayerAnimating(player), DEPLOY_TIMEOUT);
        }
    }

    private void alchItems(NetoSailingSalvConfig config) {
        if (!alchingAvailable) {
            return;
        }
        if (!Rs2Magic.canCast(Rs2Spells.HIGH_LEVEL_ALCHEMY)) {
            disableAlching("High Level Alchemy is not castable; switching to drop fallback.");
            return;
        }
        int minimum = minimumAlchValue(config);
        while (true) {
            Rs2ItemModel next = Rs2Inventory.all().stream()
                    .filter(item -> shouldAlch(item, minimum))
                    .min(Comparator.comparingInt(Rs2ItemModel::getSlot))
                    .orElse(null);
            if (next == null) {
                return;
            }
            int countBefore = Rs2Inventory.count(next.getId());
            log.info("Alching slot {}: {} (HA value {})", next.getSlot(), next.getName(), next.getHaPrice());
            Rs2Magic.alch(next);
            Rs2Player.waitForXpDrop(Skill.MAGIC, 10000, false);
            if (Rs2Inventory.count(next.getId()) >= countBefore) {
                disableAlching("High Level Alchemy failed; switching to drop fallback.");
                return;
            }
        }
    }

    private void dropJunk(NetoSailingSalvConfig config) {
        int minimum = minimumAlchValue(config);
        Rs2Inventory.dropAll(item -> shouldDrop(item, minimum));
    }

    private void fillContainers() {
        for (String container : CONTAINERS) {
            if (Rs2Inventory.hasItem(container)) {
                Rs2Inventory.interact(container, "Fill");
                sleep(250, 500);
            }
        }
    }

    static int parseMinimumAlchValue(String configured) {
        try {
            int value = Integer.parseInt(configured == null ? "" : configured.trim());
            return value < 0 ? DEFAULT_MINIMUM_ALCH_VALUE : value;
        } catch (NumberFormatException ignored) {
            return DEFAULT_MINIMUM_ALCH_VALUE;
        }
    }

    private int minimumAlchValue(NetoSailingSalvConfig config) {
        return parseMinimumAlchValue(config.minimumAlchValue());
    }

    private boolean shouldAlch(Rs2ItemModel item, int minimum) {
        return alchingAvailable && item != null && !isProtected(item) && item.isTradeable()
                && !item.isStackable() && item.getHaPrice() >= minimum;
    }

    private boolean shouldDrop(Rs2ItemModel item, int minimum) {
        if (item == null || isProtected(item) || !item.isTradeable()) {
            return false;
        }
        return !alchingAvailable || item.getHaPrice() < minimum;
    }

    private boolean isProtected(Rs2ItemModel item) {
        String name = item.getName();
        if (name == null) {
            return true;
        }
        String lower = name.toLowerCase();
        return PROTECTED_ITEM_NAMES.contains(lower)
                || lower.contains("rune pouch")
                || lower.startsWith("crafting cape")
                || lower.startsWith("sailors' amulet")
                || lower.startsWith("skills necklace")
                || lower.startsWith("ring of dueling")
                || FIRE_STAVES.stream().anyMatch(staff -> staff.equalsIgnoreCase(name));
    }

    private void disableAlching(String reason) {
        if (alchingAvailable) {
            alchingAvailable = false;
            Microbot.log(reason);
        }
    }
}
