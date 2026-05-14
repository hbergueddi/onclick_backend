package com.onesley.oneclick.modules.system.internal;

import org.springframework.data.jpa.repository.JpaRepository;

/** Repository {@link AppDocument} — PK textuelle ({@code "plan"}). */
public interface AppDocumentRepository extends JpaRepository<AppDocument, String> {
}
