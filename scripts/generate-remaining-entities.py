#!/usr/bin/env python3
"""
Génère les entités JPA restantes pour les modules payment/promotion/community/
social/event/support/analytics.

Pattern : entité minimale avec @Id, @Column, FK ManyToOne LAZY, equals/hashCode
anti-proxy, audit conservé via SoftDeletableAuditedEntity ou TimestampedEntity.

Output : 1 fichier .java par entité + 1 package-info.java par module.
"""
import os
from pathlib import Path

ROOT = Path('/Users/hh/Documents/Onesley/OneClick_Spring/src/main/java/com/onesley/oneclick/modules')


def equals_hash(class_name):
    return f'''    @Override
    public boolean equals(Object o) {{
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        return id != null && java.util.Objects.equals(id, (({class_name}) o).id);
    }}

    @Override
    public int hashCode() {{
        return this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }}'''


def package_info(module_name, allowed_deps, doc):
    return f'''/**
 * Module {{@code modules/{module_name}}} — {doc}
 */
@ApplicationModule(displayName = "modules/{module_name}", allowedDependencies = {{{allowed_deps}}})
package com.onesley.oneclick.modules.{module_name};

import org.springframework.modulith.ApplicationModule;
'''


# ─── Module payment ──────────────────────────────────────────────────────────
PAYMENT_PKG = package_info('payment', '"core/identity"',
                           'paiements (carte, wallet, etc.) §12 — NOUVEAU module')

PAYMENT_METHOD = f'''package com.onesley.oneclick.modules.payment;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.core.identity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Moyen de paiement enregistré (carte tokenisée, wallet, etc.). */
@Entity
@Table(name = "payment_methods")
public class PaymentMethod extends TimestampedEntity {{

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "user_id", nullable = false, insertable = false, updatable = false) private UUID userId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private User user;
    @Pattern(regexp = "^(card|bank_account|wallet|cash_on_site)$") @Column(name = "type", nullable = false) private String type;
    @Column(name = "last4") private String last4;
    @Column(name = "provider") private String provider;
    @Column(name = "provider_token") private String providerToken;
    @Column(name = "is_default", nullable = false) private boolean isDefault = false;
    @Column(name = "expires_at") private LocalDate expiresAt;
    @Column(name = "deleted_at") private Instant deletedAt;

    protected PaymentMethod() {{}}
    public PaymentMethod(UUID id, User user, String type) {{ this.id = id; this.user = user; this.type = type; }}

    public UUID getId() {{ return id; }}
    public UUID getUserId() {{ return userId; }}
    public User getUser() {{ return user; }}
    public String getType() {{ return type; }}
    public String getLast4() {{ return last4; }}
    public void setLast4(String last4) {{ this.last4 = last4; }}
    public String getProvider() {{ return provider; }}
    public void setProvider(String provider) {{ this.provider = provider; }}
    public String getProviderToken() {{ return providerToken; }}
    public void setProviderToken(String providerToken) {{ this.providerToken = providerToken; }}
    public boolean isDefault() {{ return isDefault; }}
    public void setDefault(boolean aDefault) {{ this.isDefault = aDefault; }}
    public LocalDate getExpiresAt() {{ return expiresAt; }}
    public void setExpiresAt(LocalDate expiresAt) {{ this.expiresAt = expiresAt; }}
    public Instant getDeletedAt() {{ return deletedAt; }}
    public void markDeleted() {{ this.deletedAt = Instant.now(); }}

{equals_hash('PaymentMethod')}
}}
'''

PAYMENT = f'''package com.onesley.oneclick.modules.payment;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.core.identity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Paiement applicatif (réservation, redemption, etc.). */
@Entity
@Table(name = "payments")
public class Payment extends TimestampedEntity {{

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "user_id", nullable = false, insertable = false, updatable = false) private UUID userId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private User user;
    @Column(name = "payment_method_id", insertable = false, updatable = false) private UUID paymentMethodId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "payment_method_id") private PaymentMethod paymentMethod;
    @NotNull @DecimalMin("0.01") @Column(name = "amount", nullable = false, precision = 12, scale = 2) private BigDecimal amount;
    @NotBlank @Column(name = "currency", nullable = false) private String currency = "MAD";
    @Pattern(regexp = "^(pending|processing|succeeded|failed|cancelled|refunded)$")
    @Column(name = "status", nullable = false) private String status = "pending";
    @Column(name = "provider") private String provider;
    @Column(name = "transaction_ref") private String transactionRef;
    @Column(name = "reference_type") private String referenceType;
    @Column(name = "reference_id") private UUID referenceId;
    @Column(name = "completed_at") private Instant completedAt;

    protected Payment() {{}}
    public Payment(UUID id, User user, BigDecimal amount) {{ this.id = id; this.user = user; this.amount = amount; }}

    public UUID getId() {{ return id; }}
    public UUID getUserId() {{ return userId; }}
    public User getUser() {{ return user; }}
    public UUID getPaymentMethodId() {{ return paymentMethodId; }}
    public PaymentMethod getPaymentMethod() {{ return paymentMethod; }}
    public void setPaymentMethod(PaymentMethod paymentMethod) {{ this.paymentMethod = paymentMethod; }}
    public BigDecimal getAmount() {{ return amount; }}
    public String getCurrency() {{ return currency; }}
    public void setCurrency(String currency) {{ this.currency = currency; }}
    public String getStatus() {{ return status; }}
    public void setStatus(String status) {{ this.status = status; }}
    public String getProvider() {{ return provider; }}
    public void setProvider(String provider) {{ this.provider = provider; }}
    public String getTransactionRef() {{ return transactionRef; }}
    public void setTransactionRef(String transactionRef) {{ this.transactionRef = transactionRef; }}
    public String getReferenceType() {{ return referenceType; }}
    public void setReferenceType(String referenceType) {{ this.referenceType = referenceType; }}
    public UUID getReferenceId() {{ return referenceId; }}
    public void setReferenceId(UUID referenceId) {{ this.referenceId = referenceId; }}
    public Instant getCompletedAt() {{ return completedAt; }}
    public void markCompleted() {{ this.completedAt = Instant.now(); this.status = "succeeded"; }}

{equals_hash('Payment')}
}}
'''

REFUND = f'''package com.onesley.oneclick.modules.payment;

import com.onesley.oneclick.core.identity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Remboursement partiel ou total d'un Payment. */
@Entity
@Table(name = "refunds")
@EntityListeners(AuditingEntityListener.class)
public class Refund {{

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "payment_id", nullable = false, insertable = false, updatable = false) private UUID paymentId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "payment_id", nullable = false) private Payment payment;
    @NotNull @DecimalMin("0.01") @Column(name = "amount", nullable = false, precision = 12, scale = 2) private BigDecimal amount;
    @Column(name = "reason") private String reason;
    @Pattern(regexp = "^(pending|succeeded|failed)$") @Column(name = "status", nullable = false) private String status = "pending";
    @CreatedDate @Column(name = "created_at", updatable = false, nullable = false) private Instant createdAt;
    @Column(name = "processed_at") private Instant processedAt;
    @CreatedBy @Column(name = "created_by", updatable = false) private UUID createdById;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "created_by", insertable = false, updatable = false) private User createdBy;

    protected Refund() {{}}
    public Refund(UUID id, Payment payment, BigDecimal amount) {{ this.id = id; this.payment = payment; this.amount = amount; }}

    public UUID getId() {{ return id; }}
    public UUID getPaymentId() {{ return paymentId; }}
    public Payment getPayment() {{ return payment; }}
    public BigDecimal getAmount() {{ return amount; }}
    public String getReason() {{ return reason; }}
    public void setReason(String reason) {{ this.reason = reason; }}
    public String getStatus() {{ return status; }}
    public void setStatus(String status) {{ this.status = status; }}
    public Instant getCreatedAt() {{ return createdAt; }}
    public Instant getProcessedAt() {{ return processedAt; }}
    public void markProcessed() {{ this.processedAt = Instant.now(); }}
    public UUID getCreatedById() {{ return createdById; }}

{equals_hash('Refund')}
}}
'''

PAYMENT_TX = f'''package com.onesley.oneclick.modules.payment;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Journal des événements provider (Stripe webhook, CMI callback, etc.). */
@Entity
@Table(name = "payment_transactions")
@EntityListeners(AuditingEntityListener.class)
public class PaymentTransaction {{

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "payment_id", nullable = false, insertable = false, updatable = false) private UUID paymentId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "payment_id", nullable = false) private Payment payment;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "provider_response", columnDefinition = "jsonb") private Map<String, Object> providerResponse = new HashMap<>();
    @NotBlank @Column(name = "event_type", nullable = false) private String eventType;
    @CreatedDate @Column(name = "created_at", updatable = false, nullable = false) private Instant createdAt;

    protected PaymentTransaction() {{}}
    public PaymentTransaction(UUID id, Payment payment, String eventType, Map<String, Object> providerResponse) {{
        this.id = id; this.payment = payment; this.eventType = eventType;
        if (providerResponse != null) this.providerResponse = providerResponse;
    }}

    public UUID getId() {{ return id; }}
    public UUID getPaymentId() {{ return paymentId; }}
    public Payment getPayment() {{ return payment; }}
    public Map<String, Object> getProviderResponse() {{ return providerResponse; }}
    public String getEventType() {{ return eventType; }}
    public Instant getCreatedAt() {{ return createdAt; }}

{equals_hash('PaymentTransaction')}
}}
'''

# ─── Module promotion ────────────────────────────────────────────────────────
PROMOTION_PKG = package_info('promotion', '"core/identity", "modules/restaurant"',
                             'offres / promotions par restaurant §7')

OFFER = f'''package com.onesley.oneclick.modules.promotion;

import com.onesley.oneclick.audit.SoftDeletableAuditedEntity;
import com.onesley.oneclick.modules.restaurant.Restaurant;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Offre / promotion par restaurant. */
@Entity
@Table(name = "offers")
public class Offer extends SoftDeletableAuditedEntity {{

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "restaurant_id", nullable = false, insertable = false, updatable = false) private UUID restaurantId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "restaurant_id", nullable = false) private Restaurant restaurant;
    @NotBlank @Column(name = "title", nullable = false) private String title;
    @Column(name = "description") private String description;
    @NotNull @Column(name = "starts_at", nullable = false) private Instant startsAt;
    @NotNull @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @DecimalMin("0.00") @DecimalMax("100.00") @Column(name = "discount_pct", precision = 5, scale = 2) private BigDecimal discountPct;
    @Column(name = "discount_amount", precision = 12, scale = 2) private BigDecimal discountAmount;
    @Column(name = "enabled", nullable = false) private boolean enabled = true;

    protected Offer() {{}}
    public Offer(UUID id, Restaurant restaurant, String title, Instant startsAt, Instant expiresAt) {{
        this.id = id; this.restaurant = restaurant; this.title = title; this.startsAt = startsAt; this.expiresAt = expiresAt;
    }}

    public UUID getId() {{ return id; }}
    public UUID getRestaurantId() {{ return restaurantId; }}
    public Restaurant getRestaurant() {{ return restaurant; }}
    public String getTitle() {{ return title; }}
    public void setTitle(String title) {{ this.title = title; }}
    public String getDescription() {{ return description; }}
    public void setDescription(String description) {{ this.description = description; }}
    public Instant getStartsAt() {{ return startsAt; }}
    public Instant getExpiresAt() {{ return expiresAt; }}
    public BigDecimal getDiscountPct() {{ return discountPct; }}
    public void setDiscountPct(BigDecimal discountPct) {{ this.discountPct = discountPct; }}
    public BigDecimal getDiscountAmount() {{ return discountAmount; }}
    public void setDiscountAmount(BigDecimal discountAmount) {{ this.discountAmount = discountAmount; }}
    public boolean isEnabled() {{ return enabled; }}
    public void setEnabled(boolean enabled) {{ this.enabled = enabled; }}

{equals_hash('Offer')}
}}
'''

# ─── Module community ────────────────────────────────────────────────────────
COMMUNITY_PKG = package_info('community', '"core/identity"', 'posts, commentaires, likes §8')

POST = f'''package com.onesley.oneclick.modules.community;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.core.identity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.UUID;

/** Post communauté (feed social). */
@Entity
@Table(name = "posts")
public class Post extends TimestampedEntity {{

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "author_id", nullable = false, insertable = false, updatable = false) private UUID authorId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "author_id", nullable = false) private User author;
    @NotBlank @Column(name = "content", nullable = false) private String content;
    @Pattern(regexp = "^(public|friends|private)$") @Column(name = "visibility", nullable = false) private String visibility = "public";
    @Column(name = "deleted_at") private Instant deletedAt;

    protected Post() {{}}
    public Post(UUID id, User author, String content) {{ this.id = id; this.author = author; this.content = content; }}

    public UUID getId() {{ return id; }}
    public UUID getAuthorId() {{ return authorId; }}
    public User getAuthor() {{ return author; }}
    public String getContent() {{ return content; }}
    public void setContent(String content) {{ this.content = content; }}
    public String getVisibility() {{ return visibility; }}
    public void setVisibility(String visibility) {{ this.visibility = visibility; }}
    public Instant getDeletedAt() {{ return deletedAt; }}
    public void markDeleted() {{ this.deletedAt = Instant.now(); }}

{equals_hash('Post')}
}}
'''

COMMENT = f'''package com.onesley.oneclick.modules.community;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.core.identity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.UUID;

/** Commentaire sur un post. */
@Entity
@Table(name = "comments")
public class Comment extends TimestampedEntity {{

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "post_id", nullable = false, insertable = false, updatable = false) private UUID postId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "post_id", nullable = false) private Post post;
    @Column(name = "author_id", nullable = false, insertable = false, updatable = false) private UUID authorId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "author_id", nullable = false) private User author;
    @NotBlank @Column(name = "content", nullable = false) private String content;
    @Column(name = "deleted_at") private Instant deletedAt;

    protected Comment() {{}}
    public Comment(UUID id, Post post, User author, String content) {{
        this.id = id; this.post = post; this.author = author; this.content = content;
    }}

    public UUID getId() {{ return id; }}
    public UUID getPostId() {{ return postId; }}
    public Post getPost() {{ return post; }}
    public UUID getAuthorId() {{ return authorId; }}
    public User getAuthor() {{ return author; }}
    public String getContent() {{ return content; }}
    public void setContent(String content) {{ this.content = content; }}
    public Instant getDeletedAt() {{ return deletedAt; }}
    public void markDeleted() {{ this.deletedAt = Instant.now(); }}

{equals_hash('Comment')}
}}
'''

POST_LIKE = f'''package com.onesley.oneclick.modules.community;

import com.onesley.oneclick.core.identity.User;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/** Like sur un post. UNIQUE (post_id, user_id). */
@Entity
@Table(name = "post_likes", uniqueConstraints = @UniqueConstraint(columnNames = {{"post_id", "user_id"}}))
@EntityListeners(AuditingEntityListener.class)
public class PostLike {{

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "post_id", nullable = false, insertable = false, updatable = false) private UUID postId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "post_id", nullable = false) private Post post;
    @Column(name = "user_id", nullable = false, insertable = false, updatable = false) private UUID userId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private User user;
    @CreatedDate @Column(name = "created_at", updatable = false, nullable = false) private Instant createdAt;

    protected PostLike() {{}}
    public PostLike(UUID id, Post post, User user) {{ this.id = id; this.post = post; this.user = user; }}

    public UUID getId() {{ return id; }}
    public UUID getPostId() {{ return postId; }}
    public Post getPost() {{ return post; }}
    public UUID getUserId() {{ return userId; }}
    public User getUser() {{ return user; }}
    public Instant getCreatedAt() {{ return createdAt; }}

{equals_hash('PostLike')}
}}
'''

# ─── Module social ───────────────────────────────────────────────────────────
SOCIAL_PKG = package_info('social', '"core/identity"', 'amitiés et parrainages §8')

FRIENDSHIP = f'''package com.onesley.oneclick.modules.social;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.core.identity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.UUID;

/** Amitié bidirectionnelle — 1 row par couple (user1 < user2 par convention). */
@Entity
@Table(name = "friendships", uniqueConstraints = @UniqueConstraint(columnNames = {{"user1_id", "user2_id"}}))
public class Friendship extends TimestampedEntity {{

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "user1_id", nullable = false, insertable = false, updatable = false) private UUID user1Id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user1_id", nullable = false) private User user1;
    @Column(name = "user2_id", nullable = false, insertable = false, updatable = false) private UUID user2Id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user2_id", nullable = false) private User user2;
    @Pattern(regexp = "^(pending|accepted|declined|blocked)$") @Column(name = "status", nullable = false) private String status = "pending";
    @Column(name = "accepted_at") private Instant acceptedAt;

    protected Friendship() {{}}
    public Friendship(UUID id, User user1, User user2) {{
        // Convention canonique : user1.id < user2.id pour éviter doublons.
        if (user1.getId().compareTo(user2.getId()) < 0) {{ this.user1 = user1; this.user2 = user2; }}
        else {{ this.user1 = user2; this.user2 = user1; }}
        this.id = id;
    }}

    public UUID getId() {{ return id; }}
    public UUID getUser1Id() {{ return user1Id; }}
    public User getUser1() {{ return user1; }}
    public UUID getUser2Id() {{ return user2Id; }}
    public User getUser2() {{ return user2; }}
    public String getStatus() {{ return status; }}
    public void setStatus(String status) {{ this.status = status; }}
    public Instant getAcceptedAt() {{ return acceptedAt; }}
    public void markAccepted() {{ this.acceptedAt = Instant.now(); this.status = "accepted"; }}

{equals_hash('Friendship')}
}}
'''

REFERRAL = f'''package com.onesley.oneclick.modules.social;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.core.identity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.UUID;

/** Parrainage avec code et status d'activation. */
@Entity
@Table(name = "referrals")
public class Referral extends TimestampedEntity {{

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "referrer_id", nullable = false, insertable = false, updatable = false) private UUID referrerId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "referrer_id", nullable = false) private User referrer;
    @Column(name = "referred_user_id", insertable = false, updatable = false) private UUID referredUserId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "referred_user_id") private User referredUser;
    @NotBlank @Column(name = "referral_code", nullable = false) private String referralCode;
    @Pattern(regexp = "^(pending|activated|expired)$") @Column(name = "status", nullable = false) private String status = "pending";
    @Column(name = "activated_at") private Instant activatedAt;

    protected Referral() {{}}
    public Referral(UUID id, User referrer, String referralCode) {{
        this.id = id; this.referrer = referrer; this.referralCode = referralCode;
    }}

    public UUID getId() {{ return id; }}
    public UUID getReferrerId() {{ return referrerId; }}
    public User getReferrer() {{ return referrer; }}
    public UUID getReferredUserId() {{ return referredUserId; }}
    public User getReferredUser() {{ return referredUser; }}
    public void setReferredUser(User referredUser) {{ this.referredUser = referredUser; }}
    public String getReferralCode() {{ return referralCode; }}
    public String getStatus() {{ return status; }}
    public void setStatus(String status) {{ this.status = status; }}
    public Instant getActivatedAt() {{ return activatedAt; }}
    public void markActivated() {{ this.activatedAt = Instant.now(); this.status = "activated"; }}

{equals_hash('Referral')}
}}
'''

# ─── Module event ────────────────────────────────────────────────────────────
EVENT_PKG = package_info('event', '"core/identity", "core/tenant", "modules/restaurant"', 'événements §9')

EVENT = f'''package com.onesley.oneclick.modules.event;

import com.onesley.oneclick.audit.SoftDeletableAuditedEntity;
import com.onesley.oneclick.core.tenant.Tenant;
import com.onesley.oneclick.modules.restaurant.Restaurant;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/** Événement (soirée, dégustation, séminaire). */
@Entity
@Table(name = "events")
public class Event extends SoftDeletableAuditedEntity {{

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "tenant_id", nullable = false, insertable = false, updatable = false) private UUID tenantId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "tenant_id", nullable = false) private Tenant tenant;
    @Column(name = "restaurant_id", insertable = false, updatable = false) private UUID restaurantId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "restaurant_id") private Restaurant restaurant;
    @NotBlank @Column(name = "title", nullable = false) private String title;
    @Column(name = "description") private String description;
    @Column(name = "event_type") private String eventType;
    @NotNull @Column(name = "event_at", nullable = false) private Instant eventAt;
    @Column(name = "capacity") private Integer capacity;

    protected Event() {{}}
    public Event(UUID id, Tenant tenant, String title, Instant eventAt) {{
        this.id = id; this.tenant = tenant; this.title = title; this.eventAt = eventAt;
    }}

    public UUID getId() {{ return id; }}
    public UUID getTenantId() {{ return tenantId; }}
    public Tenant getTenant() {{ return tenant; }}
    public UUID getRestaurantId() {{ return restaurantId; }}
    public Restaurant getRestaurant() {{ return restaurant; }}
    public void setRestaurant(Restaurant restaurant) {{ this.restaurant = restaurant; }}
    public String getTitle() {{ return title; }}
    public void setTitle(String title) {{ this.title = title; }}
    public String getDescription() {{ return description; }}
    public void setDescription(String description) {{ this.description = description; }}
    public String getEventType() {{ return eventType; }}
    public void setEventType(String eventType) {{ this.eventType = eventType; }}
    public Instant getEventAt() {{ return eventAt; }}
    public Integer getCapacity() {{ return capacity; }}
    public void setCapacity(Integer capacity) {{ this.capacity = capacity; }}

{equals_hash('Event')}
}}
'''

EVENT_PART = f'''package com.onesley.oneclick.modules.event;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.core.identity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

/** RSVP sur un Event. */
@Entity
@Table(name = "event_participations", uniqueConstraints = @UniqueConstraint(columnNames = {{"event_id", "user_id"}}))
public class EventParticipation extends TimestampedEntity {{

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "event_id", nullable = false, insertable = false, updatable = false) private UUID eventId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "event_id", nullable = false) private Event event;
    @Column(name = "user_id", nullable = false, insertable = false, updatable = false) private UUID userId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private User user;
    @Pattern(regexp = "^(going|maybe|declined|attended)$") @Column(name = "status", nullable = false) private String status = "going";

    protected EventParticipation() {{}}
    public EventParticipation(UUID id, Event event, User user, String status) {{
        this.id = id; this.event = event; this.user = user; this.status = status;
    }}

    public UUID getId() {{ return id; }}
    public UUID getEventId() {{ return eventId; }}
    public Event getEvent() {{ return event; }}
    public UUID getUserId() {{ return userId; }}
    public User getUser() {{ return user; }}
    public String getStatus() {{ return status; }}
    public void setStatus(String status) {{ this.status = status; }}

{equals_hash('EventParticipation')}
}}
'''

# ─── Module support ──────────────────────────────────────────────────────────
SUPPORT_PKG = package_info('support', '"core/identity"', 'tickets support client §13')

SUPPORT_TICKET = f'''package com.onesley.oneclick.modules.support;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.core.identity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.UUID;

/** Ticket support client. */
@Entity
@Table(name = "support_tickets")
public class SupportTicket extends TimestampedEntity {{

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "opened_by", nullable = false, insertable = false, updatable = false) private UUID openedById;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "opened_by", nullable = false) private User openedBy;
    @NotBlank @Column(name = "category", nullable = false) private String category;
    @Pattern(regexp = "^(low|normal|high|urgent)$") @Column(name = "priority", nullable = false) private String priority = "normal";
    @Pattern(regexp = "^(open|in_progress|resolved|closed)$") @Column(name = "status", nullable = false) private String status = "open";
    @NotBlank @Column(name = "subject", nullable = false) private String subject;
    @Column(name = "resolved_at") private Instant resolvedAt;
    @Column(name = "closed_at") private Instant closedAt;
    @Column(name = "assigned_to", insertable = false, updatable = false) private UUID assignedToId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "assigned_to") private User assignedTo;

    protected SupportTicket() {{}}
    public SupportTicket(UUID id, User openedBy, String category, String subject) {{
        this.id = id; this.openedBy = openedBy; this.category = category; this.subject = subject;
    }}

    public UUID getId() {{ return id; }}
    public UUID getOpenedById() {{ return openedById; }}
    public User getOpenedBy() {{ return openedBy; }}
    public String getCategory() {{ return category; }}
    public String getPriority() {{ return priority; }}
    public void setPriority(String priority) {{ this.priority = priority; }}
    public String getStatus() {{ return status; }}
    public void setStatus(String status) {{ this.status = status; }}
    public String getSubject() {{ return subject; }}
    public void setSubject(String subject) {{ this.subject = subject; }}
    public Instant getResolvedAt() {{ return resolvedAt; }}
    public void markResolved() {{ this.resolvedAt = Instant.now(); this.status = "resolved"; }}
    public Instant getClosedAt() {{ return closedAt; }}
    public void markClosed() {{ this.closedAt = Instant.now(); this.status = "closed"; }}
    public UUID getAssignedToId() {{ return assignedToId; }}
    public User getAssignedTo() {{ return assignedTo; }}
    public void setAssignedTo(User assignedTo) {{ this.assignedTo = assignedTo; }}

{equals_hash('SupportTicket')}
}}
'''

TICKET_MSG = f'''package com.onesley.oneclick.modules.support;

import com.onesley.oneclick.core.identity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/** Message dans un thread support. */
@Entity
@Table(name = "ticket_messages")
@EntityListeners(AuditingEntityListener.class)
public class TicketMessage {{

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "ticket_id", nullable = false, insertable = false, updatable = false) private UUID ticketId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "ticket_id", nullable = false) private SupportTicket ticket;
    @Column(name = "author_id", nullable = false, insertable = false, updatable = false) private UUID authorId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "author_id", nullable = false) private User author;
    @NotBlank @Column(name = "message", nullable = false) private String message;
    @CreatedDate @Column(name = "created_at", updatable = false, nullable = false) private Instant createdAt;

    protected TicketMessage() {{}}
    public TicketMessage(UUID id, SupportTicket ticket, User author, String message) {{
        this.id = id; this.ticket = ticket; this.author = author; this.message = message;
    }}

    public UUID getId() {{ return id; }}
    public UUID getTicketId() {{ return ticketId; }}
    public SupportTicket getTicket() {{ return ticket; }}
    public UUID getAuthorId() {{ return authorId; }}
    public User getAuthor() {{ return author; }}
    public String getMessage() {{ return message; }}
    public Instant getCreatedAt() {{ return createdAt; }}

{equals_hash('TicketMessage')}
}}
'''

TICKET_ATT = f'''package com.onesley.oneclick.modules.support;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/** Pièce jointe d'un ticket support. */
@Entity
@Table(name = "ticket_attachments")
@EntityListeners(AuditingEntityListener.class)
public class TicketAttachment {{

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "ticket_id", nullable = false, insertable = false, updatable = false) private UUID ticketId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "ticket_id", nullable = false) private SupportTicket ticket;
    @NotBlank @Column(name = "url", nullable = false) private String url;
    @Column(name = "file_name") private String fileName;
    @Column(name = "mime_type") private String mimeType;
    @CreatedDate @Column(name = "created_at", updatable = false, nullable = false) private Instant createdAt;

    protected TicketAttachment() {{}}
    public TicketAttachment(UUID id, SupportTicket ticket, String url) {{
        this.id = id; this.ticket = ticket; this.url = url;
    }}

    public UUID getId() {{ return id; }}
    public UUID getTicketId() {{ return ticketId; }}
    public SupportTicket getTicket() {{ return ticket; }}
    public String getUrl() {{ return url; }}
    public String getFileName() {{ return fileName; }}
    public void setFileName(String fileName) {{ this.fileName = fileName; }}
    public String getMimeType() {{ return mimeType; }}
    public void setMimeType(String mimeType) {{ this.mimeType = mimeType; }}
    public Instant getCreatedAt() {{ return createdAt; }}

{equals_hash('TicketAttachment')}
}}
'''

# ─── Module analytics ─────────────────────────────────────────────────────────
ANALYTICS_PKG = package_info('analytics', '"core/identity", "core/tenant", "modules/restaurant"',
                             'search + API management + webhooks §19')

# RestaurantSearchDocument — tsvector index full-text
RESTAURANT_SEARCH_DOC = f'''package com.onesley.oneclick.modules.analytics;

import com.onesley.oneclick.modules.restaurant.Restaurant;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Document de recherche full-text (tsvector) — sync vers Elasticsearch en Phase 2.
 *
 * <p>PK = restaurant_id (1-1 avec Restaurant via @MapsId).
 */
@Entity
@Table(name = "restaurant_search_documents")
public class RestaurantSearchDocument {{

    @Id @Column(name = "restaurant_id", nullable = false, updatable = false) private UUID restaurantId;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @MapsId @JoinColumn(name = "restaurant_id") private Restaurant restaurant;
    // tsvector mapping — Hibernate ne le supporte pas nativement, on stocke string
    @Column(name = "document", columnDefinition = "tsvector", insertable = false, updatable = false) private String document;
    @Column(name = "indexed_at", nullable = false) private Instant indexedAt = Instant.now();

    protected RestaurantSearchDocument() {{}}
    public RestaurantSearchDocument(Restaurant restaurant) {{ this.restaurant = restaurant; }}

    public UUID getRestaurantId() {{ return restaurantId; }}
    public Restaurant getRestaurant() {{ return restaurant; }}
    public String getDocument() {{ return document; }}
    public Instant getIndexedAt() {{ return indexedAt; }}

    @Override
    public boolean equals(Object o) {{
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        return restaurantId != null && java.util.Objects.equals(restaurantId, ((RestaurantSearchDocument) o).restaurantId);
    }}

    @Override
    public int hashCode() {{
        return this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }}
}}
'''

API_CLIENT = f'''package com.onesley.oneclick.modules.analytics;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.core.tenant.Tenant;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/** Client API (intégration partenaire). */
@Entity
@Table(name = "api_clients")
public class ApiClient extends TimestampedEntity {{

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "tenant_id", insertable = false, updatable = false) private UUID tenantId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "tenant_id") private Tenant tenant;
    @NotBlank @Column(name = "name", nullable = false) private String name;
    @Column(name = "description") private String description;
    @Column(name = "enabled", nullable = false) private boolean enabled = true;

    protected ApiClient() {{}}
    public ApiClient(UUID id, String name) {{ this.id = id; this.name = name; }}

    public UUID getId() {{ return id; }}
    public UUID getTenantId() {{ return tenantId; }}
    public Tenant getTenant() {{ return tenant; }}
    public void setTenant(Tenant tenant) {{ this.tenant = tenant; }}
    public String getName() {{ return name; }}
    public void setName(String name) {{ this.name = name; }}
    public String getDescription() {{ return description; }}
    public void setDescription(String description) {{ this.description = description; }}
    public boolean isEnabled() {{ return enabled; }}
    public void setEnabled(boolean enabled) {{ this.enabled = enabled; }}

{equals_hash('ApiClient')}
}}
'''

API_KEY = f'''package com.onesley.oneclick.modules.analytics;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Clé API hashée. */
@Entity
@Table(name = "api_keys")
@EntityListeners(AuditingEntityListener.class)
public class ApiKey {{

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "api_client_id", nullable = false, insertable = false, updatable = false) private UUID apiClientId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "api_client_id", nullable = false) private ApiClient apiClient;
    @NotBlank @Column(name = "key_hash", nullable = false, unique = true) private String keyHash;
    @NotBlank @Column(name = "key_prefix", nullable = false) private String keyPrefix;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "scopes", columnDefinition = "jsonb") private List<String> scopes = new ArrayList<>();
    @Column(name = "enabled", nullable = false) private boolean enabled = true;
    @Column(name = "last_used_at") private Instant lastUsedAt;
    @Column(name = "expires_at") private Instant expiresAt;
    @CreatedDate @Column(name = "created_at", updatable = false, nullable = false) private Instant createdAt;
    @Column(name = "revoked_at") private Instant revokedAt;

    protected ApiKey() {{}}
    public ApiKey(UUID id, ApiClient apiClient, String keyHash, String keyPrefix) {{
        this.id = id; this.apiClient = apiClient; this.keyHash = keyHash; this.keyPrefix = keyPrefix;
    }}

    public UUID getId() {{ return id; }}
    public UUID getApiClientId() {{ return apiClientId; }}
    public ApiClient getApiClient() {{ return apiClient; }}
    public String getKeyHash() {{ return keyHash; }}
    public String getKeyPrefix() {{ return keyPrefix; }}
    public List<String> getScopes() {{ return scopes; }}
    public boolean isEnabled() {{ return enabled; }}
    public void setEnabled(boolean enabled) {{ this.enabled = enabled; }}
    public Instant getLastUsedAt() {{ return lastUsedAt; }}
    public void markUsed() {{ this.lastUsedAt = Instant.now(); }}
    public Instant getExpiresAt() {{ return expiresAt; }}
    public void setExpiresAt(Instant expiresAt) {{ this.expiresAt = expiresAt; }}
    public Instant getCreatedAt() {{ return createdAt; }}
    public Instant getRevokedAt() {{ return revokedAt; }}
    public void revoke() {{ this.revokedAt = Instant.now(); }}

{equals_hash('ApiKey')}
}}
'''

WEBHOOK = f'''package com.onesley.oneclick.modules.analytics;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Webhook sortant — URL à appeler quand un event se produit. */
@Entity
@Table(name = "webhooks")
public class Webhook extends TimestampedEntity {{

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "api_client_id", nullable = false, insertable = false, updatable = false) private UUID apiClientId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "api_client_id", nullable = false) private ApiClient apiClient;
    @NotBlank @Column(name = "url", nullable = false) private String url;
    @Column(name = "secret") private String secret;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "event_types", columnDefinition = "jsonb") private List<String> eventTypes = new ArrayList<>();
    @Column(name = "enabled", nullable = false) private boolean enabled = true;

    protected Webhook() {{}}
    public Webhook(UUID id, ApiClient apiClient, String url) {{
        this.id = id; this.apiClient = apiClient; this.url = url;
    }}

    public UUID getId() {{ return id; }}
    public UUID getApiClientId() {{ return apiClientId; }}
    public ApiClient getApiClient() {{ return apiClient; }}
    public String getUrl() {{ return url; }}
    public void setUrl(String url) {{ this.url = url; }}
    public String getSecret() {{ return secret; }}
    public void setSecret(String secret) {{ this.secret = secret; }}
    public List<String> getEventTypes() {{ return eventTypes; }}
    public boolean isEnabled() {{ return enabled; }}
    public void setEnabled(boolean enabled) {{ this.enabled = enabled; }}

{equals_hash('Webhook')}
}}
'''

WEBHOOK_DELIVERY = f'''package com.onesley.oneclick.modules.analytics;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Historique des appels webhook. */
@Entity
@Table(name = "webhook_deliveries")
@EntityListeners(AuditingEntityListener.class)
public class WebhookDelivery {{

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "webhook_id", nullable = false, insertable = false, updatable = false) private UUID webhookId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "webhook_id", nullable = false) private Webhook webhook;
    @NotBlank @Column(name = "event_type", nullable = false) private String eventType;
    @NotNull @JdbcTypeCode(SqlTypes.JSON) @Column(name = "payload", nullable = false, columnDefinition = "jsonb") private Map<String, Object> payload = new HashMap<>();
    @Column(name = "status_code") private Integer statusCode;
    @Column(name = "response_body", columnDefinition = "text") private String responseBody;
    @Column(name = "attempts", nullable = false) private Integer attempts = 0;
    @Column(name = "succeeded_at") private Instant succeededAt;
    @Column(name = "failed_at") private Instant failedAt;
    @CreatedDate @Column(name = "created_at", updatable = false, nullable = false) private Instant createdAt;

    protected WebhookDelivery() {{}}
    public WebhookDelivery(UUID id, Webhook webhook, String eventType, Map<String, Object> payload) {{
        this.id = id; this.webhook = webhook; this.eventType = eventType;
        if (payload != null) this.payload = payload;
    }}

    public UUID getId() {{ return id; }}
    public UUID getWebhookId() {{ return webhookId; }}
    public Webhook getWebhook() {{ return webhook; }}
    public String getEventType() {{ return eventType; }}
    public Map<String, Object> getPayload() {{ return payload; }}
    public Integer getStatusCode() {{ return statusCode; }}
    public void setStatusCode(Integer statusCode) {{ this.statusCode = statusCode; }}
    public String getResponseBody() {{ return responseBody; }}
    public void setResponseBody(String responseBody) {{ this.responseBody = responseBody; }}
    public Integer getAttempts() {{ return attempts; }}
    public void incrementAttempts() {{ this.attempts = (this.attempts == null ? 0 : this.attempts) + 1; }}
    public Instant getSucceededAt() {{ return succeededAt; }}
    public void markSucceeded() {{ this.succeededAt = Instant.now(); }}
    public Instant getFailedAt() {{ return failedAt; }}
    public void markFailed() {{ this.failedAt = Instant.now(); }}
    public Instant getCreatedAt() {{ return createdAt; }}

{equals_hash('WebhookDelivery')}
}}
'''


# ─── Write everything ────────────────────────────────────────────────────────
modules = {
    'payment': {
        'package-info.java': PAYMENT_PKG,
        'PaymentMethod.java': PAYMENT_METHOD,
        'Payment.java': PAYMENT,
        'Refund.java': REFUND,
        'PaymentTransaction.java': PAYMENT_TX,
    },
    'promotion': {
        'package-info.java': PROMOTION_PKG,
        'Offer.java': OFFER,
    },
    'community': {
        'package-info.java': COMMUNITY_PKG,
        'Post.java': POST,
        'Comment.java': COMMENT,
        'PostLike.java': POST_LIKE,
    },
    'social': {
        'package-info.java': SOCIAL_PKG,
        'Friendship.java': FRIENDSHIP,
        'Referral.java': REFERRAL,
    },
    'event': {
        'package-info.java': EVENT_PKG,
        'Event.java': EVENT,
        'EventParticipation.java': EVENT_PART,
    },
    'support': {
        'package-info.java': SUPPORT_PKG,
        'SupportTicket.java': SUPPORT_TICKET,
        'TicketMessage.java': TICKET_MSG,
        'TicketAttachment.java': TICKET_ATT,
    },
    'analytics': {
        'package-info.java': ANALYTICS_PKG,
        'RestaurantSearchDocument.java': RESTAURANT_SEARCH_DOC,
        'ApiClient.java': API_CLIENT,
        'ApiKey.java': API_KEY,
        'Webhook.java': WEBHOOK,
        'WebhookDelivery.java': WEBHOOK_DELIVERY,
    },
}

count = 0
for module, files in modules.items():
    module_path = ROOT / module
    module_path.mkdir(parents=True, exist_ok=True)
    for file, content in files.items():
        out = module_path / file
        out.write_text(content)
        count += 1

print(f'Generated {count} files in {len(modules)} modules')
