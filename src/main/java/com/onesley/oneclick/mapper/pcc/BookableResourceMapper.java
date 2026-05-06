package com.onesley.oneclick.mapper.pcc;

import com.onesley.oneclick.dto.pcc.BookableResourceDto;
import com.onesley.oneclick.entity.pcc.BookableResource;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code BookableResource ↔ BookableResourceDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface BookableResourceMapper {

    BookableResourceDto toDto(BookableResource entity);

    List<BookableResourceDto> toDtoList(List<BookableResource> entities);
}
