package com.onesley.oneclick.mapper.admin;

import com.onesley.oneclick.dto.admin.EmailBounceDto;
import com.onesley.oneclick.entity.admin.EmailBounce;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code EmailBounce ↔ EmailBounceDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface EmailBounceMapper {

    EmailBounceDto toDto(EmailBounce entity);

    List<EmailBounceDto> toDtoList(List<EmailBounce> entities);
}
