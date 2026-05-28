package com.onesley.oneclick.modules.financial.internal;

import com.onesley.oneclick.audit.SoftDeletableAuditedEntity;
import com.onesley.oneclick.modules.financial.api.FinancialDtos.ContractDto;
import jakarta.persistence.*;
import org.hibernate.proxy.HibernateProxy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Contrat partenaire — commission_rate par restaurant. */
@Entity
@Table(name = "contracts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Contract extends SoftDeletableAuditedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "contract_number", nullable = false, unique = true, length = 64)
    private String contractNumber;

    @Column(name = "commission_rate", nullable = false, precision = 5, scale = 2)
    @Setter private BigDecimal commissionRate;

    /** Taux (%) reversé au wallet admin OneClick — par contrat (V49). Défaut plateforme 2.00. */
    @Column(name = "wallet_admin_rate", nullable = false, precision = 5, scale = 2)
    @Setter private BigDecimal walletAdminRate = new BigDecimal("2.00");

    @Column(name = "starts_at", nullable = false)
    private LocalDate startsAt;

    @Column(name = "ends_at")
    @Setter private LocalDate endsAt;

    @Column(name = "status", nullable = false, length = 64)
    @Setter private String status = "active";

    // ─── V54 — champs du contrat partenaire legacy (tous nullable) ───────────
    @Column(name = "represented_by", length = 128) @Setter private String representedBy;
    @Column(name = "represented_title", length = 64) @Setter private String representedTitle;
    @Column(name = "oneclick_commission_rate", precision = 5, scale = 2) @Setter private BigDecimal oneclickCommissionRate;
    @Column(name = "payment_terms", length = 256) @Setter private String paymentTerms;
    @Column(name = "plafond_commission_mensuel", precision = 12, scale = 2) @Setter private BigDecimal plafondCommissionMensuel;
    @Column(name = "auto_renew", nullable = false) @Setter private boolean autoRenew = false;
    @Column(name = "signed_at") @Setter private Instant signedAt;
    @Column(name = "renewal_number", nullable = false) @Setter private int renewalNumber = 0;
    @Column(name = "raison_sociale", length = 128) @Setter private String raisonSociale;
    @Column(name = "forme_juridique", length = 64) @Setter private String formeJuridique;
    @Column(name = "numero_rc", length = 64) @Setter private String numeroRc;
    @Column(name = "numero_if", length = 64) @Setter private String numeroIf;
    @Column(name = "numero_ice", length = 64) @Setter private String numeroIce;
    @Column(name = "capital_social", length = 64) @Setter private String capitalSocial;
    @Column(name = "banque", length = 128) @Setter private String banque;
    @Column(name = "rib", length = 64) @Setter private String rib;
    @Column(name = "capacite_couverts") @Setter private Integer capaciteCouverts;
    @Column(name = "horaires_exploitation", length = 256) @Setter private String horairesExploitation;
    @Column(name = "jours_fermeture", length = 256) @Setter private String joursFermeture;
    @Column(name = "duree_engagement_mois") @Setter private Integer dureeEngagementMois;
    @Column(name = "preavis_resiliation_mois") @Setter private Integer preavisResiliationMois;
    @Column(name = "penalite_resiliation", precision = 12, scale = 2) @Setter private BigDecimal penaliteResiliation;
    @Column(name = "lieu_signature", length = 128) @Setter private String lieuSignature;
    @Column(name = "nombre_exemplaires") @Setter private Integer nombreExemplaires;

    public Contract(UUID id, UUID restaurantId, String contractNumber, BigDecimal commissionRate, LocalDate startsAt) {
        this.id = id;
        this.restaurantId = restaurantId;
        this.contractNumber = contractNumber;
        this.commissionRate = commissionRate;
        this.startsAt = startsAt;
    }

    /** Mapping vers le DTO public exposé hors du module. */
    public ContractDto toDto() {
        return new ContractDto(id, restaurantId, contractNumber, commissionRate, walletAdminRate,
            startsAt, endsAt, status, getCreatedAt(),
            representedBy, representedTitle, oneclickCommissionRate, paymentTerms, plafondCommissionMensuel,
            autoRenew, signedAt, renewalNumber, raisonSociale, formeJuridique,
            numeroRc, numeroIf, numeroIce, capitalSocial, banque, rib,
            capaciteCouverts, horairesExploitation, joursFermeture, dureeEngagementMois,
            preavisResiliationMois, penaliteResiliation, lieuSignature, nombreExemplaires);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        return id != null && Objects.equals(id, ((Contract) o).id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
