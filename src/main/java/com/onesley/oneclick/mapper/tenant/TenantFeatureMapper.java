package com.onesley.oneclick.mapper.tenant;

import com.onesley.oneclick.dto.tenant.TenantFeatureDto;
import com.onesley.oneclick.entity.tenant.TenantFeature;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code TenantFeature ↔ TenantFeatureDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface TenantFeatureMapper {

    TenantFeatureDto toDto(TenantFeature entity);

    List<TenantFeatureDto> toDtoList(List<TenantFeature> entities);
}
