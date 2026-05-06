package com.onesley.oneclick.mapper.tenant;

import com.onesley.oneclick.dto.tenant.TenantBrandingDto;
import com.onesley.oneclick.entity.tenant.TenantBranding;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code TenantBranding ↔ TenantBrandingDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface TenantBrandingMapper {

    TenantBrandingDto toDto(TenantBranding entity);

    List<TenantBrandingDto> toDtoList(List<TenantBranding> entities);
}
