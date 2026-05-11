package com.onesley.oneclick;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Test debug — affiche le nom canonique de chaque module détecté par Modulith.
 * Sert à diagnostiquer les erreurs "No module found with name 'X'".
 */
class ModulesInspectionTest {

    @Test
    void listAllModuleNames() {
        ApplicationModules modules = ApplicationModules.of(OneClickSpringApplication.class);
        System.out.println("\n══════════════════════════════════════════════════");
        System.out.println("  Modules détectés par Spring Modulith");
        System.out.println("══════════════════════════════════════════════════");
        modules.forEach(m -> System.out.println(
            "  name=" + m.getName()
            + "  basePackage=" + m.getBasePackage().getName()
            + "  displayName=" + m.getDisplayName()));
        System.out.println("══════════════════════════════════════════════════\n");
    }
}
