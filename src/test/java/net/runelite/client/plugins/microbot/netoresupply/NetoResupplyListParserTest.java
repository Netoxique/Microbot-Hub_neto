package net.runelite.client.plugins.microbot.netoresupply;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetoResupplyListParserTest {
    @Test
    void aggregatesWhitespaceCaseAndDuplicatesAcrossLists() {
        NetoResupplyListParser.ParseResult result = NetoResupplyListParser.parse(Arrays.asList(
                " Law rune:100, Fire rune:500,law RUNE:25 ",
                "Fire Rune:250"
        ));

        assertTrue(result.getErrors().isEmpty());
        assertEquals(125, result.getItems().get("law rune").getQuantity());
        assertEquals(750, result.getItems().get("fire rune").getQuantity());
        assertEquals("Law rune", result.getItems().get("law rune").getName());
    }

    @Test
    void ignoresDisabledListsWhenCallerOnlyPassesEnabledLists() {
        NetoResupplyListParser.ParseResult result = NetoResupplyListParser.parse(
                Collections.singletonList("Law rune:100"));

        assertEquals(1, result.getItems().size());
        assertFalse(result.getItems().containsKey("fire rune"));
    }

    @Test
    void reportsMalformedAndNonPositiveEntriesWhileKeepingValidOnes() {
        NetoResupplyListParser.ParseResult result = NetoResupplyListParser.parse(Collections.singletonList(
                "Law rune,Fire rune:nope,Air rune:0,Water rune:-1,Earth rune:50,"));

        assertEquals(1, result.getItems().size());
        assertEquals(50, result.getItems().get("earth rune").getQuantity());
        assertEquals(5, result.getErrors().size());
    }

    @Test
    void protectsCombinedQuantityFromOverflow() {
        NetoResupplyListParser.ParseResult result = NetoResupplyListParser.parse(Collections.singletonList(
                "Coins:2147483647,coins:1"));

        assertEquals(Integer.MAX_VALUE, result.getItems().get("coins").getQuantity());
        assertEquals(1, result.getErrors().size());
    }

    @Test
    void parsesMinMaxFormats() {
        NetoResupplyListParser.ParseResult result = NetoResupplyListParser.parse(Collections.singletonList(
                "Law rune:10-50, Fire rune: 5 - 20"
        ));

        assertTrue(result.getErrors().isEmpty());
        NetoResupplyListParser.RequestedItem law = result.getItems().get("law rune");
        assertEquals("Law rune", law.getName());
        assertEquals(10, law.getMinQuantity());
        assertEquals(50, law.getMaxQuantity());
        assertTrue(law.isMinMax());

        NetoResupplyListParser.RequestedItem fire = result.getItems().get("fire rune");
        assertEquals(5, fire.getMinQuantity());
        assertEquals(20, fire.getMaxQuantity());
        assertTrue(fire.isMinMax());
    }

    @Test
    void aggregatesMixedSingleAndMinMax() {
        NetoResupplyListParser.ParseResult result = NetoResupplyListParser.parse(Arrays.asList(
                "Law rune:100",
                "law RUNE:10-50"
        ));

        assertTrue(result.getErrors().isEmpty());
        NetoResupplyListParser.RequestedItem law = result.getItems().get("law rune");
        assertEquals(110, law.getMinQuantity());
        assertEquals(150, law.getMaxQuantity());
        assertTrue(law.isMinMax());
    }

    @Test
    void reportsMalformedMinMaxStrings() {
        NetoResupplyListParser.ParseResult result = NetoResupplyListParser.parse(Collections.singletonList(
                "Law rune:50-10,Fire rune:10-,Air rune:-10-20,Water rune:10-a,Earth rune:-5--10"
        ));

        assertTrue(result.getItems().isEmpty());
        assertEquals(5, result.getErrors().size());
    }

    @Test
    void calculatesOnlyPositiveDeficits() {
        // Single value
        assertEquals(100, NetoResupplyListParser.deficit(100, 0));
        assertEquals(40, NetoResupplyListParser.deficit(100, 60));
        assertEquals(0, NetoResupplyListParser.deficit(100, 100));
        assertEquals(0, NetoResupplyListParser.deficit(100, 150));

        // Min-max
        assertEquals(50, NetoResupplyListParser.deficit(10, 50, true, 0));
        assertEquals(45, NetoResupplyListParser.deficit(10, 50, true, 5));
        assertEquals(0, NetoResupplyListParser.deficit(10, 50, true, 10));
        assertEquals(0, NetoResupplyListParser.deficit(10, 50, true, 12));
    }
}
