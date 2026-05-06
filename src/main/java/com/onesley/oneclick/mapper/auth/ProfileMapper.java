package com.onesley.oneclick.mapper.auth;

import com.onesley.oneclick.dto.auth.ProfileDto;
import com.onesley.oneclick.dto.auth.ProfileUpdateDto;
import com.onesley.oneclick.entity.auth.Profile;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

/**
 * Mapper {@code Profile ↔ DTO}.
 *
 * <p>L'usage de {@link MappingTarget} + {@code NullValuePropertyMappingStrategy.IGNORE}
 * permet un PATCH partiel : seuls les champs non-null du DTO sont copiés sur
 * l'entité. C'est le pattern senior pour respecter la sémantique HTTP PATCH.
 *
 * <p>Note : MapStruct n'écrira pas dans {@code id}, {@code createdAt}, {@code updatedAt}
 * et autres champs absents du {@link ProfileUpdateDto} (rien à copier).
 */
@Mapper
public interface ProfileMapper {

    ProfileDto toDto(Profile entity);

    List<ProfileDto> toDtoList(List<Profile> entities);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void applyPatch(ProfileUpdateDto dto, @MappingTarget Profile entity);
}
