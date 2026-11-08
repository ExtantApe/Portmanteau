package com.extantape.portmanteau.sorting.category;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * One category, read from a text file in ChestSort's format, so existing files carry over unchanged:
 *
 * <ul>
 *   <li>one item per line, such as {@code DIAMOND_PICKAXE} or {@code minecraft:diamond_pickaxe}, in any case;</li>
 *   <li>{@code *} at the start or end of a line as a wildcard, such as {@code *_PICKAXE};</li>
 *   <li>{@code #} starts a comment;</li>
 *   <li>{@code sticky=true} keeps the file's own order inside the category.</li>
 * </ul>
 *
 * Two additions: an item tag on a line of its own, such as {@code #minecraft:logs}, matches every item in the tag, so new
 * blocks find their category without edits; and {@code display=Tools} names the category in menus.
 */
public final class CategoryFile {
    private static final Pattern TAG = Pattern.compile("#[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern FILE = Pattern.compile("(\\d{1,4})[-_ ]?(.*)\\.txt", Pattern.CASE_INSENSITIVE);

    public enum Kind { EXACT, PREFIX, SUFFIX, CONTAINS, TAG, ANY }

    /** One line that matches items. {@code text} is lower case, without a namespace for names. */
    public record Rule(Kind kind, String text, int line) {}

    private final String id;
    private final String fileName;
    private final int number;
    private final String display;
    private final boolean sticky;
    private final List<Rule> rules;
    private final List<String> warnings;

    private CategoryFile(String id, String fileName, int number, String display, boolean sticky, List<Rule> rules,
                         List<String> warnings) {
        this.id = id;
        this.fileName = fileName;
        this.number = number;
        this.display = display;
        this.sticky = sticky;
        this.rules = List.copyOf(rules);
        this.warnings = List.copyOf(warnings);
    }

    /**
     * Whether a file name is a category file: a number, then an optional name, then {@code .txt}, such as
     * {@code 010-tools.txt}. Anything else in the folder, such as ChestSort's read-me, is not a category.
     */
    public static boolean isCategoryFileName(String fileName) {
        return FILE.matcher(fileName).matches();
    }

    public static CategoryFile parse(String fileName, List<String> lines) {
        var m = FILE.matcher(fileName);
        int number = 9999;
        String name = fileName.replaceFirst("(?i)\\.txt$", "");
        if (m.matches()) {
            number = Integer.parseInt(m.group(1));
            name = m.group(2).isBlank() ? name : m.group(2);
        }
        String id = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        String display = pretty(name);
        boolean sticky = false;
        List<Rule> rules = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int ruleIndex = 0;
        for (int n = 0; n < lines.size(); n++) {
            String line = lines.get(n).strip();
            if (line.isEmpty()) continue;
            if (TAG.matcher(line.toLowerCase(Locale.ROOT)).matches()) {
                rules.add(new Rule(Kind.TAG, line.substring(1).toLowerCase(Locale.ROOT), ruleIndex++));
                continue;
            }
            if (line.startsWith("#")) continue;
            int hash = line.indexOf(" #");
            if (hash >= 0) line = line.substring(0, hash).strip();
            String lower = line.toLowerCase(Locale.ROOT).replace(" ", "");
            if (lower.startsWith("sticky=")) {
                sticky = lower.equals("sticky=true");
                continue;
            }
            if (lower.startsWith("display=")) {
                String value = line.substring(line.indexOf('=') + 1).strip();
                if (!value.isEmpty()) display = value;
                continue;
            }
            Rule rule = rule(lower, ruleIndex);
            if (rule == null) {
                warnings.add(fileName + " line " + (n + 1) + ": couldn't read \"" + line + "\"");
                continue;
            }
            rules.add(rule);
            ruleIndex++;
        }
        return new CategoryFile(id.isEmpty() ? "category-" + number : id, fileName, number, display, sticky, rules, warnings);
    }

    private static Rule rule(String text, int index) {
        if (text.equals("*")) return new Rule(Kind.ANY, "", index);
        boolean start = text.startsWith("*"), end = text.endsWith("*") && text.length() > 1;
        String core = text.substring(start ? 1 : 0, text.length() - (end ? 1 : 0));
        int colon = core.indexOf(':');
        if (colon >= 0 && !start) core = core.substring(colon + 1);
        if (core.isEmpty() || !core.matches("[a-z0-9_./-]+")) return null;
        Kind kind = start && end ? Kind.CONTAINS : start ? Kind.SUFFIX : end ? Kind.PREFIX : Kind.EXACT;
        return new Rule(kind, core, index);
    }

    private static String pretty(String name) {
        String words = name.replaceAll("[-_]+", " ").strip();
        if (words.isEmpty()) return name;
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    /**
     * The index of the first rule that matches the item, or -1. {@code path} is the item's key without its namespace,
     * such as {@code oak_log}; {@code tags} holds the item tags it's in, such as {@code minecraft:logs}.
     */
    public int match(String path, java.util.Set<String> tags) {
        for (Rule r : rules) {
            boolean hit = switch (r.kind()) {
                case EXACT -> path.equals(r.text());
                case PREFIX -> path.startsWith(r.text());
                case SUFFIX -> path.endsWith(r.text());
                case CONTAINS -> path.contains(r.text());
                case TAG -> tags.contains(r.text());
                case ANY -> true;
            };
            if (hit) return r.line();
        }
        return -1;
    }

    public String id() { return id; }
    public String fileName() { return fileName; }
    public int number() { return number; }
    public String display() { return display; }
    public boolean sticky() { return sticky; }
    public List<Rule> rules() { return rules; }
    public List<String> warnings() { return warnings; }
}
