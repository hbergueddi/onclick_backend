package com.onesley.oneclick.mapper.loyalty;

import com.onesley.oneclick.dto.loyalty.AiUsageDto;
import com.onesley.oneclick.entity.loyalty.AiUsage;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code AiUsage ↔ AiUsageDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface AiUsageMapper {

    AiUsageDto toDto(AiUsage entity);

    List<AiUsageDto> toDtoList(List<AiUsage> entities);
}
