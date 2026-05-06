package com.onesley.oneclick.mapper.marketing;

import com.onesley.oneclick.dto.marketing.ExploreFeaturedDto;
import com.onesley.oneclick.entity.marketing.ExploreFeatured;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code ExploreFeatured ↔ ExploreFeaturedDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface ExploreFeaturedMapper {

    ExploreFeaturedDto toDto(ExploreFeatured entity);

    List<ExploreFeaturedDto> toDtoList(List<ExploreFeatured> entities);
}
