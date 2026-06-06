package com.onesley.oneclick.modules.membercircle.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Repo des posts membres (modération C4.8c). Lectures de liste enrichies = native SQL dans le service. */
interface MemberPostRepository extends JpaRepository<MemberPost, UUID> {
}
