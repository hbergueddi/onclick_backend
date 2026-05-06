package com.onesley.oneclick.mapper.admin;

import com.onesley.oneclick.dto.admin.ContactImportEventDto;
import com.onesley.oneclick.entity.admin.ContactImportEvent;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code ContactImportEvent ↔ ContactImportEventDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface ContactImportEventMapper {

    ContactImportEventDto toDto(ContactImportEvent entity);

    List<ContactImportEventDto> toDtoList(List<ContactImportEvent> entities);
}
