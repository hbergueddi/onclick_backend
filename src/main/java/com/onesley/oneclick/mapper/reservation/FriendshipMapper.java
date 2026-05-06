package com.onesley.oneclick.mapper.reservation;

import com.onesley.oneclick.dto.reservation.FriendshipDto;
import com.onesley.oneclick.entity.reservation.Friendship;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code Friendship ↔ FriendshipDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface FriendshipMapper {

    FriendshipDto toDto(Friendship entity);

    List<FriendshipDto> toDtoList(List<Friendship> entities);
}
