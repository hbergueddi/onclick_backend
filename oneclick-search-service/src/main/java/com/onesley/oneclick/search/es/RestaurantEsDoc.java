package com.onesley.oneclick.search.es;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.Instant;
import java.util.UUID;

/**
 * Document Elasticsearch indexé pour la recherche full-text restaurants.
 *
 * <p>Phase 3.4 spec §X (architecture enterprise) : ES devient le moteur de
 * recherche principal pour le scaling cross-tenant. Sync depuis Postgres
 * via {@code RestaurantEsSyncService} (job @Scheduled toutes les 60s).
 *
 * <p>Index name {@code restaurants} ; analyzer par défaut {@code standard}
 * (compatible multi-langue Maroc : français + arabe + anglais).
 *
 * <p>Pattern microservice : restaurants est l'agrégat root de oneclick-core
 * (PostgreSQL). search-service projette une vue dénormalisée dans ES, mais
 * jamais d'écriture cross-service. La cohérence est éventuelle (≤ 60s drift).
 */
@Document(indexName = "restaurants", createIndex = true)
public class RestaurantEsDoc {

    @Id
    private String id;  // UUID stringifié

    @Field(type = FieldType.Keyword)
    private UUID tenantId;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String name;

    @Field(type = FieldType.Keyword)
    private String city;

    @Field(type = FieldType.Text)
    private String address;

    @Field(type = FieldType.Text)
    private String description;

    @Field(type = FieldType.Keyword)
    private String phone;

    /** {@code active|paused|archived} — colonne restaurants.status (text). */
    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Date)
    private Instant indexedAt;

    public RestaurantEsDoc() {
        // ES needs no-arg ctor
    }

    public RestaurantEsDoc(UUID id, UUID tenantId, String name, String city, String address,
                            String description, String phone, String status) {
        this.id = id.toString();
        this.tenantId = tenantId;
        this.name = name;
        this.city = city;
        this.address = address;
        this.description = description;
        this.phone = phone;
        this.status = status;
        this.indexedAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getIndexedAt() { return indexedAt; }
    public void setIndexedAt(Instant indexedAt) { this.indexedAt = indexedAt; }
}
