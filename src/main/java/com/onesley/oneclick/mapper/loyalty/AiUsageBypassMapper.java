package com.onesley.oneclick.mapper.loyalty;

import com.onesley.oneclick.dto.loyalty.AiUsageBypassDto;
import com.onesley.oneclick.entity.loyalty.AiUsageBypass;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code AiUsageBypass ↔ AiUsageBypassDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface AiUsageBypassMapper {

    AiUsageBypassDto toDto(AiUsageBypass entity);

    List<AiUsageBypassDto> toDtoList(List<AiUsageBypass> entities);
}
