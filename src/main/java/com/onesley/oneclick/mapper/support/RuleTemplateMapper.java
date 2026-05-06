package com.onesley.oneclick.mapper.support;

import com.onesley.oneclick.dto.support.RuleTemplateDto;
import com.onesley.oneclick.entity.support.RuleTemplate;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code RuleTemplate ↔ RuleTemplateDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface RuleTemplateMapper {

    RuleTemplateDto toDto(RuleTemplate entity);

    List<RuleTemplateDto> toDtoList(List<RuleTemplate> entities);
}
