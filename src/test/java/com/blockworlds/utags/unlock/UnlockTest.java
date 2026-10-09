package com.blockworlds.utags.unlock;

import com.blockworlds.utags.api.GrantOutcome;
import com.blockworlds.utags.api.GrantResult;
import com.blockworlds.utags.api.UTagsApi;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UnlockTest {

    static Map<String, Object> rule(String id, String tag, String kind, Object value) {
        return Map.of("id", id, "tag", tag, "when", Map.of(kind, value));
    }

    static final class Facts implements PlayerFacts {
        Set<String> perms = Set.of();
        double hours;
        Set<String> advancements = Set.of();
        Map<String, String> placeholders = Map.of();
        public boolean hasPermission(String n) { return perms.contains(n); }
        public double playtimeHours() { return hours; }
        public boolean hasAdvancement(String k) { return advancements.contains(k); }
        public String placeholder(String p) { return placeholders.get(p); }
    }

    private static Logger quiet() {
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        return log;
    }

    // --- parsing ---

    @Test
    void parsesAllFourKinds() {
        var r = UnlockRules.parse(List.of(
                rule("a", "t1", "permission", "some.node"),
                rule("b", "t2", "playtime-hours", 100),
                rule("c", "t3", "advancement", "minecraft:story/mine_diamond"),
                rule("d", "t4", "placeholder", "%statistic_mob_kills% >= 500")));
        assertEquals(List.of(), r.errors());
        assertEquals(4, r.rules().size());
        assertEquals(new UnlockRule.Permission("some.node"), r.rules().get(0).when());
        assertEquals(new UnlockRule.Playtime(100), r.rules().get(1).when());
        assertEquals(new UnlockRule.Advancement("minecraft:story/mine_diamond"), r.rules().get(2).when());
        assertEquals(new UnlockRule.Placeholder("%statistic_mob_kills%", UnlockRule.Op.GE, 500), r.rules().get(3).when());
    }

    @Test
    void advancementWithoutNamespaceDefaultsToMinecraft() {
        var r = UnlockRules.parse(List.of(rule("a", "t", "advancement", "story/mine_diamond")));
        assertEquals(new UnlockRule.Advancement("minecraft:story/mine_diamond"), r.rules().get(0).when());
    }

    @Test
    void badRulesAreRejectedWithAMessageAndGoodOnesStay() {
        var r = UnlockRules.parse(List.of(
                rule("Bad Id", "t", "permission", "x"),
                Map.of("id", "no-tag", "when", Map.of("permission", "x")),
                Map.of("id", "no-when", "tag", "t"),
                Map.of("id", "two", "tag", "t", "when", Map.of("permission", "x", "playtime-hours", 1)),
                rule("nan", "t", "playtime-hours", "lots"),
                rule("neg", "t", "playtime-hours", -3),
                rule("badph", "t", "placeholder", "%x% ~ 3"),
                rule("badph2", "t", "placeholder", "%x% >= many"),
                rule("kind", "t", "moon-phase", "full"),
                rule("ok", "t", "permission", "p"),
                rule("ok", "t", "permission", "p")));
        assertEquals(1, r.rules().size());
        assertEquals(10, r.errors().size());
        assertTrue(r.errors().stream().anyMatch(e -> e.contains("'badph'")));
        assertTrue(r.errors().stream().anyMatch(e -> e.contains("duplicate id")));
    }

    @Test
    void placeholderOperators() {
        // {operator, actual value, expected result of "actual <op> 5"}
        for (String[] c : new String[][]{{">", "6", "true"}, {">", "5", "false"}, {">=", "5", "true"}, {"<", "5", "false"},
                {"<", "4", "true"}, {"<=", "5", "true"}, {"==", "5", "true"}, {"==", "4", "false"}}) {
            var r = UnlockRules.parse(List.of(rule("p", "t", "placeholder", "%v% " + c[0] + " 5")));
            Facts f = new Facts();
            f.placeholders = Map.of("%v%", c[1]);
            assertEquals(Boolean.parseBoolean(c[2]), r.rules().get(0).when().test(f), c[1] + " " + c[0] + " 5");
        }
    }

    // --- evaluation ---

    @Test
    void conditionsEvaluate() {
        Facts f = new Facts();
        f.perms = Set.of("vip");
        f.hours = 99.5;
        f.advancements = Set.of("minecraft:story/mine_diamond");
        assertTrue(new UnlockRule.Permission("vip").test(f));
        assertFalse(new UnlockRule.Permission("other").test(f));
        assertFalse(new UnlockRule.Playtime(100).test(f));
        f.hours = 100;
        assertTrue(new UnlockRule.Playtime(100).test(f));
        assertTrue(new UnlockRule.Advancement("minecraft:story/mine_diamond").test(f));
        assertFalse(new UnlockRule.Advancement("minecraft:story/other").test(f));
        var ph = new UnlockRule.Placeholder("%x%", UnlockRule.Op.GE, 1);
        assertFalse(ph.test(f)); // unresolved
        f.placeholders = Map.of("%x%", "%x%");
        assertFalse(ph.test(f)); // not a number
        f.placeholders = Map.of("%x%", "1,200");
        assertTrue(ph.test(f));
    }

    // --- service ---

    private UTagsApi api(Set<String> owned, Set<String> known) {
        UTagsApi api = mock(UTagsApi.class);
        when(api.ownedCached(any())).thenReturn(owned);
        when(api.tagIds()).thenReturn(known);
        when(api.grant(any(), anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(new GrantResult(GrantOutcome.GRANTED, false)));
        return api;
    }

    private static List<UnlockRule> rules(String id, String tag) {
        return List.of(new UnlockRule(id, tag, new UnlockRule.Permission("p")));
    }

    private static Facts meets() {
        Facts f = new Facts();
        f.perms = Set.of("p");
        return f;
    }

    @Test
    void grantsWithDeterministicSourceAndOpId() {
        UTagsApi api = api(Set.of(), Set.of("vet"));
        UnlockService s = UnlockService.create(Optional.of(api), rules("veteran", "vet"), quiet()).orElseThrow();
        UUID u = UUID.randomUUID();
        s.check(u, meets());
        s.check(u, meets());
        verify(api, times(2)).grant(u, "vet", "unlock:veteran", "unlock:veteran:" + u);
    }

    @Test
    void doesNothingWhenConditionUnmet() {
        UTagsApi api = api(Set.of(), Set.of("vet"));
        UnlockService s = UnlockService.create(Optional.of(api), rules("veteran", "vet"), quiet()).orElseThrow();
        s.check(UUID.randomUUID(), new Facts());
        verify(api, never()).grant(any(), anyString(), anyString(), anyString());
    }

    @Test
    void skipsOwnedTags() {
        UTagsApi api = api(Set.of("vet"), Set.of("vet"));
        UnlockService s = UnlockService.create(Optional.of(api), rules("veteran", "vet"), quiet()).orElseThrow();
        s.check(UUID.randomUUID(), meets());
        verify(api, never()).grant(any(), anyString(), anyString(), anyString());
    }

    @Test
    void neverRevokes() {
        UTagsApi api = api(Set.of("vet"), Set.of("vet"));
        UnlockService s = UnlockService.create(Optional.of(api), rules("veteran", "vet"), quiet()).orElseThrow();
        s.check(UUID.randomUUID(), new Facts()); // the condition no longer holds
        verify(api, never()).revoke(any(), anyString(), anyString(), anyString());
    }

    @Test
    void unknownTagLogsOnceAndIsSkipped() {
        UTagsApi api = api(Set.of(), Set.of());
        List<String> logged = new ArrayList<>();
        Logger log = quiet();
        log.addHandler(new java.util.logging.Handler() {
            public void publish(java.util.logging.LogRecord r) { logged.add(r.getMessage()); }
            public void flush() { }
            public void close() { }
        });
        UnlockService s = UnlockService.create(Optional.of(api), rules("veteran", "ghost"), log).orElseThrow();
        s.check(UUID.randomUUID(), meets());
        s.check(UUID.randomUUID(), meets());
        assertEquals(1, logged.size());
        assertTrue(logged.get(0).contains("ghost"));
        verify(api, never()).grant(any(), anyString(), anyString(), anyString());
    }

    @Test
    void noApiMeansOff() {
        assertTrue(UnlockService.create(Optional.empty(), rules("a", "t"), quiet()).isEmpty());
    }

    @Test
    void failedGrantIsRetriedNextCheckWithTheSameOpId() {
        UTagsApi api = api(Set.of(), Set.of("vet"));
        when(api.grant(any(), anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("db down")))
                .thenReturn(CompletableFuture.completedFuture(new GrantResult(GrantOutcome.GRANTED, false)));
        UnlockService s = UnlockService.create(Optional.of(api), rules("veteran", "vet"), quiet()).orElseThrow();
        UUID u = UUID.randomUUID();
        s.check(u, meets());
        s.check(u, meets());
        verify(api, times(2)).grant(u, "vet", "unlock:veteran", "unlock:veteran:" + u);
    }
}
