package com.onesley.oneclick.modules.system.internal;

import com.onesley.oneclick.AbstractIntegrationTest;
import com.onesley.oneclick.realtime.RealtimeFingerprint;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Intégration (audit R3) — valide que {@link SystemHealthDashboardPublisher#computeSnapshot()}
 * s'exécute contre le VRAI schéma : la requête native {@code SELECT count(*), max(checked_at)
 * FROM system_health_checks} doit passer (nom de colonne / table corrects) et renvoyer une
 * empreinte non nulle. Garde-fou contre une faute de colonne qui ne se verrait qu'au runtime WS.
 */
class SystemHealthDashboardPublisherIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    SystemHealthDashboardPublisher publisher;

    @Test
    @Transactional(readOnly = true)
    void computeSnapshot_runsAgainstRealSchema() {
        RealtimeFingerprint fp = publisher.computeSnapshot();
        assertThat(fp).isNotNull();
        assertThat(fp.rowCount()).isGreaterThanOrEqualTo(0L);
        assertThat(publisher.topic()).isEqualTo("/topic/admin/system-health");
    }
}
