package com.onesley.oneclick.mapper.pcc;

import com.onesley.oneclick.dto.pcc.PccFamilyMemberDto;
import com.onesley.oneclick.entity.pcc.PccFamilyMember;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code PccFamilyMember ↔ PccFamilyMemberDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface PccFamilyMemberMapper {

    PccFamilyMemberDto toDto(PccFamilyMember entity);

    List<PccFamilyMemberDto> toDtoList(List<PccFamilyMember> entities);
}
