package com.onesley.oneclick.modules.social.internal;

import com.onesley.oneclick.core.identity.api.User;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Journal d'un import de contacts (carnet d'adresses → matching OneClick) — V64.
 *
 * <p>1 ligne = 1 import déclenché par un user. Sert au <b>quota 10/jour/user</b>
 * enforcé par {@link SocialService#importContacts} : on compte les lignes du user
 * sur une fenêtre glissante de 24 h.
 *
 * <p>Table {@code contact_imports} : {@code created_at} uniquement (pas
 * d'{@code updated_at}/{@code deleted_at}), donc on n'hérite pas de
 * {@link com.onesley.oneclick.audit.TimestampedEntity} (même choix que
 * {@link UserFavorite}). {@code contactCount} = taille du batch soumis (debug /
 * observabilité). <b>Aucune PII des contacts n'est persistée</b> (RGPD) — on ne
 * stocke que le fait + le volume de l'import.
 */
@Entity
@Table(name = "contact_imports")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContactImport {

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;

    @Column(name = "user_id", nullable = false, insertable = false, updatable = false) private UUID userId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "contact_count", nullable = false) private int contactCount;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    public ContactImport(UUID id, User user, int contactCount) {
        this.id = id;
        this.user = user;
        this.contactCount = contactCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        return id != null && java.util.Objects.equals(id, ((ContactImport) o).id);
    }

    @Override
    public int hashCode() {
        return this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
