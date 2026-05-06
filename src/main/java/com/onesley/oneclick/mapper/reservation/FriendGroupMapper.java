package com.onesley.oneclick.mapper.reservation;

import com.onesley.oneclick.dto.reservation.FriendGroupDto;
import com.onesley.oneclick.entity.reservation.FriendGroup;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code FriendGroup ↔ FriendGroupDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface FriendGroupMapper {

    FriendGroupDto toDto(FriendGroup entity);

    List<FriendGroupDto> toDtoList(List<FriendGroup> entities);
}
