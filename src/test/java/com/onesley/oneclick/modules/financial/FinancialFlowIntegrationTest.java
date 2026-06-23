package com.onesley.oneclick.modules.financial;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** L4 « profondeur » — {@code /api/financial} : contracts + invoices CRUD + lines/wallet-tx/templates reads. */
class FinancialFlowIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();
    private String restaurantId() { return jdbc.queryForObject("SELECT id::text FROM restaurants WHERE deleted_at IS NULL LIMIT 1", String.class); }
    private String rand() { return UUID.randomUUID().toString().substring(0, 8); }

    // ─── B1.5b — wallet/balances batch ───────────────────────────────────────

    @Test
    void walletBalances_admin200_restaurateurScoped() {
        String admin = adminBearer();
        String rid = restaurantId();
        ResponseEntity<String> adminRes = restTemplate.exchange(
            url("/api/financial/wallet/balances?restaurantIds=" + rid),
            HttpMethod.GET, jwtEntity(admin), String.class);
        assertThat(adminRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(adminRes.getBody()).startsWith("[");

        // RESTAURATEUR (non-admin) staffé : 200 sur son resto, 403 si un resto étranger dans la liste.
        java.util.Map<String, Object> row = jdbc.queryForMap(
            "SELECT u.id::text AS uid, rs.restaurant_id::text AS rid "
            + "FROM users u JOIN roles r ON r.id = u.role_id "
            + "JOIN restaurant_staffs rs ON rs.user_id = u.id "
            + "WHERE r.code = 'RESTAURATEUR' AND rs.deleted_at IS NULL AND u.deleted_at IS NULL "
            + "ORDER BY u.id LIMIT 1");
        String ownerId = (String) row.get("uid");
        String ownedRid = (String) row.get("rid");
        String ownerBearer = jwtIssuer.issueAccessToken(UUID.fromString(ownerId), "RESTAURATEUR").token();
        String foreignRid = jdbc.queryForObject(
            "SELECT id::text FROM restaurants WHERE deleted_at IS NULL "
            + "AND id NOT IN (SELECT restaurant_id FROM restaurant_staffs WHERE user_id = ?::uuid AND deleted_at IS NULL) "
            + "LIMIT 1", String.class, ownerId);

        assertThat(restTemplate.exchange(url("/api/financial/wallet/balances?restaurantIds=" + ownedRid),
            HttpMethod.GET, jwtEntity(ownerBearer), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/financial/wallet/balances?restaurantIds=" + ownedRid + "," + foreignRid),
            HttpMethod.GET, jwtEntity(ownerBearer), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void walletBalances_noBearer_401() {
        assertThat(restTemplate.exchange(url("/api/financial/wallet/balances?restaurantIds=" + restaurantId()),
            HttpMethod.GET, jwtEntity(null), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void contract_crud() throws Exception {
        String admin = adminBearer();
        assertThat(restTemplate.exchange(url("/api/financial/contracts?page=0&size=5"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> post = restTemplate.exchange(url("/api/financial/contracts"), HttpMethod.POST,
            jsonJwtEntity(Map.of("restaurantId", restaurantId(), "contractNumber", "L4-C-" + rand(),
                "commissionRate", 3.0, "walletAdminRate", 1.5, "startsAt", LocalDate.now().toString(), "endsAt", LocalDate.now().plusYears(1).toString()), admin), String.class);
        assertThat(post.getStatusCode().is2xxSuccessful()).isTrue();
        // walletAdminRate (V49) persisté + exposé dans le DTO
        assertThat(om.readTree(post.getBody()).get("walletAdminRate").asDouble()).isEqualTo(1.5);
        String id = om.readTree(post.getBody()).get("id").asText();
        ResponseEntity<String> get = restTemplate.exchange(url("/api/financial/contracts/" + id), HttpMethod.GET, jwtEntity(admin), String.class);
        assertThat(get.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(om.readTree(get.getBody()).get("walletAdminRate").asDouble()).isEqualTo(1.5);
        assertThat(restTemplate.exchange(url("/api/financial/contracts/" + id), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("commissionRate", 4.5, "walletAdminRate", 2.75), admin), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        jdbc.update("DELETE FROM contracts WHERE id = ?::uuid", UUID.fromString(id)); // self-clean (pas d'endpoint DELETE)
    }

    /**
     * #3 — un PATCH de contrat (statut + commission) écrit des lignes dans
     * contract_history, lues par GET /contracts/{id}/history (ContractHistoryPanel).
     */
    @Test
    void contractHistory_recordsFieldChanges() throws Exception {
        String admin = adminBearer();
        ResponseEntity<String> post = restTemplate.exchange(url("/api/financial/contracts"), HttpMethod.POST,
            jsonJwtEntity(Map.of("restaurantId", restaurantId(), "contractNumber", "L4-H-" + rand(),
                "commissionRate", 3.0, "startsAt", LocalDate.now().toString()), admin), String.class);
        assertThat(post.getStatusCode().is2xxSuccessful()).isTrue();
        String id = om.readTree(post.getBody()).get("id").asText();
        try {
            // active → terminated + commission 3.0 → 4.5 : 2 champs suivis modifiés.
            assertThat(restTemplate.exchange(url("/api/financial/contracts/" + id), HttpMethod.PATCH,
                jsonJwtEntity(Map.of("status", "terminated", "commissionRate", 4.5), admin), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
            ResponseEntity<String> hist = restTemplate.exchange(url("/api/financial/contracts/" + id + "/history"),
                HttpMethod.GET, jwtEntity(admin), String.class);
            assertThat(hist.getStatusCode()).isEqualTo(HttpStatus.OK);
            var arr = om.readTree(hist.getBody());
            assertThat(arr.isArray()).isTrue();
            boolean statusRow = false;
            for (var n : arr)
                if ("status".equals(n.get("fieldName").asText()) && "terminated".equals(n.get("newValue").asText())) statusRow = true;
            assertThat(statusRow).as("ligne d'historique pour le changement de statut").isTrue();
        } finally {
            jdbc.update("DELETE FROM contracts WHERE id = ?::uuid", UUID.fromString(id)); // history cascade ON DELETE
        }
    }

    @Test
    void invoice_crud_andLines() throws Exception {
        String admin = adminBearer();
        assertThat(restTemplate.exchange(url("/api/financial/invoices?page=0&size=5"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> post = restTemplate.exchange(url("/api/financial/invoices"), HttpMethod.POST,
            jsonJwtEntity(Map.of("restaurantId", restaurantId(), "invoiceNumber", "L4-I-" + rand(),
                "periodStart", LocalDate.now().withDayOfMonth(1).toString(), "periodEnd", LocalDate.now().toString()), admin), String.class);
        assertThat(post.getStatusCode().is2xxSuccessful()).isTrue();
        String id = om.readTree(post.getBody()).get("id").asText();
        assertThat(restTemplate.exchange(url("/api/financial/invoices/" + id), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/financial/invoices/" + id), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("subtotal", 1000.0), admin), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/financial/invoices/" + id + "/lines"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        jdbc.update("DELETE FROM invoices WHERE id = ?::uuid", UUID.fromString(id)); // self-clean (pas d'endpoint DELETE)
    }

    @Test
    void reads_walletTx_contractTemplates() {
        String admin = adminBearer();
        assertThat(restTemplate.exchange(url("/api/financial/wallet-tx?page=0&size=5"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/financial/contract-templates"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void contractTemplate_byCode_unknown_404() {
        assertThat(restTemplate.exchange(url("/api/financial/contract-templates/by-code/inconnu-" + UUID.randomUUID()),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void contract_unknownId_404() {
        assertThat(restTemplate.exchange(url("/api/financial/contracts/" + UUID.randomUUID()),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createContract_asClient_403() {
        assertThat(restTemplate.exchange(url("/api/financial/contracts"), HttpMethod.POST,
            jsonJwtEntity(Map.of("restaurantId", restaurantId(), "contractNumber", "x", "commissionRate", 3.0,
                "startsAt", LocalDate.now().toString()), bearerForRole("CLIENT")), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ─── Endpoints jusqu'ici non couverts (templates CRUD, line, wallet-tx, generate) ───

    @Test
    void contractTemplate_crud() throws Exception {
        String admin = adminBearer();
        String code = "L4-CT-" + rand();
        ResponseEntity<String> post = restTemplate.exchange(url("/api/financial/contract-templates"), HttpMethod.POST,
            jsonJwtEntity(Map.of("code", code, "name", "L4 Template", "version", 1, "language", "fr",
                "title", "Titre L4", "body", "Corps du contrat L4", "isActive", true), admin), String.class);
        assertThat(post.getStatusCode().is2xxSuccessful()).isTrue();
        String id = om.readTree(post.getBody()).get("id").asText();
        assertThat(restTemplate.exchange(url("/api/financial/contract-templates/" + id), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/financial/contract-templates/by-code/" + code + "?language=fr&version=1"),
            HttpMethod.GET, jwtEntity(admin), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/financial/contract-templates/" + id), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("name", "L4 Template renommé"), admin), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/financial/contract-templates/" + id), HttpMethod.DELETE, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        jdbc.update("DELETE FROM contract_templates WHERE id = ?::uuid", UUID.fromString(id)); // hard-clean après soft-delete
    }

    @Test
    void contractTemplateArticles_crud() throws Exception {
        String admin = adminBearer();
        String code = "L4-CTA-" + rand();
        ResponseEntity<String> tpl = restTemplate.exchange(url("/api/financial/contract-templates"), HttpMethod.POST,
            jsonJwtEntity(Map.of("code", code, "name", "Tpl Articles", "version", 1, "language", "fr",
                "title", "T", "body", "B", "isActive", true), admin), String.class);
        String templateId = om.readTree(tpl.getBody()).get("id").asText();

        // CREATE article → 201
        ResponseEntity<String> post = restTemplate.exchange(
            url("/api/financial/contract-templates/" + templateId + "/articles"), HttpMethod.POST,
            jsonJwtEntity(Map.of("articleNumber", 1, "title", "Objet du contrat", "content", "Le présent contrat…", "sortOrder", 0), admin),
            String.class);
        assertThat(post.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String articleId = om.readTree(post.getBody()).get("id").asText();
        assertThat(om.readTree(post.getBody()).get("templateId").asText()).isEqualTo(templateId);

        // LIST → 200 + contient l'article
        ResponseEntity<String> list = restTemplate.exchange(
            url("/api/financial/contract-templates/" + templateId + "/articles"), HttpMethod.GET, jwtEntity(admin), String.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).contains("Objet du contrat");

        // PATCH → 200 + titre modifié
        ResponseEntity<String> patch = restTemplate.exchange(
            url("/api/financial/contract-templates/" + templateId + "/articles/" + articleId), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("title", "Objet (révisé)"), admin), String.class);
        assertThat(patch.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(om.readTree(patch.getBody()).get("title").asText()).isEqualTo("Objet (révisé)");

        // DELETE → 204
        assertThat(restTemplate.exchange(
            url("/api/financial/contract-templates/" + templateId + "/articles/" + articleId), HttpMethod.DELETE, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // create article sur template inconnu → 404
        assertThat(restTemplate.exchange(
            url("/api/financial/contract-templates/" + UUID.randomUUID() + "/articles"), HttpMethod.POST,
            jsonJwtEntity(Map.of("articleNumber", 1, "title", "x", "content", "y"), admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        jdbc.update("DELETE FROM contract_templates WHERE id = ?::uuid", UUID.fromString(templateId));
    }

    @Test
    void invoiceLine_create() throws Exception {
        String admin = adminBearer();
        ResponseEntity<String> inv = restTemplate.exchange(url("/api/financial/invoices"), HttpMethod.POST,
            jsonJwtEntity(Map.of("restaurantId", restaurantId(), "invoiceNumber", "L4-IL-" + rand(),
                "periodStart", LocalDate.now().withDayOfMonth(1).toString(), "periodEnd", LocalDate.now().toString()), admin), String.class);
        String invoiceId = om.readTree(inv.getBody()).get("id").asText();
        ResponseEntity<String> line = restTemplate.exchange(url("/api/financial/lines"), HttpMethod.POST,
            jsonJwtEntity(Map.of("invoiceId", invoiceId, "label", "Commission L4", "quantity", 2, "unitPrice", 150.0, "sortOrder", 0), admin), String.class);
        assertThat(line.getStatusCode().is2xxSuccessful()).isTrue();
        // self-clean : lignes (FK) puis facture
        jdbc.update("DELETE FROM invoice_lines WHERE invoice_id = ?::uuid", UUID.fromString(invoiceId));
        jdbc.update("DELETE FROM invoices WHERE id = ?::uuid", UUID.fromString(invoiceId));
    }

    /**
     * P1.8 — le filtre {@code reason} isole les lignes wallet d'un motif (ex. commissions de
     * parrainage). 200 pour le propriétaire du resto ; ne renvoie QUE les lignes 'referral_commission' ;
     * 403 cross-resto et 403 sans VIEW:FINANCIAL (CLIENT).
     */
    @Test
    void walletTx_reasonFilter_isolatesAndScopes() throws Exception {
        String admin = adminBearer();
        // Propriétaire réel d'un resto (RESTAURATEUR staffé) → VIEW:FINANCIAL + ABAC sur SON resto.
        Map<String, Object> row = jdbc.queryForMap(
            "SELECT u.id::text AS uid, rs.restaurant_id::text AS rid "
            + "FROM users u JOIN roles r ON r.id = u.role_id "
            + "JOIN restaurant_staffs rs ON rs.user_id = u.id "
            + "WHERE r.code = 'RESTAURATEUR' AND rs.deleted_at IS NULL AND u.deleted_at IS NULL "
            + "ORDER BY u.id LIMIT 1");
        String ownerId = (String) row.get("uid");
        String rid = (String) row.get("rid");
        String ownerBearer = jwtIssuer.issueAccessToken(UUID.fromString(ownerId), "RESTAURATEUR").token();

        // 2 lignes referral_commission + 1 ligne d'un autre motif sur le même resto (créées via admin).
        String id1 = createWalletTx(admin, rid, "commission", "referral_commission");
        String id2 = createWalletTx(admin, rid, "commission", "referral_commission");
        String id3 = createWalletTx(admin, rid, "credit", "monthly_settlement");
        try {
            // Filtré sur reason=referral_commission → exactement les 2 lignes referral.
            ResponseEntity<String> filtered = restTemplate.exchange(
                url("/api/financial/wallet-tx?restaurantId=" + rid + "&reason=referral_commission&page=0&size=50"),
                HttpMethod.GET, jwtEntity(ownerBearer), String.class);
            assertThat(filtered.getStatusCode()).isEqualTo(HttpStatus.OK);
            var content = om.readTree(filtered.getBody()).get("content");
            boolean sawId1 = false, sawId2 = false;
            for (var n : content) {
                // toutes les lignes renvoyées portent bien le motif referral_commission
                assertThat(n.get("reason").asText()).isEqualTo("referral_commission");
                String tid = n.get("id").asText();
                if (tid.equals(id1)) sawId1 = true;
                if (tid.equals(id2)) sawId2 = true;
                assertThat(tid).as("la ligne d'un autre motif ne doit pas apparaître").isNotEqualTo(id3);
            }
            assertThat(sawId1 && sawId2).as("les 2 lignes referral présentes").isTrue();

            // 403 cross-resto : un resto étranger au owner.
            String foreignRid = jdbc.queryForObject(
                "SELECT id::text FROM restaurants WHERE deleted_at IS NULL "
                + "AND id NOT IN (SELECT restaurant_id FROM restaurant_staffs WHERE user_id = ?::uuid AND deleted_at IS NULL) "
                + "LIMIT 1", String.class, ownerId);
            assertThat(restTemplate.exchange(
                url("/api/financial/wallet-tx?restaurantId=" + foreignRid + "&reason=referral_commission"),
                HttpMethod.GET, jwtEntity(ownerBearer), String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

            // 403 sans VIEW:FINANCIAL (CLIENT).
            assertThat(restTemplate.exchange(
                url("/api/financial/wallet-tx?restaurantId=" + rid + "&reason=referral_commission"),
                HttpMethod.GET, jwtEntity(bearerForRole("CLIENT")), String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        } finally {
            jdbc.update("DELETE FROM wallet_transactions WHERE id IN (?::uuid, ?::uuid, ?::uuid)",
                UUID.fromString(id1), UUID.fromString(id2), UUID.fromString(id3));
        }
    }

    private String createWalletTx(String admin, String rid, String type, String reason) throws Exception {
        ResponseEntity<String> post = restTemplate.exchange(url("/api/financial/wallet-tx"), HttpMethod.POST,
            jsonJwtEntity(Map.of("restaurantId", rid, "type", type, "amount", 42.0, "reason", reason), admin), String.class);
        assertThat(post.getStatusCode().is2xxSuccessful()).isTrue();
        return om.readTree(post.getBody()).get("id").asText();
    }

    @Test
    void walletTx_create() throws Exception {
        String admin = adminBearer();
        ResponseEntity<String> post = restTemplate.exchange(url("/api/financial/wallet-tx"), HttpMethod.POST,
            jsonJwtEntity(Map.of("restaurantId", restaurantId(), "type", "credit", "amount", 100.0, "reason", "L4 test"), admin), String.class);
        assertThat(post.getStatusCode().is2xxSuccessful()).isTrue();
        String id = om.readTree(post.getBody()).get("id").asText();
        jdbc.update("DELETE FROM wallet_transactions WHERE id = ?::uuid", UUID.fromString(id)); // self-clean
    }

    @Test
    void generateMonthlyInvoices_futurePeriod_noop() {
        String admin = adminBearer();
        // période lointaine → aucun contrat actif → 0 généré (exerce l'endpoint sans polluer la base)
        assertThat(restTemplate.exchange(url("/api/financial/invoices/generate-monthly?periodMonth=2099-12"),
            HttpMethod.POST, jwtEntity(admin), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        jdbc.update("DELETE FROM invoices WHERE period_start >= DATE '2099-12-01'"); // défensif
    }

    @Test
    void contractDisabledArticles_setAndGet() throws Exception {
        String admin = adminBearer();
        ResponseEntity<String> tpl = restTemplate.exchange(url("/api/financial/contract-templates"), HttpMethod.POST,
            jsonJwtEntity(Map.of("code", "L4-DA-" + rand(), "name", "Tpl DA", "version", 1, "language", "fr",
                "title", "T", "body", "B", "isActive", true), admin), String.class);
        String templateId = om.readTree(tpl.getBody()).get("id").asText();
        ResponseEntity<String> art = restTemplate.exchange(url("/api/financial/contract-templates/" + templateId + "/articles"), HttpMethod.POST,
            jsonJwtEntity(Map.of("articleNumber", 1, "title", "Clause", "content", "…", "sortOrder", 0), admin), String.class);
        String articleId = om.readTree(art.getBody()).get("id").asText();
        ResponseEntity<String> ct = restTemplate.exchange(url("/api/financial/contracts"), HttpMethod.POST,
            jsonJwtEntity(Map.of("restaurantId", restaurantId(), "contractNumber", "L4-DA-" + rand(),
                "commissionRate", 3.0, "startsAt", LocalDate.now().toString()), admin), String.class);
        String contractId = om.readTree(ct.getBody()).get("id").asText();

        // PUT [articleId] → GET le contient
        assertThat(restTemplate.exchange(url("/api/financial/contracts/" + contractId + "/disabled-articles"), HttpMethod.PUT,
            jsonJwtEntity(Map.of("articleIds", java.util.List.of(articleId)), admin), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/financial/contracts/" + contractId + "/disabled-articles"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getBody()).contains(articleId);
        // PUT [] → GET vide
        assertThat(restTemplate.exchange(url("/api/financial/contracts/" + contractId + "/disabled-articles"), HttpMethod.PUT,
            jsonJwtEntity(Map.of("articleIds", java.util.List.of()), admin), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/financial/contracts/" + contractId + "/disabled-articles"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getBody()).doesNotContain(articleId);

        jdbc.update("DELETE FROM contracts WHERE id = ?::uuid", UUID.fromString(contractId));
        jdbc.update("DELETE FROM contract_templates WHERE id = ?::uuid", UUID.fromString(templateId));
    }

    @Test
    void renewContracts_extendsAutoRenewExpiring() throws Exception {
        String admin = adminBearer();
        ResponseEntity<String> ct = restTemplate.exchange(url("/api/financial/contracts"), HttpMethod.POST,
            jsonJwtEntity(Map.of("restaurantId", restaurantId(), "contractNumber", "L4-RN-" + rand(),
                "commissionRate", 3.0, "startsAt", LocalDate.now().minusMonths(11).toString(),
                "endsAt", LocalDate.now().plusDays(10).toString(),
                "details", Map.of("autoRenew", true, "dureeEngagementMois", 12)), admin), String.class);
        String contractId = om.readTree(ct.getBody()).get("id").asText();
        String endsBefore = om.readTree(ct.getBody()).get("endsAt").asText();

        ResponseEntity<String> renew = restTemplate.exchange(url("/api/financial/contracts/renew"), HttpMethod.POST, jwtEntity(admin), String.class);
        assertThat(renew.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(om.readTree(renew.getBody()).get("renewed").asInt()).isGreaterThanOrEqualTo(1);

        ResponseEntity<String> get = restTemplate.exchange(url("/api/financial/contracts/" + contractId), HttpMethod.GET, jwtEntity(admin), String.class);
        assertThat(om.readTree(get.getBody()).get("endsAt").asText()).isGreaterThan(endsBefore); // échéance repoussée (ISO comparable)
        assertThat(om.readTree(get.getBody()).get("renewalNumber").asInt()).isGreaterThanOrEqualTo(1);

        jdbc.update("DELETE FROM contracts WHERE id = ?::uuid", UUID.fromString(contractId));
    }
}
