package com.onesley.oneclick.shared;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests unitaires de {@link Temporals#toInstant(Object)} — toutes les variantes de type. */
class TemporalsTest {

    private static final Instant REF = Instant.parse("2026-01-01T10:00:00Z");

    @Test
    void toInstant_handlesAllSupportedTypes() {
        assertThat(Temporals.toInstant(null)).isNull();
        assertThat(Temporals.toInstant(REF)).isSameAs(REF);                                    // déjà un Instant
        assertThat(Temporals.toInstant(Timestamp.from(REF))).isEqualTo(REF);                   // java.sql.Timestamp
        assertThat(Temporals.toInstant(OffsetDateTime.ofInstant(REF, ZoneOffset.UTC))).isEqualTo(REF); // OffsetDateTime
        assertThat(Temporals.toInstant("2026-01-01T10:00:00Z")).isEqualTo(REF);                // fallback String ISO-8601
    }

    @Test
    void toInstant_invalidString_throwsParseException() {
        assertThatThrownBy(() -> Temporals.toInstant("pas-une-date"))
            .isInstanceOf(DateTimeParseException.class);
    }
}
