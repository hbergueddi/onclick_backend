package com.onesley.oneclick.mapper.loyalty;

import com.onesley.oneclick.dto.loyalty.UserFavoriteDto;
import com.onesley.oneclick.entity.loyalty.UserFavorite;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code UserFavorite ↔ UserFavoriteDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface UserFavoriteMapper {

    UserFavoriteDto toDto(UserFavorite entity);

    List<UserFavoriteDto> toDtoList(List<UserFavorite> entities);
}
