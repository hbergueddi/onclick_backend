package com.onesley.oneclick.modules.system.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

/** Repository {@link DocumentVersion} — historique trié par date décroissante. */
public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, UUID> {

    @Query("SELECT v FROM DocumentVersion v WHERE v.documentId = :documentId ORDER BY v.createdAt DESC")
    List<DocumentVersion> findByDocument(String documentId);
}
