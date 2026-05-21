package com.onesley.oneclick.modules.analytics.search.es;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires Mockito de {@link RestaurantEsSyncService} (L3 — search/ES).
 * Sync PostgreSQL → ES : startup bulk, delta sync, kill-switch esEnabled,
 * resilience (exceptions avalées). JdbcTemplate + ES repo mockés.
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class RestaurantEsSyncServiceTest {

    @Mock JdbcTemplate jdbc;
    @Mock RestaurantEsRepository esRepo;

    private RestaurantEsSyncService svc(boolean enabled) {
        return new RestaurantEsSyncService(jdbc, esRepo, enabled);
    }
    private RestaurantEsDoc doc() {
        return new RestaurantEsDoc(UUID.randomUUID(), UUID.randomUUID(), "Resto", "Casa", "addr", "desc", "phone", "active");
    }

    @Test
    void onStartup_disabled_noop() {
        svc(false).onStartup();
        verify(jdbc, never()).query(anyString(), any(RowMapper.class));
    }

    @Test
    void onStartup_enabled_bulkReindexes() {
        when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(List.of(doc(), doc()));
        svc(true).onStartup();
        verify(esRepo).saveAll(any());
    }

    @Test
    void onStartup_enabled_swallowsException() {
        when(jdbc.query(anyString(), any(RowMapper.class))).thenThrow(new RuntimeException("ES down"));
        svc(true).onStartup(); // ne doit pas propager
        verify(esRepo, never()).saveAll(any());
    }

    @Test
    void bulkReindex_batchesAndReturnsCount() {
        when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(List.of(doc(), doc(), doc()));
        assertThat(svc(true).bulkReindex()).isEqualTo(3);
        verify(esRepo).saveAll(any());
    }

    @Test
    void bulkReindex_empty_returnsZero_noSave() {
        when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(List.of());
        assertThat(svc(true).bulkReindex()).isZero();
        verify(esRepo, never()).saveAll(any());
    }

    @Test
    void deltaSync_disabled_noop() {
        svc(false).deltaSync();
        verify(jdbc, never()).query(anyString(), any(PreparedStatementSetter.class), any(RowMapper.class));
    }

    @Test
    void deltaSync_withDocs_savesAll() {
        when(jdbc.query(anyString(), any(PreparedStatementSetter.class), any(RowMapper.class)))
            .thenReturn(List.of(doc()));
        svc(true).deltaSync();
        verify(esRepo).saveAll(any());
    }

    @Test
    void deltaSync_empty_noSave() {
        when(jdbc.query(anyString(), any(PreparedStatementSetter.class), any(RowMapper.class)))
            .thenReturn(List.of());
        svc(true).deltaSync();
        verify(esRepo, never()).saveAll(any());
    }

    @Test
    void deltaSync_exceptionSwallowed() {
        when(jdbc.query(anyString(), any(PreparedStatementSetter.class), any(RowMapper.class)))
            .thenThrow(new RuntimeException("boom"));
        svc(true).deltaSync(); // ne doit pas propager
        verify(esRepo, never()).saveAll(any());
    }
}
