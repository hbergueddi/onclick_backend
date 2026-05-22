package com.onesley.oneclick.realtime;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Tests unitaires de {@link RealtimeFingerprint} — count+max(change) + coercion temporelle. */
class RealtimeFingerprintTest {

    private EntityManager emReturning(Object[] row) {
        EntityManager em = mock(EntityManager.class);
        Query q = mock(Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(q);
        when(q.getSingleResult()).thenReturn(row);
        return em;
    }

    @Test
    void of_instant_passthrough() {
        Instant now = Instant.parse("2026-01-01T10:00:00Z");
        var fp = RealtimeFingerprint.of(emReturning(new Object[]{5L, now}), "events");
        assertThat(fp.rowCount()).isEqualTo(5L);
        assertThat(fp.lastChange()).isEqualTo(now);
    }

    @Test
    void of_withColumn_timestampAndOffsetAndNull() {
        Instant base = Instant.parse("2026-02-02T08:00:00Z");
        assertThat(RealtimeFingerprint.of(emReturning(new Object[]{3L, Timestamp.from(base)}), "t", "created_at").lastChange())
            .isEqualTo(base);
        assertThat(RealtimeFingerprint.of(emReturning(new Object[]{2L, OffsetDateTime.ofInstant(base, ZoneOffset.UTC)}), "t", "created_at").lastChange())
            .isEqualTo(base);
        var fpNull = RealtimeFingerprint.of(emReturning(new Object[]{0L, null}), "t", "created_at");
        assertThat(fpNull.rowCount()).isZero();
        assertThat(fpNull.lastChange()).isNull();
    }

    @Test
    void record_equalsByValue() {
        Instant now = Instant.now();
        assertThat(new RealtimeFingerprint(1L, now)).isEqualTo(new RealtimeFingerprint(1L, now));
        assertThat(new RealtimeFingerprint(1L, now)).isNotEqualTo(new RealtimeFingerprint(2L, now));
    }
}
