package com.onesley.oneclick.mapper.admin;

import com.onesley.oneclick.dto.admin.QuotaChangeLogDto;
import com.onesley.oneclick.entity.admin.QuotaChangeLog;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code QuotaChangeLog ↔ QuotaChangeLogDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface QuotaChangeLogMapper {

    QuotaChangeLogDto toDto(QuotaChangeLog entity);

    List<QuotaChangeLogDto> toDtoList(List<QuotaChangeLog> entities);
}
