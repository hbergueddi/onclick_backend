package com.onesley.oneclick.modules.loyalty.api;

import java.util.Map;

/**
 * DTOs Sprint I.2 — Wallet pass (Apple .pkpass + Google Wallet save-url).
 *
 * <p>Le pass Apple est livré en binaire ({@code application/vnd.apple.pkpass}),
 * le pass Google sous forme JSON {@code { save_url, jwt }} consommable par
 * l'Android Wallet.
 */
public final class WalletPassDtos {

    private WalletPassDtos() {}

    /** Métadonnées du pass : tier, points, nom, etc. */
    public record WalletPassMetadataDto(
        String userId,
        String firstName,
        String lastName,
        String tier,
        Integer totalPoints,
        String serialNumber,
        Map<String, Object> extra
    ) {}

    /** Réponse Google Wallet : URL "Add to Wallet" + JWT signé. */
    public record GoogleWalletResponseDto(
        String saveUrl,
        String jwt,
        Long expiresAt
    ) {}
}
