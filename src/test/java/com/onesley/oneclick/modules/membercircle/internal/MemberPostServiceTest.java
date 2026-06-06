package com.onesley.oneclick.modules.membercircle.internal;

import com.onesley.oneclick.modules.membercircle.api.MemberPostDtos.MemberPostDto;
import com.onesley.oneclick.modules.membercircle.api.MemberPostDtos.MemberPostsSummaryDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires isolés du résumé de modération (C4.8c) — fonction pure
 * {@link MemberPostService#summarize}. Le flux SQL/modération est couvert par l'intégration.
 */
class MemberPostServiceTest {

    private static MemberPostDto post(String status) {
        return new MemberPostDto(UUID.randomUUID(), UUID.randomUUID(), "A", "B", null,
            "contenu", null, null, status, null, null);
    }

    @Test
    void summarize_empty_isAllZero() {
        MemberPostsSummaryDto s = MemberPostService.summarize(List.of());
        assertThat(s.total()).isZero();
        assertThat(s.pending()).isZero();
        assertThat(s.approved()).isZero();
        assertThat(s.rejected()).isZero();
    }

    @Test
    void summarize_countsByStatus() {
        MemberPostsSummaryDto s = MemberPostService.summarize(List.of(
            post("pending"), post("pending"), post("approved"), post("rejected"), post("weird")));
        assertThat(s.total()).isEqualTo(5);
        assertThat(s.pending()).isEqualTo(2);
        assertThat(s.approved()).isEqualTo(1);
        assertThat(s.rejected()).isEqualTo(1);  // statut inconnu non compté dans les buckets
    }
}
