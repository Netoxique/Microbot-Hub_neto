package net.runelite.client.plugins.microbot.netogeseller;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetoGeSellerScriptTest {
    private final NetoGeSellerScript script = new NetoGeSellerScript();

    @Test
    void parsesNamesAndExactItemIds() {
        Map<String, NetoGeSellerScript.SellItemConfig> items = script.parseItemsToSell(
                " Bird nest, 5075, +5076, 5077:100, 5078:>200 ");

        assertEquals(5, items.size());
        assertFalse(items.get("name:bird nest").isItemIdSelector());
        assertEquals("Bird nest", items.get("name:bird nest").getName());

        NetoGeSellerScript.SellItemConfig plainId = items.get("id:5075");
        assertEquals(5075, plainId.getItemId());
        assertEquals(NetoGeSellerScript.SellMode.SELL_ALL, plainId.getMode());

        NetoGeSellerScript.SellItemConfig highValue = items.get("id:5076");
        assertTrue(highValue.isSellHighest());

        NetoGeSellerScript.SellItemConfig keepAmount = items.get("id:5077");
        assertEquals(100, keepAmount.getThreshold());
        assertEquals(NetoGeSellerScript.SellMode.SELL_EXCESS, keepAmount.getMode());

        NetoGeSellerScript.SellItemConfig allIfOver = items.get("id:5078");
        assertEquals(200, allIfOver.getThreshold());
        assertEquals(NetoGeSellerScript.SellMode.SELL_ALL_IF_OVER, allIfOver.getMode());
    }

    @Test
    void skipsInvalidNumericIds() {
        Map<String, NetoGeSellerScript.SellItemConfig> items = script.parseItemsToSell(
                "0, 999999999999999999999, Bird nest");

        assertEquals(1, items.size());
        assertTrue(items.containsKey("name:bird nest"));
        assertNull(items.get("id:0"));
    }

    @Test
    void exactIdMatchesItsNotedVariantButNotSameNameAlternatives() {
        assertTrue(NetoGeSellerScript.matchesConfiguredId(5075, 5075, 5076));
        assertTrue(NetoGeSellerScript.matchesConfiguredId(5075, 5076, 5075));
        assertFalse(NetoGeSellerScript.matchesConfiguredId(5075, 22798, 22799));
    }
}
