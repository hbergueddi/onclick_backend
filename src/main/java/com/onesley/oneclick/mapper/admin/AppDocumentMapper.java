package com.onesley.oneclick.mapper.admin;

import com.onesley.oneclick.dto.admin.AppDocumentDto;
import com.onesley.oneclick.entity.admin.AppDocument;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code AppDocument ↔ AppDocumentDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface AppDocumentMapper {

    AppDocumentDto toDto(AppDocument entity);

    List<AppDocumentDto> toDtoList(List<AppDocument> entities);
}
