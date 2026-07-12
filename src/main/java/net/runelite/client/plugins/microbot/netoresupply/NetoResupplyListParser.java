package net.runelite.client.plugins.microbot.netoresupply;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class NetoResupplyListParser {
    private NetoResupplyListParser() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static ParseResult parse(List<String> enabledLists) {
        Map<String, RequestedItem> items = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        if (enabledLists == null) return new ParseResult(items, errors);

        for (String list : enabledLists) {
            if (list == null || list.trim().isEmpty()) continue;
            for (String rawEntry : list.split(",", -1)) {
                String entry = rawEntry.trim();
                if (entry.isEmpty()) {
                    errors.add("Empty list entry");
                    continue;
                }
                int separator = entry.lastIndexOf(':');
                if (separator <= 0 || separator == entry.length() - 1 || entry.indexOf(':') != separator) {
                    errors.add("Invalid entry '" + entry + "' (expected item:quantity)");
                    continue;
                }
                String name = entry.substring(0, separator).trim();
                String quantityText = entry.substring(separator + 1).trim();
                if (name.isEmpty()) {
                    errors.add("Invalid entry '" + entry + "' (item name is empty)");
                    continue;
                }
                final int quantity;
                try {
                    quantity = Integer.parseInt(quantityText);
                } catch (NumberFormatException ex) {
                    errors.add("Invalid quantity in '" + entry + "'");
                    continue;
                }
                if (quantity <= 0) {
                    errors.add("Quantity must be positive in '" + entry + "'");
                    continue;
                }

                String key = name.toLowerCase(Locale.ROOT);
                RequestedItem existing = items.get(key);
                if (existing == null) {
                    items.put(key, new RequestedItem(name, quantity));
                } else if (existing.quantity > Integer.MAX_VALUE - quantity) {
                    errors.add("Combined quantity is too large for '" + name + "'");
                } else {
                    existing.quantity += quantity;
                }
            }
        }
        return new ParseResult(items, errors);
    }

    public static int deficit(int target, int owned) {
        if (target <= owned) return 0;
        return target - Math.max(0, owned);
    }

    public static final class RequestedItem {
        private final String name;
        private int quantity;

        RequestedItem(String name, int quantity) {
            this.name = name;
            this.quantity = quantity;
        }

        public String getName() { return name; }
        public int getQuantity() { return quantity; }
    }

    public static final class ParseResult {
        private final Map<String, RequestedItem> items;
        private final List<String> errors;

        ParseResult(Map<String, RequestedItem> items, List<String> errors) {
            this.items = items;
            this.errors = errors;
        }

        public Map<String, RequestedItem> getItems() { return items; }
        public List<String> getErrors() { return errors; }
    }
}
