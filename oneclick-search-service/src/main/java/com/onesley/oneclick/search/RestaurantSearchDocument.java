package com.onesley.oneclick.search;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Document de recherche full-text — 1 row par restaurant.
 *
 * <p>Pattern microservice (Phase 2 §21) : pas de @OneToOne JPA vers Restaurant
 * (entité dans oneclick-core). Le {@code restaurant_id} est un UUID + FK Postgres.
 *
 * <p>{@code document} = colonne tsvector PostgreSQL native (maintenue par trigger
 * DB depuis name/city/cuisine/tags). Hibernate ne gère pas tsvector → champ read-only
 * (insertable/updatable=false). Les requêtes full-text passent par JdbcTemplate
 * avec {@code to_tsquery} natif.
 */
@Entity
@Table(name = "restaurant_search_documents")
public class RestaurantSearchDocument {

    @Id
    @Column(name = "restaurant_id", nullable = false, updatable = false)
    private UUID restaurantId;

    @Column(name = "document", columnDefinition = "tsvector", insertable = false, updatable = false)
    private String document;

    @Column(name = "indexed_at", nullable = false)
    private Instant indexedAt = Instant.now();

    protected RestaurantSearchDocument() {
        // JPA
    }

    public RestaurantSearchDocument(UUID restaurantId) {
        this.restaurantId = restaurantId;
    }

    public UUID getRestaurantId() { return restaurantId; }
    public String getDocument() { return document; }
    public Instant getIndexedAt() { return indexedAt; }
}
