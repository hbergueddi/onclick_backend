package com.onesley.oneclick.mapper.auth;

import com.onesley.oneclick.dto.auth.UserRoleCreateDto;
import com.onesley.oneclick.dto.auth.UserRoleDto;
import com.onesley.oneclick.entity.auth.UserRole;
import org.mapstruct.Mapper;

import java.util.List;
import java.util.UUID;

/**
 * Mapper {@code UserRole ↔ DTO} via MapStruct.
 *
 * <p>Pattern pilote : {@code componentModel="spring"} défini globalement dans
 * {@code pom.xml} ({@code -Amapstruct.defaultComponentModel=spring}), donc le mapper
 * est injectable directement comme bean {@link org.springframework.stereotype.Component}.
 */
@Mapper
public interface UserRoleMapper {

    UserRoleDto toDto(UserRole entity);

    List<UserRoleDto> toDtoList(List<UserRole> entities);

    /**
     * Construction d'une nouvelle entité depuis un payload de création.
     * L'ID est fourni par l'appelant (généralement le service, qui demande à
     * Postgres un {@code gen_random_uuid()} ou laisse le DEFAULT s'appliquer).
     */
    default UserRole toEntity(UUID id, UserRoleCreateDto dto) {
        return new UserRole(id, dto.userId(), dto.role());
    }
}
