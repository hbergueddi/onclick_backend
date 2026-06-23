package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.modules.loyalty.api.LoyaltyExtensionDtos.ClientScoreConfigDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Configuration singleton du moteur de notation client (fiabilité) — V52.
 *
 * <p>Une seule ligne en base (seed par défaut). Pilote l'écran admin
 * "Notation client — Paramètres" (/reservations · onglet Scoring) : seuils de
 * classification (excellent/fiable/moyen) + règles de calcul (score initial,
 * pénalité no-show, honorées pour remonter, gain par palier, fenêtre glissante).
 */
@Entity
@Table(name = "client_score_config")
@Getter
public class ClientScoreConfig extends TimestampedEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "min_reservations", nullable = false)
    @Setter private Integer minReservations = 3;

    @Column(name = "seuil_excellent", nullable = false, precision = 5, scale = 2)
    @Setter private BigDecimal seuilExcellent = new BigDecimal("95");

    @Column(name = "seuil_fiable", nullable = false, precision = 5, scale = 2)
    @Setter private BigDecimal seuilFiable = new BigDecimal("80");

    @Column(name = "seuil_moyen", nullable = false, precision = 5, scale = 2)
    @Setter private BigDecimal seuilMoyen = new BigDecimal("60");

    @Column(name = "score_initial", nullable = false, precision = 3, scale = 1)
    @Setter private BigDecimal scoreInitial = new BigDecimal("5.0");

    @Column(name = "penalite_no_show", nullable = false, precision = 3, scale = 1)
    // Parité legacy (2026-06-20) : un no-show coûte 0.5 (et non 0.1). Avec scoreInitial=5.0,
    // un 1er no-show fait passer le client à 4.5 ; un honoré (+gainParPalier=0.1) le maintient à 5.0.
    @Setter private BigDecimal penaliteNoShow = new BigDecimal("0.5");

    @Column(name = "honorees_pour_remonter", nullable = false)
    @Setter private Integer honoreesPourRemonter = 5;

    @Column(name = "gain_par_palier", nullable = false, precision = 3, scale = 1)
    @Setter private BigDecimal gainParPalier = new BigDecimal("0.1");

    @Column(name = "fenetre_mois", nullable = false)
    @Setter private Integer fenetreMois = 6;

    public ClientScoreConfigDto toDto() {
        return new ClientScoreConfigDto(
            id, minReservations, seuilExcellent, seuilFiable, seuilMoyen,
            scoreInitial, penaliteNoShow, honoreesPourRemonter, gainParPalier,
            fenetreMois, getUpdatedAt());
    }
}
