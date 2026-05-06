package com.onesley.oneclick.mapper.contract;

import com.onesley.oneclick.dto.contract.CompanySettingDto;
import com.onesley.oneclick.entity.contract.CompanySetting;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code CompanySetting ↔ CompanySettingDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface CompanySettingMapper {

    CompanySettingDto toDto(CompanySetting entity);

    List<CompanySettingDto> toDtoList(List<CompanySetting> entities);
}
