package com.onesley.oneclick;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Timestamp;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Intégration du {@link StuckEventPublicationReaper} contre la vraie table {@code event_publication} :
 * une publication incomplète au-delà de la grâce est clôturée ; une récente est préservée.
 * Valide la syntaxe SQL native réelle (interval) — pas seulement le wiring.
 */
class StuckEventPublicationReaperIntegrationTest extends AbstractIntegrationTest {

    @Autowired StuckEventPublicationReaper reaper;

    @Test
    void reap_completesStaleIncomplete_keepsRecent() {
        UUID stale = UUID.randomUUID();
        UUID recent = UUID.randomUUID();
        // poison ancien (10 j, antérieur à la baseline du test) → doit être clôturé
        jdbc.update("INSERT INTO event_publication (id, listener_id, event_type, serialized_event, publication_date) "
            + "VALUES (?::uuid, 'test-reaper', 'TestEvent', '{}', now() - interval '10 days')", stale.toString());
        // récent → dans la période de grâce → doit rester incomplet
        jdbc.update("INSERT INTO event_publication (id, listener_id, event_type, serialized_event, publication_date) "
            + "VALUES (?::uuid, 'test-reaper', 'TestEvent', '{}', now())", recent.toString());
        try {
            reaper.reapStuckPublications();

            Timestamp staleDone = jdbc.queryForObject(
                "SELECT completion_date FROM event_publication WHERE id = ?::uuid", Timestamp.class, stale.toString());
            Timestamp recentDone = jdbc.queryForObject(
                "SELECT completion_date FROM event_publication WHERE id = ?::uuid", Timestamp.class, recent.toString());

            assertThat(staleDone).as("poison > grâce → clôturé").isNotNull();
            assertThat(recentDone).as("récent → préservé (rejeu normal possible)").isNull();
        } finally {
            jdbc.update("DELETE FROM event_publication WHERE listener_id = 'test-reaper'");
        }
    }
}
