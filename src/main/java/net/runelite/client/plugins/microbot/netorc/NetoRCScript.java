package net.runelite.client.plugins.microbot.netorc;

import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.netorc.enums.BloodStep;
import net.runelite.client.plugins.microbot.netorc.enums.RuneType;
import net.runelite.client.plugins.microbot.netorc.enums.State;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.netorc.enums.Teleports;
import net.runelite.client.plugins.microbot.netorc.enums.WrathStep;
import net.runelite.client.plugins.microbot.shared.session.NetoBreakManager;
import net.runelite.client.plugins.microbot.shared.session.NetoRuntimeDisable;
import net.runelite.client.plugins.microbot.shared.session.NetoWorldHopManager;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectQueryable;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;

import javax.inject.Inject;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class NetoRCScript extends Script {
    private final NetoRCPlugin plugin;
    public static State state = State.BANKING;

    private int lumbyElite = -1;

	private volatile boolean forceDrinkAtFerox = false;
    private volatile boolean forceBankOnStart = true;
    private volatile int activeRunId = 0;
    private WrathStep wrathStep = WrathStep.MYTH_CAPE;
    private BloodStep bloodStep = BloodStep.CAVE_1;
    private final ThreadLocal<Integer> scheduledRunId = new ThreadLocal<>();

    public static final int pureEss = 7936;

    public static final int bloodAltar = ObjectID.BLOOD_ALTAR;
    public static final int wrathAltar = ObjectID.WRATH_ALTAR;

    public static final int activeBloodEssence = ItemID.BLOOD_ESSENCE_ACTIVE;
    public static final int inactiveBloodEssence = ItemID.BLOOD_ESSENCE_INACTIVE;
    public static final int bloodRune = ItemID.BLOODRUNE;
    public static final int wrathRune = ItemID.WRATHRUNE;
    public static final int colossalPouch = ItemID.RCU_POUCH_COLOSSAL;
    public static final int dramenStaff = ItemID.DRAMEN_STAFF;
    public static final int lunarStaff = ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF;
    public static final int mythCape = ItemID.MYTHICAL_CAPE;
    public static final int dragonShield = ItemID.ANTIDRAGONBREATHSHIELD;

    @Inject
    public NetoRCScript(NetoRCPlugin plugin) {
        this.plugin = plugin;
    }

    @Inject
    private NetoRCConfig config;
    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;
    @Inject
    private NetoBreakManager breakManager;
    @Inject
    private NetoWorldHopManager worldHopManager;
    @Inject
    private NetoRuntimeDisable runtimeDisable;

    private Rs2TileObjectModel findObject(int id) {
        return new Rs2TileObjectQueryable().withId(id).first();
    }

    private boolean interactObject(int id, String action) {
        return new Rs2TileObjectQueryable().interact(id, action);
    }

    private boolean hoverObject(int id) {
        var obj = new Rs2TileObjectQueryable().withId(id).first();
        if (obj != null && Rs2AntibanSettings.naturalMouse) {
            java.awt.Rectangle clickbox = net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper.getObjectClickbox(obj);
            if (clickbox != null) {
                net.runelite.api.Point point = net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper.getClickingPoint(clickbox, true);
                if (point.getX() != 1 && point.getY() != 1) {
                    Microbot.getNaturalMouse().moveTo(point.getX(), point.getY());
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hoverInvItem(int itemId) {
        var item = Rs2Inventory.get(itemId);
        if (item != null) {
            return Rs2Inventory.hover(item);
        }
        return false;
    }

    private boolean hoverInvItem(String itemName) {
        var item = Rs2Inventory.get(itemName);
        if (item != null) {
            return Rs2Inventory.hover(item);
        }
        return false;
    }

    private int getStaminaThreshold() {
        if (config.runeType() == RuneType.WRATH) {
            return 15;
        }
        if (config.usePoh()) {
            return 15;
        }
        if (config.runeType() == RuneType.BLOOD && Rs2Player.getRealSkillLevel(Skill.AGILITY) < 93) {
            return 40;
        }
        return 25;
    }

    private boolean needsRestore() {
        return Rs2Player.getRunEnergy() <= getStaminaThreshold() || Rs2Player.getHealthPercentage() <= 20;
    }

    private boolean needsFeroxRestore() {
        return !config.usePoh() && (forceDrinkAtFerox || needsRestore());
    }

    public boolean run() {
        if (mainScheduledFuture != null && !mainScheduledFuture.isDone()) {
            mainScheduledFuture.cancel(true);
        }

        int runId = startNewRun();
        Microbot.enableAutoRunOn = false;
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyRunecraftingSetup();
        Rs2AntibanSettings.actionCooldownChance = 0;
        Rs2Antiban.setActivity(Activity.CRAFTING_BLOODS_TRUE_ALTAR);
        Rs2Camera.setZoom(100);
        Rs2Camera.setPitch(305);
        sleepGaussian(300, 25);
        if (config.runeType() == RuneType.WRATH) {
            Rs2Camera.setYaw(1024);
        } else {
            Rs2Camera.setYaw(1536);
        }
        sleepGaussian(700, 200);
        Microbot.log("Script has started");
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            scheduledRunId.set(runId);
            try {
                if (!isCurrentRun(runId)) return;
                if (runtimeDisable.updateRuntime(NetoRCPlugin.class)) return;
                if (breakManager.updateBreakState()) return;
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (!isCurrentRun(runId)) return;
                long startTime = System.currentTimeMillis();

                if (lumbyElite == -1) {
                    clientThread.invoke(() -> {
                        lumbyElite = Microbot.getClient().getVarbitValue(Varbits.DIARY_LUMBRIDGE_ELITE);
                    });
                    return;
                }

                if (Rs2Inventory.anyPouchUnknown()) {
                    if (Rs2Bank.isOpen()) {
                        Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
                        sleepUntil(() -> !Rs2Bank.isOpen(), 1200);
                    }
                    checkPouches();
                    return;
                }

                State initialState;
                do {
                    initialState = state;
                    switch (state) {
                        case BANKING:
                            handleBanking();
                            break;
                        case GOING_HOME:
                            if (config.usePoh()) {
                                handleGoingHome();
                                break;
                            } else if (config.runeType() == RuneType.BLOOD && !config.usePoh()) {
                                handleArdyCloak();
                                break;
                            } else if (config.runeType() == RuneType.WRATH) {
                                handleWrathWalking();
                                break;
                            }
                        case WALKING_TO:
                            handleWalking();
                            break;
                        case CRAFTING:
                            handleCrafting();
                            break;
                    }
                } while (state != initialState && isCurrentRun(runId) && Microbot.isLoggedIn());

                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
                Microbot.log("Error in script" + ex.getMessage());
            } finally {
                scheduledRunId.remove();
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        invalidateCurrentRun();
        breakManager.reset();
        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
        Microbot.log("Script has been stopped");
        //Rs2Player.logout();
    }

    public synchronized void resetToBanking() {
        state = State.BANKING;
        lumbyElite = -1;
        forceDrinkAtFerox = false;
        forceBankOnStart = true;
        wrathStep = WrathStep.MYTH_CAPE;
        bloodStep = BloodStep.CAVE_1;
        breakManager.configure(config, "Neto RC");
        worldHopManager.configure(config, "Neto RC");
        runtimeDisable.configure(config, "Neto RC");
        breakManager.reset();
        worldHopManager.reset();
        runtimeDisable.reset();
    }

    private synchronized int startNewRun() {
        activeRunId++;
        resetToBanking();
        return activeRunId;
    }

    private synchronized void invalidateCurrentRun() {
        activeRunId++;
        resetToBanking();
    }

    private boolean isCurrentRun(int runId) {
        return runId == activeRunId;
    }

    private void setState(State nextState) {
        Integer runId = scheduledRunId.get();
        if (runId != null && !isCurrentRun(runId)) {
            return;
        }

        if (nextState == State.CRAFTING) {
            Rs2Antiban.setActivityIntensity(ActivityIntensity.HIGH);
        } else {
            Rs2Antiban.setActivityIntensity(ActivityIntensity.LOW);
        }

        state = nextState;
    }

    private void checkPouches() {
        Rs2Inventory.interact(colossalPouch, "Check");
        sleepGaussian(600, 200);
    }

    private void handleBanking() {
        boolean forceBank = forceBankOnStart;

        if (!ensureBankingLocation(forceBank)) {
            return;
        }

        if (!prepareBankingUiAndPouches()) {
            return;
        }

        if (!forceBank && Rs2Inventory.isFull() && Rs2Inventory.allPouchesFull() && Rs2Inventory.contains(pureEss)) {
            Microbot.log("We are full, skipping bank");
            setState(State.GOING_HOME);
            return;
        }
        if (!config.usePoh()) {
            handleFeroxRunEnergy();
        }

        openBankIfNeeded();
        withdrawRuneTypeSupplies();
        ensureTravelItems(client.getRealSkillLevel(Skill.RUNECRAFT));
        handleFillPouch();
        finishBankingAndRoute();
    }

    private boolean ensureBankingLocation(boolean forceBank) {
        int currentRegion = plugin.getMyWorldPoint().getRegionID();

        if (forceBank && !isBankingRegion(currentRegion)) {
            Microbot.log("Startup banking requested, teleporting to bank");
            handleBankTeleport();
            return false;
        }

        if (!forceBank
                && !Rs2Inventory.allPouchesFull()
                && !Rs2Inventory.contains(pureEss)
                && !isBankingRegion(currentRegion)) {
            Microbot.log("Not in banking region, teleporting");
            handleBankTeleport();
        }

        return true;
    }

    private boolean prepareBankingUiAndPouches() {
		if (plugin.isBreakHandlerEnabled()) {
			BreakHandlerScript.setLockState(true);
		}

        Rs2Tab.switchTo(InterfaceTab.INVENTORY);

		if (Rs2Inventory.hasDegradedPouch()) {
			Rs2Magic.repairPouchesWithLunar();
			return false;
		}

        if (Rs2Inventory.anyPouchUnknown()) {
            checkPouches();
        }
        return true;
    }

    private void openBankIfNeeded() {
        while (!Rs2Bank.isOpen() && isRunning() && Rs2Bank.isNearBank(26) &&
                (forceBankOnStart || needsBankingSupplies())) {
            Microbot.log("Opening bank");
            new Thread(Rs2Bank::openBank).start();
            new Thread(() -> {
                sleepGaussian(850, 75); // 700 to 1000 ms
                hoverInvItem(config.runeType() == RuneType.BLOOD ? bloodRune : wrathRune);
            }).start();
            sleepUntil(Rs2Bank::isOpen);
        }

        if (forceBankOnStart && Rs2Bank.isOpen()) {
            forceBankOnStart = false;
        }
    }

    private void withdrawRuneTypeSupplies() {
        if (config.runeType() == RuneType.WRATH) {
            handleWrathReqEquip();
        }

        if (config.runeType() == RuneType.BLOOD) {
            ensureBloodStaff();

            if (!config.usePoh() && !Rs2Equipment.isWearing("Ardougne cloak")) {
                Rs2Bank.withdrawAndEquip("Ardougne cloak");
                sleepGaussian(700, 200);
            }

            if (!Rs2Inventory.contains(activeBloodEssence) && !Rs2Inventory.contains(inactiveBloodEssence)) {
                if (!Rs2Bank.hasItem(activeBloodEssence)) {
                    Rs2Bank.withdrawItem(inactiveBloodEssence);
                    Microbot.log("Withdrawing blood essence");
                    sleepGaussian(900, 200);
                } else {
                    Rs2Bank.withdrawItem(activeBloodEssence);
                    sleepGaussian(900, 200);
                }
            }
        }
    }

    private void ensureBloodStaff() {
        if (lumbyElite == 1 || Rs2Equipment.isWearing(lunarStaff) || Rs2Equipment.isWearing(dramenStaff)) {
            return;
        }

        if (Rs2Bank.hasItem(lunarStaff)) {
            Microbot.log("Looking for and withdrawing lunar staff");
            Rs2Bank.withdrawAndEquip(lunarStaff);
            sleepUntil(() -> Rs2Equipment.isWearing(lunarStaff));
            sleepGaussian(700, 200);
        } else if (Rs2Bank.hasItem(dramenStaff)) {
            Microbot.log("No lunar staff found, withdrawing dramen staff");
            Rs2Bank.withdrawAndEquip(dramenStaff);
            sleepUntil(() -> Rs2Equipment.isWearing(dramenStaff));
            sleepGaussian(700, 200);
        }
    }

    private void ensureTravelItems(int runecraftLevel) {
        if (runecraftLevel >= 99) {
            if (!Rs2Equipment.isWearing("Runecraft cape")) {
                Rs2Bank.withdrawAndEquip("Runecraft cape");
                sleepGaussian(700, 200);
            }
        }

        if (config.usePoh()) {
            ensurePohTeleport(runecraftLevel);
        } else {
            if (runecraftLevel < 99 && !Rs2Inventory.hasRunePouch()) {
                Rs2Bank.withdrawRunePouch();
                sleepGaussian(700, 200);
            }
        }

        ensureEquippedTeleport(Teleports.SAILORS_AMULET);
        ensureEquippedTeleport(Teleports.FEROX_ENCLAVE);
    }

    private void ensurePohTeleport(int runecraftLevel) {
        boolean hasPohTeleport = Teleports.CONSTRUCTION_CAPE.isWearing()
                || Teleports.CONSTRUCTION_CAPE.isInInventory()
                || Teleports.HOUSE_TAB.isInInventory()
                || (Rs2Inventory.hasRunePouch() && runecraftLevel >= 99);

        if (hasPohTeleport) {
            return;
        }

        if (Rs2Bank.hasItem(Teleports.CONSTRUCTION_CAPE.getItemIds())) {
            if (runecraftLevel >= 99) {
                Rs2Bank.withdrawItem(Teleports.CONSTRUCTION_CAPE.firstItemId());
                sleepUntil(Teleports.CONSTRUCTION_CAPE::isInInventory);
            } else {
                Rs2Bank.withdrawAndEquip(Teleports.CONSTRUCTION_CAPE.firstItemId());
                sleepUntil(Teleports.CONSTRUCTION_CAPE::isWearing);
            }
        } else if (runecraftLevel >= 99 && Rs2Bank.hasRunePouch()) {
            Rs2Bank.withdrawRunePouch();
            sleepUntil(Rs2Inventory::hasRunePouch);
        } else if (Rs2Bank.hasItem(Teleports.HOUSE_TAB.getItemIds())) {
            Rs2Bank.withdrawAll(Teleports.HOUSE_TAB.firstItemId());
            sleepUntil(Teleports.HOUSE_TAB::isInInventory);
        }
    }

    private void ensureEquippedTeleport(Teleports teleport) {
        if (!teleport.isWearing() && Rs2Bank.hasItem(teleport.getItemIds())) {
            Microbot.log("Withdrawing bank teleport " + teleport.getName());
            Rs2Bank.withdrawAndEquip(teleport.firstItemId());
            sleepUntil(teleport::isWearing);
        }
    }

    private void finishBankingAndRoute() {
        if (Rs2Bank.isOpen() && Rs2Inventory.allPouchesFull() && Rs2Inventory.isFull()) {
            Microbot.log("We are full, lets go");
            Rs2Bank.closeBank();
            sleepUntil(() -> !Rs2Bank.isOpen(), 1200);

            if (config.runeType() == RuneType.BLOOD) {
                if (Rs2Inventory.contains(inactiveBloodEssence)) {
                    Rs2Inventory.interact(inactiveBloodEssence, "Activate");
                    Microbot.log("Activating blood essence");
                    sleepGaussian(700, 200);
                }
            }

            if (worldHopManager.tryHopIfDue(this::isRunning).isAttempted()) {
                return;
            }

            setState(State.GOING_HOME);
        }
    }

    private void handleFillPouch() {
        while (!Rs2Inventory.allPouchesFull() || !Rs2Inventory.isFull() && isRunning()) {
            Microbot.log("Pouches are not full yet");
            if (Rs2Bank.isOpen()) {
                if (Rs2Inventory.contains(bloodRune)) {
                    Rs2Bank.depositAll(bloodRune);
                    sleepGaussian(150, 25); // 100 to 200 ms
                }
                if (Rs2Inventory.contains(wrathRune)) {
                    Rs2Bank.depositAll(wrathRune);
                    sleepGaussian(150, 25); // 100 to 200 ms
                }
                Rs2Bank.withdrawAll(pureEss);
                sleepUntil(Rs2Inventory::isFull);
                Rs2Inventory.fillPouches();
                sleepUntilOnClientThread(() -> !Rs2Inventory.isFull());
            }
            if (!Rs2Inventory.isFull()) {
                Rs2Bank.withdrawAll(pureEss);
                sleepUntil(Rs2Inventory::isFull);
            }
        }
    }

    private void handleFeroxRunEnergy() {
		if (needsFeroxRestore()) {
			Microbot.log("We are thirsty...let us Drink");
            forceDrinkAtFerox = true;

            Microbot.log("Walking to Ferox pool");
            smartWalk(NetoRcConstants.FEROX_POOL, 5);

            Microbot.log("Interacting with the Ferox pool");
            interactObject(NetoRcConstants.FEROX_POOL_OBJECT, "Drink");

            sleepUntil(() -> (!Rs2Player.isInteracting()) && !Rs2Player.isAnimating() && Rs2Player.getRunEnergy() > 90);
            sleepGaussian(700, 50);
			forceDrinkAtFerox = false;
        }
    }

    private void handleArdyCloak() {
        Teleports ardyCloakTeleport = Teleports.ARDOUGNE_CLOAK;

        if (plugin.isBreakHandlerEnabled()) {
            BreakHandlerScript.setLockState(true);
        }

        for (Integer itemId : ardyCloakTeleport.getItemIds()) {
            if (Rs2Equipment.isWearing(itemId)) {
                Microbot.log("Using Ardy cloak");
                Rs2Equipment.interact(itemId, ardyCloakTeleport.getInteraction());
                Microbot.log("Waiting for region " + NetoRcConstants.MONASTERY_REGION);
                sleepUntil(Rs2Player::isAnimating, 5000);
                sleepUntil(() -> !Rs2Player.isAnimating(), 5000);
            }
        }

        smartWalk(NetoRcConstants.MONASTERY_FAIRY_RING);

//        sleepUntilOnClientThread(() -> findObject(29495) != null, 10000); // Wait for Monastery Ring
//        var fairyRing = new Rs2TileObjectQueryable().withNameContains("fairy").first();

        Microbot.log("Interacting with fairies");
        interactObject(29495, "Last-destination");
//        interactObject(fairyRing.getId(), "Last-destination");

        setState(State.WALKING_TO);
    }

    private void handleFarmingCape() {
        Teleports farmingCapeTeleport = Teleports.FARMING_CAPE;
        if (config.usePoh()) {
            if (farmingCapeTeleport.isWearing()) {
                if (plugin.getMyWorldPoint().getRegionID() != 4922) {
                    farmingCapeTeleport.interactWorn();
                    sleepUntil(() -> plugin.getMyWorldPoint().getRegionID() == 4922);
                    sleepGaussian(1100, 200);
                }
                if (plugin.getMyWorldPoint().distanceTo(NetoRcConstants.GUILD_SPIRIT_TREE) > 7) {
                    smartWalk(NetoRcConstants.GUILD_SPIRIT_TREE);
                } else {
                    interactObject(NetoRcConstants.GUILD_SPIRIT_TREE_OBJECT, "Travel");
                    sleepUntil(() -> Rs2Widget.isWidgetVisible(187, 3), 10000);
                    sleepGaussian(1100, 200);

                    Widget parent = client.getWidget(187, 3);
                    if (parent != null && parent.getChildren() != null) {
                        for (Widget child : parent.getChildren()) {
                            if (child != null && child.getText() != null && child.getText().toLowerCase().contains("house")) {
                                Microbot.log("Found house widgetId");
                                Rs2Widget.clickWidget(child);
                                sleepUntil(() -> !Rs2Player.isAnimating(), 5000);
                                sleepUntil(() -> Microbot.getClient().getTopLevelWorldView() != null, 5000);
                                sleepGaussian(1300, 200);
                                break;
                            }
                        }
                    }
                    restoreAtPohIfNeeded();
                    if (!needsRestore()) {
                        if (config.runeType() == RuneType.BLOOD) {
                            sleepGaussian(700, 200);
                            Microbot.log("Looking for fairies");
                            handlePohFairyRing();
                        }
                    }
                }
            }
        }
    }


    private boolean isBankingRegion(int currentRegion) {
        return Teleports.CRAFTING_CAPE.matchesRegion(currentRegion)
                || Teleports.FEROX_ENCLAVE.matchesRegion(currentRegion)
                || Teleports.FARMING_CAPE.matchesRegion(currentRegion)
                || Teleports.SAILORS_AMULET.matchesRegion(currentRegion)
                || Teleports.CASTLE_WARS.matchesRegion(currentRegion);
    }

    private boolean needsBankingSupplies() {
        return !Rs2Inventory.allPouchesFull()
                || !Rs2Inventory.contains(colossalPouch)
                || !Rs2Inventory.contains(pureEss);
    }

    private void handleWrathReqEquip() {
        if (!Rs2Equipment.isWearing(dragonShield)) {
            Microbot.log("Withdrawing " + dragonShield);
            Rs2Bank.withdrawAndEquip(dragonShield);
            sleepUntil(() -> Rs2Equipment.isWearing(dragonShield));
            sleepGaussian(900, 200);
        }
        if (!Rs2Equipment.isWearing(mythCape) && !Rs2Inventory.contains(mythCape)) {
            Microbot.log("Withdrawing " + mythCape);
            Rs2Bank.withdrawItem(mythCape);
            sleepUntil(() -> Rs2Inventory.contains(mythCape));
            sleepGaussian(900, 200);
        }
    }

    private void teleportToPoh() {
        boolean teleportInitiated = false;

        if (client.getRealSkillLevel(Skill.RUNECRAFT) >= 99 && Rs2Inventory.hasRunePouch()) {
            Microbot.log("Using Teleport to House spell");
            Rs2Magic.cast(Rs2Spells.TELEPORT_TO_HOUSE);
            teleportInitiated = true;
        } else if (Teleports.CONSTRUCTION_CAPE.isWearing()) {
            Microbot.log("Using worn Construction cape");
            Teleports.CONSTRUCTION_CAPE.interactWorn();
            teleportInitiated = true;
        } else if (Teleports.CONSTRUCTION_CAPE.isInInventory()) {
            Microbot.log("Using Construction cape from inventory");
            Teleports.CONSTRUCTION_CAPE.interactInventory();
            teleportInitiated = true;
        } else if (Teleports.HOUSE_TAB.isInInventory()) {
            Microbot.log("Using House tab");
            Teleports.HOUSE_TAB.interactInventory();
            teleportInitiated = true;
        } else {
            Microbot.log("No PoH teleport found! Resetting to banking.");
            setState(State.BANKING);
            return;
        }

        if (teleportInitiated) {
            // wait for PoH portal
            sleepUntil(() -> Microbot.getRs2TileObjectCache().query().withId(4525).first() != null, 10000);
            sleepGaussian(150, 25);
            Microbot.log("We should be in poh fully loaded");
        }
    }

    private void restoreAtPohIfNeeded() {
        if (!needsRestore()) {
            return;
        }

        sleepGaussian(700, 200);
        Microbot.log("We are thirsty..let us Drink");
        OptionalInt poolObjectId = Arrays.stream(NetoRcConstants.POH_POOL_OBJECTS)
                .filter(id -> findObject(id) != null)
                .findFirst();

        if (poolObjectId.isPresent()) {
            interactObject(poolObjectId.getAsInt(), "Drink");
            sleepUntil(() -> !Rs2Player.isInteracting() && Rs2Player.getRunEnergy() > 90, 5000);
        } else {
            Microbot.log("Unable to find POH pool, resetting to banking for a retry");
            setState(State.BANKING);
        }
    }

    private void handleGoingHome() {
        if (plugin.isBreakHandlerEnabled()) {
            BreakHandlerScript.setLockState(true);
        }

        if (config.runeType() == RuneType.WRATH && !needsRestore()) {
            setState(State.WALKING_TO);
            return;
        }

        if (config.runeType() == RuneType.BLOOD && Teleports.FARMING_CAPE.isWearing()) {
            handleFarmingCape();
        } else {
            teleportToPoh();
        }

        restoreAtPohIfNeeded();

//        if (needsRestore()) {
//            return;
//        }

        if (config.runeType() == RuneType.BLOOD) {
            sleepGaussian(700, 200);
            handlePohFairyRing();
        } else if (config.runeType() == RuneType.WRATH) {
            setState(State.WALKING_TO);
        }
    }

    private void handlePohFairyRing() {

        // Wait for Fairy Ring / Tree with ring
        sleepUntil(() ->
            findObject(ObjectID.POH_FAIRY_RING) != null ||
            new Rs2TileObjectQueryable().withNameContains("spirit").first() != null, 10000);

        if (findObject(ObjectID.POH_FAIRY_RING) != null) {
            interactObject(ObjectID.POH_FAIRY_RING, "Last-destination");
            Microbot.log("Using fairy ring");
        }
        else {
            var pohTreeRing = new Rs2TileObjectQueryable().withNameContains("spirit").first();
            if (pohTreeRing != null) {
                interactObject(pohTreeRing.getId(), "Last-destination");
                Microbot.log("Using fairy tree");
                Rs2Player.waitForAnimation();
            }
            else {
                Microbot.log("Unable to find fairy ring, resetting to banking for a retry");
                setState(State.BANKING);
            }
        }
        setState(State.WALKING_TO);
    }


    private void smartWalk(WorldPoint dst) {
        smartWalk(dst, 7);
    }

    private void smartWalk(WorldPoint dst, int distanceThreshold) {
        WorldPoint myLocation = plugin.getMyWorldPoint();
        if (myLocation == null) {
            Microbot.log("MyLocation is null");
            return;
        }
        if (plugin.isBreakHandlerEnabled()) {
            BreakHandlerScript.setLockState(true);
        }
        Microbot.log("Walking from (" + myLocation.getX() + "," + myLocation.getY() + "," + myLocation.getPlane() +
                ") to (" + dst.getX() + "," + dst.getY() + "," + dst.getPlane() + ") with threshold " + distanceThreshold);

        var future = scheduledExecutorService.submit(() -> Rs2Walker.walkTo(dst));

        while (!future.isDone()) {
            WorldPoint currentLoc = plugin.getMyWorldPoint();
            if (currentLoc != null && currentLoc.distanceTo(dst) <= distanceThreshold) {
                Rs2Walker.setTarget(null);
                future.cancel(true);
                break;
            }
            sleep(100);
        }

        if (plugin.isBreakHandlerEnabled()) {
            BreakHandlerScript.setLockState(false);
        }
    }

    private void handleWalking() {
        if (plugin.isBreakHandlerEnabled()) {
            BreakHandlerScript.setLockState(true);
        }

        if (config.runeType() == RuneType.WRATH) {
            handleWrathWalking();
        } else if (config.runeType() == RuneType.BLOOD) {
            handleBloodWalking();
        }
    }


    private boolean handleTransition(int objectId, String action) {
        WorldPoint startPoint = plugin.getMyWorldPoint();
        var obj = findObject(objectId);
        if (obj == null) return false;

        Microbot.log("Interacting with object " + objectId + " (" + action + ")");
        interactObject(objectId, action);
        sleepUntil(Rs2Player::isAnimating, 5000);
        sleepUntil(() -> !Rs2Player.isAnimating(), 15000);
        sleepGaussian(150, 25);
        boolean success = !plugin.getMyWorldPoint().equals(startPoint);
        if (success) {
            Microbot.log("Successfully transitioned from " + startPoint + " to " + plugin.getMyWorldPoint());
        } else {
            Microbot.log("Transition failed for object " + objectId);
        }
        return success;
    }

    // Transition that handles multiple objects with the same ID
    private boolean handleTransition(int objectId, WorldPoint location, String action) {
        WorldPoint startPoint = plugin.getMyWorldPoint();
        var obj = Rs2GameObject.findObjectByLocation(location);
        if (obj == null || obj.getId() != objectId) return false;

        Microbot.log("Interacting with object " + objectId + " at " + location + " (" + action + ")");
        Rs2GameObject.interact(obj, action);
        sleepUntil(Rs2Player::isAnimating, 5000);
        sleepUntil(() -> !Rs2Player.isAnimating(), 15000);
        sleepGaussian(150, 25);
        boolean success = !plugin.getMyWorldPoint().equals(startPoint);
        if (success) {
            Microbot.log("Successfully transitioned from " + startPoint + " to " + plugin.getMyWorldPoint());
        } else {
            Microbot.log("Transition failed for object " + objectId + " at " + location);
        }
        return success;
    }

    private boolean handleTransLoc(int objectId, WorldPoint waitLocation, String action) {
        var obj = findObject(objectId);
        if (obj == null) return false;

        Microbot.log("Interacting with object " + objectId + " (" + action + ") waiting for loc " + waitLocation);
        interactObject(objectId, action);
        boolean success = sleepUntil(() -> plugin.getMyWorldPoint().equals(waitLocation), 20000);
        if (success) {
            Microbot.log("Successfully transitioned to " + waitLocation);
            sleepGaussian(150, 25);
        } else {
            Microbot.log("Transition failed for object " + objectId + " to loc " + waitLocation);
        }
        return success;
    }


    private void handleBloodWalking() {
        BloodStep initialStep;
        do {
            initialStep = bloodStep;
            switch (bloodStep) {
                case CAVE_1:
                    if (handleTransLoc(16308, new WorldPoint(3460, 9813, 0), "Enter")) {
                        bloodStep = BloodStep.CAVE_2;
                    }
                    break;
                case CAVE_2:
                    if (handleTransLoc(5046, new WorldPoint(3481, 9824, 0), "Enter")) {
                        bloodStep = BloodStep.CAVE_3;
                    }
                    break;
                case CAVE_3:
                    int agilityLevel = Rs2Player.getRealSkillLevel(Skill.AGILITY);
                    if (agilityLevel >= 93) {
                        if (handleTransition(43759, "Enter")) {
                            bloodStep = BloodStep.CAVE_4;
                        }
                    }
                    else if (agilityLevel >= 74) {
                        if (handleTransition(12770, "Enter")) {
                            bloodStep = BloodStep.CAVE_4;
                        }
                    }
                    break;
                case CAVE_4:
                    int agilityLevel2 = Rs2Player.getRealSkillLevel(Skill.AGILITY);
                    if (agilityLevel2 >= 93) {
                        if (handleTransition(43762, "Enter")) {
                            bloodStep = BloodStep.RUINS;
                        }
                    }
                    else if (agilityLevel2 >= 74) {
                        if (handleTransition(12771, new WorldPoint(3492, 9861, 0), "Enter")) {
                            bloodStep = BloodStep.CAVE_5;
                        }
                    }
                    break;
                case CAVE_5:
                    if (handleTransLoc(43755, new WorldPoint(3560, 9809, 0), "Enter")) {
                        bloodStep = BloodStep.CAVE_6;
                    }
                    break;
                case CAVE_6:
                    if (handleTransLoc(43758, new WorldPoint(3555, 9783, 0), "Enter")) {
                        bloodStep = BloodStep.RUINS;
                    }
                    break;
                case RUINS:
                    if (handleTransLoc(ObjectID.BLOODTEMPLE_RUINED, new WorldPoint(3239, 4832, 0), "Enter")) {
                        bloodStep = BloodStep.ALTAR;
                    }
                    break;
                case ALTAR:
                    var altar = findObject(bloodAltar);
                    if (altar != null) {
                        interactObject(altar.getId(), "Craft-rune");
                        bloodStep = BloodStep.CAVE_1;
                        setState(State.CRAFTING);
                        return;
                    }
                    break;
            }
        } while (bloodStep != initialStep);
    }


    private void handleWrathWalking() {
        if (plugin.isBreakHandlerEnabled()) {
            BreakHandlerScript.setLockState(true);
        }

        if (Rs2Bank.isOpen()) {
            Rs2Bank.closeBank();
        }

        WrathStep initialStep;
        do {
            initialStep = wrathStep;
            switch (wrathStep) {
                case MYTH_CAPE:
                    if (Rs2Equipment.isWearing(mythCape) || Rs2Inventory.contains(mythCape)) {
                        sleepUntil(() -> !Rs2Player.isAnimating(), 10000);
                        if (Rs2Equipment.isWearing(mythCape)) {
                            Rs2Equipment.interact(mythCape, "Teleport");
                        } else {
                            Rs2Inventory.interact(mythCape, "Teleport");
                        }
                        sleepUntil(Rs2Player::isAnimating, 5000);
                        sleepUntil(() -> !Rs2Player.isAnimating(), 5000);
                        sleepUntilOnClientThread(() -> findObject(31626) != null, 5000); // Wait for Myth Statue
                        wrathStep = WrathStep.MYTH_STATUE;
                    }
                    break;
                case MYTH_STATUE:
                    var statue = findObject(31626);
                    if (statue != null && !Rs2Player.isAnimating()) {
                        sleepGaussian(150, 25); // 100 to 200 ms
                        interactObject(statue.getId(), "Teleport");
                        sleepUntilOnClientThread(() -> findObject(31807) != null, 5000); // Wait for Cave
                        wrathStep = WrathStep.CAVE;
                    }
                    break;
                case CAVE:
                    var cave = findObject(31807);
                    if (cave != null) {
                        sleepGaussian(150, 25); // 100 to 200 ms
                        interactObject(cave.getId(), "Enter");
                        sleepUntilOnClientThread(() -> findObject(ObjectID.WRATHTEMPLE_RUINED) != null, 20000); // Wait for Ruins
                        wrathStep = WrathStep.RUINS;
                    }
                    break;
                case RUINS:
                    var ruins = findObject(ObjectID.WRATHTEMPLE_RUINED);
                    if (ruins != null) {
                        sleepGaussian(1250, 250); // 500 to 2000 ms
                        interactObject(ruins.getId(), "Enter");
                        sleepUntilOnClientThread(() -> findObject(wrathAltar) != null, 5000); // Wait for Altar
                        wrathStep = WrathStep.ALTAR;
                    }
                    break;
                case ALTAR:
                    var altar = findObject(wrathAltar);
                    if (altar != null) {
                        sleepGaussian(150, 25); // 100 to 200 ms
                        interactObject(altar.getId(), "Craft-rune");
                        wrathStep = WrathStep.MYTH_CAPE;
                        setState(State.CRAFTING);
                        return;
                    }
                    break;
            }
        } while (wrathStep != initialStep);
    }


    private void handleCrafting() {
        if (plugin.isBreakHandlerEnabled()) {
            BreakHandlerScript.setLockState(true);
        }

        sleepGaussian(150, 25); // 100 to 200 ms
        Rs2Tab.switchTo(InterfaceTab.INVENTORY);
        sleepGaussian(150, 25); // 100 to 200 ms
        hoverInvItem("Colossal Pouch");

        // Wait for first batch to be crafted
        sleepUntilOnClientThread(() -> !Rs2Inventory.contains(pureEss), 15000);
        
        plugin.updateXpGained();
        handleEmptyPouch();

        if (pauseForBreakAfterCrafting()) {
            setState(State.BANKING);
            return;
        }

        setState(State.BANKING);
    }

    private boolean pauseForBreakAfterCrafting() {
        if (!isBankingRegion(plugin.getMyWorldPoint().getRegionID())) {
            handleBankTeleport();
            sleepUntil(() -> isBankingRegion(plugin.getMyWorldPoint().getRegionID()), 5000);
        }

        if (!isBankingRegion(plugin.getMyWorldPoint().getRegionID())) {
            if (plugin.isBreakHandlerEnabled()) {
                BreakHandlerScript.setLockState(true);
            }
            return false;
        }

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

    private void handleEmptyPouch() {
        while (!Rs2Inventory.allPouchesEmpty()) {
            Microbot.log("Pouches are not empty. Crafting more");
            Rs2Inventory.interact("Colossal Pouch", "Empty");
            hoverObject(bloodAltar);
            boolean hasEssence = sleepUntil(() -> Rs2Inventory.contains(pureEss), 2000);

            if (hasEssence) {
                if (config.runeType() == RuneType.BLOOD) {
                    interactObject(bloodAltar, "Craft-rune");
                }
                if (config.runeType() == RuneType.WRATH) {
                    interactObject(wrathAltar, "Craft-rune");
                }
                hoverInvItem("Colossal Pouch");
                sleepUntil(() -> !Rs2Inventory.contains(pureEss), 3000);
            } else {
                Microbot.log("Failed to empty pouch, retrying...");
            }
        }
    }

    private void handleBankTeleport() {
        if (config.usePoh()) {
            forceDrinkAtFerox = false;
        }

        boolean needRefill = needsRestore() || (!config.usePoh() && forceDrinkAtFerox);

        if (!needRefill && Teleports.CRAFTING_CAPE.isInInventory()) {
            Microbot.log("Using: " + Teleports.CRAFTING_CAPE.getName());
            Teleports.CRAFTING_CAPE.interactInventory();
            sleepUntil(() -> Teleports.CRAFTING_CAPE.matchesRegion(plugin.getMyWorldPoint().getRegionID()));
            sleepGaussian(600, 200);
            return;
        }

        Rs2Tab.switchTo(InterfaceTab.INVENTORY);
        sleepGaussian(1300, 200);
        List<Teleports> bankTeleport;
        if (needRefill && !config.usePoh()) {
            bankTeleport = Arrays.asList(
                    Teleports.FEROX_ENCLAVE,
                    Teleports.CRAFTING_CAPE,
                    Teleports.FARMING_CAPE);
        } else {
            bankTeleport = Arrays.asList(
                    Teleports.CRAFTING_CAPE,
                    Teleports.FARMING_CAPE,
                    Teleports.SAILORS_AMULET,
                    Teleports.CASTLE_WARS);
        }
        for (Teleports teleport : bankTeleport) {
            if (teleport.isWearing()) {
                Microbot.log("Using: " + teleport.getName());
                teleport.interactWorn();
                sleepUntil(() -> teleport.matchesRegion(plugin.getMyWorldPoint().getRegionID()));
                sleepGaussian(600, 200);
                if (!config.usePoh() && teleport == Teleports.FEROX_ENCLAVE) {
                    forceDrinkAtFerox = true;
                    handleFeroxRunEnergy();
                }
                break;
            }
        }
    }
}
