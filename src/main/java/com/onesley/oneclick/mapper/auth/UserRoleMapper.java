package com.onesley.oneclick.mapper.auth;

import com.onesley.oneclick.dto.auth.UserRoleCreateDto;
import com.onesley.oneclick.dto.auth.UserRoleDto;
import com.onesley.oneclick.entity.auth.Profile;
import com.onesley.oneclick.entity.auth.UserRole;
import org.mapstruct.Mapper;

import java.util.List;
import java.util.UUID;

/**
 * Mapper {@code UserRole ↔ DTO} via MapStruct.
 *
 * <p>Pattern : {@code componentModel="spring"} défini globalement dans
 * {@code pom.xml} ({@code -Amapstruct.defaultComponentModel=spring}), donc le mapper
 * est injectable directement comme bean {@link org.springframework.stereotype.Component}.
 *
 * <h3>Note jointures (passe 3)</h3>
 * <p>Depuis la matérialisation de {@link UserRole#getUser()}, l'entité s'instancie
 * avec un {@link Profile} (pas un UUID). Le service appelant doit fournir une
 * référence Profile — typiquement via {@code EntityManager.getReference(Profile.class, id)}
 * pour obtenir un proxy LAZY sans round-trip DB inutile.
 */
@Mapper
public interface UserRoleMapper {

    /**
     * Le DTO expose {@code userId} (UUID) plutôt que le {@link Profile} — la frontière
     * application/UI ne doit pas leak l'entité. MapStruct mappe automatiquement
     * le champ {@code userId} de l'entité (raccourci read-only insertable=false,
     * updatable=false) vers le {@code userId} du DTO sans toucher au proxy LAZY {@code user}.
     */
    UserRoleDto toDto(UserRole entity);

    List<UserRoleDto> toDtoList(List<UserRole> entities);

    /**
     * Construction d'une nouvelle entité depuis un payload de création.
     *
     * @param id   ID fourni par l'appelant (généralement {@code UUID.randomUUID()}
     *             ou laissé au DEFAULT Postgres).
     * @param user référence Profile (LAZY proxy via
     *             {@code EntityManager.getReference(Profile.class, dto.userId())} —
     *             pas de SELECT déclenché côté DB).
     * @param dto  payload de création (rôle uniquement, le userId du DTO sert à
     *             créer la référence en amont).
     */
    default UserRole toEntity(UUID id, Profile user, UserRoleCreateDto dto) {
        return new UserRole(id, user, dto.role());
    }
}
