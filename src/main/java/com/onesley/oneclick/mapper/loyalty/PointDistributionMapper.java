package com.onesley.oneclick.mapper.loyalty;

import com.onesley.oneclick.dto.loyalty.PointDistributionDto;
import com.onesley.oneclick.entity.loyalty.PointDistribution;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Mapper {@code PointDistribution ↔ PointDistributionDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface PointDistributionMapper {

    @Mapping(target = "status", source = "status.code")
    PointDistributionDto toDto(PointDistribution entity);

    List<PointDistributionDto> toDtoList(List<PointDistribution> entities);
}
