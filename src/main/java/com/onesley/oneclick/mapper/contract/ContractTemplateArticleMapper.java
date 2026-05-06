package com.onesley.oneclick.mapper.contract;

import com.onesley.oneclick.dto.contract.ContractTemplateArticleDto;
import com.onesley.oneclick.entity.contract.ContractTemplateArticle;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code ContractTemplateArticle ↔ ContractTemplateArticleDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface ContractTemplateArticleMapper {

    ContractTemplateArticleDto toDto(ContractTemplateArticle entity);

    List<ContractTemplateArticleDto> toDtoList(List<ContractTemplateArticle> entities);
}
