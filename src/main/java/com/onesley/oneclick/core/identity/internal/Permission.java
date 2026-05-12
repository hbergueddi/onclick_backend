package com.onesley.oneclick.core.identity.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import com.onesley.oneclick.core.identity.api.Role;

/**
 * Junction {@code role × (menu | action)} — la liste des choses qu'un rôle peut faire/voir.
 *
 * <p>Contrainte DB : au moins un des deux ({@code menu_id} ou {@code action_id}) non null.
 * Convention : si {@code menu_id} seul → permission de voir l'item de menu.
 * Si {@code action_id} seul → permission d'exécuter l'action.
 * Si les deux → l'action est attachée à un item de menu spécifique.
 */
@Entity
@Table(
    name = "permissions",
    uniqueConstraints = @UniqueConstraint(columnNames = {"role_id", "menu_id", "action_id"})
)
@EntityListeners(AuditingEntityListener.class)
public class Permission {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "role_id", nullable = false, insertable = false, updatable = false)
    private UUID roleId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(name = "menu_id", insertable = false, updatable = false)
    private UUID menuId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_id")
    private Menu menu;

    @Column(name = "action_id", insertable = false, updatable = false)
    private UUID actionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "action_id")
    private Action action;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    protected Permission() {
        // JPA
    }

    public Permission(UUID id, Role role, Menu menu, Action action) {
        this.id = id;
        this.role = role;
        this.menu = menu;
        this.action = action;
    }

    public UUID getId() { return id; }
    public UUID getRoleId() { return roleId; }
    public Role getRole() { return role; }
    public UUID getMenuId() { return menuId; }
    public Menu getMenu() { return menu; }
    public UUID getActionId() { return actionId; }
    public Action getAction() { return action; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffective = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffective = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffective != oEffective) return false;
        Permission that = (Permission) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p
            ? p.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
