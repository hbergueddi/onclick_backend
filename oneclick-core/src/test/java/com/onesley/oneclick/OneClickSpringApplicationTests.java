package com.onesley.oneclick;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Smoke test : valide que le contexte Spring boot complet se charge.
 *
 * <p>Profil {@code enterprise} actif → cible la DB {@code oneclick_enterprise}
 * (où Flyway V1-V12 est cohérent). Le profil {@code dev} (par défaut) cible
 * {@code oneclick_local} qui a une dérive de checksum legacy non bloquante
 * pour le runtime mais qui bloque le contexte test.
 *
 * <p>Désactive aussi Kafka/Modulith externalization et JPA Auditing pour ne
 * pas exiger un broker Kafka durant les tests unitaires (la verify Modulith
 * reste assurée par {@link ModularityTests}).
 */
@SpringBootTest
@ActiveProfiles("enterprise")
@TestPropertySource(properties = {
    "spring.modulith.events.externalization.enabled=false",
    "spring.kafka.bootstrap-servers=",
    "app.security.oauth2.enabled=false"
})
class OneClickSpringApplicationTests {

    @Test
    void contextLoads() {
    }

}
