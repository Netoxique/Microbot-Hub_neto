package net.runelite.client.plugins.microbot.banksbankstander;

import lombok.Data;
import net.runelite.client.config.ConfigManager;

import java.util.Objects;

@Data
public class BanksBankStanderState
{
    private String name;
    private BanksInteractOrder interactOrder;
    private String firstItemIdentifier;
    private int firstItemQuantity;
    private String secondItemIdentifier;
    private int secondItemQuantity;
    private String thirdItemIdentifier;
    private int thirdItemQuantity;
    private String fourthItemIdentifier;
    private int fourthItemQuantity;
    private boolean pause;
    private boolean needPromptEntry;
    private boolean waitForAnimation;
    private boolean depositAll;
    private boolean amuletOfChemistry;
    private boolean prescriptionGoggles;
    private String interactionOption;
    private int sleepMin;
    private int sleepMax;
    private int sleepTarget;

    public static BanksBankStanderState fromConfig(BanksBankStanderConfig config)
    {
        BanksBankStanderState state = new BanksBankStanderState();
        state.setInteractOrder(config.interactOrder());
        state.setFirstItemIdentifier(config.firstItemIdentifier());
        state.setFirstItemQuantity(config.firstItemQuantity());
        state.setSecondItemIdentifier(config.secondItemIdentifier());
        state.setSecondItemQuantity(config.secondItemQuantity());
        state.setThirdItemIdentifier(config.thirdItemIdentifier());
        state.setThirdItemQuantity(config.thirdItemQuantity());
        state.setFourthItemIdentifier(config.fourthItemIdentifier());
        state.setFourthItemQuantity(config.fourthItemQuantity());
        state.setPause(config.pause());
        state.setNeedPromptEntry(config.needPromptEntry());
        state.setWaitForAnimation(config.waitForAnimation());
        state.setDepositAll(config.depositAll());
        state.setAmuletOfChemistry(config.amuletOfChemistry());
        state.setPrescriptionGoggles(config.prescriptionGoggles());
        state.setInteractionOption(config.menu());
        state.setSleepMin(config.sleepMin());
        state.setSleepMax(config.sleepMax());
        state.setSleepTarget(config.sleepTarget());
        return state;
    }

    public void apply(ConfigManager configManager)
    {
        BanksInteractOrder order = interactOrder != null ? interactOrder : BanksInteractOrder.STANDARD;
        configManager.setConfiguration(BanksBankStanderPlugin.CONFIG_GROUP, "interactOrder", order);
        configManager.setConfiguration(BanksBankStanderPlugin.CONFIG_GROUP, "First Item", sanitize(firstItemIdentifier));
        configManager.setConfiguration(BanksBankStanderPlugin.CONFIG_GROUP, "First Item Quantity", firstItemQuantity);
        configManager.setConfiguration(BanksBankStanderPlugin.CONFIG_GROUP, "Second Item", sanitize(secondItemIdentifier));
        configManager.setConfiguration(BanksBankStanderPlugin.CONFIG_GROUP, "Second Item Quantity", secondItemQuantity);
        configManager.setConfiguration(BanksBankStanderPlugin.CONFIG_GROUP, "Third Item", sanitize(thirdItemIdentifier));
        configManager.setConfiguration(BanksBankStanderPlugin.CONFIG_GROUP, "Third Item Quantity", thirdItemQuantity);
        configManager.setConfiguration(BanksBankStanderPlugin.CONFIG_GROUP, "Fourth Item", sanitize(fourthItemIdentifier));
        configManager.setConfiguration(BanksBankStanderPlugin.CONFIG_GROUP, "Fourth Item Quantity", fourthItemQuantity);
        configManager.setConfiguration(BanksBankStanderPlugin.CONFIG_GROUP, "pause", pause);
        configManager.setConfiguration(BanksBankStanderPlugin.CONFIG_GROUP, "Prompt", needPromptEntry);
        configManager.setConfiguration(BanksBankStanderPlugin.CONFIG_GROUP, "WaitForProcess", waitForAnimation);
        configManager.setConfiguration(BanksBankStanderPlugin.CONFIG_GROUP, "DepositAll", depositAll);
        configManager.setConfiguration(BanksBankStanderPlugin.CONFIG_GROUP, "AmuletofChemistry", amuletOfChemistry);
        configManager.setConfiguration(BanksBankStanderPlugin.CONFIG_GROUP, "PrescriptionGoggles", prescriptionGoggles);
        configManager.setConfiguration(BanksBankStanderPlugin.CONFIG_GROUP, "Interaction Option", sanitize(interactionOption));
        configManager.setConfiguration(BanksBankStanderPlugin.CONFIG_GROUP, "Sleep Min", sleepMin);
        configManager.setConfiguration(BanksBankStanderPlugin.CONFIG_GROUP, "Sleep Max", sleepMax);
        configManager.setConfiguration(BanksBankStanderPlugin.CONFIG_GROUP, "Sleep Target", sleepTarget);
    }

    private static String sanitize(String value)
    {
        return Objects.requireNonNullElse(value, "").trim();
    }
}
