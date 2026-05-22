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

    @Test
    void contract_crud() throws Exception {
        String admin = adminBearer();
        assertThat(restTemplate.exchange(url("/api/financial/contracts?page=0&size=5"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> post = restTemplate.exchange(url("/api/financial/contracts"), HttpMethod.POST,
            jsonJwtEntity(Map.of("restaurantId", restaurantId(), "contractNumber", "L4-C-" + rand(),
                "commissionRate", 3.0, "startsAt", LocalDate.now().toString(), "endsAt", LocalDate.now().plusYears(1).toString()), admin), String.class);
        assertThat(post.getStatusCode().is2xxSuccessful()).isTrue();
        String id = om.readTree(post.getBody()).get("id").asText();
        assertThat(restTemplate.exchange(url("/api/financial/contracts/" + id), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/financial/contracts/" + id), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("commissionRate", 4.5), admin), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        jdbc.update("DELETE FROM contracts WHERE id = ?::uuid", UUID.fromString(id)); // self-clean (pas d'endpoint DELETE)
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
}
