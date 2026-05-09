package com.onesley.oneclick.entity.admin;

import com.onesley.oneclick.audit.CreatedAuthorEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.data.annotation.CreatedBy;

import java.util.Objects;
import java.util.UUID;

/**
 * Entité {@code public.document_versions} — versions historiques d'un
 * {@link AppDocument} (CGU, confidentialité, etc.).
 *
 * <h3>Jointures JPA (passe 3)</h3>
 * <ul>
 *   <li>{@code document_id NOT NULL} → {@link AppDocument} en {@code @ManyToOne(LAZY)},
 *       optional=false. <b>Note</b> : la FK est une <b>String</b> (slug) pas un UUID
 *       — AppDocument utilise un slug textuel comme PK ({@code "cgu"}, {@code "confidentialite"}).</li>
 *   <li>{@code created_by} : audit field via {@code @CreatedBy}, reste UUID brut.</li>
 * </ul>
 */
@Entity
@Table(name = "document_versions")
public class DocumentVersion extends CreatedAuthorEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "document_id", nullable = false, insertable = false, updatable = false)
    private String documentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private AppDocument document;

    @NotBlank
    @Column(name = "version", nullable = false)
    private String version;

    @NotBlank
    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "notes")
    private String notes;

    protected DocumentVersion() {
        // JPA
    }

    public UUID getId() { return id; }
    public String getDocumentId() { return documentId; }
    public AppDocument getDocument() { return document; }
    public void setDocument(AppDocument document) { this.document = document; }
    public String getVersion() { return version; }
    public String getContent() { return content; }
    public String getNotes() { return notes; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass()
            : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass()
            : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        DocumentVersion that = (DocumentVersion) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
