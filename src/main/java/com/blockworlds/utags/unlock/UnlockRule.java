package com.blockworlds.utags.unlock;

/** One configured unlock: when {@code when} holds for a player, they are granted {@code tag}. */
public record UnlockRule(String id, String tag, Condition when) {

    /** What has to be true. */
    public sealed interface Condition {
        boolean test(PlayerFacts facts);
    }

    public record Permission(String node) implements Condition {
        @Override
        public boolean test(PlayerFacts facts) {
            return facts.hasPermission(node);
        }
    }

    public record Playtime(double hours) implements Condition {
        @Override
        public boolean test(PlayerFacts facts) {
            return facts.playtimeHours() >= hours;
        }
    }

    public record Advancement(String key) implements Condition {
        @Override
        public boolean test(PlayerFacts facts) {
            return facts.hasAdvancement(key);
        }
    }

    public enum Op {
        GT(">"), GE(">="), LT("<"), LE("<="), EQ("==");

        final String symbol;

        Op(String symbol) {
            this.symbol = symbol;
        }

        boolean apply(double actual, double expected) {
            return switch (this) {
                case GT -> actual > expected;
                case GE -> actual >= expected;
                case LT -> actual < expected;
                case LE -> actual <= expected;
                case EQ -> actual == expected;
            };
        }
    }

    /** {@code <placeholder> <op> <number>}; a placeholder that does not resolve to a number never matches. */
    public record Placeholder(String placeholder, Op op, double value) implements Condition {
        @Override
        public boolean test(PlayerFacts facts) {
            String text = facts.placeholder(placeholder);
            if (text == null) return false;
            try {
                return op.apply(Double.parseDouble(text.trim().replace(",", "")), value);
            } catch (NumberFormatException e) {
                return false;
            }
        }
    }
}
