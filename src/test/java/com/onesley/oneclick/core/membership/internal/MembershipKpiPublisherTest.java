package com.onesley.oneclick.core.membership.internal;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit isolé de {@link MembershipKpiPublisher} — parsing/format du topic STOMP (logique pure).
 */
class MembershipKpiPublisherTest {

    @Test
    void topicFor_buildsPerTenantDestination() {
        UUID t = UUID.fromString("0cccc000-0000-4000-8000-000000000001");
        assertThat(MembershipKpiPublisher.topicFor(t))
                .isEqualTo("/topic/admin/membership-kpis/" + t);
    }

    @Test
    void parseTenantId_roundTripsValidDestination() {
        UUID t = UUID.randomUUID();
        assertThat(MembershipKpiPublisher.parseTenantId(MembershipKpiPublisher.topicFor(t))).isEqualTo(t);
    }

    @Test
    void parseTenantId_rejectsInvalidDestinations() {
        assertThat(MembershipKpiPublisher.parseTenantId(null)).isNull();
        assertThat(MembershipKpiPublisher.parseTenantId("/topic/other/" + UUID.randomUUID())).isNull();
        assertThat(MembershipKpiPublisher.parseTenantId("/topic/admin/membership-kpis/")).isNull();
        assertThat(MembershipKpiPublisher.parseTenantId("/topic/admin/membership-kpis/not-a-uuid")).isNull();
        assertThat(MembershipKpiPublisher.parseTenantId(
                "/topic/admin/membership-kpis/" + UUID.randomUUID() + "/extra")).isNull();
    }
}
