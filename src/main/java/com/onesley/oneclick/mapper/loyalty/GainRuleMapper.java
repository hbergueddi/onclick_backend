package com.onesley.oneclick.mapper.loyalty;

import com.onesley.oneclick.dto.loyalty.GainRuleDto;
import com.onesley.oneclick.entity.loyalty.GainRule;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code GainRule ↔ GainRuleDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface GainRuleMapper {

    GainRuleDto toDto(GainRule entity);

    List<GainRuleDto> toDtoList(List<GainRule> entities);
}
