package net.runelite.client.plugins.microbot.banksbankstander;

import net.runelite.client.plugins.microbot.util.inventory.InteractOrder;

import java.util.Arrays;
import java.util.Optional;

/**
 * Represents the interact order options available for the Bank's BankStander plugin.
 *
 * <p>The enum mirrors the base {@link InteractOrder} values that ship with the client and
 * extends them with plugin specific behaviour such as the {@link #LAST_AND_FIRST}
 * ordering.</p>
 */
public enum BanksInteractOrder
{
    STANDARD("Standard", InteractOrder.STANDARD),
    RANDOM("Random", InteractOrder.RANDOM),
    COLUMN("Column", InteractOrder.COLUMN),
    EFFICIENT_ROW("Efficient Row", InteractOrder.EFFICIENT_ROW),
    ZIGZAG("Zigzag", InteractOrder.ZIGZAG),
    LAST_AND_FIRST("Last and First", null);

    private final String displayName;
    private final InteractOrder interactOrder;

    BanksInteractOrder(String displayName, InteractOrder interactOrder)
    {
        this.displayName = displayName;
        this.interactOrder = interactOrder;
    }

    public Optional<InteractOrder> getInteractOrder()
    {
        return Optional.ofNullable(interactOrder);
    }

    public boolean hasDelegate()
    {
        return interactOrder != null;
    }

    @Override
    public String toString()
    {
        return displayName;
    }

    public static BanksInteractOrder fromInteractOrder(InteractOrder order)
    {
        return Arrays.stream(values())
                .filter(value -> value.interactOrder == order && value.interactOrder != null)
                .findFirst()
                .orElse(STANDARD);
    }
}
