package com.onesley.oneclick.mapper.contract;

import com.onesley.oneclick.dto.contract.ContractDisabledArticleDto;
import com.onesley.oneclick.entity.contract.ContractDisabledArticle;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code ContractDisabledArticle ↔ ContractDisabledArticleDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface ContractDisabledArticleMapper {

    ContractDisabledArticleDto toDto(ContractDisabledArticle entity);

    List<ContractDisabledArticleDto> toDtoList(List<ContractDisabledArticle> entities);
}
