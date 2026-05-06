package com.onesley.oneclick.entity.support;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entité {@code public.client_score_config} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : entité simple.
 */
@Entity
@Table(name = "client_score_config")
public class ClientScoreConfig {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "min_reservations", nullable = false)
    private Integer minReservations;

    @Column(name = "seuil_excellent", nullable = false)
    private BigDecimal seuilExcellent;

    @Column(name = "seuil_fiable", nullable = false)
    private BigDecimal seuilFiable;

    @Column(name = "seuil_moyen", nullable = false)
    private BigDecimal seuilMoyen;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "score_initial", nullable = false)
    private BigDecimal scoreInitial;

    @Column(name = "penalite_no_show", nullable = false)
    private BigDecimal penaliteNoShow;

    @Column(name = "honorees_pour_remonter", nullable = false)
    private Integer honoreesPourRemonter;

    @Column(name = "gain_par_palier", nullable = false)
    private BigDecimal gainParPalier;

    @Column(name = "fenetre_mois", nullable = false)
    private Integer fenetreMois;

    protected ClientScoreConfig() {
        // JPA
    }

    public UUID getId() { return id; }
    public Integer getMinReservations() { return minReservations; }
    public BigDecimal getSeuilExcellent() { return seuilExcellent; }
    public BigDecimal getSeuilFiable() { return seuilFiable; }
    public BigDecimal getSeuilMoyen() { return seuilMoyen; }
    public Instant getUpdatedAt() { return updatedAt; }
    public BigDecimal getScoreInitial() { return scoreInitial; }
    public BigDecimal getPenaliteNoShow() { return penaliteNoShow; }
    public Integer getHonoreesPourRemonter() { return honoreesPourRemonter; }
    public BigDecimal getGainParPalier() { return gainParPalier; }
    public Integer getFenetreMois() { return fenetreMois; }
}
