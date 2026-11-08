package com.extantape.portmanteau.trunk;

import com.extantape.portmanteau.trunk.LinkRules.Audience;
import com.extantape.portmanteau.trunk.LinkRules.Link;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LinkRulesTest {
    private static final Map<String, Set<String>> GROUPS = Map.of(
            "kids_a", Set.of("ka1", "ka2"),
            "kids_b", Set.of("kb1"),
            "adults_a", Set.of("aa1"),
            "adults_b", Set.of("ab1"),
            "both_ages", Set.of("m_kids", "m_adults"),
            "plain", Set.of("p1"));
    private static final Map<String, Audience> GROUP_LABELS = Map.of(
            "kids_a", Audience.KIDS, "kids_b", Audience.KIDS, "adults_a", Audience.ADULTS, "adults_b", Audience.ADULTS);
    private static final Map<String, Audience> WORLD_LABELS = Map.of("m_kids", Audience.KIDS, "m_adults", Audience.ADULTS);

    private LinkRules.Result resolve(Link... links) {
        return LinkRules.resolve(GROUPS, GROUP_LABELS, WORLD_LABELS, List.of(links));
    }

    @Test
    void withoutLinksEachGroupHasItsOwnScope() {
        var r = resolve();
        assertTrue(r.errors().isEmpty());
        assertNotEquals(r.scopeOf("kids_a"), r.scopeOf("kids_b"));
    }

    @Test
    void sameAudienceGroupsLink() {
        var r = resolve(new Link("kids_a", "kids_b"));
        assertTrue(r.errors().isEmpty(), r.errors().toString());
        assertEquals(r.scopeOf("kids_a"), r.scopeOf("kids_b"));
        assertNotEquals(r.scopeOf("kids_a"), r.scopeOf("adults_a"));
    }

    @Test
    void kidsAndAdultsNeverLinkDirectly() {
        var r = resolve(new Link("kids_a", "adults_a"));
        assertFalse(r.errors().isEmpty());
        assertNotEquals(r.scopeOf("kids_a"), r.scopeOf("adults_a"));
    }

    @Test
    void kidsAndAdultsNeverLinkThroughAChain() {
        // kids_a - plain is refused anyway, so make a chain of labelled groups: kids_b - kids_a and kids_a - adults_b.
        var r = resolve(new Link("kids_b", "kids_a"), new Link("kids_a", "adults_b"));
        assertFalse(r.errors().isEmpty());
        // One bad link refuses them all.
        assertNotEquals(r.scopeOf("kids_a"), r.scopeOf("kids_b"));
        assertNotEquals(r.scopeOf("kids_b"), r.scopeOf("adults_b"));
    }

    @Test
    void anUnlabelledGroupCantBeLinked() {
        var r = resolve(new Link("plain", "adults_a"));
        assertFalse(r.errors().isEmpty());
        assertNotEquals(r.scopeOf("plain"), r.scopeOf("adults_a"));
    }

    @Test
    void aMixedGroupCantBeLinkedEvenIfLabelledOneWay() {
        var labels = new java.util.HashMap<>(GROUP_LABELS);
        labels.put("both_ages", Audience.ADULTS);
        var r = LinkRules.resolve(GROUPS, labels, WORLD_LABELS, List.of(new Link("both_ages", "adults_a")));
        assertEquals(Audience.MIXED, LinkRules.audience("both_ages", GROUPS.get("both_ages"), labels, WORLD_LABELS));
        assertFalse(r.errors().isEmpty());
        assertNotEquals(r.scopeOf("both_ages"), r.scopeOf("adults_a"));
    }

    @Test
    void aLinkToAMissingGroupIsRefused() {
        var r = resolve(new Link("kids_a", "nowhere"));
        assertFalse(r.errors().isEmpty());
    }
}
