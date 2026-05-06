package com.onesley.oneclick.entity.restaurant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Entité {@code public.v_restaurants_config} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : vue read-only @Immutable.
 */
@Entity
@Immutable
@Table(name = "v_restaurants_config")
public class RestaurantsConfigView {

    @Id
    @Column(name = "id", insertable = false, updatable = false)
    private UUID id;

    @Column(name = "name", insertable = false, updatable = false)
    private String name;

    @Column(name = "phone", insertable = false, updatable = false)
    private String phone;

    @Column(name = "address", insertable = false, updatable = false)
    private String address;

    @Column(name = "description", insertable = false, updatable = false)
    private String description;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "tags", insertable = false, updatable = false)
    private List<String> tags = new ArrayList<>();

    @Column(name = "max_staff", insertable = false, updatable = false)
    private Integer maxStaff;

    @Column(name = "open_now", insertable = false, updatable = false)
    private Boolean openNow;

    protected RestaurantsConfigView() {
        // JPA
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getAddress() { return address; }
    public String getDescription() { return description; }
    public List<String> getTags() { return tags; }
    public Integer getMaxStaff() { return maxStaff; }
    public Boolean getOpenNow() { return openNow; }
}
