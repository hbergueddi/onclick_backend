package com.onesley.oneclick.dto.pcc;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code pcc_family_members} (généré par scripts/scaffold-jpa.mjs).
 */
public record PccFamilyMemberDto(
    UUID id,
    UUID memberId,
    UUID relatedMemberId,
    String relation,
    Instant createdAt,
    Instant updatedAt
) {
}
