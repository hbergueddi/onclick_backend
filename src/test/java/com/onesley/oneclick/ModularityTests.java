package com.onesley.oneclick;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Tests d'architecture Spring Modulith (Phase 2 §21 prérequis).
 *
 * <ul>
 *   <li>{@link #verifyModuleBoundaries} — Échoue à la build si un module viole
 *       les {@code allowedDependencies} déclarées dans son {@code @ApplicationModule}.</li>
 *   <li>{@link #generateModuleDocs} — Génère C4-style diagrammes PlantUML +
 *       Asciidoc dans {@code target/spring-modulith-docs/}. Lisible pour onboarding.</li>
 * </ul>
 *
 * <p>Cette protection est cruciale Phase 2 : si un module commence à dépendre
 * d'un autre qu'il ne devrait pas, ce test l'attrape avant que ça parte en prod.
 */
class ModularityTests {

    private static final ApplicationModules MODULES = ApplicationModules.of(OneClickSpringApplication.class);

    @Test
    void verifyModuleBoundaries() {
        MODULES.verify();
    }

    @Test
    void generateModuleDocs() {
        new Documenter(MODULES)
            .writeDocumentation()
            .writeModulesAsPlantUml()
            .writeIndividualModulesAsPlantUml();
    }
}
