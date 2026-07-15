package net.runelite.client.plugins.microbot.netowoodcutting;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.AnimationID;
import net.runelite.api.GameObject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.grounditem.LootingParameters;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.InteractOrder;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2LogBasket;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.skills.fletching.Rs2Fletching;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.netowoodcutting.enums.*;
import net.runelite.client.plugins.microbot.netowoodcutting.enums.WoodcuttingTreeLocations;
import net.runelite.client.plugins.microbot.shared.session.NetoBreakManager;
import net.runelite.client.plugins.microbot.shared.session.NetoWorldHopManager;
import net.runelite.client.plugins.microbot.shared.session.NetoRuntimeDisable;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;

import javax.inject.Inject;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static net.runelite.api.gameval.AnimationID.*;
import static net.runelite.api.gameval.ItemID.TINDERBOX;
import static net.runelite.client.plugins.microbot.util.player.Rs2Player.getRealSkillLevel;


@Slf4j
public class NetoWoodcuttingScript extends Script {

    public static final List<Integer> BURNING_ANIMATION_IDS = List.of(
            FORESTRY_CAMPFIRE_BURNING_LOGS,
            FORESTRY_CAMPFIRE_BURNING_MAGIC_LOGS,
            FORESTRY_CAMPFIRE_BURNING_MAHOGANY_LOGS,
            FORESTRY_CAMPFIRE_BURNING_MAPLE_LOGS,
            FORESTRY_CAMPFIRE_BURNING_OAK_LOGS,
            FORESTRY_CAMPFIRE_BURNING_REDWOOD_LOGS,
            FORESTRY_CAMPFIRE_BURNING_TEAK_LOGS,
            FORESTRY_CAMPFIRE_BURNING_WILLOW_LOGS,
            FORESTRY_CAMPFIRE_BURNING_YEW_LOGS,
            HUMAN_CREATEFIRE
    );

    public static final int FORESTRY_DISTANCE = 15;
    private static final List<WoodcuttingTree> PROGRESSIVE_TREE_ORDER = List.of(
            WoodcuttingTree.TREE,
            WoodcuttingTree.OAK,
            WoodcuttingTree.WILLOW,
            WoodcuttingTree.TEAK_TREE,
            WoodcuttingTree.MAPLE,
            WoodcuttingTree.MAHOGANY,
            WoodcuttingTree.YEW,
            WoodcuttingTree.MAGIC,
            WoodcuttingTree.REDWOOD
    );
    private static WorldPoint returnPoint;
    public volatile boolean cannotLightFire = false;
    WoodcuttingScriptState woodcuttingScriptState = WoodcuttingScriptState.PREP;
    private boolean hasAutoHopMessageShown = false;
    private final NetoWoodcuttingPlugin plugin;
    public int currentLogBasketCount = -1;
    private WoodcuttingTree activeTree = WoodcuttingTree.TREE;
    private ResourceLocationOption activeLocation;
    @Inject
    public NetoWoodcuttingScript(NetoWoodcuttingPlugin plugin) {
        this.plugin = plugin;
    }
    @Inject
    private NetoBreakManager breakManager;
    @Inject
    private NetoWorldHopManager worldHopManager;
    @Inject
    private NetoRuntimeDisable runtimeDisable;
    @Inject
    Rs2TileObjectCache rs2TileObjectCache;

    private void handleFiremaking(NetoWoodcuttingConfig config) {
        WoodcuttingTree treeType = getActiveTree();

        if (!Rs2Inventory.hasItem(TINDERBOX)) {
            Rs2Bank.openBank();
            sleepUntil(Rs2Bank::isOpen, 20000);
            Rs2Bank.withdrawItem(true, "Tinderbox");
        }

        if (!Rs2Inventory.hasItem(treeType.getLog())) {
            Microbot.log("Opening bank");
            Rs2Bank.openBank();
            sleepUntil(Rs2Bank::isOpen, 20000);
            Rs2Bank.withdrawAll(treeType.getLog());
            Rs2Bank.closeBank();
            sleep(500, 1200);
        }
    }

    public static WorldPoint getReturnPoint(NetoWoodcuttingConfig config) {
        if (config.walkBack().equals(WoodcuttingWalkBack.LAST_LOCATION)) {
            return returnPoint == null ? Rs2Player.getWorldLocation() : returnPoint;
        } else {
            return initialPlayerLocation == null ? Rs2Player.getWorldLocation() : initialPlayerLocation;
        }
    }

    public boolean run(NetoWoodcuttingConfig config) {
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyWoodcuttingSetup();
        Rs2AntibanSettings.dynamicActivity = false;
        Rs2AntibanSettings.dynamicIntensity = false;
        Rs2Antiban.setActivityIntensity(ActivityIntensity.LOW);
        woodcuttingScriptState = WoodcuttingScriptState.PREP;
        activeTree = config.TREE();
        activeLocation = null;

        breakManager.configure(config, "Neto Woodcutting");
        worldHopManager.configure(config, "Neto Woodcutting");
        runtimeDisable.configure(config, "Neto Woodcutting");

        breakManager.reset();
        worldHopManager.reset();
        runtimeDisable.reset();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (preFlightChecks(config)) return;
                switch (woodcuttingScriptState) {
                    case PREP:
                        handlePrep(config);
                        break;
                    case WOODCUTTING:
                        if (beforeCuttingTreesChecks(config)) return;
                        handleWoodcutting(config);
                        break;
                    case FIREMAKING:
                        handleFiremaking(config);
                        walkBack(config);
                        woodcuttingScriptState = WoodcuttingScriptState.RESETTING;
                        break;
                    case RESETTING:
                        resetInventory(config);
                }
            } catch (Exception ex) {
                Microbot.log(ex.getMessage());
                ex.printStackTrace();
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }

    private void handleWoodcutting(NetoWoodcuttingConfig config) {
        WoodcuttingTree treeType = getActiveTree();
        Rs2TileObjectModel tree = null;
        if (config.HardwoodTreePatch()) {
            var patchIds = List.of(30480, 30481, 30482);
            tree = rs2TileObjectCache.query()
                    .where(x -> patchIds.contains(x.getId()))
                    .nearest();
        } else {
            tree = rs2TileObjectCache.query().within(getInitialPlayerLocation(), config.distanceToStray()).withName(treeType.getName()).nearestOnClientThread();
        }

        if (tree != null) {
            if (tree.click(treeType.getAction())) {
                Rs2Player.waitForAnimation();
                Rs2Antiban.actionCooldown();

                if (config.walkBack().equals(WoodcuttingWalkBack.LAST_LOCATION)) {
                    returnPoint = Rs2Player.getWorldLocation();
                }
            }
        }
    }

    private boolean beforeCuttingTreesChecks(NetoWoodcuttingConfig config) {
        WoodcuttingTree treeType = getActiveTree();

        if (Rs2Equipment.isWearing(ItemID.DRAGON_AXE) || Rs2Equipment.isWearing(ItemID.DRAGON_AXE_2H) || Rs2Equipment.isWearing(ItemID.CRYSTAL_AXE) ||
                Rs2Equipment.isWearing(ItemID.CRYSTAL_AXE_2H) || Rs2Equipment.isWearing(ItemID.INFERNAL_AXE) ||
                Rs2Equipment.isWearing(ItemID.TRAILBLAZER_AXE))
            Rs2Combat.setSpecState(true, 1000);
        boolean willBank = willBankItems(config);
        int currentLogCountBeforeFill = Rs2Inventory.count(treeType.getLogID());
        if ( currentLogCountBeforeFill > 0 && currentLogBasketCount < Rs2LogBasket.LOG_BASKET_CAPACITY && Rs2LogBasket.hasLogBasket()  && willBank) {
            if (currentLogBasketCount == -1) {
                Rs2LogBasket.BasketContents content  = Rs2LogBasket.getCurrentBasketContents();
                currentLogBasketCount = content == null ? 0 : content.quantity;
                log.info("Initialized log basket count to {}", currentLogBasketCount);
            }
            if(currentLogBasketCount < Rs2LogBasket.LOG_BASKET_CAPACITY && Rs2Inventory.isFull() && Rs2Inventory.contains(treeType.getLog())) {

                if (Rs2LogBasket.fillLogBasket()) {
                    Rs2Antiban.actionCooldown();
                }
                int currentLogCountAfterFill = Rs2Inventory.count(treeType.getLogID());
                int addedLogs = currentLogCountBeforeFill - currentLogCountAfterFill;
                currentLogBasketCount += addedLogs;
                log.info("Added {} logs to basket, current count: {}", addedLogs, currentLogBasketCount);
            }
        }
       

        if (Rs2Inventory.isFull()) {
            woodcuttingScriptState = WoodcuttingScriptState.RESETTING;
            return true;
        }

        if (handleLooting(config)) {
            Rs2Antiban.actionCooldown();
            return true;
        }

        return false;
    }

    private boolean preFlightChecks(NetoWoodcuttingConfig config) {
        if (!Microbot.isLoggedIn()) {
            if (runtimeDisable.updateRuntime(NetoWoodcuttingPlugin.class)) return true;
            if (breakManager.updateBreakState()) return true;
            return true;
        }
        if (!super.run()) return true;
        if (Rs2Player.getRealSkillLevel(Skill.WOODCUTTING) <= 0) return true;

        if (!config.enableWoodcutting()) {
            updateActiveTree(config);
            return true;
        }

        if (config.hopWhenPlayerDetected()) {
            if (Rs2Player.logoutIfPlayerDetected(1, 10000))
                return true;
        }

        if (Rs2AntibanSettings.actionCooldownActive) return true;

        if (!hasAutoHopMessageShown && config.hopWhenPlayerDetected()) {
            Microbot.showMessage("Make sure autologin plugin is enabled and randomWorld checkbox is checked!");
            hasAutoHopMessageShown = true;
        }

        if (config.hopWhenPlayerDetected() && config.enableForestry()) {
            Microbot.showMessage("Autohop is not supported with forestry enabled, shutting down.");
            shutdown();
            return true;
        }

        if (woodcuttingScriptState != WoodcuttingScriptState.PREP) {
            if (initialPlayerLocation == null) {
                initialPlayerLocation = Rs2Player.getWorldLocation();
            }

            if (returnPoint == null) {
                returnPoint = Rs2Player.getWorldLocation();
            }
        }

        updateActiveTree(config);

        if (config.progressiveMode() && ensureProgressiveLocation(config)) {
            return true;
        }

        if (!getActiveTree().hasRequiredLevel()) {
            Microbot.getClientThread().invoke(() -> {
                Microbot.getClient().addChatMessage(ChatMessageType.ENGINE, "", "<col=ff0000>You do not have the required woodcutting level to cut this tree. " + Rs2Player.getRealSkillLevel(Skill.WOODCUTTING) + "</col>", "");
            });
            shutdown();
            return true;
        }

        if (woodcuttingScriptState != WoodcuttingScriptState.PREP) {
            if (!Rs2Inventory.hasItem("axe")) {
                if (!Rs2Equipment.isWearing("axe")) {
                    Microbot.showMessage("Unable to find axe in inventory/equipped");
                    shutdown();
                    return true;
                }
            }
        }

        if (woodcuttingScriptState != WoodcuttingScriptState.RESETTING && woodcuttingScriptState != WoodcuttingScriptState.PREP &&
                (Rs2Player.isMoving() || (Rs2Player.isAnimating() && !BURNING_ANIMATION_IDS.contains(Rs2Player.getLastAnimationID())))) {
            return true;
        }

        if (this.plugin.currentForestryEvent != ForestryEvents.NONE) {
            this.plugin.currentForestryEvent = ForestryEvents.NONE;
        }

        return Rs2AntibanSettings.actionCooldownActive;
    }

    private void resetInventory(NetoWoodcuttingConfig config) {
        switch (config.primaryAction()) {
            case DROP:
                var itemNames = Arrays.stream(config.itemsToKeep().split(",")).map(String::trim).toArray(String[]::new);
                Rs2Inventory.dropAllExcept(false, config.interactOrder(), itemNames);
                woodcuttingScriptState = WoodcuttingScriptState.WOODCUTTING;
                break;
            case BANK:
                if (!handleBanking(config))
                    return;
                woodcuttingScriptState = WoodcuttingScriptState.WOODCUTTING;
                break;
            case BURN_CAMPFIRE:
            case BURN:
                woodcuttingScriptState = WoodcuttingScriptState.FIREMAKING;
                burnLog(config);

                if (Rs2Inventory.contains(getActiveTree().getLog())) return;

                walkBack(config);

                if (config.firemakeOnly()){
                    woodcuttingScriptState = WoodcuttingScriptState.FIREMAKING;
                } else {
                    woodcuttingScriptState = WoodcuttingScriptState.WOODCUTTING;
                }
                break;
            case FLETCH:
                if (handleFletchingWorkflow(config)) {
                    woodcuttingScriptState = WoodcuttingScriptState.WOODCUTTING;
                }
                break;
        }
    }

    private boolean ensureProgressiveLocation(NetoWoodcuttingConfig config) {
        if (activeLocation == null || activeLocation.getWorldPoint() == null) {
            return false;
        }

        WorldPoint targetPoint = activeLocation.getWorldPoint();

        if (initialPlayerLocation == null || !initialPlayerLocation.equals(targetPoint)) {
            initialPlayerLocation = targetPoint;
        }

        if (returnPoint == null || !returnPoint.equals(targetPoint)) {
            returnPoint = targetPoint;
        }

        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null) {
            return true;
        }

        int acceptableDistance = Math.min(Math.max(1, config.distanceToStray()), 5);
        int distanceToTarget = playerLocation.distanceTo(targetPoint);
        if (distanceToTarget > acceptableDistance) {
            if (Rs2Player.isMoving()) {
                return true;
            }

            Rs2Walker.walkTo(targetPoint, 3);
            return true;
        }

        return false;
    }

    private boolean handleBanking(NetoWoodcuttingConfig config) {
        if (!needsToBank(config)) {
            walkToLocation(getReturnPoint(config), 5);
            return true;
        }

        BankLocation nearestBank = Rs2Bank.getNearestBank();
        boolean isBankOpen = Rs2Bank.isNearBank(nearestBank, 8) ? Rs2Bank.openBank() : Rs2Bank.walkToBankAndUseBank(nearestBank);
        if (!isBankOpen || !Rs2Bank.isOpen()) return false;

        // empty log basket first if we have one
        Rs2LogBasket.emptyLogBasketAtBank();
        currentLogBasketCount = 0;
        // deposit items
        List<String> itemsToKeep = Arrays.stream(config.itemsToKeepBanking().split(","))
                .map(String::trim)
                .filter(x -> !x.isEmpty())
                .collect(Collectors.toList());
        if (!itemsToKeep.contains("log basket")) {
            itemsToKeep.add("log basket");
        }
        Rs2Bank.depositAllExcept(false, itemsToKeep.toArray(new String[0]));
        Rs2Inventory.waitForInventoryChanges(1800);

        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen());

        if (checkForBreakOrHop()) {
            return false;
        }

        walkToLocation(getReturnPoint(config), 5);
        return true;
    }

    private boolean handleLooting(NetoWoodcuttingConfig config)
    {
        if (!config.lootBirdNests() && !config.lootSeeds()) {
            return false; // No looting options selected
        }

        List<String> itemsToLootList = new ArrayList<>();

            if (config.lootSeeds()) {
                itemsToLootList.add("seed");
            }
            if (config.lootBirdNests()) {
                itemsToLootList.add("nest");
            }

            String[] itemsToLoot = itemsToLootList.toArray(new String[0]);

        LootingParameters itemLootParams = new LootingParameters(
                15,
                1,
                1,
                1,
                false,
                config.lootMyItemsOnly(),
                itemsToLoot
        );
        return Rs2GroundItem.lootItemsBasedOnNames(itemLootParams);
    }

    private void burnLog(NetoWoodcuttingConfig config) {
        WoodcuttingTree treeType = getActiveTree();
        WorldPoint fireSpot;
        boolean useCampfire = false;

        // prioritize campfire if available
        Rs2TileObjectModel fire = rs2TileObjectCache.query().where(x -> x.getId() == 49927).nearest(6); // Forester's campfire
        if (fire == null) {
            fire = rs2TileObjectCache.query().where(x -> x.getId() == 26185).nearest(6);
        }
        if (config.primaryAction() == WoodcuttingPrimaryAction.BURN_CAMPFIRE) {
            if (fire != null) {
                useCampfire = true;
            }
        }
        if ((Rs2Player.isStandingOnGameObject() || cannotLightFire) && !Rs2Player.isAnimating() && !useCampfire) {
            fireSpot = fireSpot(1);
            Rs2Walker.walkFastCanvas(fireSpot);
            cannotLightFire = false;
        }
        if (!isFiremake() && !useCampfire) {
            Rs2Inventory.waitForInventoryChanges(() -> {
                Rs2Inventory.use("tinderbox");
                sleepUntil(Rs2Inventory::isItemSelected);
                Rs2Inventory.useLast(treeType.getLogID());
            }, 300, 100);
        } else if (!isFiremake() && useCampfire) {
            Rs2Inventory.useItemOnObject(treeType.getLogID(), fire.getId());
            sleepUntil(() -> (!Rs2Player.isMoving() && Rs2Widget.findWidget("How many would you like to burn?", null, false) != null), 5000);
            Rs2Random.waitEx(400, 200);
            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
            sleepUntil(Rs2Player::isAnimating, 2000);
            Microbot.log("Sleeping until not animating or no more logs");
            sleepUntil(() -> !Rs2Inventory.contains(treeType.getLog()) || !Rs2Player.isAnimating(), 40000);

            return;
        }
        sleepUntil(() -> !isFiremake());
        if (!isFiremake()) {
            sleepUntil(() -> cannotLightFire, 1500);
        }
        if (!cannotLightFire && isFiremake()) {
            sleepUntil(() -> Rs2Player.waitForXpDrop(Skill.FIREMAKING, 40000), 40000);
        }
    }

    private WorldPoint fireSpot(int distance) {
        List<WorldPoint> worldPoints = Rs2Tile.getWalkableTilesAroundPlayer(distance);
        WorldPoint playerLocation = Rs2Player.getWorldLocation();

        // Create a map to group tiles by their distance from the player
        Map<Integer, WorldPoint> distanceMap = new HashMap<>();

        for (WorldPoint walkablePoint : worldPoints) {
            if (rs2TileObjectCache.query().where(x -> x.getWorldLocation().equals(walkablePoint)).nearest(distance) == null) {
                int tileDistance = playerLocation.distanceTo(walkablePoint);
                distanceMap.putIfAbsent(tileDistance, walkablePoint);
            }
        }

        // Find the minimum distance that has walkable points
        Optional<Integer> minDistanceOpt = distanceMap.keySet().stream().min(Integer::compare);

        if (minDistanceOpt.isPresent()) {
            return distanceMap.get(minDistanceOpt.get());
        }

        // Recursively increase the distance if no valid point is found
        return fireSpot(distance + 1);
    }

    private boolean isFiremake() {
        if (cannotLightFire) return false;
        return Rs2Player.isAnimating(1800) && BURNING_ANIMATION_IDS.contains(Rs2Player.getLastAnimationID());
    }

    private void fletchArrowShaft(NetoWoodcuttingConfig config) {
        Rs2Inventory.combineClosest("knife", getActiveTree().getLog());
        sleepUntil(Rs2Widget::isProductionWidgetOpen, 5000);
        Rs2Widget.clickWidget("arrow shafts");
        Rs2Player.waitForAnimation();
        sleepUntil(() -> !isFlectching(), 5000);
    }

    private boolean isFlectching() {
        return Rs2Player.isAnimating(3000) && Rs2Player.getLastAnimationID() == AnimationID.FLETCHING_BOW_CUTTING;
    }

    private void walkBack(NetoWoodcuttingConfig config) {
        walkToLocation(getReturnPoint(config), 5);
    }

    private void walkToLocation(WorldPoint dst, int distance) {
        WorldPoint myLocation = Rs2Player.getWorldLocation();
        if (myLocation == null) return;

        if (myLocation.distanceTo(dst) <= distance) {
            return;
        }

        try {
            var future = scheduledExecutorService.submit(() -> Rs2Walker.walkTo(dst));

            while (!future.isDone()) {
                WorldPoint currentLocation = Rs2Player.getWorldLocation();
                if (currentLocation != null && currentLocation.distanceTo(dst) <= distance) {
                    Rs2Walker.setTarget(null);
                    future.cancel(true);
                    break;
                }
                sleep(100);
            }
        } catch (Exception e) {
            log.error("Error walking to location", e);
        }
    }
    
    /**
     * determine if this workflow will bank items
     */
    private boolean willBankItems(NetoWoodcuttingConfig config) {
        return config.primaryAction() == WoodcuttingPrimaryAction.BANK || 
               (config.primaryAction() == WoodcuttingPrimaryAction.FLETCH && 
                config.secondaryAction() == WoodcuttingSecondaryAction.BANK);
    }
    
    /**
     * handle fletching workflow with secondary actions
     */
    private boolean handleFletchingWorkflow(NetoWoodcuttingConfig config) {
        // fletch logs in inventory
        if (!Rs2Fletching.hasKnife()) {
            log.info("Unable to find knife in inventory/equipped");
            switch (config.secondaryAction()) {
                case BANK:
                case STRING_AND_BANK:
                    if (!handleBanking(config)) {
                        walkBack(config);
                        return false;
                    }
                    break;
                case DROP:
                case STRING_AND_DROP:
                    log.info("Dropping items to find knife");
                    String [] itemNames = Arrays.stream(config.itemsToKeep().split(",")).map(String::trim).toArray(String[]::new);
                    // additional item to keep axe and log basket
                    itemNames = Arrays.copyOf(itemNames, itemNames.length + 1);
                    itemNames[itemNames.length - 1] = "axe";
                    if (Rs2Inventory.hasItem("log basket")) {
                        itemNames = Arrays.copyOf(itemNames, itemNames.length + 1);
                        itemNames[itemNames.length - 1] = "log basket";
                    }


                    Rs2Inventory.dropAllExcept(false, InteractOrder.COLUMN,itemNames );
                    break;
                case NONE:
                    break;
            }
            return !Rs2Inventory.isFull();
        }
        WoodcuttingTree treeType = getActiveTree();
        int logCount = Rs2Inventory.count(treeType.getLogID());
        if (logCount > 0) {
            //TODO: should we really stop script if fletching failed?
            boolean startFletchingSucces = Rs2Fletching.fletchItems(treeType.getLogID(), config.fletchingType().getContainsInventoryName(), "All");
            int fletchedItems = Rs2Inventory.getList(itemBounds -> itemBounds.getName().contains(config.fletchingType().getContainsInventoryName())).size();
            log.info("We fletched {} {} into {} of {} , success: {}", logCount, treeType, fletchedItems, config.fletchingType().getContainsInventoryName(), startFletchingSucces);
            if (!startFletchingSucces) {
                return false;
            }
            if (Rs2Inventory.count(treeType.getLogID())!=0){
                return false;
            }
        }

        // handle secondary action
        switch (config.secondaryAction()) {
            case BANK:
                if (!handleBanking(config)) return false;
                walkBack(config);
                break;
            case DROP:

                Rs2Fletching.dropFletchedItems(config.fletchingType().getContainsInventoryName());
                Rs2Inventory.waitForInventoryChanges(1800);
                break;
            case STRING_AND_DROP:
            case STRING_AND_BANK:
                if (Rs2Inventory.contains("bow string")) {
                    Rs2Fletching.stringBows(config.fletchingType().getContainsInventoryName());
                }
                if (config.secondaryAction() == WoodcuttingSecondaryAction.STRING_AND_BANK) {
                    if (!handleBanking(config)) return false;
                    walkBack(config);
                } else {
                    Rs2Fletching.dropFletchedItems(config.fletchingType().getContainsInventoryName());
                    Rs2Inventory.waitForInventoryChanges(1800);
                }
                break;
            case NONE:
                break;
        }


        return !Rs2Inventory.isFull();
    }

    public WoodcuttingTree getActiveTree() {
        return activeTree;
    }

    private void updateActiveTree(NetoWoodcuttingConfig config) {
        WoodcuttingTree previousTree = activeTree;
        WoodcuttingTree resolvedTree;
        ResourceLocationOption candidateLocation = null;
        boolean progressive = config.progressiveMode();

        if (progressive) {
            int woodcuttingLevel = getRealSkillLevel(Skill.WOODCUTTING);
            ProgressiveSelection selection = determineProgressiveSelection(woodcuttingLevel);
            resolvedTree = selection.getTree();
            candidateLocation = selection.getLocation();
        } else {
            resolvedTree = config.TREE();
        }

        if (resolvedTree == null) {
            resolvedTree = WoodcuttingTree.TREE;
        }

        activeTree = resolvedTree;

        if (progressive) {
            boolean keepExistingLocation = previousTree == resolvedTree && activeLocation != null && activeLocation.hasRequirements();

            if (!keepExistingLocation) {
                activeLocation = candidateLocation;
            }
        } else {
            activeLocation = null;
        }
    }

    private ProgressiveSelection determineProgressiveSelection(int woodcuttingLevel) {
        ProgressiveSelection bestSelection = null;

        for (WoodcuttingTree tree : PROGRESSIVE_TREE_ORDER) {
            if (woodcuttingLevel < tree.getWoodcuttingLevel()) {
                break;
            }

            ResourceLocationOption location = WoodcuttingTreeLocations.getBestAccessibleLocation(tree);
            if (location != null) {
                bestSelection = new ProgressiveSelection(tree, location);
            }
        }

        if (bestSelection != null) {
            return bestSelection;
        }

        ResourceLocationOption fallbackLocation = WoodcuttingTreeLocations.getBestAccessibleLocation(WoodcuttingTree.TREE);
        return new ProgressiveSelection(WoodcuttingTree.TREE, fallbackLocation);
    }

    private static class ProgressiveSelection {
        private final WoodcuttingTree tree;
        private final ResourceLocationOption location;

        private ProgressiveSelection(WoodcuttingTree tree, ResourceLocationOption location) {
            this.tree = tree;
            this.location = location;
        }

        public WoodcuttingTree getTree() {
            return tree;
        }

        public ResourceLocationOption getLocation() {
            return location;
        }
    }

    private void withdrawAndEquipFirstAvailable(int[] ids) {
        for (int id : ids) {
            if (Rs2Equipment.isWearing(id)) {
                return;
            }
        }
        for (int id : ids) {
            if (Rs2Bank.hasItem(id)) {
                Rs2Bank.withdrawAndEquip(id);
                sleep(600, 1200);
                return;
            }
        }
    }

    private void handlePrep(NetoWoodcuttingConfig config) {
        Microbot.log("Prep State: Walking to nearest bank...");
        BankLocation nearestBank = Rs2Bank.getNearestBank();
        boolean isBankOpen = Rs2Bank.isNearBank(nearestBank, 8) ? Rs2Bank.openBank() : Rs2Bank.walkToBankAndUseBank(nearestBank);
        if (!isBankOpen || !Rs2Bank.isOpen()) {
            return;
        }

        Microbot.log("Prep State: Depositing inventory...");
        Rs2Bank.depositAll();
        sleepUntil(Rs2Inventory::isEmpty, 5000);

        Microbot.log("Prep State: Equipping Lumberjack outfit...");
        withdrawAndEquipFirstAvailable(new int[]{10941, 28173, 28174}); // Hats
        withdrawAndEquipFirstAvailable(new int[]{10939, 28169, 28170}); // Tops
        withdrawAndEquipFirstAvailable(new int[]{10940, 28171, 28172}); // Legs
        withdrawAndEquipFirstAvailable(new int[]{10933, 28175, 28176}); // Boots

        Microbot.log("Prep State: Equipping best axe...");
        withdrawAndEquipFirstAvailable(new int[]{28217, 6739, 28214, 1359}); // Dragon felling, Dragon, Rune felling, Rune axe

        Microbot.log("Prep State: Equipping back/cape slot item...");
        if (Rs2Equipment.isWearing(28136) || Rs2Bank.hasItem(28136)) {
            if (!Rs2Equipment.isWearing(28136)) {
                Microbot.log("Prep State: Withdrawing Forestry kit...");
                Rs2Bank.withdrawItem(28136);
                if (sleepUntil(() -> Rs2Inventory.hasItem(28136), 3000)) {
                    Microbot.log("Prep State: Closing bank to equip Forestry kit...");
                    Rs2Bank.closeBank();
                    sleepUntil(() -> !Rs2Bank.isOpen(), 3000);

                    Rs2Inventory.wield(28136);
                    sleepUntil(() -> Rs2Equipment.isWearing(28136), 3000);

                    Microbot.log("Prep State: Re-opening bank...");
                    isBankOpen = Rs2Bank.isNearBank(nearestBank, 8) ? Rs2Bank.openBank() : Rs2Bank.walkToBankAndUseBank(nearestBank);
                    if (!isBankOpen || !Rs2Bank.isOpen()) {
                        return;
                    }
                }
            }
        } else if (Rs2Equipment.isWearing("Agility cape") || Rs2Bank.hasItem("Agility cape")) {
            if (!Rs2Equipment.isWearing("Agility cape")) {
                Rs2Bank.withdrawAndEquip("Agility cape");
                sleep(600, 1200);
            }
        } else if (Rs2Equipment.isWearing("Graceful cape") || Rs2Bank.hasItem("Graceful cape")) {
            if (!Rs2Equipment.isWearing("Graceful cape")) {
                Rs2Bank.withdrawAndEquip("Graceful cape");
                sleep(600, 1200);
            }
        }

        Microbot.log("Prep State: Preparing travel teleport item...");
        boolean isPrifddinas = Rs2Random.between(1, 100) <= 75;

        if (isPrifddinas) {
            boolean isSongOfTheElvesFinished = Rs2Player.getQuestState(Quest.SONG_OF_THE_ELVES) == QuestState.FINISHED;
            if (!isSongOfTheElvesFinished) {
                Microbot.log("Prep State: Song of the Elves quest is not completed. Defaulting to Woodcutting Guild.");
                isPrifddinas = false;
            }
        }

        int teleportItemId = -1;
        String teleportItemName = null;

        if (isPrifddinas) {
            Microbot.log("Prep State: Rolled Prifddinas (75%)");
            if (Rs2Bank.hasItem(59409) || Rs2Inventory.hasItem(59409)) {
                teleportItemId = 59409;
            }
        } else {
            Microbot.log("Prep State: Selecting Woodcutting Guild (25% or quest fallback)");
            for (int i = 1; i <= 6; i++) {
                String name = "Skills necklace(" + i + ")";
                if (Rs2Bank.hasItem(name) || Rs2Inventory.hasItem(name)) {
                    teleportItemName = name;
                    break;
                }
            }
        }

        if (teleportItemId != -1) {
            if (!Rs2Inventory.hasItem(teleportItemId)) {
                Rs2Bank.withdrawItem(teleportItemId);
                sleepUntil(() -> Rs2Inventory.hasItem(59409), 3000);
            }
        } else if (teleportItemName != null) {
            if (!Rs2Inventory.hasItem(teleportItemName)) {
                Rs2Bank.withdrawItem(teleportItemName);
                final String nameToWaitFor = teleportItemName;
                sleepUntil(() -> Rs2Inventory.hasItem(nameToWaitFor), 3000);
            }
        }

        Microbot.log("Prep State: Depositing remaining items...");
        if (teleportItemId != -1) {
            Rs2Bank.depositAllExcept(false, "Eternal teleport crystal");
        } else if (teleportItemName != null) {
            Rs2Bank.depositAllExcept(true, teleportItemName);
        } else {
            Rs2Bank.depositAll();
        }
        sleep(600, 1200);

        Microbot.log("Prep State: Closing bank...");
        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 3000);

        if (isPrifddinas) {
            if (Rs2Inventory.hasItem(59409)) {
                Microbot.log("Prep State: Teleporting to Prifddinas...");
                if (Rs2Inventory.interact(59409, "teleport")) {
                    if (Rs2Dialogue.sleepUntilSelectAnOption()) {
                        Rs2Dialogue.clickOption("Prifddinas");
                        sleepUntil(() -> !Rs2Player.isAnimating() && Rs2Player.getWorldLocation().distanceTo(new WorldPoint(3264, 6065, 0)) < 20, 10000);
                    }
                }
            }
            WorldPoint targetPoint = new WorldPoint(3294, 6060, 0);
            Microbot.log("Prep State: Walking to Prifddinas destination...");
            Rs2Walker.walkTo(targetPoint);
            sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(targetPoint) <= 4, 60000);
        } else {
            WorldPoint targetPoint = new WorldPoint(1588, 3483, 0);
            Microbot.log("Prep State: Walking to Woodcutting Guild destination...");
            Rs2Walker.walkTo(targetPoint);
            sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(targetPoint) <= 4, 60000);
        }

        initialPlayerLocation = Rs2Player.getWorldLocation();
        returnPoint = Rs2Player.getWorldLocation();

        if (config.firemakeOnly()) {
            woodcuttingScriptState = WoodcuttingScriptState.FIREMAKING;
        } else {
            woodcuttingScriptState = WoodcuttingScriptState.WOODCUTTING;
        }
        Microbot.log("Prep State: Preparation complete! Next state: " + woodcuttingScriptState);
    }

    private boolean needsToBank(NetoWoodcuttingConfig config) {
        if (Rs2LogBasket.hasLogBasket() && currentLogBasketCount > 0) {
            return true;
        }
        List<String> itemsToKeep = Arrays.stream(config.itemsToKeepBanking().split(","))
                .map(String::trim)
                .filter(x -> !x.isEmpty())
                .collect(Collectors.toList());
        if (!itemsToKeep.contains("log basket")) {
            itemsToKeep.add("log basket");
        }
        return Rs2Inventory.items().anyMatch(item -> {
            if (item == null || item.getName() == null) return false;
            String name = item.getName().toLowerCase();
            return itemsToKeep.stream().noneMatch(keep -> name.contains(keep.toLowerCase()));
        });
    }

    private boolean checkForBreakOrHop() {
        if (runtimeDisable.updateRuntime(NetoWoodcuttingPlugin.class)) {
            return true;
        }

        if (breakManager.tryStartBreakAtSafePoint()) {
            return true;
        }

        if (worldHopManager.tryHopIfDue(this::isRunning).isAttempted()) {
            return true;
        }

        return false;
    }

    @Override
    public void shutdown() {
        breakManager.reset();
        worldHopManager.reset();
        runtimeDisable.reset();
        super.shutdown();
        currentLogBasketCount = -1;
        Rs2Fletching.stopFletchingWhileMoving();
        Rs2Walker.setTarget(null);
        returnPoint = null;
        initialPlayerLocation = null;
        hasAutoHopMessageShown = false;
        Rs2Antiban.resetAntibanSettings();
        activeLocation = null;
    }
}

