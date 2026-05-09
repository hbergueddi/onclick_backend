package com.onesley.oneclick.mapper.tenant;

import com.onesley.oneclick.dto.tenant.TenantDto;
import com.onesley.oneclick.entity.tenant.Tenant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Mapper {@link Tenant} ↔ {@link TenantDto}.
 *
 * <p>Le DTO expose le {@code code} String du statut (ex: {@code "actif"}) plutôt
 * que l'entité {@code EntityStatus} complète — la frontière API ne doit pas
 * leak l'entité interne. Le LAZY load du proxy est déclenché par MapStruct au
 * moment de la conversion, ce qui est OK dans une transaction
 * {@code @Transactional(readOnly=true)} du service.
 */
@Mapper
public interface TenantMapper {

    @Mapping(target = "status", source = "status.code")
    TenantDto toDto(Tenant entity);

    List<TenantDto> toDtoList(List<Tenant> entities);
}
