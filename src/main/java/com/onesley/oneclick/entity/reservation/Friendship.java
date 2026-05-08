package com.onesley.oneclick.entity.reservation;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.entity.auth.Profile;
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

/**
 * Entité {@code public.friendships} — relations d'amitié bilatérales
 * (status: pending/accepted/rejected/blocked).
 *
 * <h3>Jointures JPA (passe 3) — entité d'association Profile ↔ Profile</h3>
 * <ul>
 *   <li>{@code requester_id NOT NULL} → {@link Profile} (l'envoyeur de la demande)
 *       en {@code @ManyToOne(LAZY)}, optional=false.</li>
 *   <li>{@code addressee_id NOT NULL} → {@link Profile} (le destinataire de la demande)
 *       en {@code @ManyToOne(LAZY)}, optional=false.</li>
 *   <li>Pas d'inverse {@code Profile.friendships} : 2 FKs vers la même cible
 *       Profile → impossible de matérialiser un seul {@code @OneToMany} naturellement.
 *       Repository custom {@code findByRequesterIdOrAddresseeId} à la place.</li>
 * </ul>
 */
@Entity
@Table(name = "friendships")
public class Friendship extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "requester_id", nullable = false, insertable = false, updatable = false)
    private UUID requesterId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private Profile requester;

    @Column(name = "addressee_id", nullable = false, insertable = false, updatable = false)
    private UUID addresseeId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "addressee_id", nullable = false)
    private Profile addressee;

    @NotBlank
    @Column(name = "status", nullable = false)
    private String status;

    protected Friendship() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getRequesterId() { return requesterId; }
    public Profile getRequester() { return requester; }
    public void setRequester(Profile requester) { this.requester = requester; }
    public UUID getAddresseeId() { return addresseeId; }
    public Profile getAddressee() { return addressee; }
    public void setAddressee(Profile addressee) { this.addressee = addressee; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

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
        Friendship that = (Friendship) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
