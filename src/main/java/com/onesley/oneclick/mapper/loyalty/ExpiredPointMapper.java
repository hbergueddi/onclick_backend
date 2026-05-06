package com.onesley.oneclick.mapper.loyalty;

import com.onesley.oneclick.dto.loyalty.ExpiredPointDto;
import com.onesley.oneclick.entity.loyalty.ExpiredPoint;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code ExpiredPoint ↔ ExpiredPointDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface ExpiredPointMapper {

    ExpiredPointDto toDto(ExpiredPoint entity);

    List<ExpiredPointDto> toDtoList(List<ExpiredPoint> entities);
}
