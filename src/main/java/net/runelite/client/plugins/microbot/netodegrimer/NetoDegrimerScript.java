package net.runelite.client.plugins.microbot.netodegrimer;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Staff;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

@Slf4j
public class NetoDegrimerScript extends Script
{
    private static final String NATURE_RUNE_NAME = "Nature rune";

    private final NetoDegrimerPlugin plugin;
    private final NetoDegrimerConfig injectedConfig;

    private NetoDegrimerConfig config;
    private NetoDegrimerState state = NetoDegrimerState.PREPARE;
    private boolean notifiedOutOfHerbs = false;

    @Inject
    public NetoDegrimerScript(NetoDegrimerPlugin plugin, NetoDegrimerConfig config)
    {
        this.plugin = plugin;
        this.injectedConfig = config;
    }

    public boolean run(NetoDegrimerConfig config)
    {
        this.config = config != null ? config : injectedConfig;
        this.state = NetoDegrimerState.PREPARE;
        this.notifiedOutOfHerbs = false;
        Microbot.enableAutoRunOn = false;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            try
            {
                if (!Microbot.isLoggedIn())
                {
                    return;
                }

                if (!super.run())
                {
                    return;
                }

                switch (state)
                {
                    case PREPARE:
                        handlePrepare();
                        break;
                    case WITHDRAW_HERBS:
                        handleWithdrawHerbs();
                        break;
                    case CAST:
                        handleCasting();
                        break;
                    case BANK_CLEANED:
                        handleBankCleaned();
                        break;
                    case IDLE:
                        // Nothing to do when idle
                        break;
                }
            }
            catch (Exception ex)
            {
                Microbot.logStackTrace(plugin.getClass().getSimpleName(), ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);

        return true;
    }

    private void handlePrepare()
    {
        if (!Rs2Bank.isOpen())
        {
            Rs2Bank.walkToBankAndUseBank();
            return;
        }

        Rs2Bank.depositAll();
        Rs2Inventory.waitForInventoryChanges(1200);

        if (!ensureEarthStaffEquipped())
        {
            return;
        }

        if (!Rs2Inventory.hasItem(NATURE_RUNE_NAME))
        {
            if (!Rs2Bank.hasItem(NATURE_RUNE_NAME))
            {
                Microbot.showMessage("No nature runes available. Neto Degrimer is shutting down.");
                shutdown();
                state = NetoDegrimerState.IDLE;
                return;
            }

            Rs2Bank.withdrawAll(NATURE_RUNE_NAME);
            Rs2Inventory.waitForInventoryChanges(1200);
        }

        state = NetoDegrimerState.WITHDRAW_HERBS;
    }

    private void handleWithdrawHerbs()
    {
        if (!Rs2Bank.isOpen())
        {
            Rs2Bank.walkToBankAndUseBank();
            return;
        }

        Rs2Bank.depositAllExcept(NATURE_RUNE_NAME);
        Rs2Inventory.waitForInventoryChanges(1200);

        List<GrimyHerb> herbsToClean = getSelectedHerbs();
        if (herbsToClean.isEmpty())
        {
            Microbot.showMessage("Select at least one herb in the Neto Degrimer configuration.");
            shutdown();
            state = NetoDegrimerState.IDLE;
            return;
        }

        boolean withdrewAny = false;
        for (GrimyHerb herb : herbsToClean)
        {
            if (Rs2Bank.hasItem(herb.getItemName()))
            {
                Rs2Bank.withdrawAll(herb.getItemName());
//                Rs2Inventory.waitForInventoryChanges(1200);
                withdrewAny = true;
            }
        }

        if (!withdrewAny)
        {
            if (!notifiedOutOfHerbs)
            {
                Microbot.showMessage("No selected grimy herbs found in the bank. Neto Degrimer is stopping.");
                notifiedOutOfHerbs = true;
            }
            shutdown();
            state = NetoDegrimerState.IDLE;
            return;
        }

        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 2000);
        state = NetoDegrimerState.CAST;
    }

    private void handleCasting()
    {
        if (Rs2Bank.isOpen())
        {
            Rs2Bank.closeBank();
            return;
        }

        sleepUntil(this::hasGrimyHerbsInInventory, 1200);

        if (!hasGrimyHerbsInInventory())
        {
            state = NetoDegrimerState.BANK_CLEANED;
            return;
        }

        if (!Rs2Magic.canCast(MagicAction.DEGRIME))
        {
            Microbot.showMessage("Unable to cast Degrime with current setup. Neto Degrimer is stopping.");
            shutdown();
            state = NetoDegrimerState.IDLE;
            return;
        }

        Map<String, Integer> inventorySnapshot = snapshotInventory();
        int startingMagicXp = Microbot.getClient().getSkillExperience(Skill.MAGIC);

        if (!Rs2Magic.cast(MagicAction.DEGRIME))
        {
            return;
        }

//        sleepUntil(() -> !Rs2Player.isAnimating(), 3000);
        sleepUntil(() -> inventoryChanged(inventorySnapshot) || magicXpGained(startingMagicXp), 5000);

        state = NetoDegrimerState.BANK_CLEANED;
    }

    private void handleBankCleaned()
    {
        if (!Rs2Bank.isOpen())
        {
            Rs2Bank.walkToBankAndUseBank();
            return;
        }

        Rs2Bank.depositAllExcept(NATURE_RUNE_NAME);
        Rs2Inventory.waitForInventoryChanges(1200);

        if (hasGrimyHerbsInInventory())
        {
            return;
        }

        List<GrimyHerb> herbsToClean = getSelectedHerbs();
        boolean hasMoreHerbs = herbsToClean.stream().anyMatch(herb -> Rs2Bank.hasItem(herb.getItemName()));

        if (!hasMoreHerbs)
        {
            if (!notifiedOutOfHerbs)
            {
                Microbot.showMessage("No more selected grimy herbs remain. Neto Degrimer completed.");
                notifiedOutOfHerbs = true;
            }
            shutdown();
            state = NetoDegrimerState.IDLE;
            return;
        }

        state = NetoDegrimerState.WITHDRAW_HERBS;
    }

    private boolean ensureEarthStaffEquipped()
    {
        List<Rs2Staff> staffOptions = Rs2Magic.findStavesByRunes(List.of(Runes.EARTH));
        if (staffOptions == null || staffOptions.isEmpty())
        {
            Microbot.showMessage("Unable to determine earth rune staves. Neto Degrimer is stopping.");
            shutdown();
            state = NetoDegrimerState.IDLE;
            return false;
        }

        boolean hasEquipped = staffOptions.stream().anyMatch(staff -> Rs2Equipment.isWearing(staff.getItemID()));
        if (hasEquipped)
        {
            return true;
        }

        for (Rs2Staff staff : staffOptions)
        {
            int staffId = staff.getItemID();
            if (staffId == -1)
            {
                continue;
            }

            if (Rs2Inventory.hasItem(staffId))
            {
                Rs2Inventory.wear(staffId);
                if (sleepUntil(() -> Rs2Equipment.isWearing(staffId), 3000))
                {
                    return true;
                }
            }
            else if (Rs2Bank.hasItem(staffId))
            {
                Rs2Bank.withdrawAndEquip(staffId);
                if (sleepUntil(() -> Rs2Equipment.isWearing(staffId), 3000))
                {
                    return true;
                }
            }
        }

        Microbot.showMessage("No staff that provides earth runes was found. Neto Degrimer is stopping.");
        shutdown();
        state = NetoDegrimerState.IDLE;
        return false;
    }

    private boolean hasGrimyHerbsInInventory()
    {
        return Rs2Inventory.items()
            .filter(Objects::nonNull)
            .anyMatch(item -> item.getName() != null && item.getName().toLowerCase().startsWith("grimy"));
    }

    private Map<String, Integer> snapshotInventory()
    {
        return Rs2Inventory.items()
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(
                Rs2ItemModel::getName,
                Rs2ItemModel::getQuantity,
                Integer::sum
            ));
    }

    private boolean inventoryChanged(Map<String, Integer> snapshot)
    {
        Map<String, Integer> current = snapshotInventory();
        return !current.equals(snapshot);
    }

    private boolean magicXpGained(int startingXp)
    {
        return Microbot.getClient().getSkillExperience(Skill.MAGIC) > startingXp;
    }

    private List<GrimyHerb> getSelectedHerbs()
    {
        return Arrays.stream(GrimyHerb.values())
            .filter(herb -> herb.isEnabled(config))
            .collect(Collectors.toList());
    }

    @Override
    public void shutdown()
    {
        super.shutdown();
    }

    private enum NetoDegrimerState
    {
        PREPARE,
        WITHDRAW_HERBS,
        CAST,
        BANK_CLEANED,
        IDLE
    }

    private enum GrimyHerb
    {
        GUAM("Grimy guam leaf", NetoDegrimerConfig::cleanGuam),
        MARRENTILL("Grimy marrentill", NetoDegrimerConfig::cleanMarrentill),
        TARROMIN("Grimy tarromin", NetoDegrimerConfig::cleanTarromin),
        HARRALANDER("Grimy harralander", NetoDegrimerConfig::cleanHarralander),
        RANARR("Grimy ranarr weed", NetoDegrimerConfig::cleanRanarr),
        TOADFLAX("Grimy toadflax", NetoDegrimerConfig::cleanToadflax),
        IRIT("Grimy irit leaf", NetoDegrimerConfig::cleanIrit),
        AVANTOE("Grimy avantoe", NetoDegrimerConfig::cleanAvantoe),
        KWUARM("Grimy kwuarm", NetoDegrimerConfig::cleanKwuarm),
        HUASCA("Grimy huasca", NetoDegrimerConfig::cleanHuasca),
        SNAPDRAGON("Grimy snapdragon", NetoDegrimerConfig::cleanSnapdragon),
        CADANTINE("Grimy cadantine", NetoDegrimerConfig::cleanCadantine),
        LANTADYME("Grimy lantadyme", NetoDegrimerConfig::cleanLantadyme),
        DWARF_WEED("Grimy dwarf weed", NetoDegrimerConfig::cleanDwarfWeed),
        TORSTOL("Grimy torstol", NetoDegrimerConfig::cleanTorstol);

        private final String itemName;
        private final Function<NetoDegrimerConfig, Boolean> enabledSupplier;

        GrimyHerb(String itemName, Function<NetoDegrimerConfig, Boolean> enabledSupplier)
        {
            this.itemName = itemName;
            this.enabledSupplier = enabledSupplier;
        }

        public String getItemName()
        {
            return itemName;
        }

        public boolean isEnabled(NetoDegrimerConfig config)
        {
            return enabledSupplier.apply(config);
        }
    }
}
