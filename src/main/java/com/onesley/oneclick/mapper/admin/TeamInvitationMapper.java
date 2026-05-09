package com.onesley.oneclick.mapper.admin;

import com.onesley.oneclick.dto.admin.TeamInvitationDto;
import com.onesley.oneclick.entity.admin.TeamInvitation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Mapper {@code TeamInvitation ↔ TeamInvitationDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface TeamInvitationMapper {

    @Mapping(target = "status", source = "status.code")
    TeamInvitationDto toDto(TeamInvitation entity);

    List<TeamInvitationDto> toDtoList(List<TeamInvitation> entities);
}
