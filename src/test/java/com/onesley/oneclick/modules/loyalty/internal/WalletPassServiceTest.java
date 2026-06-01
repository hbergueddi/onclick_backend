package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.core.identity.api.UserDirectoryApi;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires isolés de {@link WalletPassService#getMetadata} (L3 — modules.loyalty).
 *
 * <p>P2 — le nom provient désormais du contrat {@link UserDirectoryApi} (mocké),
 * les points de la requête native intra-module {@code loyalty_accounts} (scalaire).
 */
class WalletPassServiceTest {

    private WalletPassService service(UserDirectoryApi dir, Number points) {
        EntityManager em = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(points);
        WalletPassService service = new WalletPassService(dir);
        ReflectionTestUtils.setField(service, "em", em);
        return service;
    }

    @Test
    void getMetadata_resolvesNameViaDirectory_andComputesTier() {
        UUID uid = UUID.randomUUID();
        UserDirectoryApi dir = mock(UserDirectoryApi.class);
        when(dir.nameById(eq(uid)))
            .thenReturn(Optional.of(new UserDirectoryApi.UserName(uid, "Ali", "Bennani", "+212600000000", "ali@x.ma")));

        var dto = service(dir, 1500).getMetadata(uid);

        assertThat(dto.firstName()).isEqualTo("Ali");
        assertThat(dto.lastName()).isEqualTo("Bennani");
        assertThat(dto.totalPoints()).isEqualTo(1500);
        assertThat(dto.serialNumber()).startsWith("OC-");
        assertThat(dto.tier()).isEqualTo("Sapphire"); // 1500 → palier Sapphire
        verify(dir).nameById(uid);
    }

    @Test
    void getMetadata_unknownUser_nullName_butPointsAndTierStillComputed() {
        UUID uid = UUID.randomUUID();
        UserDirectoryApi dir = mock(UserDirectoryApi.class);
        when(dir.nameById(any())).thenReturn(Optional.empty());

        var dto = service(dir, 0).getMetadata(uid);

        assertThat(dto.firstName()).isNull();
        assertThat(dto.lastName()).isNull();
        assertThat(dto.totalPoints()).isZero();
        assertThat(dto.tier()).isEqualTo("Ruby"); // 0 pt → palier de base
    }

    @Test
    void generateApplePass_nullName_doesNotThrow_safeQuotesEmpty() {
        UUID uid = UUID.randomUUID();
        UserDirectoryApi dir = mock(UserDirectoryApi.class);
        when(dir.nameById(any())).thenReturn(Optional.empty());

        byte[] pass = service(dir, 0).generateApplePass(uid);

        assertThat(new String(pass)).contains("\"organizationName\"");
        verify(dir, never()).namesByIds(any()); // pass single → pas d'appel batch
    }
}
