package com.onesley.oneclick.entity.contract;

import com.onesley.oneclick.audit.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Entité {@code public.partner_contracts} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : audit niveau 1 (4 colonnes).
 */
@Entity
@Table(name = "partner_contracts")
public class PartnerContract extends AuditedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "commission_rate", nullable = false)
    private BigDecimal commissionRate;

    @Column(name = "contract_start", nullable = false)
    private LocalDate contractStart;

    @Column(name = "contract_end", nullable = false)
    private LocalDate contractEnd;

    @Column(name = "payment_terms", nullable = false)
    private String paymentTerms;

    @Column(name = "auto_renew", nullable = false)
    private Boolean autoRenew;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "represented_by")
    private String representedBy;

    @Column(name = "represented_title")
    private String representedTitle;

    @Column(name = "restaurant_address")
    private String restaurantAddress;

    @Column(name = "restaurant_city")
    private String restaurantCity;

    @Column(name = "restaurant_phone")
    private String restaurantPhone;

    @Column(name = "client_commission_rate", nullable = false)
    private BigDecimal clientCommissionRate;

    @Column(name = "wallet_admin_rate", nullable = false)
    private BigDecimal walletAdminRate;

    @Column(name = "oneclick_commission_rate", nullable = false)
    private BigDecimal oneclickCommissionRate;

    @Column(name = "template_id")
    private UUID templateId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contract_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> contractSnapshot = new HashMap<>();

    @Column(name = "signed_at")
    private Instant signedAt;

    @Column(name = "signed_by")
    private String signedBy;

    @Column(name = "contract_number")
    private String contractNumber;

    @Column(name = "expiration_notified_at")
    private Instant expirationNotifiedAt;

    @Column(name = "raison_sociale")
    private String raisonSociale;

    @Column(name = "forme_juridique")
    private String formeJuridique;

    @Column(name = "numero_rc")
    private String numeroRc;

    @Column(name = "numero_if")
    private String numeroIf;

    @Column(name = "numero_ice")
    private String numeroIce;

    @Column(name = "capital_social")
    private String capitalSocial;

    @Column(name = "banque")
    private String banque;

    @Column(name = "rib")
    private String rib;

    @Column(name = "capacite_couverts")
    private Integer capaciteCouverts;

    @Column(name = "horaires_exploitation")
    private String horairesExploitation;

    @Column(name = "jours_fermeture")
    private String joursFermeture;

    @Column(name = "duree_engagement_mois")
    private Integer dureeEngagementMois;

    @Column(name = "preavis_resiliation_mois")
    private Integer preavisResiliationMois;

    @Column(name = "penalite_resiliation")
    private BigDecimal penaliteResiliation;

    @Column(name = "plafond_commission_mensuel")
    private BigDecimal plafondCommissionMensuel;

    @Column(name = "lieu_signature")
    private String lieuSignature;

    @Column(name = "nombre_exemplaires")
    private Integer nombreExemplaires;

    @Column(name = "restaurant_name")
    private String restaurantName;

    @Column(name = "parent_contract_id")
    private UUID parentContractId;

    @Column(name = "renewal_number")
    private Integer renewalNumber;

    @Column(name = "expiration_notified_30d_at")
    private Instant expirationNotified30dAt;

    @Column(name = "expiration_notified_15d_at")
    private Instant expirationNotified15dAt;

    @Column(name = "expiration_notified_7d_at")
    private Instant expirationNotified7dAt;

    protected PartnerContract() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getRestaurantId() { return restaurantId; }
    public BigDecimal getCommissionRate() { return commissionRate; }
    public LocalDate getContractStart() { return contractStart; }
    public LocalDate getContractEnd() { return contractEnd; }
    public String getPaymentTerms() { return paymentTerms; }
    public Boolean getAutoRenew() { return autoRenew; }
    public String getStatus() { return status; }
    public String getRepresentedBy() { return representedBy; }
    public String getRepresentedTitle() { return representedTitle; }
    public String getRestaurantAddress() { return restaurantAddress; }
    public String getRestaurantCity() { return restaurantCity; }
    public String getRestaurantPhone() { return restaurantPhone; }
    public BigDecimal getClientCommissionRate() { return clientCommissionRate; }
    public BigDecimal getWalletAdminRate() { return walletAdminRate; }
    public BigDecimal getOneclickCommissionRate() { return oneclickCommissionRate; }
    public UUID getTemplateId() { return templateId; }
    public Map<String, Object> getContractSnapshot() { return contractSnapshot; }
    public Instant getSignedAt() { return signedAt; }
    public String getSignedBy() { return signedBy; }
    public String getContractNumber() { return contractNumber; }
    public Instant getExpirationNotifiedAt() { return expirationNotifiedAt; }
    public String getRaisonSociale() { return raisonSociale; }
    public String getFormeJuridique() { return formeJuridique; }
    public String getNumeroRc() { return numeroRc; }
    public String getNumeroIf() { return numeroIf; }
    public String getNumeroIce() { return numeroIce; }
    public String getCapitalSocial() { return capitalSocial; }
    public String getBanque() { return banque; }
    public String getRib() { return rib; }
    public Integer getCapaciteCouverts() { return capaciteCouverts; }
    public String getHorairesExploitation() { return horairesExploitation; }
    public String getJoursFermeture() { return joursFermeture; }
    public Integer getDureeEngagementMois() { return dureeEngagementMois; }
    public Integer getPreavisResiliationMois() { return preavisResiliationMois; }
    public BigDecimal getPenaliteResiliation() { return penaliteResiliation; }
    public BigDecimal getPlafondCommissionMensuel() { return plafondCommissionMensuel; }
    public String getLieuSignature() { return lieuSignature; }
    public Integer getNombreExemplaires() { return nombreExemplaires; }
    public String getRestaurantName() { return restaurantName; }
    public UUID getParentContractId() { return parentContractId; }
    public Integer getRenewalNumber() { return renewalNumber; }
    public Instant getExpirationNotified30dAt() { return expirationNotified30dAt; }
    public Instant getExpirationNotified15dAt() { return expirationNotified15dAt; }
    public Instant getExpirationNotified7dAt() { return expirationNotified7dAt; }
}
