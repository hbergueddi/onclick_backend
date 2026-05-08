package com.onesley.oneclick.entity.contract;

import com.onesley.oneclick.audit.AuditedEntity;
import com.onesley.oneclick.entity.restaurant.Restaurant;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.type.SqlTypes;

/**
 * Entité {@code public.partner_contracts} — contrat de partenariat entre un
 * restaurant et OneClick (3 taux + plafond + identité légale + clauses).
 *
 * <h3>Jointures JPA (passe 3) — aggregate root</h3>
 * <ul>
 *   <li>{@code restaurant_id NOT NULL} → {@link Restaurant} en {@code @ManyToOne(LAZY)}, optional=false.</li>
 *   <li>{@code template_id} → {@link ContractTemplate} en {@code @ManyToOne(LAZY)}, nullable
 *       (les contrats anciens peuvent ne pas avoir de template).</li>
 *   <li>{@code parent_contract_id} → self-ref en {@code @ManyToOne(LAZY)}, nullable
 *       (chaîne de renouvellements).</li>
 *   <li>{@code @OneToMany history} (ContractHistory) cascade {PERSIST, MERGE} +
 *       @BatchSize(50) — audit interne du contrat (volume ~20).</li>
 *   <li>{@code @OneToMany disabledArticles} (ContractDisabledArticle) cascade ALL +
 *       orphanRemoval (opt-out par contrat, lié strictement).</li>
 *   <li>Pas de {@code @OneToMany invoices} — volume non borné (24+ mois),
 *       repository paginé à la place.</li>
 * </ul>
 *
 * <p>{@code @DynamicUpdate} : 45+ colonnes, évite UPDATE complet à chaque save.
 */
@Entity
@Table(name = "partner_contracts")
@DynamicUpdate
public class PartnerContract extends AuditedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false, insertable = false, updatable = false)
    private UUID restaurantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @NotNull
    @Column(name = "commission_rate", nullable = false)
    private BigDecimal commissionRate;

    @NotNull
    @Column(name = "contract_start", nullable = false)
    private LocalDate contractStart;

    @NotNull
    @Column(name = "contract_end", nullable = false)
    private LocalDate contractEnd;

    @NotBlank
    @Column(name = "payment_terms", nullable = false)
    private String paymentTerms;

    @NotNull
    @Column(name = "auto_renew", nullable = false)
    private Boolean autoRenew;

    @NotBlank
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

    @NotNull
    @Column(name = "client_commission_rate", nullable = false)
    private BigDecimal clientCommissionRate;

    @NotNull
    @Column(name = "wallet_admin_rate", nullable = false)
    private BigDecimal walletAdminRate;

    @NotNull
    @Column(name = "oneclick_commission_rate", nullable = false)
    private BigDecimal oneclickCommissionRate;

    @Column(name = "template_id", insertable = false, updatable = false)
    private UUID templateId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private ContractTemplate template;

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

    // ─── Self-reference parent_contract_id (chaîne de renouvellements) ──────
    @Column(name = "parent_contract_id", insertable = false, updatable = false)
    private UUID parentContractId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_contract_id")
    private PartnerContract parentContract;

    @Column(name = "renewal_number")
    private Integer renewalNumber;

    @Column(name = "expiration_notified_30d_at")
    private Instant expirationNotified30dAt;

    @Column(name = "expiration_notified_15d_at")
    private Instant expirationNotified15dAt;

    @Column(name = "expiration_notified_7d_at")
    private Instant expirationNotified7dAt;

    // ─── Aggregate members ──────────────────────────────────────────────────
    @OneToMany(mappedBy = "contract", fetch = FetchType.LAZY,
               cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    @BatchSize(size = 50)
    private Set<ContractHistory> history = new HashSet<>();

    @OneToMany(mappedBy = "contract", fetch = FetchType.LAZY,
               cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private Set<ContractDisabledArticle> disabledArticles = new HashSet<>();

    protected PartnerContract() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getRestaurantId() { return restaurantId; }
    public Restaurant getRestaurant() { return restaurant; }
    public void setRestaurant(Restaurant restaurant) { this.restaurant = restaurant; }
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
    public ContractTemplate getTemplate() { return template; }
    public void setTemplate(ContractTemplate template) { this.template = template; }
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
    public PartnerContract getParentContract() { return parentContract; }
    public void setParentContract(PartnerContract parentContract) { this.parentContract = parentContract; }
    public Integer getRenewalNumber() { return renewalNumber; }
    public Instant getExpirationNotified30dAt() { return expirationNotified30dAt; }
    public Instant getExpirationNotified15dAt() { return expirationNotified15dAt; }
    public Instant getExpirationNotified7dAt() { return expirationNotified7dAt; }
    public Set<ContractHistory> getHistory() { return history; }
    public Set<ContractDisabledArticle> getDisabledArticles() { return disabledArticles; }

    public void addHistory(ContractHistory h) {
        history.add(h);
        h.setContract(this);
    }

    public void addDisabledArticle(ContractDisabledArticle a) {
        disabledArticles.add(a);
        a.setContract(this);
    }

    public void removeDisabledArticle(ContractDisabledArticle a) {
        disabledArticles.remove(a);
        a.setContract(null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass()
            : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass()
            : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        PartnerContract that = (PartnerContract) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
