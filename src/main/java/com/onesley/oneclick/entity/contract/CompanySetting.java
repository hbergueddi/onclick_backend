package com.onesley.oneclick.entity.contract;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Entité {@code public.company_settings} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at + updated_at hérités.
 */
@Entity
@Table(name = "company_settings")
public class CompanySetting extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "raison_sociale", nullable = false)
    private String raisonSociale;

    @Column(name = "forme_juridique")
    private String formeJuridique;

    @Column(name = "capital_social")
    private String capitalSocial;

    @Column(name = "adresse")
    private String adresse;

    @Column(name = "ville")
    private String ville;

    @Column(name = "pays")
    private String pays;

    @Column(name = "telephone")
    private String telephone;

    @Column(name = "email")
    private String email;

    @Column(name = "site_web")
    private String siteWeb;

    @Column(name = "numero_ice")
    private String numeroIce;

    @Column(name = "numero_if")
    private String numeroIf;

    @Column(name = "numero_rc")
    private String numeroRc;

    @Column(name = "numero_cnss")
    private String numeroCnss;

    @Column(name = "rib")
    private String rib;

    @Column(name = "banque")
    private String banque;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "tva_rate", nullable = false)
    private BigDecimal tvaRate;

    @Column(name = "payment_delay_days", nullable = false)
    private Integer paymentDelayDays;

    @Column(name = "invoice_prefix", nullable = false)
    private String invoicePrefix;

    @Column(name = "invoice_footer_text")
    private String invoiceFooterText;

    @Column(name = "numero_tp")
    private String numeroTp;

    protected CompanySetting() {
        // JPA
    }

    public UUID getId() { return id; }
    public String getRaisonSociale() { return raisonSociale; }
    public String getFormeJuridique() { return formeJuridique; }
    public String getCapitalSocial() { return capitalSocial; }
    public String getAdresse() { return adresse; }
    public String getVille() { return ville; }
    public String getPays() { return pays; }
    public String getTelephone() { return telephone; }
    public String getEmail() { return email; }
    public String getSiteWeb() { return siteWeb; }
    public String getNumeroIce() { return numeroIce; }
    public String getNumeroIf() { return numeroIf; }
    public String getNumeroRc() { return numeroRc; }
    public String getNumeroCnss() { return numeroCnss; }
    public String getRib() { return rib; }
    public String getBanque() { return banque; }
    public String getLogoUrl() { return logoUrl; }
    public BigDecimal getTvaRate() { return tvaRate; }
    public Integer getPaymentDelayDays() { return paymentDelayDays; }
    public String getInvoicePrefix() { return invoicePrefix; }
    public String getInvoiceFooterText() { return invoiceFooterText; }
    public String getNumeroTp() { return numeroTp; }
}
