package com.onesley.oneclick.mapper.contract;

import com.onesley.oneclick.dto.contract.ContractTemplateDto;
import com.onesley.oneclick.entity.contract.ContractTemplate;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code ContractTemplate ↔ ContractTemplateDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface ContractTemplateMapper {

    ContractTemplateDto toDto(ContractTemplate entity);

    List<ContractTemplateDto> toDtoList(List<ContractTemplate> entities);
}
