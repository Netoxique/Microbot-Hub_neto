package net.runelite.client.plugins.microbot.netobirdhouseruns;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.coords.WorldPoint;

import net.runelite.client.Notifier;
import net.runelite.client.config.Notification;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.netobirdhouseruns.NetoBirdhouseRunsInfo.states;
import net.runelite.client.plugins.microbot.netobirdhouseruns.enums.Log;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.shortestpath.Restriction;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.netobirdhouseruns.NetoBirdhouseRunsInfo.*;

@Slf4j
public class NetoBirdhouseRunsScript extends Script {
    private static final WorldPoint birdhouseLocation1 = new WorldPoint(3763, 3755, 0);
    private static final WorldPoint birdhouseLocation2 = new WorldPoint(3768, 3761, 0);
    private static final WorldPoint birdhouseLocation3 = new WorldPoint(3677, 3882, 0);
    private static final WorldPoint birdhouseLocation4 = new WorldPoint(3679, 3815, 0);
    private static final WorldPoint VERDANT_MUSHTREE = new WorldPoint(3757, 3757, 0);
    private static final int MUSHTREE_OBJECT_ID = 30924;
    // Each location maps to a BIRDHOUSE_TRANSMIT_* varp. See isEmpty/isBuilt/isSeeded
    // below for the canonical state decoding (matches RuneLite's BirdHouseState).
    private static final int VARP_HOUSE_1 = VarPlayerID.BIRDHOUSE_TRANSMIT_D; // Verdant SW
    private static final int VARP_HOUSE_2 = VarPlayerID.BIRDHOUSE_TRANSMIT_C; // Verdant NE
    private static final int VARP_HOUSE_3 = VarPlayerID.BIRDHOUSE_TRANSMIT_A; // Meadow N
    private static final int VARP_HOUSE_4 = VarPlayerID.BIRDHOUSE_TRANSMIT_B; // Meadow S
    private static final int ARRIVAL_RADIUS = 4;
    private static final int SCENE_INTERACT_RANGE = 25;
    private static final int[] DIGSITE_PENDANT_IDS_LOWEST_CHARGE_FIRST = {
            ItemID.NECKLACE_OF_DIGSITE_1,
            ItemID.NECKLACE_OF_DIGSITE_2,
            ItemID.NECKLACE_OF_DIGSITE_3,
            ItemID.NECKLACE_OF_DIGSITE_4,
            ItemID.NECKLACE_OF_DIGSITE_5
    };
    private static final int[] DUELING_RING_IDS_LOWEST_CHARGE_FIRST = {
            ItemID.RING_OF_DUELING_1,
            ItemID.RING_OF_DUELING_2,
            ItemID.RING_OF_DUELING_3,
            ItemID.RING_OF_DUELING_4,
            ItemID.RING_OF_DUELING_5,
            ItemID.RING_OF_DUELING_6,
            ItemID.RING_OF_DUELING_7,
            ItemID.RING_OF_DUELING_8
    };
    private static final int[] CRAFTING_CAPE_IDS = {9780, 9781};
    private static final int[] FARMING_CAPE_IDS = {9810, 9811};
    private static final int VARROCK_TELEPORT_TABLET_ID = net.runelite.api.ItemID.VARROCK_TELEPORT;
    // Canonical Fossil Island region IDs (matches RuneLite's BirdHouseTracker).
    private static final java.util.Set<Integer> FOSSIL_ISLAND_REGIONS = java.util.Set.of(
            14650, 14651, 14652, 14906, 14907, 14908, 15162, 15163);
    // Single source of truth: a birdhouse-accepted seed is one whose item name
    // (lowercased) is in this set. Bank lookup, inventory lookup, and seed pick
    // all match the same way — no id-list drift, no placeholder/variant gotchas.
    // Set lists every allotment/hop/flower seed birdhouses accept (Farming
    // level ≤ 35, per OSRS Wiki).
    private static final Set<String> BIRDHOUSE_SEED_NAMES = Set.of(
            "potato seed",
            "onion seed",
            "cabbage seed",
            "tomato seed",
            "sweetcorn seed",
            "strawberry seed",
            "barley seed",
            "hammerstone seed",
            "asgarnian seed",
            "jute seed",
            "yanillian seed",
            "krandorian seed",
            "wildblood seed",
            "marigold seed",
            "rosemary seed",
            "nasturtium seed",
            "woad seed",
            "limpwurt seed"
    );
    private static final long STATE_STALL_TIMEOUT_MS = 120_000L;
    private boolean initialized;
    private String setupErrorMessage = "";
    private states lastObservedStatus;
    private long stateEnteredAtMs;
    private Log selectedLogType;
    private TravelTeleport travelTeleport = TravelTeleport.NONE;
    private BankingTeleport bankingTeleport = BankingTeleport.NONE;
    private boolean fossilTravelUsed;
    @Inject
    private Notifier notifier;
    private final NetoBirdhouseRunsPlugin plugin;

    @Inject
    NetoBirdhouseRunsScript(NetoBirdhouseRunsPlugin plugin) {
        this.plugin = plugin;
    }

    private enum TravelTeleport {
        NONE,
        DIGSITE_PENDANT,
        DUELING_RING
    }

    private enum BankingTeleport {
        NONE,
        CRAFTING_CAPE,
        FARMING_CAPE,
        DUELING_RING,
        VARROCK_TABLET
    }

    public boolean run() {
        Microbot.enableAutoRunOn = true;
        botStatus = states.GEARING;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
             
                if (!initialized) {
                    if (Rs2Player.getQuestState(Quest.BONE_VOYAGE) != QuestState.FINISHED) {
                        failAndStop("Birdhouse run failed: finish the quest 'BONE VOYAGE' first");
                        return;
                    }
                    if (Rs2Player.getRealSkillLevel(Skill.HUNTER) < 5
                            || Rs2Player.getRealSkillLevel(Skill.CRAFTING) < 5) {
                        failAndStop("Birdhouse run requires at least level 5 Hunter and Crafting");
                        return;
                    }
                    initialized = true;
                    selectedLogType = getBestLogType();
                    botStatus = states.GEARING;
                }
                if (!super.run()) return;

                if (!Rs2Walker.disableTeleports && isOnFossilIsland()) {
                    Rs2Walker.disableTeleports = true;
                    blockRubberCapMushrooms();
                    log.info("On Fossil Island — disabling teleports and rubber cap mushrooms for remaining walks");
                }

                boolean advanced = true;
                while (advanced) {
                    advanced = false;
                    if (botStatus != lastObservedStatus) {
                        log.info("State → {} (player at {}, region={}, onFossilIsland={}, inv=[{}])",
                                botStatus,
                                Rs2Player.getWorldLocation(),
                                Rs2Player.getWorldLocation() == null ? "null" : Rs2Player.getWorldLocation().getRegionID(),
                                isOnFossilIsland(),
                                dumpInventory());
                        lastObservedStatus = botStatus;
                        stateEnteredAtMs = System.currentTimeMillis();
                    } else if (botStatus != states.FINISHED
                            && System.currentTimeMillis() - stateEnteredAtMs > STATE_STALL_TIMEOUT_MS) {
                        log.error("Birdhouse run stalled in state {} for >{}ms — player at {}, inv=[{}] — aborting",
                                botStatus, STATE_STALL_TIMEOUT_MS,
                                Rs2Player.getWorldLocation(), dumpInventory());
                        shutdown();
                        return;
                    }
                    switch (botStatus) {
                        case GEARING:
                            if (!hasRequiredInventory()) {
                                // Auto bank withdrawal — only if inventory isn't already prepared.
                                if (!setupManualInventory()) {
                                    failAndStop("Birdhouse run failed: " + setupErrorMessage);
                                    return;
                                }
                            } else {
                                log.info("Inventory already prepared — skipping bank trip");
                            }
                            botStatus = states.TELEPORTING;
                            advanced = true;
                            break;
                        case TELEPORTING:
                        case VERDANT_TELEPORT:
                            if (!isOnFossilIsland() && !fossilTravelUsed) {
                                if (!useFossilIslandTeleport()) {
                                    failAndStop("Could not use the selected Fossil Island travel teleport");
                                    return;
                                }
                                fossilTravelUsed = true;
                            }
                            Rs2Walker.walkTo(birdhouseLocation1);
                            botStatus = states.DISMANTLE_HOUSE_1;
                            advanced = true;
                            break;
                        case DISMANTLE_HOUSE_1:
                            if (dismantleBirdhouse(birdhouseLocation1, VARP_HOUSE_1)) {
                                botStatus = states.BUILD_HOUSE_1;
                                advanced = true;
                            }
                            break;
                        case BUILD_HOUSE_1:
                            if (buildBirdhouse(birdhouseLocation1, VARP_HOUSE_1)) {
                                botStatus = states.SEED_HOUSE_1;
                                advanced = true;
                            }
                            break;
                        case SEED_HOUSE_1:
                            if (seedHouse(birdhouseLocation1, VARP_HOUSE_1)) {
                                botStatus = states.DISMANTLE_HOUSE_2;
                                advanced = true;
                            }
                            break;
                        case DISMANTLE_HOUSE_2:
                            if (dismantleBirdhouse(birdhouseLocation2, VARP_HOUSE_2)) {
                                  botStatus = states.BUILD_HOUSE_2;
                                advanced = true;
                            }
                            break;
                        case BUILD_HOUSE_2:
                            if (buildBirdhouse(birdhouseLocation2, VARP_HOUSE_2)) {
                                botStatus = states.SEED_HOUSE_2;
                                advanced = true;
                            }
                            break;
                        case SEED_HOUSE_2:
                            if (seedHouse(birdhouseLocation2, VARP_HOUSE_2)) {
                                botStatus = states.MUSHROOM_TELEPORT;
                                advanced = true;
                            }
                            break;
                        case MUSHROOM_TELEPORT:
                            Rs2GameObject.interact(MUSHTREE_OBJECT_ID, "Use");
                            sleepUntil(() -> Rs2Widget.findWidget("Mycelium Transportation System") != null, 5000);
                            Rs2Widget.clickWidget("Mushroom Meadow");
                            sleepUntil(() -> Rs2Player.distanceTo(birdhouseLocation3) < 20, 10000);
                            botStatus = states.DISMANTLE_HOUSE_3;
                            advanced = true;
                            break;
                        case DISMANTLE_HOUSE_3:
                            if (dismantleBirdhouse(birdhouseLocation3, VARP_HOUSE_3)) {
                                botStatus = states.BUILD_HOUSE_3;
                                advanced = true;
                            }
                            break;
                        case BUILD_HOUSE_3:
                            if (buildBirdhouse(birdhouseLocation3, VARP_HOUSE_3)) {
                                botStatus = states.SEED_HOUSE_3;
                                advanced = true;
                            }
                            break;
                        case SEED_HOUSE_3:
                            if (seedHouse(birdhouseLocation3, VARP_HOUSE_3)) {
                                botStatus = states.DISMANTLE_HOUSE_4;
                                advanced = true;
                            }
                            break;
                        case DISMANTLE_HOUSE_4:
                            if (dismantleBirdhouse(birdhouseLocation4, VARP_HOUSE_4)) {
                                botStatus = states.BUILD_HOUSE_4;
                                advanced = true;
                            }
                            break;
                        case BUILD_HOUSE_4:
                            if (buildBirdhouse(birdhouseLocation4, VARP_HOUSE_4)) {
                                botStatus = states.SEED_HOUSE_4;
                                advanced = true;
                            }
                            break;
                        case SEED_HOUSE_4:
                            if (seedHouse(birdhouseLocation4, VARP_HOUSE_4)) {
                                botStatus = states.FINISHING;
                                advanced = true;
                            }
                            break;
                        case FINISHING:
                            emptyNests();
                            Rs2Walker.disableTeleports = false;
                            useBankingTeleport();
                            Rs2Walker.walkTo(Rs2Bank.getNearestBank().getWorldPoint(), 20);
                            if (!Rs2Bank.isOpen()) Rs2Bank.openBank();
                            if (!sleepUntil(Rs2Bank::isOpen, 10000)) {
                                failAndStop("Could not open a bank after the birdhouse run");
                                return;
                            }
                            Rs2Bank.depositAll();

                            botStatus = states.FINISHED;
                            notifier.notify(Notification.ON, "Birdhouse run is finished.");
                            log.info("Birdhouse run finished — disabling plugin.");

                            Microbot.stopPlugin(plugin);
                            break;
                        case FINISHED:
                            break;
                    }
                }

            } catch (Exception ex) {
                log.error("Error in birdhouse run script", ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private boolean useFossilIslandTeleport() {
        WorldPoint origin = Rs2Player.getWorldLocation();
        boolean interacted;
        switch (travelTeleport) {
            case DIGSITE_PENDANT:
                int pendantId = firstInventoryItem(DIGSITE_PENDANT_IDS_LOWEST_CHARGE_FIRST);
                interacted = pendantId != -1 && Rs2Inventory.interact(pendantId, "Rub");
                if (!interacted) return false;
                if (!sleepUntil(() -> Rs2Dialogue.hasDialogueOption("Fossil Island"), 5000)) return false;
                if (!Rs2Dialogue.clickOption("Fossil Island")) return false;
                break;
            case DUELING_RING:
                int ringId = firstInventoryItem(DUELING_RING_IDS_LOWEST_CHARGE_FIRST);
                interacted = ringId != -1 && Rs2Inventory.interact(ringId, "Emir's Arena");
                if (!interacted) return false;
                break;
            case NONE:
                return isOnFossilIsland();
            default:
                return false;
        }
        return sleepUntil(() -> hasTeleportedFrom(origin), 10000);
    }

    private boolean useBankingTeleport() {
        if (bankingTeleport == BankingTeleport.NONE) {
            log.info("Walking to the nearest bank because no banking teleport was available");
            return false;
        }
        WorldPoint origin = Rs2Player.getWorldLocation();
        boolean interacted;
        switch (bankingTeleport) {
            case CRAFTING_CAPE:
                interacted = interactTeleport(CRAFTING_CAPE_IDS, "Teleport");
                break;
            case FARMING_CAPE:
                interacted = interactTeleport(FARMING_CAPE_IDS, "Teleport");
                break;
            case DUELING_RING:
                interacted = interactTeleport(DUELING_RING_IDS_LOWEST_CHARGE_FIRST, "Castle Wars");
                break;
            case VARROCK_TABLET:
                interacted = Rs2Inventory.interact(VARROCK_TELEPORT_TABLET_ID, "Break");
                break;
            case NONE:
            default:
                interacted = false;
                break;
        }
        if (!interacted || !sleepUntil(() -> hasTeleportedFrom(origin), 10000)) {
            log.warn("Banking teleport {} failed; walking to the nearest bank", bankingTeleport);
            return false;
        }
        return true;
    }

    private static boolean interactTeleport(int[] itemIds, String action) {
        for (int itemId : itemIds) {
            if (Rs2Equipment.isWearing(itemId)) return Rs2Equipment.interact(itemId, action);
        }
        int inventoryItemId = firstInventoryItem(itemIds);
        return inventoryItemId != -1 && Rs2Inventory.interact(inventoryItemId, action);
    }

    private static boolean hasTeleportedFrom(WorldPoint origin) {
        WorldPoint current = Rs2Player.getWorldLocation();
        return origin != null && current != null && origin.distanceTo(current) > 20;
    }

    private void failAndStop(String message) {
        setupErrorMessage = message;
        log.error(message);
        Microbot.showMessage(message);
        Microbot.stopPlugin(plugin);
    }

    private void emptyNests() {
        var ids = List.of(
                ItemID.BIRD_NEST_EGG_RED,
                ItemID.BIRD_NEST_EGG_GREEN,
                ItemID.BIRD_NEST_EGG_BLUE,
                ItemID.BIRD_NEST_SEEDS,
                ItemID.BIRD_NEST_RING,
                ItemID.BIRD_NEST_SEEDS_JAN2019,
                ItemID.BIRD_NEST_DECENTSEEDS_JAN2019
        );

        Rs2Inventory.items().forEachOrdered(item -> {
            if (ids.contains(item.getId())) {
                Rs2Inventory.interact(item, "Search");
            }
        });
    }

    @Override
    public void shutdown() {
        super.shutdown();
        Rs2Walker.disableTeleports = false;
        ShortestPathPlugin.getPathfinderConfig().setRestrictedTiles();
        initialized = false;
        botStatus = states.TELEPORTING;
        lastObservedStatus = null;
        stateEnteredAtMs = 0L;
        selectedLogType = null;
        travelTeleport = TravelTeleport.NONE;
        bankingTeleport = BankingTeleport.NONE;
        fossilTravelUsed = false;
    }

    private static void blockRubberCapMushrooms() {
        Restriction[] restrictions = new Restriction[] {
                new Restriction(3663, 3808, 0),
                new Restriction(3664, 3808, 0),
                new Restriction(3665, 3808, 0),
                new Restriction(3666, 3809, 0),
                new Restriction(3666, 3810, 0)
        };
        ShortestPathPlugin.getPathfinderConfig().setRestrictedTiles(restrictions);
    }

    /** Throttle for arrivedAndStill log lines (one per second per target). */
    private long lastArrivedLogMs;
    private WorldPoint lastArrivedLogTarget;

    private boolean arrivedAndStill(WorldPoint loc) {
        WorldPoint pos = Rs2Player.getWorldLocation();
        int dist = Rs2Player.distanceTo(loc);
        if (dist <= ARRIVAL_RADIUS) {
            return true;
        }
        boolean moving = Rs2Player.isMoving();
        long now = System.currentTimeMillis();
        boolean logThisTick = !loc.equals(lastArrivedLogTarget) || now - lastArrivedLogMs >= 1000;
        if (moving) {
            if (logThisTick) {
                log.info("arrivedAndStill[{}]: moving (at {}, dist={})", loc, pos, dist);
                lastArrivedLogMs = now;
                lastArrivedLogTarget = loc;
            }
            return false;
        }
        if (logThisTick) {
            log.info("arrivedAndStill[{}]: not arrived (at {}, dist={}); walking via WebWalker (stop at {})",
                    loc, pos, dist, SCENE_INTERACT_RANGE);
            lastArrivedLogMs = now;
            lastArrivedLogTarget = loc;
        }
        Rs2Walker.walkTo(loc, SCENE_INTERACT_RANGE);
        return false;
    }

    // Canonical state predicates, matching BirdHouseState.fromVarpValue:
    //   varp == 0          → EMPTY space (need to Build)
    //   varp > 0, %3 != 0  → BUILT (covers "just built, no seeds" and "seeded, growing")
    //   varp > 0, %3 == 0  → SEEDED, ready (Empty action available)
    private static boolean isEmpty(int varp)  { return varp == 0; }
    private static boolean isBuilt(int varp)  { return varp > 0 && varp % 3 != 0; }
    private static boolean isSeeded(int varp) { return varp > 0 && varp % 3 == 0; }

    /** Click Empty on the birdhouse at {@code loc}. Wait for varp to hit 0. */
    private boolean dismantleBirdhouse(WorldPoint loc, int varpId) {
        if (!isOnFossilIsland()) {
            if (!arrivedAndStill(loc)) return false;
        }
        int varp = Microbot.getVarbitPlayerValue(varpId);
        if (!isSeeded(varp)) {
            log.info("Dismantle[{}]: varp={} not seeded (empty={}, built={}) — skipping",
                    varpId, varp, isEmpty(varp), isBuilt(varp));
            return true;
        }
        log.info("Dismantle[{}]: varp={} → Empty at {}", varpId, varp, loc);
        if (!Rs2GameObject.interact(loc, "Empty")) {
            if (!arrivedAndStill(loc)) return false;
            log.warn("Dismantle[{}]: object not found at {} after arriving", varpId, loc);
            return false;
        }
        if (!sleepUntil(() -> isEmpty(Microbot.getVarbitPlayerValue(varpId)), 10000)) {
            log.warn("Dismantle[{}]: timeout waiting for varp→0 after Empty click; varp={} (player at {})",
                    varpId, Microbot.getVarbitPlayerValue(varpId), Rs2Player.getWorldLocation());
            return false;
        }
        log.info("Dismantle[{}]: success (varp=0)", varpId);
        return true;
    }

    /** Click Build at {@code loc}. Game auto-combines hammer+log. Wait for varp != 0. */
    private boolean buildBirdhouse(WorldPoint loc, int varpId) {
        if (!isOnFossilIsland()) {
            if (!arrivedAndStill(loc)) return false;
        }
        int varp = Microbot.getVarbitPlayerValue(varpId);
        if (!isEmpty(varp)) {
            log.info("Build[{}]: varp={} not empty (built={}, seeded={}) — skipping",
                    varpId, varp, isBuilt(varp), isSeeded(varp));
            return true;
        }
        int logCount = Rs2Inventory.count(selectedLogType.getItemId());
        if (logCount == 0) {
            log.error("Build[{}]: no {} (id={}) in inventory — aborting. Inventory: [{}]",
                    varpId, selectedLogType.getItemName(), selectedLogType.getItemId(), dumpInventory());
            shutdown();
            return false;
        }
        log.info("Build[{}]: varp=0 → Build at {} (logs in inv: {})", varpId, loc, logCount);
        if (!Rs2GameObject.interact(loc, "Build")) {
            if (!arrivedAndStill(loc)) return false;
            log.warn("Build[{}]: object not found at {} after arriving", varpId, loc);
            return false;
        }
        if (!sleepUntil(() -> !isEmpty(Microbot.getVarbitPlayerValue(varpId)), 15000)) {
            log.warn("Build[{}]: timeout waiting for varp!=0 after Build click; varp={} (player at {}, logs={})",
                    varpId, Microbot.getVarbitPlayerValue(varpId), Rs2Player.getWorldLocation(),
                    Rs2Inventory.count(selectedLogType.getItemId()));
            return false;
        }
        log.info("Build[{}]: success (varp={})", varpId, Microbot.getVarbitPlayerValue(varpId));
        return true;
    }

    /** Use a seed stack on the birdhouse at {@code loc}. Wait for seeds-down OR varp change. */
    private boolean seedHouse(WorldPoint loc, int varpId) {
        if (!isOnFossilIsland()) {
            if (!arrivedAndStill(loc)) return false;
        }
        int varp = Microbot.getVarbitPlayerValue(varpId);
        if (isEmpty(varp)) {
            log.error("Seed[{}]: varp=0, can't seed empty space — aborting. Inventory: [{}]",
                    varpId, dumpInventory());
            shutdown();
            return false;
        }
        if (isSeeded(varp)) {
            log.info("Seed[{}]: varp={} already seeded — skipping", varpId, varp);
            return true;
        }
        Rs2ItemModel seed = findInventoryBirdhouseSeed(10).orElse(null);
        if (seed == null) {
            log.error("Seed[{}]: no birdhouse-seed stack of ≥10 — aborting. Inventory: [{}]",
                    varpId, dumpInventory());
            shutdown();
            return false;
        }
        int seedId = seed.getId();
        int seedsBefore = seed.getQuantity();
        int varpBefore = varp;
        log.info("Seed[{}]: use {} id={} (x{}) on {} (varp before={})",
                varpId, seed.getName(), seedId, seedsBefore, loc, varpBefore);
        if (!Rs2Inventory.use(seedId)) {
            log.warn("Seed[{}]: Rs2Inventory.use({}) returned false. Inventory: [{}]",
                    varpId, seedId, dumpInventory());
            return false;
        }
        if (!sleepUntil(() -> Rs2Inventory.getSelectedItemId() == seedId, 2000)) {
            log.warn("Seed[{}]: seed not selected within 2s. getSelectedItemId={}, looking for {}",
                    varpId, Rs2Inventory.getSelectedItemId(), seedId);
            return false;
        }
        log.info("Seed[{}]: seed selected (id={}); clicking birdhouse at {}", varpId, seedId, loc);
        if (!Rs2GameObject.interact(loc)) {
            if (!arrivedAndStill(loc)) return false;
            log.warn("Seed[{}]: object not found at {} after arriving", varpId, loc);
            return false;
        }
        if (!sleepUntil(() ->
                findInventoryBirdhouseSeed(1).map(Rs2ItemModel::getQuantity).orElse(0) < seedsBefore,
                10000)) {
            int seedsNow = findInventoryBirdhouseSeed(1).map(Rs2ItemModel::getQuantity).orElse(0);
            log.warn("Seed[{}]: no completion signal within 10s. seeds={} (before {}), varp={} (before {})",
                    varpId, seedsNow, seedsBefore,
                    Microbot.getVarbitPlayerValue(varpId), varpBefore);
            return false;
        }
        int seedsAfter = findInventoryBirdhouseSeed(1).map(Rs2ItemModel::getQuantity).orElse(0);
        log.info("Seed[{}]: success (varp={}, seeds left={})",
                varpId, Microbot.getVarbitPlayerValue(varpId), seedsAfter);
        return true;
    }

    /** True if {@code item}'s lowercased name is in {@link #BIRDHOUSE_SEED_NAMES}. */
    private static boolean isBirdhouseSeed(Rs2ItemModel item) {
        if (item == null) return false;
        String name = item.getName();
        return name != null && BIRDHOUSE_SEED_NAMES.contains(name.toLowerCase());
    }

    /** First inventory stack of a birdhouse-accepted seed with quantity ≥ {@code minQty}. */
    private static Optional<Rs2ItemModel> findInventoryBirdhouseSeed(int minQty) {
        return Rs2Inventory.items()
                .filter(NetoBirdhouseRunsScript::isBirdhouseSeed)
                .filter(item -> item.getQuantity() >= minQty)
                .findFirst();
    }

    /** First bank stack of a birdhouse-accepted seed with quantity ≥ {@code minQty}. */
    private static Optional<Rs2ItemModel> findBankBirdhouseSeed(int minQty) {
        return Rs2Bank.bankItems().stream()
                .filter(NetoBirdhouseRunsScript::isBirdhouseSeed)
                .filter(item -> item.getQuantity() >= minQty)
                .findFirst();
    }

    /** Compact "name×qty(id=...)" listing of every inventory item, for diagnostics. */
    private static String dumpInventory() {
        return Rs2Inventory.items()
                .map(item -> item.getName() + "×" + item.getQuantity() + "(id=" + item.getId() + ")")
                .collect(Collectors.joining(", "));
    }

    private boolean isOnFossilIsland() {
        WorldPoint loc = Rs2Player.getWorldLocation();
        return loc != null && FOSSIL_ISLAND_REGIONS.contains(loc.getRegionID());
    }

    private Log getBestLogType() {
        return getBestLogType(
                Rs2Player.getRealSkillLevel(Skill.HUNTER),
                Rs2Player.getRealSkillLevel(Skill.CRAFTING));
    }

    static Log getBestLogType(int hunterLevel, int craftingLevel) {
        if (hunterLevel >= 89 && craftingLevel >= 90) return Log.REDWOOD_LOGS;
        if (hunterLevel >= 74 && craftingLevel >= 75) return Log.MAGIC_LOGS;
        if (hunterLevel >= 59 && craftingLevel >= 60) return Log.YEW_LOGS;
        if (hunterLevel >= 49 && craftingLevel >= 50) return Log.MAHOGANY_LOGS;
        if (hunterLevel >= 44 && craftingLevel >= 45) return Log.MAPLE_LOGS;
        if (hunterLevel >= 34 && craftingLevel >= 35) return Log.TEAK_LOGS;
        if (hunterLevel >= 24 && craftingLevel >= 25) return Log.WILLOW_LOGS;
        if (hunterLevel >= 14 && craftingLevel >= 15) return Log.OAK_LOGS;
        return Log.NORMAL_LOGS;
    }

    private static boolean hasAnyInventoryItem(int[] itemIds) {
        return firstInventoryItem(itemIds) != -1;
    }

    private static int firstInventoryItem(int[] itemIds) {
        for (int itemId : itemIds) {
            if (Rs2Inventory.contains(itemId)) return itemId;
        }
        return -1;
    }

    private static boolean hasAnyEquippedItem(int[] itemIds) {
        for (int itemId : itemIds) {
            if (Rs2Equipment.isWearing(itemId)) return true;
        }
        return false;
    }

    private void selectCarriedTeleports() {
        if (isOnFossilIsland()) {
            travelTeleport = TravelTeleport.NONE;
        } else if (hasAnyInventoryItem(DIGSITE_PENDANT_IDS_LOWEST_CHARGE_FIRST)) {
            travelTeleport = TravelTeleport.DIGSITE_PENDANT;
        } else if (hasAnyInventoryItem(DUELING_RING_IDS_LOWEST_CHARGE_FIRST)) {
            travelTeleport = TravelTeleport.DUELING_RING;
        } else {
            travelTeleport = TravelTeleport.NONE;
        }

        if (hasAnyEquippedItem(CRAFTING_CAPE_IDS) || hasAnyInventoryItem(CRAFTING_CAPE_IDS)) {
            bankingTeleport = BankingTeleport.CRAFTING_CAPE;
        } else if (hasAnyEquippedItem(FARMING_CAPE_IDS) || hasAnyInventoryItem(FARMING_CAPE_IDS)) {
            bankingTeleport = BankingTeleport.FARMING_CAPE;
        } else if (hasAnyEquippedItem(DUELING_RING_IDS_LOWEST_CHARGE_FIRST)
                || countInventoryItems(DUELING_RING_IDS_LOWEST_CHARGE_FIRST)
                >= (travelTeleport == TravelTeleport.DUELING_RING ? 2 : 1)) {
            bankingTeleport = BankingTeleport.DUELING_RING;
        } else if (Rs2Inventory.contains(VARROCK_TELEPORT_TABLET_ID)) {
            bankingTeleport = BankingTeleport.VARROCK_TABLET;
        } else {
            bankingTeleport = BankingTeleport.NONE;
        }
    }

    private static int countInventoryItems(int[] itemIds) {
        int count = 0;
        for (int itemId : itemIds) count += Rs2Inventory.count(itemId);
        return count;
    }

    /** True if the inventory already has everything a full run needs. The digsite
     *  pendant is only required when off Fossil Island (its sole purpose is the
     *  teleport onto the island); on-island, we can just walk. */
    private boolean hasRequiredInventory() {
        if (Rs2Inventory.count(ItemID.CHISEL) < 1) {
            log.info("hasRequiredInventory: no chisel");
            return false;
        }
        if (Rs2Inventory.count(ItemID.HAMMER) < 1) {
            log.info("hasRequiredInventory: no hammer");
            return false;
        }
        selectCarriedTeleports();
        if (!isOnFossilIsland()) {
            if (travelTeleport == TravelTeleport.NONE) {
                log.info("hasRequiredInventory: off-island and no digsite pendant or ring of dueling");
                return false;
            }
        }
        int logCount = Rs2Inventory.count(selectedLogType.getItemId());
        if (logCount < 4) {
            log.info("hasRequiredInventory: only {} {} (need 4)", logCount, selectedLogType.getItemName());
            return false;
        }
        Optional<Rs2ItemModel> seed = findInventoryBirdhouseSeed(40);
        if (seed.isEmpty()) {
            log.info("hasRequiredInventory: no birdhouse-seed stack ≥ 40 in inventory. Inventory: [{}]",
                    dumpInventory());
            return false;
        }
        log.info("hasRequiredInventory: OK ({} x{}, {} logs)",
                seed.get().getName(), seed.get().getQuantity(), logCount);
        return true;
    }

    private boolean setupManualInventory() {
        log.info("setupManualInventory: start (player at {}, onFossilIsland={}, inv=[{}])",
                Rs2Player.getWorldLocation(), isOnFossilIsland(), dumpInventory());
        Rs2Walker.walkTo(Rs2Bank.getNearestBank().getWorldPoint(), 20);

        if (!Rs2Bank.openBank()) {
            setupErrorMessage = "Could not open bank";
            log.error(setupErrorMessage);
            return false;
        }
        sleepUntil(Rs2Bank::isOpen);
        log.info("setupManualInventory: bank open at {}", Rs2Player.getWorldLocation());

        Rs2Bank.depositAll();
        Rs2Inventory.waitForInventoryChanges(5000);
        log.info("setupManualInventory: after depositAll, inv=[{}]", dumpInventory());

        if (!Rs2Bank.withdrawX(ItemID.CHISEL, 1)) {
            setupErrorMessage = "Missing chisel in bank";
            log.error(setupErrorMessage);
            return false;
        }
        Rs2Inventory.waitForInventoryChanges(2000);
        log.info("setupManualInventory: chisel withdrawn (inv count={})", Rs2Inventory.count(ItemID.CHISEL));

        if (!Rs2Bank.withdrawX(ItemID.HAMMER, 1)) {
            setupErrorMessage = "Missing hammer in bank";
            log.error(setupErrorMessage);
            return false;
        }
        Rs2Inventory.waitForInventoryChanges(2000);
        log.info("setupManualInventory: hammer withdrawn (inv count={})", Rs2Inventory.count(ItemID.HAMMER));

        if (!withdrawSeeds()) {
            // setupErrorMessage is set in withdrawSeeds
            return false;
        }

        int bankLogCount = Rs2Bank.count(selectedLogType.getItemId());
        if (bankLogCount < 4) {
            setupErrorMessage = "Need 4 " + selectedLogType.getItemName().toLowerCase() + " but only have " + bankLogCount + " in bank";
            log.error(setupErrorMessage);
            return false;
        }
        if (!Rs2Bank.withdrawX(selectedLogType.getItemId(), 4)) {
            setupErrorMessage = "Failed to withdraw " + selectedLogType.getItemName().toLowerCase();
            log.error(setupErrorMessage);
            return false;
        }
        Rs2Inventory.waitForInventoryChanges(2000);
        log.info("setupManualInventory: 4× {} withdrawn (inv count={})",
                selectedLogType.getItemName(), Rs2Inventory.count(selectedLogType.getItemId()));

        if (!withdrawFossilIslandTeleport()) {
            return false;
        }

        withdrawBankingTeleport();

        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen());

        log.info("setupManualInventory: complete. Final inv=[{}]", dumpInventory());
        return true;
    }

    private boolean withdrawFossilIslandTeleport() {
        // Withdraw digsite pendant (prefer lower charges) — only when off Fossil Island.
        // If we're already on the island, the pendant is dead weight; don't burn a charge.
        if (isOnFossilIsland()) {
            travelTeleport = TravelTeleport.NONE;
            log.info("setupManualInventory: on Fossil Island, skipping arrival teleport withdrawal");
            return true;
        }
        int itemId = withdrawFirstAvailable(DIGSITE_PENDANT_IDS_LOWEST_CHARGE_FIRST);
        if (itemId != -1) {
            travelTeleport = TravelTeleport.DIGSITE_PENDANT;
            log.info("setupManualInventory: digsite pendant withdrawn (id={})", itemId);
            return true;
        }
        itemId = withdrawFirstAvailable(DUELING_RING_IDS_LOWEST_CHARGE_FIRST);
        if (itemId != -1) {
            travelTeleport = TravelTeleport.DUELING_RING;
            log.info("setupManualInventory: ring of dueling withdrawn for Emir's Arena (id={})", itemId);
            return true;
        }
        setupErrorMessage = "Missing digsite pendant or ring of dueling in bank";
        log.error(setupErrorMessage);
        return false;
    }

    private void withdrawBankingTeleport() {
        if (hasAnyEquippedItem(CRAFTING_CAPE_IDS)) {
            bankingTeleport = BankingTeleport.CRAFTING_CAPE;
            return;
        }
        int itemId = withdrawFirstAvailable(CRAFTING_CAPE_IDS);
        if (itemId != -1) {
            bankingTeleport = BankingTeleport.CRAFTING_CAPE;
            return;
        }
        if (hasAnyEquippedItem(FARMING_CAPE_IDS)) {
            bankingTeleport = BankingTeleport.FARMING_CAPE;
            return;
        }
        itemId = withdrawFirstAvailable(FARMING_CAPE_IDS);
        if (itemId != -1) {
            bankingTeleport = BankingTeleport.FARMING_CAPE;
            return;
        }
        if (travelTeleport != TravelTeleport.DUELING_RING
                && (hasAnyEquippedItem(DUELING_RING_IDS_LOWEST_CHARGE_FIRST)
                || hasAnyInventoryItem(DUELING_RING_IDS_LOWEST_CHARGE_FIRST))) {
            bankingTeleport = BankingTeleport.DUELING_RING;
            return;
        }
        itemId = withdrawFirstAvailable(DUELING_RING_IDS_LOWEST_CHARGE_FIRST);
        if (itemId != -1) {
            bankingTeleport = BankingTeleport.DUELING_RING;
            return;
        }
        if (Rs2Bank.count(VARROCK_TELEPORT_TABLET_ID) > 0
                && Rs2Bank.withdrawX(VARROCK_TELEPORT_TABLET_ID, 1)) {
            Rs2Inventory.waitForInventoryChanges(2000);
            bankingTeleport = BankingTeleport.VARROCK_TABLET;
            return;
        }
        bankingTeleport = BankingTeleport.NONE;
        log.info("No banking teleport available; the run will walk to the nearest bank");
    }

    private int withdrawFirstAvailable(int[] itemIds) {
        for (int itemId : itemIds) {
            if (!isRunning()) break;
            if (Rs2Bank.count(itemId) > 0 && Rs2Bank.withdrawX(itemId, 1)) {
                Rs2Inventory.waitForInventoryChanges(2000);
                return itemId;
            }
        }
        return -1;
    }

    private boolean withdrawSeeds() {
        // Log every birdhouse-eligible seed stack the bank has, so we can see
        // both what was picked AND what the alternatives were.
        String bankSeedSummary = Rs2Bank.bankItems().stream()
                .filter(NetoBirdhouseRunsScript::isBirdhouseSeed)
                .map(item -> item.getName() + "×" + item.getQuantity() + "(id=" + item.getId() + ")")
                .collect(Collectors.joining(", "));
        log.info("withdrawSeeds: bank birdhouse-seed stacks: [{}]", bankSeedSummary);

        Rs2ItemModel bankSeed = findBankBirdhouseSeed(40).orElse(null);
        if (bankSeed == null) {
            setupErrorMessage = "Need 40 seeds but no birdhouse seed type has 40+ in bank";
            log.error(setupErrorMessage);
            return false;
        }
        int invBefore = Rs2Inventory.count(bankSeed.getId());
        log.info("withdrawSeeds: selected {} (id={}, bank qty={}); inv before withdraw: {} of id={}",
                bankSeed.getName(), bankSeed.getId(), bankSeed.getQuantity(), invBefore, bankSeed.getId());
        if (!Rs2Bank.withdrawX(bankSeed.getId(), 40)) {
            setupErrorMessage = "Failed to withdraw 40 " + bankSeed.getName();
            log.error(setupErrorMessage);
            return false;
        }
        Rs2Inventory.waitForInventoryChanges(3000);
        int invAfter = findInventoryBirdhouseSeed(1).map(Rs2ItemModel::getQuantity).orElse(0);
        log.info("withdrawSeeds: withdrew 40 {} (id={}); inv seed qty = {}",
                bankSeed.getName(), bankSeed.getId(), invAfter);
        if (invAfter < 40) {
            setupErrorMessage = "Withdrew seeds but only got " + invAfter + " (need 40)";
            log.error(setupErrorMessage);
            return false;
        }
        return true;
    }
}
