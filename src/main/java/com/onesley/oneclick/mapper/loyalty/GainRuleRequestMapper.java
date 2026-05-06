package com.onesley.oneclick.mapper.loyalty;

import com.onesley.oneclick.dto.loyalty.GainRuleRequestDto;
import com.onesley.oneclick.entity.loyalty.GainRuleRequest;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code GainRuleRequest ↔ GainRuleRequestDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface GainRuleRequestMapper {

    GainRuleRequestDto toDto(GainRuleRequest entity);

    List<GainRuleRequestDto> toDtoList(List<GainRuleRequest> entities);
}
