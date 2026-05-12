package com.onesley.oneclick.modules.loyalty.api;

import java.math.BigDecimal;

/**
 * DTO de sortie pour {@code POST /api/loyalty/ocr-receipt}.
 *
 * <p>Si {@code app.ocr.space.api-key} n'est pas configurée, le service renvoie
 * {@code amountDetected=null, confidence=0, ocrText="(OCR not configured)"}
 * + log warn.
 *
 * @param ocrText        Texte brut extrait par OCR (vide si stub mode)
 * @param amountDetected Montant détecté via regex (peut être {@code null} si pas trouvé)
 * @param confidence     {@code high} / {@code medium} / {@code low} / {@code none}
 * @param matchedPattern Nom du pattern qui a matché (ex: {@code TOTAL TTC}) — {@code null} si pas trouvé
 */
public record OcrReceiptResultDto(
    String ocrText,
    BigDecimal amountDetected,
    String confidence,
    String matchedPattern
) {
}
