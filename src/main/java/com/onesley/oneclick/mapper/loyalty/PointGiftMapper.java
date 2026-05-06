package com.onesley.oneclick.mapper.loyalty;

import com.onesley.oneclick.dto.loyalty.PointGiftDto;
import com.onesley.oneclick.entity.loyalty.PointGift;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code PointGift ↔ PointGiftDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface PointGiftMapper {

    PointGiftDto toDto(PointGift entity);

    List<PointGiftDto> toDtoList(List<PointGift> entities);
}
