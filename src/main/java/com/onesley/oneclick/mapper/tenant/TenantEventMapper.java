package com.onesley.oneclick.mapper.tenant;

import com.onesley.oneclick.dto.tenant.TenantEventDto;
import com.onesley.oneclick.entity.tenant.TenantEvent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Mapper {@link TenantEvent} ↔ {@link TenantEventDto}.
 *
 * <p>{@code status} : DTO expose le code String de l'EntityStatus (i18n côté client).
 */
@Mapper
public interface TenantEventMapper {

    @Mapping(target = "status", source = "status.code")
    TenantEventDto toDto(TenantEvent entity);

    List<TenantEventDto> toDtoList(List<TenantEvent> entities);
}
