package com.onesley.oneclick.modules.loyalty.internal;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Tests unitaires de {@link WalletPassService#getMetadata} (L3 — modules.loyalty). */
class WalletPassServiceTest {

    @Test
    void getMetadata_mapsRow_andComputesTier() {
        EntityManager em = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(new Object[]{"Ali", "Bennani", 1500});

        WalletPassService service = new WalletPassService();
        ReflectionTestUtils.setField(service, "em", em);

        var dto = service.getMetadata(UUID.randomUUID());
        assertThat(dto.firstName()).isEqualTo("Ali");
        assertThat(dto.lastName()).isEqualTo("Bennani");
        assertThat(dto.totalPoints()).isEqualTo(1500);
        assertThat(dto.serialNumber()).startsWith("OC-");
        assertThat(dto.tier()).isNotBlank();
    }
}
