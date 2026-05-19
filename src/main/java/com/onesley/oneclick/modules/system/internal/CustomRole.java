package com.onesley.oneclick.modules.system.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.Size;

/**
 * Rôle personnalisé admin — page {@code GestionRoles}.
 *
 * <p>Distinct des 5 {@code roles} applicatifs fixes (CLIENT/STAFF/RESTAURATEUR/
 * GROUP_ADMIN/SUPERADMIN) : un {@code custom_role} est un faisceau nommé de
 * permissions défini à la volée par un admin. Pas de duplication — les codes de
 * permission référencés vivent dans le catalogue {@code permissions}.
 */
@Entity
@Table(name = "custom_roles")
@Getter
public class CustomRole extends TimestampedEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @NotBlank
    @Column(name = "name", nullable = false)
    @Setter @Size(max = 255) private String name;

    @Column(name = "description")
    @Setter @Size(max = 2000) private String description;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "permissions", columnDefinition = "text[]", nullable = false)
    @Setter private String[] permissions = new String[0];

    @Column(name = "created_by")
    @Setter private UUID createdBy;
}
