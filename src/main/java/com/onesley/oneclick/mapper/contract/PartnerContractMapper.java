package com.onesley.oneclick.mapper.contract;

import com.onesley.oneclick.dto.contract.PartnerContractDto;
import com.onesley.oneclick.entity.contract.PartnerContract;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Mapper {@code PartnerContract ↔ PartnerContractDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface PartnerContractMapper {

    @Mapping(target = "status", source = "status.code")
    PartnerContractDto toDto(PartnerContract entity);

    List<PartnerContractDto> toDtoList(List<PartnerContract> entities);
}
