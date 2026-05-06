package com.onesley.oneclick.mapper.tenant;

import com.onesley.oneclick.dto.tenant.TenantAnnouncementDto;
import com.onesley.oneclick.entity.tenant.TenantAnnouncement;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code TenantAnnouncement ↔ TenantAnnouncementDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface TenantAnnouncementMapper {

    TenantAnnouncementDto toDto(TenantAnnouncement entity);

    List<TenantAnnouncementDto> toDtoList(List<TenantAnnouncement> entities);
}
