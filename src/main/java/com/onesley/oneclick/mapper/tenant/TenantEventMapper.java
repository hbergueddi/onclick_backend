package com.onesley.oneclick.mapper.tenant;

import com.onesley.oneclick.dto.tenant.TenantEventDto;
import com.onesley.oneclick.entity.tenant.TenantEvent;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code TenantEvent ↔ TenantEventDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface TenantEventMapper {

    TenantEventDto toDto(TenantEvent entity);

    List<TenantEventDto> toDtoList(List<TenantEvent> entities);
}
