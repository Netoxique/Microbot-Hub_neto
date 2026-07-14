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
                final int minQuantity;
                final int maxQuantity;
                final boolean minMax;

                int dashIndex = quantityText.indexOf('-');
                if (dashIndex == -1) {
                    minMax = false;
                    try {
                        int qty = Integer.parseInt(quantityText);
                        if (qty <= 0) {
                            errors.add("Quantity must be positive in '" + entry + "'");
                            continue;
                        }
                        minQuantity = qty;
                        maxQuantity = qty;
                    } catch (NumberFormatException ex) {
                        errors.add("Invalid quantity in '" + entry + "'");
                        continue;
                    }
                } else {
                    minMax = true;
                    String minStr = quantityText.substring(0, dashIndex).trim();
                    String maxStr = quantityText.substring(dashIndex + 1).trim();
                    try {
                        int minVal = Integer.parseInt(minStr);
                        int maxVal = Integer.parseInt(maxStr);
                        if (minVal <= 0 || maxVal <= 0) {
                            errors.add("Quantities must be positive in '" + entry + "'");
                            continue;
                        }
                        if (minVal >= maxVal) {
                            errors.add("Min quantity must be less than max quantity in '" + entry + "'");
                            continue;
                        }
                        minQuantity = minVal;
                        maxQuantity = maxVal;
                    } catch (NumberFormatException ex) {
                        errors.add("Invalid min-max quantity in '" + entry + "'");
                        continue;
                    }
                }

                String key = name.toLowerCase(Locale.ROOT);
                RequestedItem existing = items.get(key);
                if (existing == null) {
                    items.put(key, new RequestedItem(name, minQuantity, maxQuantity, minMax));
                } else if (existing.minQuantity > Integer.MAX_VALUE - minQuantity || existing.maxQuantity > Integer.MAX_VALUE - maxQuantity) {
                    errors.add("Combined quantity is too large for '" + name + "'");
                } else {
                    existing.minQuantity += minQuantity;
                    existing.maxQuantity += maxQuantity;
                    if (minMax) {
                        existing.minMax = true;
                    }
                }
            }
        }
        return new ParseResult(items, errors);
    }

    public static int deficit(int minQuantity, int maxQuantity, boolean minMax, int owned) {
        if (minMax) {
            if (owned < minQuantity) {
                return maxQuantity - Math.max(0, owned);
            }
            return 0;
        } else {
            if (owned < maxQuantity) {
                return maxQuantity - Math.max(0, owned);
            }
            return 0;
        }
    }

    public static int deficit(int target, int owned) {
        return deficit(target, target, false, owned);
    }

    public static final class RequestedItem {
        private final String name;
        private int minQuantity;
        private int maxQuantity;
        private boolean minMax;

        RequestedItem(String name, int minQuantity, int maxQuantity, boolean minMax) {
            this.name = name;
            this.minQuantity = minQuantity;
            this.maxQuantity = maxQuantity;
            this.minMax = minMax;
        }

        public String getName() { return name; }
        public int getQuantity() { return maxQuantity; }
        public int getMinQuantity() { return minQuantity; }
        public int getMaxQuantity() { return maxQuantity; }
        public boolean isMinMax() { return minMax; }
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
