package com.onesley.oneclick.core.identity.api;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.validation.constraints.Size;

/**
 * Entrée de menu applicatif (sidebar). Hiérarchique via {@code parent_id}.
 */
@Entity
@Table(name = "menus")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Menu extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotBlank
    @Column(name = "code", nullable = false, unique = true)
    @Size(max = 255) private String code;

    @NotBlank
    @Column(name = "name", nullable = false)
    @Size(max = 255) private String name;

    @Column(name = "icon")
    @Setter @Size(max = 1024) private String icon;

    @Column(name = "path")
    @Setter @Size(max = 512) private String path;

    // Self-reference parent — LAZY pour pouvoir naviguer l'arbre côté service
    @Column(name = "parent_id", insertable = false, updatable = false)
    private UUID parentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    @Setter private Menu parent;

    @Column(name = "sort_order", nullable = false)
    @Setter private Integer sortOrder = 0;

    public Menu(UUID id, String code, String name) {
        this.id = id;
        this.code = code;
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffective = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffective = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffective != oEffective) return false;
        Menu that = (Menu) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p
            ? p.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
