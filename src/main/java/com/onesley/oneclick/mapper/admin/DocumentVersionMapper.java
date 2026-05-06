package com.onesley.oneclick.mapper.admin;

import com.onesley.oneclick.dto.admin.DocumentVersionDto;
import com.onesley.oneclick.entity.admin.DocumentVersion;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code DocumentVersion ↔ DocumentVersionDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface DocumentVersionMapper {

    DocumentVersionDto toDto(DocumentVersion entity);

    List<DocumentVersionDto> toDtoList(List<DocumentVersion> entities);
}
