package com.onesley.oneclick.modules.loyalty.api;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO d'entrée pour {@code POST /api/loyalty/ocr-receipt}.
 *
 * <p>Porté depuis l'Edge Function Supabase legacy {@code ocr-receipt}. Le legacy
 * uploadait aussi l'image base64 dans {@code ticket-photos} Storage avant l'OCR ;
 * dans la V1 Spring on délègue l'upload au module {@code core/media} (MinIO/S3)
 * côté frontend — ce endpoint ne fait QUE l'appel OCR.space sur une URL publique.
 *
 * @param imageUrl URL publique HTTPS de la photo (typiquement S3/MinIO presigned)
 * @param language code OCR.space — {@code fre} par défaut (cf {@code application.yml})
 */
public record OcrReceiptRequestDto(
    @NotBlank String imageUrl,
    String language
) {
}
