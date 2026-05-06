package com.onesley.oneclick.entity.pcc;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Entité {@code public.pcc_family_members} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at + updated_at hérités.
 */
@Entity
@Table(name = "pcc_family_members")
public class PccFamilyMember extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(name = "related_member_id", nullable = false)
    private UUID relatedMemberId;

    @Column(name = "relation")
    private String relation;

    protected PccFamilyMember() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getMemberId() { return memberId; }
    public UUID getRelatedMemberId() { return relatedMemberId; }
    public String getRelation() { return relation; }
}
