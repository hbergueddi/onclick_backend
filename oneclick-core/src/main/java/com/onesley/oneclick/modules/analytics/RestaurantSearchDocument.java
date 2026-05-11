package com.onesley.oneclick.modules.analytics;

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
public class RestaurantSearchDocument {

    @Id @Column(name = "restaurant_id", nullable = false, updatable = false) private UUID restaurantId;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @MapsId @JoinColumn(name = "restaurant_id") private Restaurant restaurant;
    // tsvector mapping — Hibernate ne le supporte pas nativement, on stocke string
    @Column(name = "document", columnDefinition = "tsvector", insertable = false, updatable = false) private String document;
    @Column(name = "indexed_at", nullable = false) private Instant indexedAt = Instant.now();

    protected RestaurantSearchDocument() {}
    public RestaurantSearchDocument(Restaurant restaurant) { this.restaurant = restaurant; }

    public UUID getRestaurantId() { return restaurantId; }
    public Restaurant getRestaurant() { return restaurant; }
    public String getDocument() { return document; }
    public Instant getIndexedAt() { return indexedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        return restaurantId != null && java.util.Objects.equals(restaurantId, ((RestaurantSearchDocument) o).restaurantId);
    }

    @Override
    public int hashCode() {
        return this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
