package com.onesley.oneclick.mapper.admin;

import com.onesley.oneclick.dto.admin.DeviceTokenDto;
import com.onesley.oneclick.entity.admin.DeviceToken;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code DeviceToken ↔ DeviceTokenDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface DeviceTokenMapper {

    DeviceTokenDto toDto(DeviceToken entity);

    List<DeviceTokenDto> toDtoList(List<DeviceToken> entities);
}
