package com.onesley.oneclick.mapper.contract;

import com.onesley.oneclick.dto.contract.ContractHistoryDto;
import com.onesley.oneclick.entity.contract.ContractHistory;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code ContractHistory ↔ ContractHistoryDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface ContractHistoryMapper {

    ContractHistoryDto toDto(ContractHistory entity);

    List<ContractHistoryDto> toDtoList(List<ContractHistory> entities);
}
