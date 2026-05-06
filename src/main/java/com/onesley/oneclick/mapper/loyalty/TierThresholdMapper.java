package com.onesley.oneclick.mapper.loyalty;

import com.onesley.oneclick.dto.loyalty.TierThresholdDto;
import com.onesley.oneclick.entity.loyalty.TierThreshold;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code TierThreshold ↔ TierThresholdDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface TierThresholdMapper {

    TierThresholdDto toDto(TierThreshold entity);

    List<TierThresholdDto> toDtoList(List<TierThreshold> entities);
}
