package com.onesley.oneclick.etl;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriétés ETL — bind sur {@code oneclick.etl.*} dans application-etl.yml.
 */
@ConfigurationProperties(prefix = "oneclick.etl")
public class EtlProperties {

    private Legacy legacy = new Legacy();
    private String fallbackPassword = "TestLocal2026!";
    private boolean truncateBeforeEtl = true;
    private boolean runPhaseA = true;
    private boolean runPhaseB = false;

    /**
     * Sprint G.7 — Mode dry-run : skip les INSERT, exécute juste les SELECT
     * sur les tables source pour rapporter le nombre de rows qui seraient
     * migrées. Utile pour valider la connexion FDW + estimer le volume avant
     * un vrai run. Activable via ONECLICK_ETL_DRY_RUN=true.
     */
    private boolean dryRun = false;

    /**
     * Sprint G.7 — Active la phase de vérification post-ETL : compare counts
     * source (legacy.*) vs target (oneclick_enterprise) pour chaque table critique.
     * Rapport visuel ✓/⚠ pour audit. Activé par défaut.
     */
    private boolean verifyCountsAfterEtl = true;

    public Legacy getLegacy() { return legacy; }
    public void setLegacy(Legacy legacy) { this.legacy = legacy; }
    public String getFallbackPassword() { return fallbackPassword; }
    public void setFallbackPassword(String fallbackPassword) { this.fallbackPassword = fallbackPassword; }
    public boolean isTruncateBeforeEtl() { return truncateBeforeEtl; }
    public void setTruncateBeforeEtl(boolean truncateBeforeEtl) { this.truncateBeforeEtl = truncateBeforeEtl; }
    public boolean isRunPhaseA() { return runPhaseA; }
    public void setRunPhaseA(boolean runPhaseA) { this.runPhaseA = runPhaseA; }
    public boolean isRunPhaseB() { return runPhaseB; }
    public void setRunPhaseB(boolean runPhaseB) { this.runPhaseB = runPhaseB; }
    public boolean isDryRun() { return dryRun; }
    public void setDryRun(boolean dryRun) { this.dryRun = dryRun; }
    public boolean isVerifyCountsAfterEtl() { return verifyCountsAfterEtl; }
    public void setVerifyCountsAfterEtl(boolean verifyCountsAfterEtl) { this.verifyCountsAfterEtl = verifyCountsAfterEtl; }

    public static class Legacy {
        private String host = "localhost";
        private int port = 5432;
        private String database = "oneclick_local";
        private String user = "oneclick_app";
        private String password = "OneclickLocal2026";

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public String getDatabase() { return database; }
        public void setDatabase(String database) { this.database = database; }
        public String getUser() { return user; }
        public void setUser(String user) { this.user = user; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}
