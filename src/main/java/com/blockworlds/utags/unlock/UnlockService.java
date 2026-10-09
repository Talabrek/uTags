package com.blockworlds.utags.unlock;

import com.blockworlds.utags.api.GrantOutcome;
import com.blockworlds.utags.api.UTagsApi;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Grants tags whose unlock rule a player meets. {@link #check} reads Bukkit state through {@link PlayerFacts}, so call it
 * on the main thread; the grant itself goes through {@link UTagsApi#grant} with source {@code unlock:<id>} and op id
 * {@code unlock:<id>:<uuid>}, so a repeat (a second check, a restart, another server) replays and never grants twice.
 * Unlocks never revoke.
 */
public final class UnlockService {

    private final UTagsApi api;
    private final List<UnlockRule> rules;
    private final Logger log;
    private final Set<String> pending = ConcurrentHashMap.newKeySet();
    private final Set<String> unknownLogged = new HashSet<>();

    UnlockService(UTagsApi api, List<UnlockRule> rules, Logger log) {
        this.api = api;
        this.rules = rules;
        this.log = log;
    }

    /** The service, or empty (with one log line) when uTags' API is not available. */
    public static Optional<UnlockService> create(Optional<UTagsApi> api, List<UnlockRule> rules, Logger log) {
        if (api.isEmpty()) {
            log.warning("Unlocks are off: the uTags API is not available.");
            return Optional.empty();
        }
        return Optional.of(new UnlockService(api.get(), rules, log));
    }

    public static String source(UnlockRule rule) {
        return "unlock:" + rule.id();
    }

    public static String opId(UnlockRule rule, UUID player) {
        return "unlock:" + rule.id() + ":" + player;
    }

    /** Grants every rule the player meets and does not own yet. Main thread. */
    public void check(UUID player, PlayerFacts facts) {
        Set<String> owned = api.ownedCached(player);
        Set<String> known = null;
        for (UnlockRule rule : rules) {
            if (owned.contains(rule.tag())) continue;
            if (known == null) known = api.tagIds();
            if (!known.contains(rule.tag())) {
                logUnknownOnce(rule);
                continue;
            }
            String key = rule.id() + ":" + player;
            if (pending.contains(key) || !rule.when().test(facts)) continue;
            pending.add(key);
            api.grant(player, rule.tag(), source(rule), opId(rule, player)).whenComplete((result, error) -> {
                pending.remove(key);
                if (error != null) {
                    log.warning("Unlock '" + rule.id() + "' for " + player + " failed, will retry: " + error);
                } else if (result.outcome() == GrantOutcome.UNKNOWN_TAG) {
                    logUnknownOnce(rule);
                } else if (result.outcome() == GrantOutcome.GRANTED) {
                    log.info("Unlocked tag '" + rule.tag() + "' for " + player + " (rule " + rule.id() + ").");
                }
            });
        }
    }

    private void logUnknownOnce(UnlockRule rule) {
        boolean first;
        synchronized (unknownLogged) {
            first = unknownLogged.add(rule.id());
        }
        if (first) log.warning("Unlock '" + rule.id() + "' names the unknown tag '" + rule.tag() + "'; it is skipped until that tag exists.");
    }
}
