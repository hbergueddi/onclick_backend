package com.onesley.oneclick.mapper.reservation;

import com.onesley.oneclick.dto.reservation.FriendGroupMemberDto;
import com.onesley.oneclick.entity.reservation.FriendGroupMember;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code FriendGroupMember ↔ FriendGroupMemberDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface FriendGroupMemberMapper {

    FriendGroupMemberDto toDto(FriendGroupMember entity);

    List<FriendGroupMemberDto> toDtoList(List<FriendGroupMember> entities);
}
