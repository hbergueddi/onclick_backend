package com.onesley.oneclick.mapper.admin;

import com.onesley.oneclick.dto.admin.SystemAlertRuleDto;
import com.onesley.oneclick.entity.admin.SystemAlertRule;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code SystemAlertRule ↔ SystemAlertRuleDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface SystemAlertRuleMapper {

    SystemAlertRuleDto toDto(SystemAlertRule entity);

    List<SystemAlertRuleDto> toDtoList(List<SystemAlertRule> entities);
}
