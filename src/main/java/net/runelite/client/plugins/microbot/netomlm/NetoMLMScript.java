package net.runelite.client.plugins.microbot.netomlm;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameObject;
import net.runelite.api.Perspective;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.player.Rs2PlayerCache;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.netomlm.enums.MLMMiningSpot;
import net.runelite.client.plugins.microbot.netomlm.enums.MLMStatus;
import net.runelite.client.plugins.microbot.util.antiban.AntibanPlugin;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.depositbox.Rs2DepositBox;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Gembag;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

@Slf4j
public class NetoMLMScript extends Script
{
	private enum PrepStep
	{
		OPEN_BANK,
		DEPOSIT_INVENTORY,
		VALIDATE_REQUIRED_ITEMS,
		EQUIP_PROSPECTOR,
		EQUIP_PICKAXE,
		EQUIP_RING,
		EQUIP_NECKLACE,
		DEPOSIT_LEFTOVERS,
		WITHDRAW_HAMMER,
		CLOSE_BANK
	}

	private enum WalkingStep
	{
		REACH_MINING_GUILD,
		ENTER_MOTHERLODE,
		FIRST_ROCKFALL,
		SECOND_ROCKFALL,
		WALK_TO_ROCKSLIDE,
		WALK_TO_CRATE,
		OPEN_MLM_BANK,
		CLOSE_MLM_BANK
	}

    private static final WorldArea WEST_UPPER_AREA = new WorldArea(3748, 5676, 7, 9, 0);
    private static final WorldArea EAST_UPPER_AREA = new WorldArea(3756, 5667, 8, 8, 0);
    // Static areas for lower floor to avoid getting stuck behind rockfall
    private static final WorldArea WEST_LOWER_AREA = new WorldArea(3729, 5653, 10, 22, 0);
    private static final WorldArea SOUTH_LOWER_AREA = new WorldArea(3740, 5640, 20, 20, 0);

	private static final WorldPoint HOPPER_DEPOSIT_DOWN = new WorldPoint(3748, 5672, 0);
	private static final WorldPoint HOPPER_DEPOSIT_UP = new WorldPoint(3755, 5677, 0);
	private static final WorldPoint MINING_GUILD_DESTINATION = new WorldPoint(3053, 9768, 0);
	private static final WorldPoint FIRST_ROCKFALL_LOCATION = new WorldPoint(3731, 5683, 0);
	private static final WorldPoint SECOND_ROCKFALL_LOCATION = new WorldPoint(3733, 5680, 0);
	private static final WorldPoint ROCKSLIDE_LOCATION = new WorldPoint(3741, 5676, 0);
	private static final WorldPoint MLM_CRATE_LOCATION = new WorldPoint(3756, 5668, 0);
	private static final String IMCANDO_HAMMER_OFFHAND_NAME = "Imcando hammer (off-hand)";

	private static final int[] DRAGON_PICKAXE_IDS =
	{
		ItemID.DRAGON_PICKAXE,
		ItemID.DRAGON_PICKAXE_PRETTY,
		ItemID.ZALCANO_PICKAXE,
		ItemID.TRAILBLAZER_PICKAXE_NO_INFERNAL,
		ItemID.TRAILBLAZER_RELOADED_PICKAXE_NO_INFERNAL
	};
	private static final int[] PROSPECTOR_HEAD_IDS =
	{
		ItemID.MOTHERLODE_REWARD_HAT_GOLD,
		ItemID.MOTHERLODE_REWARD_HAT,
		ItemID.FOSSIL_MOTHERLODE_REWARD_HAT
	};
	private static final int[] PROSPECTOR_BODY_IDS =
	{
		ItemID.MOTHERLODE_REWARD_TOP_GOLD,
		ItemID.MOTHERLODE_REWARD_TOP,
		ItemID.FOSSIL_MOTHERLODE_REWARD_TOP
	};
	private static final int[] PROSPECTOR_LEGS_IDS =
	{
		ItemID.MOTHERLODE_REWARD_LEGS_GOLD,
		ItemID.MOTHERLODE_REWARD_LEGS,
		ItemID.FOSSIL_MOTHERLODE_REWARD_LEGS
	};
	private static final int[] PROSPECTOR_BOOTS_IDS =
	{
		ItemID.MOTHERLODE_REWARD_BOOTS_GOLD,
		ItemID.MOTHERLODE_REWARD_BOOTS,
		ItemID.FOSSIL_MOTHERLODE_REWARD_BOOTS
	};

	private static final WorldArea CRATE_AREA = new WorldArea(new WorldPoint(3750, 5659, 0), 10, 16);

	private static final WorldPoint[] CRATE_WALKPOINTS = new WorldPoint[]
	{
		new WorldPoint(3755, 5671, 0),
		new WorldPoint(3756, 5662, 0),
		new WorldPoint(3751, 5662, 0),
	};

    private static final int UPPER_FLOOR_HEIGHT = -490;
    private static final int SACK_LARGE_SIZE = 189;
    private static final int SACK_SIZE = 108;
	private static final int SECOND_ROCKFALL_MAX_ATTEMPTS = 2;
    public static MLMStatus status = MLMStatus.IDLE;
    public static Rs2TileObjectModel oreVein;
    public static MLMMiningSpot miningSpot = MLMMiningSpot.IDLE;
    private int maxSackSize;
	private List<String> itemsToKeep;

	private final NetoMLMPlugin plugin;
    private final NetoMLMConfig config;
    private final Rs2TileObjectCache rs2TileObjectCache;
    private final Rs2PlayerCache rs2PlayerCache;


	private boolean shouldEmptySack = false;
	private boolean shouldRepairWaterwheel = false;
	private boolean emptySackWorkflowActive = false;
	private long idleSince = 0;
	private int idleThreshold = 0;
    private boolean pickedUpHammer = false;
    private MLMStatus lastLoggedStatus = null;
	private PrepStep prepStep = PrepStep.OPEN_BANK;
	private WalkingStep walkingStep = WalkingStep.REACH_MINING_GUILD;
	private int secondRockfallAttempts = 0;
	private Integer selectedPickaxeId = null;

	@Inject
	public NetoMLMScript(NetoMLMPlugin plugin, NetoMLMConfig config, Rs2TileObjectCache rs2TileObjectCache, Rs2PlayerCache rs2PlayerCache)
	{
		this.plugin = plugin;
		this.config = config;
        this.rs2TileObjectCache = rs2TileObjectCache;
        this.rs2PlayerCache = rs2PlayerCache;
    }

    public boolean run()
    {
        log.info("Starting Neto MLM script");
        initialize();
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(this::executeTaskSafely, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private void initialize()
    {
        log.debug("Initializing MLM runtime state");
        Rs2Antiban.antibanSetupTemplates.applyMiningSetup();
        miningSpot = MLMMiningSpot.IDLE;
        status = MLMStatus.PREP;
        lastLoggedStatus = null;
		prepStep = PrepStep.OPEN_BANK;
		walkingStep = WalkingStep.REACH_MINING_GUILD;
		secondRockfallAttempts = 0;
		selectedPickaxeId = null;
        shouldEmptySack = false;
		shouldRepairWaterwheel = false;
		emptySackWorkflowActive = false;
    }

    private void executeTaskSafely()
    {
        try
        {
            executeTask();
        }
        catch (Exception ex)
        {
            log.error("Unhandled error in MLM main loop; resetting runtime state", ex);
            abortCurrentWorkflow();
        }
    }

    private void executeTask()
    {
        if (!super.run() || !isWorkflowRunnable())
        {
            abortCurrentWorkflow();
            return;
        }

        logStatusTransitionIfChanged();
		if (status == MLMStatus.PREP)
		{
			handlePrep();
			return;
		}
		if (status == MLMStatus.WALKING)
		{
			handleWalking();
			return;
		}
		if (status == MLMStatus.IDLE)
		{
			determineStatusFromInventory();
			return;
		}

		determineStatusFromInventory();
		logStatusTransitionIfChanged();

        switch (status)
        {
			case PREP:
			case WALKING:
            case IDLE:
                break;
            case MINING:
				if (Rs2Antiban.getActivity() != null)
				{
					Rs2Antiban.setActivityIntensity(Rs2Antiban.getActivity().getActivityIntensity());
				}
                handleMining();
                break;
            case EMPTY_SACK:
                if (Rs2Player.isAnimating()) return;
                Rs2Antiban.setActivityIntensity(ActivityIntensity.EXTREME);
                emptySack();
                break;
            case FIXING_WATERWHEEL:
                if (Rs2Player.isAnimating()) return;
                fixWaterwheel();
                break;
            case DEPOSIT_HOPPER:
                if (Rs2Player.isAnimating()) return;
                depositHopper();
                break;
            case DROP_GEMS:
                if (Rs2Player.isAnimating()) return;
                dropGems();
                break;
        }
    }
    private String[] SPEC_PICKAXES = {"dragon pickaxe", "crystal pickaxe", "infernal pickaxe"};

    private void handlePickaxeSpec() {
        if (Rs2Equipment.isWearing(SPEC_PICKAXES)) {
            Rs2Combat.setSpecState(true, 1000);
        }
    }

	private void handlePrep()
	{
		if (prepStep != PrepStep.OPEN_BANK && prepStep != PrepStep.CLOSE_BANK && !Rs2Bank.isOpen())
		{
			log.warn("Bank closed during PREP step {}, restarting bank preparation", prepStep);
			prepStep = PrepStep.OPEN_BANK;
			return;
		}

		switch (prepStep)
		{
			case OPEN_BANK:
				if (Rs2Bank.walkToBankAndUseBank() && Rs2Bank.isOpen())
				{
					prepStep = PrepStep.DEPOSIT_INVENTORY;
				}
				break;
			case DEPOSIT_INVENTORY:
				if (Rs2Inventory.isEmpty() || Rs2Bank.depositAll())
				{
					prepStep = PrepStep.VALIDATE_REQUIRED_ITEMS;
				}
				break;
			case VALIDATE_REQUIRED_ITEMS:
				selectedPickaxeId = selectPrepPickaxe();
				boolean hasPickaxe = selectedPickaxeId != null;
				boolean hasHammer = hasPrepHammerAvailable();
				if (!hasPickaxe || !hasHammer)
				{
					String missing = !hasPickaxe && !hasHammer ? "a Dragon/Rune pickaxe and a hammer"
						: !hasPickaxe ? "a usable Dragon/Rune pickaxe" : "an Imcando hammer (off-hand) or regular hammer";
					Microbot.showMessage("Neto MLM requires " + missing + ". Stopping plugin.");
					log.warn("PREP failed: missing {}", missing);
					Microbot.stopPlugin(plugin);
					return;
				}
				prepStep = PrepStep.EQUIP_PROSPECTOR;
				break;
			case EQUIP_PROSPECTOR:
				if (equipProspectorSet())
				{
					prepStep = PrepStep.EQUIP_PICKAXE;
				}
				break;
			case EQUIP_PICKAXE:
				if (equipSelectedPickaxe())
				{
					prepStep = PrepStep.EQUIP_RING;
				}
				break;
			case EQUIP_RING:
				if (equipCelestialRing())
				{
					prepStep = PrepStep.EQUIP_NECKLACE;
				}
				break;
			case EQUIP_NECKLACE:
				if (equipLowestChargeSkillsNecklace())
				{
					prepStep = PrepStep.DEPOSIT_LEFTOVERS;
				}
				break;
			case DEPOSIT_LEFTOVERS:
				if (depositPrepLeftovers())
				{
					prepStep = PrepStep.WITHDRAW_HAMMER;
				}
				break;
			case WITHDRAW_HAMMER:
				if (prepareHammer())
				{
					prepStep = PrepStep.CLOSE_BANK;
				}
				break;
			case CLOSE_BANK:
				if (!Rs2Bank.isOpen() || (Rs2Bank.closeBank() && sleepUntil(() -> !Rs2Bank.isOpen(), 3_000)))
				{
					walkingStep = WalkingStep.REACH_MINING_GUILD;
					status = MLMStatus.WALKING;
					log.info("PREP complete");
				}
				break;
		}
	}

	private Integer selectPrepPickaxe()
	{
		if (Rs2Player.getSkillRequirement(Skill.MINING, 61))
		{
			for (int id : DRAGON_PICKAXE_IDS)
			{
				if (Rs2Equipment.isWearing(id)) return id;
			}
			for (int id : DRAGON_PICKAXE_IDS)
			{
				if (Rs2Bank.hasItem(id)) return id;
			}
		}
		if (Rs2Player.getSkillRequirement(Skill.MINING, 41))
		{
			if (Rs2Equipment.isWearing(ItemID.RUNE_PICKAXE)) return ItemID.RUNE_PICKAXE;
			if (Rs2Bank.hasItem(ItemID.RUNE_PICKAXE)) return ItemID.RUNE_PICKAXE;
		}
		return null;
	}

	private Integer findAvailableAllowedPickaxe()
	{
		if (Rs2Player.getSkillRequirement(Skill.MINING, 61))
		{
			for (int id : DRAGON_PICKAXE_IDS)
			{
				if (Rs2Equipment.isWearing(id) || Rs2Inventory.hasItem(id)) return id;
			}
		}
		if (Rs2Player.getSkillRequirement(Skill.MINING, 41)
			&& (Rs2Equipment.isWearing(ItemID.RUNE_PICKAXE) || Rs2Inventory.hasItem(ItemID.RUNE_PICKAXE)))
		{
			return ItemID.RUNE_PICKAXE;
		}
		return null;
	}

	private boolean isAllowedPickaxeId(int itemId)
	{
		return itemId == ItemID.RUNE_PICKAXE
			|| Arrays.stream(DRAGON_PICKAXE_IDS).anyMatch(id -> id == itemId);
	}

	private boolean hasPrepHammerAvailable()
	{
		return isImcandoHammerEquipped()
			|| Rs2Inventory.hasItem(IMCANDO_HAMMER_OFFHAND_NAME, true)
			|| Rs2Bank.hasItem(IMCANDO_HAMMER_OFFHAND_NAME, true)
			|| Rs2Inventory.hasItem(ItemID.HAMMER)
			|| Rs2Bank.hasItem(ItemID.HAMMER);
	}

	private boolean equipProspectorSet()
	{
		return equipBestAvailableItem(PROSPECTOR_HEAD_IDS)
			&& equipBestAvailableItem(PROSPECTOR_BODY_IDS)
			&& equipBestAvailableItem(PROSPECTOR_LEGS_IDS)
			&& equipBestAvailableItem(PROSPECTOR_BOOTS_IDS);
	}

	private boolean equipBestAvailableItem(int[] priorityIds)
	{
		for (int id : priorityIds)
		{
			if (Rs2Equipment.isWearing(id)) return true;
			if (Rs2Bank.hasItem(id)) return Rs2Bank.withdrawAndEquip(id);
		}
		return true;
	}

	private boolean equipSelectedPickaxe()
	{
		if (selectedPickaxeId == null) return false;
		if (Rs2Equipment.isWearing(selectedPickaxeId)) return true;

		boolean isDragonPickaxe = Arrays.stream(DRAGON_PICKAXE_IDS).anyMatch(id -> id == selectedPickaxeId);
		int attackRequirement = isDragonPickaxe ? 60 : 40;
		// Only equip if it has attack requirements, otherwise keep in inventory
		if (Rs2Player.getSkillRequirement(Skill.ATTACK, attackRequirement))
		{
			if (Rs2Inventory.hasItem(selectedPickaxeId))
			{
				Rs2Inventory.wield(selectedPickaxeId);
				return sleepUntil(() -> Rs2Equipment.isWearing(selectedPickaxeId), 2_000);
			}
			return Rs2Bank.withdrawAndEquip(selectedPickaxeId);
		}

		return Rs2Inventory.hasItem(selectedPickaxeId) || Rs2Bank.withdrawOne(selectedPickaxeId);
	}

	private boolean equipCelestialRing()
	{
		if (Rs2Equipment.isWearing(ItemID.CELESTIAL_SIGNET_CHARGED)) return true;
		if (Rs2Bank.hasItem(ItemID.CELESTIAL_SIGNET_CHARGED))
		{
			return Rs2Bank.withdrawAndEquip(ItemID.CELESTIAL_SIGNET_CHARGED);
		}
		if (Rs2Equipment.isWearing(ItemID.CELESTIAL_RING_CHARGED)) return true;
		if (Rs2Bank.hasItem(ItemID.CELESTIAL_RING_CHARGED))
		{
			return Rs2Bank.withdrawAndEquip(ItemID.CELESTIAL_RING_CHARGED);
		}
		return true;
	}

	private boolean equipLowestChargeSkillsNecklace()
	{
		for (int charges = 1; charges <= 6; charges++)
		{
			String itemName = "Skills necklace(" + charges + ")";
			if (Rs2Equipment.isWearing(itemName, true)) return true;
			if (Rs2Bank.hasItem(itemName, true)) return Rs2Bank.withdrawAndEquip(itemName);
		}
		return true;
	}

	private boolean depositPrepLeftovers()
	{
		if (selectedPickaxeId != null && Rs2Inventory.hasItem(selectedPickaxeId))
		{
			Rs2Bank.depositAllExcept(selectedPickaxeId);
			return Rs2Inventory.items().allMatch(item -> item.getId() == selectedPickaxeId);
		}
		return Rs2Inventory.isEmpty() || (Rs2Bank.depositAll() && Rs2Inventory.isEmpty());
	}

	private boolean prepareHammer()
	{
		// Get the required hammer; gem bags are no longer part of automatic PREP
		if (isImcandoHammerEquipped()) return true;
		if (Rs2Inventory.hasItem(IMCANDO_HAMMER_OFFHAND_NAME, true))
		{
			Rs2Inventory.wield(IMCANDO_HAMMER_OFFHAND_NAME);
			return sleepUntil(this::isImcandoHammerEquipped, 2_000);
		}
		if (Rs2Bank.hasItem(IMCANDO_HAMMER_OFFHAND_NAME, true))
		{
			return Rs2Bank.withdrawAndEquip(IMCANDO_HAMMER_OFFHAND_NAME);
		}
		if (Rs2Inventory.hasItem(ItemID.HAMMER)) return true;
		return Rs2Bank.withdrawOne(ItemID.HAMMER);
	}

	private boolean isImcandoHammerEquipped()
	{
		return Rs2Equipment.isWearing(IMCANDO_HAMMER_OFFHAND_NAME, true);
	}

	private void handleWalking()
	{
		switch (walkingStep)
		{
			case REACH_MINING_GUILD:
				if (isNear(MINING_GUILD_DESTINATION, 8))
				{
					walkingStep = WalkingStep.ENTER_MOTHERLODE;
					return;
				}
				String necklace = findEquippedSkillsNecklace();
				if (necklace != null)
				{
					if (Rs2Equipment.interact(necklace, "Mining Guild", true))
					{
						sleepUntil(() -> isNear(MINING_GUILD_DESTINATION, 8), 8_000);
					}
				}
				else
				{
					Rs2Walker.walkTo(MINING_GUILD_DESTINATION, 3);
				}
				break;
			case ENTER_MOTHERLODE:
				if (isNear(FIRST_ROCKFALL_LOCATION, 15))
				{
					walkingStep = WalkingStep.FIRST_ROCKFALL;
					return;
				}
				Rs2TileObjectModel entrance = rs2TileObjectCache.query()
					.withId(ObjectID.MOTHERLODE_ENTRANCE)
					.nearestReachable();
				if (entrance == null)
				{
					Rs2Walker.walkTo(MINING_GUILD_DESTINATION, 3);
					return;
				}
				if (entrance.click("Enter"))
				{
					sleepUntil(() -> isNear(FIRST_ROCKFALL_LOCATION, 15), 8_000);
				}
				break;
			case FIRST_ROCKFALL:
				handleRockfall(ObjectID.MOTHERLODE_ROCKFALL_2, FIRST_ROCKFALL_LOCATION,
					WalkingStep.SECOND_ROCKFALL);
				if (walkingStep == WalkingStep.SECOND_ROCKFALL)
				{
					secondRockfallAttempts = 0;
				}
				break;
			case SECOND_ROCKFALL:
				handleSecondRockfall();
				break;
			case WALK_TO_ROCKSLIDE:
				if (walkToObject(11932, ROCKSLIDE_LOCATION))
				{
					sleep(4_000);
					walkingStep = WalkingStep.WALK_TO_CRATE;
				}
				break;
			case WALK_TO_CRATE:
				if (walkToObject(357, MLM_CRATE_LOCATION))
				{
					sleep(4_000);
					walkingStep = WalkingStep.OPEN_MLM_BANK;
				}
				break;
			case OPEN_MLM_BANK:
				Rs2Bank.openBank();
				if (sleepUntil(Rs2Bank::isOpen, 8_000))
				{
					walkingStep = WalkingStep.CLOSE_MLM_BANK;
				}
				else
				{
					log.warn("Unable to open the MLM bank, retrying cave entry from the first rockfall");
					walkingStep = WalkingStep.FIRST_ROCKFALL;
				}
				break;
			case CLOSE_MLM_BANK:
				if (!Rs2Bank.isOpen() || (Rs2Bank.closeBank() && sleepUntil(() -> !Rs2Bank.isOpen(), 3_000)))
				{
					status = MLMStatus.IDLE;
					log.info("WALKING complete, MLM bank access confirmed");
				}
				break;
		}
	}

	private void handleRockfall(int objectId, WorldPoint objectLocation, WalkingStep nextStep)
	{
		Rs2TileObjectModel rockfall = rs2TileObjectCache.query()
			.withId(objectId)
			.where(object -> objectLocation.equals(object.getWorldLocation()))
			.first();
		if (rockfall != null)
		{
			if (rockfall.click("Mine"))
			{
				boolean cleared = sleepUntil(() -> findObjectAt(objectId, objectLocation) == null, 8_000);
				if (cleared)
				{
					walkingStep = nextStep;
				}
			}
			return;
		}

		walkingStep = nextStep;
	}

	private void handleSecondRockfall()
	{
		Rs2TileObjectModel rockfall = findObjectAt(ObjectID.MOTHERLODE_ROCKFALL_1, SECOND_ROCKFALL_LOCATION);
		if (rockfall == null)
		{
			secondRockfallAttempts = 0;
			walkingStep = WalkingStep.WALK_TO_ROCKSLIDE;
			return;
		}

		secondRockfallAttempts++;
		boolean cleared = rockfall.click("Mine")
			&& sleepUntil(() -> findObjectAt(ObjectID.MOTHERLODE_ROCKFALL_1, SECOND_ROCKFALL_LOCATION) == null, 8_000);
		if (cleared)
		{
			secondRockfallAttempts = 0;
			walkingStep = WalkingStep.WALK_TO_ROCKSLIDE;
			return;
		}

		log.warn("Second rockfall clearance attempt {} of {} was not confirmed",
			secondRockfallAttempts, SECOND_ROCKFALL_MAX_ATTEMPTS);
		if (secondRockfallAttempts >= SECOND_ROCKFALL_MAX_ATTEMPTS)
		{
			secondRockfallAttempts = 0;
			walkingStep = WalkingStep.FIRST_ROCKFALL;
			log.warn("Second rockfall remained blocked after two attempts; retrying the first rockfall");
		}
	}

	private boolean walkToObject(int objectId, WorldPoint objectLocation)
	{
		Rs2TileObjectModel target = findObjectAt(objectId, objectLocation);
		return target != null && Rs2Walker.walkFastCanvas(target.getWorldLocation());
	}

	private Rs2TileObjectModel findObjectAt(int objectId, WorldPoint location)
	{
		return rs2TileObjectCache.query()
			.withId(objectId)
			.where(object -> location.equals(object.getWorldLocation()))
			.first();
	}

	private String findEquippedSkillsNecklace()
	{
		for (int charges = 1; charges <= 6; charges++)
		{
			String itemName = "Skills necklace(" + charges + ")";
			if (Rs2Equipment.isWearing(itemName, true)) return itemName;
		}
		return null;
	}

	private boolean isNear(WorldPoint target, int distance)
	{
		WorldPoint playerLocation = Rs2Player.getWorldLocation();
		return playerLocation != null && playerLocation.distanceTo(target) <= distance;
	}

    private void determineStatusFromInventory()
    {
        updateSackSize();
        if (!hasRequiredTools())
        {
			Microbot.showMessage("Required Dragon/Rune pickaxe is no longer available. Stopping Neto MLM.");
			log.warn("Required Dragon/Rune pickaxe is no longer available, stopping plugin");
			Microbot.stopPlugin(plugin);
            return;
        }

        if (shouldRepairWaterwheel && getBrokenStrutCount() > 1) {
            status = MLMStatus.FIXING_WATERWHEEL;
            return;
        }

        if (config.dropGems() && hasGemsInInventory()) {
            status = MLMStatus.DROP_GEMS;
            return;
        }

        int payDirtCount = payDirtCount();
        if (payDirtCount > 0 && Rs2Inventory.isFull()) {
            resetMiningState();
            status = MLMStatus.DEPOSIT_HOPPER;
            return;
        }

        if (currentSackCount() >= maxSackSize || hasOreInInventory() || (shouldEmptySack && !Rs2Inventory.contains(ItemID.PAYDIRT))) {
            resetMiningState();
            status = MLMStatus.EMPTY_SACK;
            return;
        }
        status = MLMStatus.MINING;
    }

    private boolean hasRequiredTools()
    {
		return findAvailableAllowedPickaxe() != null;
    }

    private void updateSackSize()
    {
        boolean sackUpgraded = Microbot.getVarbitValue(VarbitID.MOTHERLODE_BIGGERSACK) == 1;
        maxSackSize = sackUpgraded ? SACK_LARGE_SIZE : SACK_SIZE;
    }

	private void handleMining()
	{
		if (Rs2Player.getAnimation() != net.runelite.api.AnimationID.IDLE || Rs2Player.isMoving()) {
			idleSince = 0;
			return;
		}
		if (idleSince == 0) {
			idleSince = System.currentTimeMillis();
			idleThreshold = Math.max(2000, Rs2Random.randomGaussian(3000, 600));
			return;
		}
		if (System.currentTimeMillis() - idleSince < idleThreshold) return;
		idleSince = 0;

		if (Rs2Gembag.isUnknown()) {
			Rs2Gembag.checkGemBag();
		}

		shouldRepairWaterwheel = false;

		if (miningSpot == MLMMiningSpot.IDLE)
		{
			selectMiningSpotFromConfig();
		}

		if (isOnSelectedMiningFloor() && findClosestVein() != null && attemptToMineVein())
		{
			return;
		}

		if (!walkToMiningSpot()) return;

		attemptToMineVein();
	}

	private boolean isOnSelectedMiningFloor()
	{
		if (miningSpot.isUpstairs()) return isUpperFloor();
		if (miningSpot.isDownstairs()) return !isUpperFloor();
		return true;
	}


    private void emptySack()
	{
		if (!emptySackWorkflowActive)
		{
			emptySackWorkflowActive = true;
			log.info("Emptying sack workflow started");
		}

		if (!isWorkflowRunnable())
		{
			abortCurrentWorkflow();
			return;
		}

		ensureLowerFloor();
		if (!isWorkflowRunnable())
		{
			abortCurrentWorkflow();
			return;
		}

		if (Microbot.getVarbitValue(VarbitID.MOTHERLODE_SACK_TRANSMIT) <= 0 && !hasOreInInventory())
		{
			completeEmptySackWorkflow();
			return;
		}

		if (hasOreInInventory())
		{
			useDepositBox();
			return;
		}

        if (canDropPayDirt())
        {
            depositHopper();
            return;
        }

        rs2TileObjectCache.query().interact(ObjectID.MOTHERLODE_SACK);
		sleepUntil(() -> !isWorkflowRunnable() || hasOreInInventory(), 10_000);
	}

	private void completeEmptySackWorkflow()
	{
		shouldEmptySack = false;
		shouldRepairWaterwheel = false;
		emptySackWorkflowActive = false;
		if (config.mineUpstairs())
		{
			selectRandomUpperMiningSpot();
		}
		Rs2Antiban.takeMicroBreakByChance();
		status = MLMStatus.IDLE;
        log.info("Emptying sack workflow complete");
	}

	private boolean isWorkflowRunnable()
	{
		if (!Microbot.isLoggedIn() || Microbot.pauseAllScripts.get() || Thread.currentThread().isInterrupted())
		{
			return false;
		}

		try
		{
			return Microbot.getClientThread().runOnClientThreadOptional(() -> {
				var player = Microbot.getClient().getLocalPlayer();
				return player != null && player.getWorldView() != null;
			}).orElse(false);
		}
		catch (RuntimeException ex)
		{
			log.debug("Player state unavailable during MLM lifecycle transition", ex);
			return false;
		}
	}

	private void abortCurrentWorkflow()
	{
		resetMiningState(true);
		if (status != MLMStatus.PREP && status != MLMStatus.WALKING)
		{
			status = MLMStatus.IDLE;
		}
		idleSince = 0;
		shouldEmptySack = false;
		shouldRepairWaterwheel = false;
		emptySackWorkflowActive = false;
		pickedUpHammer = false;
	}

    private boolean hasOreInInventory()
    {
        return Rs2Inventory.contains(
                ItemID.RUNITE_ORE, ItemID.ADAMANTITE_ORE, ItemID.MITHRIL_ORE,
                ItemID.GOLD_ORE, ItemID.COAL
        );
    }

    private boolean hasGemsInInventory() {
        return Rs2Inventory.contains(ItemID.UNCUT_SAPPHIRE, ItemID.UNCUT_EMERALD, ItemID.UNCUT_RUBY, ItemID.UNCUT_DIAMOND);
    }
    
    private void dropGems() {
        if (hasGemsInInventory()) {
            Rs2Inventory.dropAll(ItemID.UNCUT_SAPPHIRE, ItemID.UNCUT_EMERALD, ItemID.UNCUT_RUBY, ItemID.UNCUT_DIAMOND);
        }
    }

    private int payDirtCount() {
        return Rs2Inventory.count(ItemID.PAYDIRT);
    }

    private boolean canDropPayDirt() {
        return payDirtCount() > 0 && currentSackCount() < maxSackSize;
    }

    private int currentSackCount() {
        return Microbot.getVarbitValue(VarbitID.MOTHERLODE_SACK_TRANSMIT);
    }

    private void fixWaterwheel() {
        log.info("Fixing waterwheel workflow started");
        ensureLowerFloor();

		if (!hasHammer()) {
			if (!obtainHammer()) return;
		}

		if (rs2TileObjectCache.query().interact(ObjectID.MOTHERLODE_WHEEL_STRUT_BROKEN))
		{
			// We use a modified version of waitForXpDrop to ensure we break out of the sleep if the strut is repaired
			final int skillExp = Microbot.getClientThread().invoke(() -> Microbot.getClient().getSkillExperience(Skill.SMITHING));
			sleepUntilTrue(() -> skillExp != Microbot.getClientThread().invoke(() -> Microbot.getClient().getSkillExperience(Skill.SMITHING)) || getBrokenStrutCount() <= 1, 250, 20_000);

			dropHammerIfNeeded();
			shouldRepairWaterwheel = false;
            log.info("Waterwheel repair complete");
		}
    }

    private void depositHopper()
    {
        // if using a gem bag, fill the gem bag and return to mining if the inventory is no longer full
        if (Rs2Inventory.isFull() && (Rs2Gembag.hasGemBag() && !Rs2Gembag.isGemBagOpen()))
        {
			Rs2Inventory.interact("gem bag", "open");
			sleepUntil(Rs2Gembag::isGemBagOpen);
            Rs2Inventory.interact("gem bag", "fill");
            if (!Rs2Inventory.isFull())
            {
                return;
            }
        }

        WorldPoint hopperDeposit = (isUpperFloor() && config.upstairsHopperUnlocked()) ? HOPPER_DEPOSIT_UP : HOPPER_DEPOSIT_DOWN;
        Rs2TileObjectModel hopper = rs2TileObjectCache.query().where(x -> x.getWorldLocation().equals(hopperDeposit)).withId(ObjectID.MOTHERLODE_HOPPER).first();

        if(isUpperFloor() && !config.upstairsHopperUnlocked())
        {
            ensureLowerFloor();
        }

        final int paydirtToDeposit = payDirtCount();

        if (hopper != null && hopper.click()) {
            log.debug("Depositing pay-dirt into hopper");
            sleepUntil(() -> payDirtCount() != paydirtToDeposit && !Rs2Player.isAnimating(), 10_000);

			shouldRepairWaterwheel = true;

            // Calculate the effective sack size after deposit as VarbitID.MOTHERLODE_SACK_TRANSMIT takes time to update
            final int currentSackAmount = currentSackCount();
            final int effectiveSackAmount = Math.max(currentSackAmount, Math.min(maxSackSize, currentSackAmount + paydirtToDeposit));

			shouldEmptySack = effectiveSackAmount >= (maxSackSize - 28);
            log.debug("Hopper deposit complete: paydirtDeposited={}, effectiveSackAmount={}, shouldEmptySack={}",
                    paydirtToDeposit, effectiveSackAmount, shouldEmptySack);
        }
        else
        {
            log.debug("Hopper unavailable, walking closer to deposit point");
            Rs2Walker.walkTo(hopperDeposit, 15);
        }
    }

    private void useDepositBox()
    {
        if (Rs2DepositBox.openDepositBox())
        {
            sleepUntil(Rs2DepositBox::isOpen);

            // if using the gem sack, empty its contents directly into the bank
            if (Rs2Gembag.hasGemBag() && Rs2Gembag.getGemBagContents().stream().anyMatch(s -> s.getQuantity() > 30))
            {
				Rs2Bank.emptyGemBag();
				sleep(100, 300);
            }

			boolean hasHammerInInventory = Rs2Inventory.hasItem(ItemID.HAMMER)
				|| Rs2Inventory.hasItem(IMCANDO_HAMMER_OFFHAND_NAME, true);
			if (isImcandoHammerEquipped() && !hasHammerInInventory) {
				Rs2DepositBox.depositAll();
			} else {
				Rs2DepositBox.depositAllExcept(getItemsToKeep(), true);
				Rs2Inventory.waitForInventoryChanges(5000);
			}

			Rectangle gameObjectBounds = getMotherloadSackBounds();
			Rectangle depositBoxBounds = Rs2DepositBox.getDepositBoxBounds();
			if (depositBoxBounds != null && (!Rs2UiHelper.isRectangleWithinViewport(gameObjectBounds) || depositBoxBounds.intersects(gameObjectBounds))) {
				Rs2DepositBox.closeDepositBox();
			}
        }
    }

    private void selectMiningSpotFromConfig() {
        if (config.mineUpstairs()) {
            selectRandomUpperMiningSpot();
            return;
        }

        MLMMiningSpot selected = MLMMiningSpot.valueOf(config.miningArea().name());

        if (selected == MLMMiningSpot.ANY) {
			MLMMiningSpot[] filteredSpots = Arrays.stream(MLMMiningSpot.values())
				.filter(s -> s.getWorldPoint() != null && s.isDownstairs())
				.toArray(MLMMiningSpot[]::new);

			int size = filteredSpots.length;
			if (size == 0) return;

			int randomIndex = Rs2Random.randomGaussian(size / 2.0, size / 6.0);
			randomIndex = Math.max(0, Math.min(size - 1, randomIndex));

			miningSpot = filteredSpots[randomIndex];
        } else {
            switch (selected) {
                case WEST_LOWER:
                case WEST_MID:
                case SOUTH_WEST:
                case SOUTH_EAST:
                    miningSpot = selected;
                    break;
                default:
                    Microbot.showMessage("Invalid mining area selected.");
                    log.warn("Invalid mining area selected: {}", selected);
                    Microbot.stopPlugin(plugin);
                    return;
            }
        }

        // Shuffle order of veins within the selected area
        if (miningSpot.getWorldPoint() != null) {
            Collections.shuffle(miningSpot.getWorldPoint());
        }
        log.info("Selected mining spot: {}", miningSpot);
    }

	private void selectRandomUpperMiningSpot()
	{
		miningSpot = ThreadLocalRandom.current().nextBoolean()
			? MLMMiningSpot.WEST_UPPER
			: MLMMiningSpot.EAST_UPPER;
		Collections.shuffle(miningSpot.getWorldPoint());
		log.info("Selected random upstairs mining spot: {}", miningSpot);
	}

    private boolean walkToMiningSpot()
    {
        WorldPoint target = miningSpot.getWorldPoint().get(0);

        // Navigates to correct floor based on selected mining area
        if (miningSpot.isUpstairs() && !isUpperFloor())
        {
            goUp();
            return false; // Wait until we've gone up
        }

        if (miningSpot.isDownstairs() && isUpperFloor()) {
            goDown();
            return false; // Wait until we've gone down
        }

        // Walk to actual mining target tile
        return Rs2Walker.walkTo(target, 10);
    }

	private boolean attemptToMineVein() {
        Rs2TileObjectModel vein = findClosestVein();
		if (vein == null) {
			repositionCameraAndMove();
			return false;
		}

		handlePickaxeSpec();

		if (!vein.click()) return false;
		oreVein = vein;

		WorldPoint veinLocation = vein.getWorldLocation();
		
		return sleepUntil(() -> {
			Rs2TileObjectModel _vein = rs2TileObjectCache.query().where(o -> Objects.equals(o.getWorldLocation(), veinLocation)).nearestReachable();
			if (_vein == null || !isValidVein(_vein)) return false;
			WorldPoint playerLoc = Microbot.getClientThread().invoke(() -> Microbot.getClient().getLocalPlayer().getWorldLocation());
			return AntibanPlugin.isMining() && playerLoc != null && _vein.getWorldLocation().distanceTo(playerLoc) <= 2;
		}, 10_000);
	}

    private Rs2TileObjectModel findClosestVein()
    {
        return rs2TileObjectCache.query().where(this::isValidVein).nearestReachable();
    }

    private boolean isValidVein(Rs2TileObjectModel wallObject)
    {
        int id = wallObject.getId();
        boolean isVein = (id == 26661 || id == 26662 || id == 26663 || id == 26664);
        if (!isVein) return false;

        WorldPoint location = wallObject.getWorldLocation();

		if (!config.mineUpstairs() && config.useAntiCrash())
		{
			boolean isPlayerNearBy = rs2PlayerCache.query().where(p -> p != null && p.getWorldLocation().distanceTo(wallObject.getWorldLocation()) <= 2).first() != null;
			if (isPlayerNearBy) return false;
		}

		if (config.mineUpstairs())
		{
        boolean inUpperArea = (miningSpot == MLMMiningSpot.WEST_UPPER && WEST_UPPER_AREA.contains(location))
                || (miningSpot == MLMMiningSpot.EAST_UPPER && EAST_UPPER_AREA.contains(location));
            return inUpperArea && hasWalkableTilesAround(wallObject);
        }
        else
        {
        boolean inLowerArea = (miningSpot == MLMMiningSpot.WEST_LOWER && WEST_LOWER_AREA.contains(location))
                || (miningSpot == MLMMiningSpot.WEST_MID && WEST_LOWER_AREA.contains(location))
                || (miningSpot == MLMMiningSpot.SOUTH_WEST && SOUTH_LOWER_AREA.contains(location))
                || (miningSpot == MLMMiningSpot.SOUTH_EAST && SOUTH_LOWER_AREA.contains(location));
        return inLowerArea && hasWalkableTilesAround(wallObject);
        }
    }

    private boolean hasWalkableTilesAround(Rs2TileObjectModel wallObject)
    {
        return Rs2Tile.areSurroundingTilesWalkable(wallObject.getWorldLocation(), 1, 1);
    }

    private void repositionCameraAndMove()
    {
        Rs2Camera.resetPitch();
        Rs2Camera.resetZoom();
		LocalPoint localTarget = Microbot.getClientThread().invoke(() ->
			LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), miningSpot.getWorldPoint().get(0))
		);
		if (localTarget != null) {
        	Rs2Camera.turnTo(localTarget);
		}
        Rs2Walker.walkFastCanvas(miningSpot.getWorldPoint().get(0));
    }

    private void goUp()
    {
        if (isUpperFloor()) return;
        log.debug("Transitioning to upper floor");

		Rs2TileObjectModel ladder = rs2TileObjectCache.query().withId(ObjectID.MOTHERLODE_LADDER_BOTTOM).nearestReachable();
		if (ladder == null) {
			Rs2Walker.walkTo(miningSpot.getWorldPoint().get(0), 6);
			return;
		}

		if (!ladder.click()) return;

		sleepUntil(() -> Rs2Player.isMoving() || Rs2Player.isAnimating(), 1_500);
		sleepUntil(this::isUpperFloor, 8_000);
    }

    private void goDown()
    {
        if (!isUpperFloor()) return;
        log.debug("Transitioning to lower floor");

		Rs2TileObjectModel ladder = rs2TileObjectCache.query().withId(ObjectID.MOTHERLODE_LADDER_TOP).nearestReachable();
		if (ladder == null) {
			Rs2Walker.walkTo(HOPPER_DEPOSIT_DOWN, 6);
			return;
		}

		if (!ladder.click()) return;

		sleepUntil(() -> Rs2Player.isMoving() || Rs2Player.isAnimating(), 1_500);
        sleepUntil(() -> !isUpperFloor(), 8_000);
    }

    private void ensureLowerFloor()
    {
        if (isUpperFloor()) goDown();
    }

    private boolean isUpperFloor()
    {
		Integer height = Microbot.getClientThread().invoke(() -> {
			if (Microbot.getClient() == null || Microbot.getClient().getLocalPlayer() == null) return null;
			return Perspective.getTileHeight(
				Microbot.getClient(),
				Microbot.getClient().getLocalPlayer().getLocalLocation(),
				0
			);
		});
		return height != null && height < UPPER_FLOOR_HEIGHT;
    }

    private void resetMiningState(boolean force)
    {
        oreVein = null;
        miningSpot = (ThreadLocalRandom.current().nextBoolean() || force) ? MLMMiningSpot.IDLE : miningSpot;
    }

	private void resetMiningState()
	{
		resetMiningState(false);
	}

	private boolean hasHammer() {
		return isImcandoHammerEquipped()
			|| Rs2Inventory.hasItem(IMCANDO_HAMMER_OFFHAND_NAME, true)
			|| Rs2Inventory.hasItem(ItemID.HAMMER);
	}

	private boolean obtainHammer() {
		/*

			Typically, the hammer is located near the hopper on the lower floor OR near the sack,
			so we should be close enough to directly interact with it.

			WorldPoint nearestCratePoint = Arrays.stream(CRATE_WALKPOINTS)
				.min(WorldPoint::distanceTo)
				.orElse(CRATE_WALKPOINTS[0]);
			if (!Rs2Walker.walkTo(nearestCratePoint)) return false;
		 */

        if (Rs2Inventory.isFull()) {
            if (Rs2Inventory.interact("pay-dirt", "drop")) {
                sleepUntil(() -> !Rs2Inventory.isFull());
            } else {
                return false;
            }
        }

        while (!Rs2Inventory.hasItem(ItemID.HAMMER) && isRunning()) {
            //The crate at this point ALWAYS gives the player a hammer
            rs2TileObjectCache.query().where(obj -> obj.getWorldLocation().equals(new WorldPoint(3752, 5674, 0))).interact("Search");
            Rs2Inventory.waitForInventoryChanges(5_000);

            if (Rs2Inventory.hasItem(ItemID.HAMMER)) {
                pickedUpHammer = true;
                log.info("Hammer obtained from crate");
                break;
            }

			sleep(50, 100);
		}

		return pickedUpHammer;
	}

	private void dropHammerIfNeeded() {
		if (pickedUpHammer && Rs2Inventory.hasItem(ItemID.HAMMER)) {
			Rs2Inventory.drop(ItemID.HAMMER);
			sleepUntil(() -> !Rs2Inventory.hasItem(ItemID.HAMMER));
			pickedUpHammer = false;
		}
	}

    private void logStatusTransitionIfChanged()
    {
        if (status == lastLoggedStatus)
        {
            return;
        }

        log.info("MLM status transition: {} -> {}", lastLoggedStatus, status);
        lastLoggedStatus = status;
    }

	private Rectangle getMotherloadSackBounds() {
		TileObject sack = rs2TileObjectCache.query().where(o -> o.getId() == ObjectID.MOTHERLODE_SACK).first();
		return Rs2UiHelper.getObjectClickbox(sack);
	}

	private int getBrokenStrutCount() {
		List<Rs2TileObjectModel> brokenStruts = rs2TileObjectCache.query().where(o -> o.getId() == ObjectID.MOTHERLODE_WHEEL_STRUT_BROKEN).toList();
		return brokenStruts.isEmpty() ? 0 : brokenStruts.size();
	}

	private List<String> getItemsToKeep() {
		if (itemsToKeep == null) {
			List<String> _itemsToKeep = new ArrayList<>();
			Rs2Inventory.items()
				.filter(item -> item.getId() == ItemID.HAMMER
					|| IMCANDO_HAMMER_OFFHAND_NAME.equalsIgnoreCase(item.getName())
					|| isAllowedPickaxeId(item.getId())
					|| item.getId() == ItemID.GEM_BAG
					|| item.getId() == ItemID.GEM_BAG_OPEN)
				.map(Rs2ItemModel::getName)
				.forEach(_itemsToKeep::add);
			itemsToKeep = _itemsToKeep;
		}
		return itemsToKeep;
	}

    @Override
    public void shutdown()
    {
        log.info("Starting Neto MLM script shutdown");
        Rs2Antiban.resetAntibanSettings();
        Rs2Walker.setTarget(null);
		itemsToKeep = null;
		prepStep = PrepStep.OPEN_BANK;
		walkingStep = WalkingStep.REACH_MINING_GUILD;
		secondRockfallAttempts = 0;
		selectedPickaxeId = null;
        super.shutdown();
        log.info("Neto MLM script shutdown complete");
    }
}
