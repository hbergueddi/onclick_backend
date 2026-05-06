package com.onesley.oneclick.entity.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Entité {@code public.contact_import_events} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : entité simple.
 */
@Entity
@Table(name = "contact_import_events")
public class ContactImportEvent {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt;

    @Column(name = "phone_count", nullable = false)
    private Integer phoneCount;

    @Column(name = "matched_count", nullable = false)
    private Integer matchedCount;

    protected ContactImportEvent() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public Instant getImportedAt() { return importedAt; }
    public Integer getPhoneCount() { return phoneCount; }
    public Integer getMatchedCount() { return matchedCount; }
}
