package com.onesley.oneclick.entity.status;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;
import java.util.UUID;

/**
 * Entité {@code public.entity_statuses} — table de référence centralisée des
 * statuts métier (retour senior dev, mai 2026).
 *
 * <h3>Pattern : table de lookup polymorphique avec discriminator</h3>
 *
 * <p>Toutes les entités métier qui avaient un champ {@code String status}
 * pointent désormais vers une ligne de cette table via {@code @ManyToOne(LAZY)}.
 * Le discriminator {@link #entityType} garantit qu'un statut d'une entité X
 * ne peut être assigné qu'à une row de la table X — la cohérence est portée
 * par la couche service (lookup {@code findByEntityTypeAndCode}).
 *
 * <h3>Conventions de codes</h3>
 * <ul>
 *   <li><b>code</b> : français snake_case sans accent ({@code actif},
 *       {@code en_attente}, {@code approuvee}, {@code refusee}…).</li>
 *   <li><b>label_fr</b> : libellé humain FR avec accents et casse propre
 *       ({@code Approuvée}, {@code En attente}…).</li>
 *   <li><b>label_en</b> : libellé EN pour i18n (nullable, fallback sur label_fr).</li>
 *   <li><b>is_terminal</b> : true pour les statuts finaux (annulee, honoree,
 *       resolu…) — utilisé par l'UI pour distinguer les transitions possibles.</li>
 *   <li><b>is_active</b> : soft-delete. Une row désactivée reste référencée
 *       par les entités historiques mais n'apparaît plus dans les dropdowns.</li>
 * </ul>
 *
 * <h3>Pas de jointure inverse</h3>
 *
 * <p>Aucun {@code @OneToMany} inverse vers les entités métier — volume non
 * borné (un statut "actif" peut être référencé par 1042 restaurants, 16k staff…).
 * Repositories paginés à la place côté entités métier ({@code findByStatusCode}).
 *
 * <h3>Lombok (POC, mai 2026)</h3>
 * <p>Cette entité est le pilote Lombok du projet. Conventions :
 * <ul>
 *   <li>{@code @Getter} au niveau classe — génère les getters publics.</li>
 *   <li>{@code @Setter} granulaire au niveau field uniquement (les @Id et fields
 *       immuables — entityType, code — n'ont pas de setter).</li>
 *   <li>{@code @NoArgsConstructor(access = PROTECTED)} — remplace le
 *       {@code protected EntityStatus() {}} pour JPA.</li>
 *   <li><b>NE JAMAIS</b> utiliser {@code @Data} ou {@code @EqualsAndHashCode} :
 *       ils génèrent un equals/hashCode basé sur tous les fields, ce qui casse
 *       le pattern anti-proxy LAZY (cf. {@link #equals(Object)}).</li>
 *   <li><b>NE JAMAIS</b> utiliser {@code @Builder} sur entité JPA — Hibernate
 *       construit l'entité, pas un builder.</li>
 * </ul>
 */
@Entity
@Table(
    name = "entity_statuses",
    uniqueConstraints = @UniqueConstraint(
        name = "entity_statuses_entity_code_uq",
        columnNames = {"entity_type", "code"}
    )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EntityStatus extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotBlank
    @Column(name = "entity_type", nullable = false, updatable = false)
    private String entityType;

    @NotBlank
    @Column(name = "code", nullable = false, updatable = false)
    private String code;

    @NotBlank
    @Column(name = "label_fr", nullable = false)
    @Setter
    private String labelFr;

    @Column(name = "label_en")
    @Setter
    private String labelEn;

    @Column(name = "description")
    @Setter
    private String description;

    @NotNull
    @Column(name = "sort_order", nullable = false)
    @Setter
    private Integer sortOrder = 0;

    @NotNull
    @Column(name = "is_terminal", nullable = false)
    @Setter
    private Boolean isTerminal = false;

    @NotNull
    @Column(name = "is_active", nullable = false)
    @Setter
    private Boolean isActive = true;

    public EntityStatus(UUID id, String entityType, String code, String labelFr) {
        this.id = id;
        this.entityType = entityType;
        this.code = code;
        this.labelFr = labelFr;
    }

    /** Helper read-only : représentation courte pour logs / DTO. */
    public String fullCode() {
        return entityType + ":" + code;
    }

    // ─── equals / hashCode anti-proxy LAZY (manuel, NE PAS lomboker) ────────
    // @EqualsAndHashCode de Lombok plante avec les proxy LAZY car il compare
    // sur les fields, pas sur la classe effective derrière le proxy.

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
        EntityStatus that = (EntityStatus) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }

    @Override
    public String toString() {
        return "EntityStatus{" + entityType + ":" + code + "}";
    }
}
