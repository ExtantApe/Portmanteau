package com.extantape.portmanteau.sorting.order;

import java.util.List;

/**
 * What sorting needs to know about one kind of item, worked out once at startup from its key, so sorting a chest does
 * no string work.
 *
 * @param path     the key without its namespace, such as {@code red_wool}
 * @param baseName the name without a colour or tier, such as {@code wool}
 * @param color    the colour's position among the dyes, or -1 for none
 * @param tier     the tier, higher is better, or 0 for none
 */
public record ItemFacts(String path, int category, int line, boolean block, String baseName, int color, int tier) {

    /** Dye colours in the creative menu's order. Longer names first where one ends another, such as light_blue. */
    static final List<String> COLORS = List.of("white", "light_gray", "gray", "black", "brown", "red", "orange", "yellow",
            "lime", "green", "cyan", "light_blue", "blue", "purple", "magenta", "pink");

    private static final List<String> COLOR_MATCH_ORDER = COLORS.stream()
            .sorted((a, b) -> Integer.compare(b.length(), a.length())).toList();

    /** Tier prefixes, best last so the index is the tier. */
    private static final List<List<String>> TIERS = List.of(
            List.of("wooden", "leather"),
            List.of("stone", "chainmail"),
            List.of("copper"),
            List.of("golden"),
            List.of("iron"),
            List.of("diamond"),
            List.of("netherite"));

    public static ItemFacts of(String path, int category, int line, boolean block) {
        String rest = path;
        int color = -1;
        for (String c : COLOR_MATCH_ORDER) {
            if (rest.startsWith(c + "_")) {
                color = COLORS.indexOf(c);
                rest = rest.substring(c.length() + 1);
                break;
            }
        }
        int tier = 0;
        for (int t = 0; t < TIERS.size() && tier == 0; t++) {
            for (String prefix : TIERS.get(t)) {
                if (rest.startsWith(prefix + "_")) {
                    tier = t + 1;
                    rest = rest.substring(prefix.length() + 1);
                    break;
                }
            }
        }
        return new ItemFacts(path, category, line, block, rest, color, tier);
    }
}
