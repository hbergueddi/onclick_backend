package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.modules.loyalty.api.OcrReceiptRequestDto;
import com.onesley.oneclick.modules.loyalty.api.OcrReceiptResultDto;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de {@link OcrReceiptService} (L3 — modules.loyalty).
 * Cœur testé : {@code extractMontantFromText} (extraction montant par regex, pur)
 * + ocr() en mode stub (clé absente) et chemin d'erreur RestClient.
 */
class OcrReceiptServiceTest {

    private static OcrReceiptService.AmountMatch extract(String text) {
        return OcrReceiptService.extractMontantFromText(text);
    }

    // ─── extractMontantFromText : patterns prioritaires ─────────────────────────

    @Test
    void extract_netAPayer_high() {
        var m = extract("Article x 10,00\nNET A PAYER : 150,00");
        assertThat(m.amount()).isEqualByComparingTo("150.00");
        assertThat(m.confidence()).isEqualTo("high");
        assertThat(m.pattern()).isEqualTo("NET A PAYER");
    }

    @Test
    void extract_totalTtc_high() {
        assertThat(extract("TOTAL TTC: 89,56").amount()).isEqualByComparingTo("89.56");
    }

    @Test
    void extract_montantTotal_high() {
        assertThat(extract("MONTANT TOTAL 200,00").pattern()).isEqualTo("MONTANT TOTAL");
    }

    @Test
    void extract_cbCarte_high() {
        var m = extract("CB 120,50");
        assertThat(m.amount()).isEqualByComparingTo("120.50");
        assertThat(m.pattern()).isEqualTo("CB/CARTE");
    }

    @Test
    void extract_aPayer_high() {
        assertThat(extract("A PAYER 75,00").pattern()).isEqualTo("A PAYER");
    }

    @Test
    void extract_totalSimple_medium_withExclusion() {
        // La ligne "TOTAL HORS TAXE" est exclue, "TOTAL : 60,00" gagne.
        var m = extract("TOTAL HORS TAXE : 40,00\nTOTAL : 60,00");
        assertThat(m.amount()).isEqualByComparingTo("60.00");
        assertThat(m.confidence()).isEqualTo("medium");
    }

    @Test
    void extract_madDh_medium() {
        assertThat(extract("150,00 MAD").pattern()).isEqualTo("AMOUNT MAD/DH");
    }

    @Test
    void extract_somme_medium() {
        assertThat(extract("SOMME 30,00").amount()).isEqualByComparingTo("30.00");
    }

    @Test
    void extract_tvaPercentLine_skipped_yieldsNone() {
        // Ligne avec % uniquement (TVA) -> exclue partout -> aucun montant.
        assertThat(extract("TVA 20% 18,00").amount()).isNull();
    }

    @Test
    void extract_fallbackLastAmount_low() {
        var m = extract("Article 1 5,00\nArticle 2 12,50");
        assertThat(m.amount()).isEqualByComparingTo("12.50");
        assertThat(m.confidence()).isEqualTo("low");
        assertThat(m.pattern()).isEqualTo("LAST_AMOUNT");
    }

    @Test
    void extract_nullOrBlank_none() {
        assertThat(extract(null).confidence()).isEqualTo("none");
        assertThat(extract("   ").confidence()).isEqualTo("none");
        assertThat(extract("aucun montant ici").amount()).isNull();
    }

    // ─── ocr() : stub mode + erreur RestClient ──────────────────────────────────

    @Test
    void ocr_noApiKey_returnsStub() {
        OcrReceiptService svc = new OcrReceiptService("", "https://ocr/parse", "fre");
        OcrReceiptResultDto r = svc.ocr(new OcrReceiptRequestDto("https://img/x.png", null));
        assertThat(r.ocrText()).isEqualTo("(OCR not configured)");
        assertThat(r.amountDetected()).isNull();
        assertThat(r.confidence()).isEqualTo("none");
    }

    @Test
    void ocr_restClientThrows_returnsErrorDto() {
        OcrReceiptService svc = new OcrReceiptService("KEY", "https://ocr/parse", "fre");
        RestClient failing = mock(RestClient.class);
        when(failing.post()).thenThrow(new RestClientException("boom"));
        ReflectionTestUtils.setField(svc, "restClient", failing);

        OcrReceiptResultDto r = svc.ocr(new OcrReceiptRequestDto("https://img/x.png", "eng"));
        assertThat(r.ocrText()).contains("OCR call failed");
        assertThat(r.amountDetected()).isNull();
        assertThat(r.confidence()).isEqualTo("none");
    }
}
