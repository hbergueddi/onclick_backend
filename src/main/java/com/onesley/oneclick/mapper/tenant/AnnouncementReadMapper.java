package com.onesley.oneclick.mapper.tenant;

import com.onesley.oneclick.dto.tenant.AnnouncementReadDto;
import com.onesley.oneclick.entity.tenant.AnnouncementRead;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code AnnouncementRead ↔ AnnouncementReadDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface AnnouncementReadMapper {

    AnnouncementReadDto toDto(AnnouncementRead entity);

    List<AnnouncementReadDto> toDtoList(List<AnnouncementRead> entities);
}
