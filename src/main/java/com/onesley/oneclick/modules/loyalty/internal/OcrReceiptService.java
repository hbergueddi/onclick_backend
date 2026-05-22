package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.modules.loyalty.api.OcrReceiptRequestDto;
import com.onesley.oneclick.modules.loyalty.api.OcrReceiptResultDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * Wrapper sur l'API OCR.space — port de l'Edge Function Supabase {@code ocr-receipt}.
 *
 * <p>Si {@code app.ocr.space.api-key} n'est pas définie (cas tests / CI sans secret),
 * le service tombe en mode stub : log warn + retour {@code amountDetected=null}.
 *
 * <p>Le legacy uploadait l'image base64 dans Storage {@code ticket-photos} puis
 * envoyait base64 à OCR.space. Ici, on accepte directement une URL publique
 * (typiquement presigned S3/MinIO via {@code core/media}) et on utilise
 * l'endpoint OCR.space {@code /parse/imageurl}.
 */
@Service
@Slf4j
public class OcrReceiptService {

    private final String apiKey;
    private final String endpoint;
    private final String defaultLanguage;
    private final RestClient restClient;

    public OcrReceiptService(
        @Value("${app.ocr.space.api-key:}") String apiKey,
        @Value("${app.ocr.space.endpoint:https://api.ocr.space/parse/imageurl}") String endpoint,
        @Value("${app.ocr.space.language:fre}") String defaultLanguage
    ) {
        this.apiKey = apiKey;
        this.endpoint = endpoint;
        this.defaultLanguage = defaultLanguage;
        this.restClient = RestClient.builder().build();
    }

    /**
     * Appelle OCR.space sur l'URL fournie et extrait le montant via regex.
     *
     * <p>Mode stub si {@code apiKey} vide : log warn + retour DTO null-safe.
     */
    public OcrReceiptResultDto ocr(OcrReceiptRequestDto dto) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[ocr-receipt] OCR_SPACE_API_KEY non configurée — réponse stub renvoyée");
            return new OcrReceiptResultDto("(OCR not configured)", null, "none", null);
        }

        String language = dto.language() != null && !dto.language().isBlank()
            ? dto.language()
            : defaultLanguage;

        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("url", dto.imageUrl());
            form.add("language", language);
            form.add("isOverlayRequired", "false");
            form.add("detectOrientation", "true");
            form.add("scale", "true");
            form.add("OCREngine", "2");

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                .uri(endpoint)
                .header("apikey", apiKey)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);

            String parsedText = extractParsedText(response);
            AmountMatch match = extractMontantFromText(parsedText);
            return new OcrReceiptResultDto(parsedText, match.amount, match.confidence, match.pattern);

        } catch (RestClientException e) {
            log.error("[ocr-receipt] Appel OCR.space en échec — {}", e.getMessage());
            return new OcrReceiptResultDto("(OCR call failed: " + e.getMessage() + ")", null, "none", null);
        }
    }

    @SuppressWarnings("unchecked")
    static String extractParsedText(Map<String, Object> response) {
        if (response == null) return "";
        Object parsedResults = response.get("ParsedResults");
        if (!(parsedResults instanceof List<?> list) || list.isEmpty()) return "";
        Object first = list.get(0);
        if (!(first instanceof Map<?, ?> map)) return "";
        Object text = ((Map<String, Object>) map).get("ParsedText");
        return text != null ? text.toString() : "";
    }

    /**
     * Extraction du montant via regex (port direct de l'Edge Function legacy).
     *
     * <p>Patterns ordonnés par priorité (most specific → least specific). Chaque
     * ligne est testée séparément pour éviter les cross-line matches.
     */
    static AmountMatch extractMontantFromText(String text) {
        if (text == null || text.isBlank()) {
            return new AmountMatch(null, "none", null);
        }
        String normalized = text
            .replace("\r\n", "\n")
            .replaceAll("[ \\t]+", " ")
            .toUpperCase();
        List<String> lines = normalized.lines()
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();

        Pattern excludeTotal = Pattern.compile("TOTAL\\s*(HORS|AVANT|TVA|HT|REMISE|ARTICLE|PRODUIT)");
        Pattern excludeMontant = Pattern.compile("MONTANT\\s*(HORS|HT|REMISE)");
        Pattern percentTva = Pattern.compile("%");
        Pattern tvaLine = Pattern.compile("TVA|TAX");

        OcrPattern[] patterns = {
            new OcrPattern(Pattern.compile("NET\\s*[AÀ]\\s*PAYER\\s*[:\\-=]?\\s*(\\d+[\\s.,]\\d{2})"), "NET A PAYER", "high", null),
            new OcrPattern(Pattern.compile("TOTAL\\s*TTC\\s*[:\\-=]?\\s*(\\d+[\\s.,]\\d{2})"), "TOTAL TTC", "high", null),
            new OcrPattern(Pattern.compile("MONTANT\\s*TOTAL\\s*[:\\-=]?\\s*(\\d+[\\s.,]\\d{2})"), "MONTANT TOTAL", "high", null),
            new OcrPattern(Pattern.compile("(?:CB|CARTE|CARD|PAIEMENT)\\s*[:\\-=]?\\s*(\\d+[\\s.,]\\d{2})"), "CB/CARTE", "high", null),
            new OcrPattern(Pattern.compile("[AÀ]\\s*PAYER\\s*[:\\-=]?\\s*(\\d+[\\s.,]\\d{2})"), "A PAYER", "high", null),
            new OcrPattern(Pattern.compile("^TOTAL\\s*[:\\-=]?\\s*(\\d+[\\s.,]\\d{2})"), "TOTAL", "medium", excludeTotal),
            new OcrPattern(Pattern.compile("(\\d+[\\s.,]\\d{2})\\s*(?:MAD|DH|DHS)"), "AMOUNT MAD/DH", "medium", null),
            new OcrPattern(Pattern.compile("MONTANT\\s*[:\\-=]?\\s*(\\d+[\\s.,]\\d{2})"), "MONTANT", "medium", excludeMontant),
            new OcrPattern(Pattern.compile("SOMME\\s*[:\\-=]?\\s*(\\d+[\\s.,]\\d{2})"), "SOMME", "medium", null),
        };

        for (OcrPattern p : patterns) {
            for (String line : lines) {
                if (percentTva.matcher(line).find() && tvaLine.matcher(line).find()) continue;
                if (p.exclude != null && p.exclude.matcher(line).find()) continue;
                Matcher m = p.regex.matcher(line);
                if (m.find()) {
                    BigDecimal amount = parseAmount(m.group(1));
                    if (amount != null) {
                        return new AmountMatch(amount, p.confidence, p.label);
                    }
                }
            }
        }

        // Fallback : dernier montant trouvé hors lignes TVA/REMISE
        Pattern fallback = Pattern.compile("(\\d+[.,]\\d{2})");
        Pattern excludeFallback = Pattern.compile("TVA|TAX|REMISE|AVANTAGE");
        BigDecimal lastAmount = null;
        for (String line : lines) {
            if (percentTva.matcher(line).find()) continue;
            if (excludeFallback.matcher(line).find()) continue;
            Matcher m = fallback.matcher(line);
            while (m.find()) {
                BigDecimal a = parseAmount(m.group(1));
                if (a != null && a.compareTo(BigDecimal.ONE) > 0) {
                    lastAmount = a;
                }
            }
        }
        if (lastAmount != null) {
            return new AmountMatch(lastAmount, "low", "LAST_AMOUNT");
        }
        return new AmountMatch(null, "none", null);
    }

    private static BigDecimal parseAmount(String raw) {
        String cleaned = raw.replaceAll("\\s", "").replace(",", ".");
        try {
            BigDecimal value = new BigDecimal(cleaned);
            if (value.compareTo(BigDecimal.ZERO) > 0 && value.compareTo(new BigDecimal("1000000")) < 0) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // fallthrough
        }
        return null;
    }

    private record OcrPattern(Pattern regex, String label, String confidence, Pattern exclude) {}

    record AmountMatch(BigDecimal amount, String confidence, String pattern) {}
}
