package com.blockworlds.utags.unlock;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the {@code unlocks:} list of config.yml. A bad rule is skipped with a message naming it; the good ones still load.
 *
 * <pre>
 * - id: veteran            # lowercase letters, digits, _ . - (becomes the grant source unlock:veteran)
 *   tag: veteran
 *   when:                  # exactly one of
 *     playtime-hours: 100
 *     permission: some.node
 *     advancement: minecraft:story/mine_diamond
 *     placeholder: "%statistic_mob_kills% >= 500"
 * </pre>
 */
public final class UnlockRules {

    static final Pattern ID = Pattern.compile("[a-z0-9_.-]{1,40}");
    private static final Pattern ADVANCEMENT = Pattern.compile("([a-z0-9_.-]+:)?[a-z0-9_./-]+");
    private static final Pattern PLACEHOLDER = Pattern.compile(
            "\\s*(%[^%\\s]+%)\\s*(>=|<=|==|>|<)\\s*(-?\\d+(?:\\.\\d+)?)\\s*");

    public record Result(List<UnlockRule> rules, List<String> errors) {
    }

    private UnlockRules() {
    }

    public static Result parse(List<? extends Map<?, ?>> entries) {
        List<UnlockRule> rules = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int n = 0;
        for (Map<?, ?> entry : entries) {
            n++;
            String where = "unlocks entry #" + n;
            Object rawId = entry.get("id");
            String id = rawId == null ? null : String.valueOf(rawId);
            if (id == null || !ID.matcher(id).matches()) {
                errors.add(where + ": 'id' must be 1-40 characters of a-z 0-9 _ . - (got " + id + ")");
                continue;
            }
            where = "unlock '" + id + "'";
            if (!seen.add(id)) {
                errors.add(where + ": duplicate id");
                continue;
            }
            Object tag = entry.get("tag");
            if (tag == null || String.valueOf(tag).isBlank()) {
                errors.add(where + ": missing 'tag'");
                continue;
            }
            if (!(entry.get("when") instanceof Map<?, ?> when) || when.size() != 1) {
                errors.add(where + ": 'when' must hold exactly one of permission, playtime-hours, advancement, placeholder");
                continue;
            }
            Map.Entry<?, ?> kind = when.entrySet().iterator().next();
            String error = condition(String.valueOf(kind.getKey()), kind.getValue(), rules, id, String.valueOf(tag));
            if (error != null) errors.add(where + ": " + error);
        }
        return new Result(List.copyOf(rules), List.copyOf(errors));
    }

    /** Adds the rule to {@code out} and returns null, or returns what is wrong with the condition. */
    private static String condition(String kind, Object value, List<UnlockRule> out, String id, String tag) {
        String text = value == null ? "" : String.valueOf(value).trim();
        UnlockRule.Condition c;
        switch (kind) {
            case "permission" -> {
                if (text.isEmpty() || text.chars().anyMatch(Character::isWhitespace)) return "'permission' needs a permission node";
                c = new UnlockRule.Permission(text);
            }
            case "playtime-hours" -> {
                double hours;
                try {
                    hours = Double.parseDouble(text);
                } catch (NumberFormatException e) {
                    return "'playtime-hours' needs a number (got " + text + ")";
                }
                if (!(hours > 0)) return "'playtime-hours' must be above 0";
                c = new UnlockRule.Playtime(hours);
            }
            case "advancement" -> {
                if (!ADVANCEMENT.matcher(text).matches()) return "'advancement' needs a key such as minecraft:story/mine_diamond (got " + text + ")";
                c = new UnlockRule.Advancement(text.contains(":") ? text : "minecraft:" + text);
            }
            case "placeholder" -> {
                Matcher m = PLACEHOLDER.matcher(text);
                if (!m.matches()) return "'placeholder' needs \"%placeholder% >= number\" (> >= < <= ==; got " + text + ")";
                UnlockRule.Op op = null;
                for (UnlockRule.Op candidate : UnlockRule.Op.values()) {
                    if (candidate.symbol.equals(m.group(2))) op = candidate;
                }
                c = new UnlockRule.Placeholder(m.group(1), op, Double.parseDouble(m.group(3)));
            }
            default -> {
                return "unknown condition '" + kind + "' (use permission, playtime-hours, advancement or placeholder)";
            }
        }
        out.add(new UnlockRule(id, tag.trim(), c));
        return null;
    }
}
