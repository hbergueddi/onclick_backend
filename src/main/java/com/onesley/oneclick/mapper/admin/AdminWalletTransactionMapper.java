package com.onesley.oneclick.mapper.admin;

import com.onesley.oneclick.dto.admin.AdminWalletTransactionDto;
import com.onesley.oneclick.entity.admin.AdminWalletTransaction;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code AdminWalletTransaction ↔ AdminWalletTransactionDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface AdminWalletTransactionMapper {

    AdminWalletTransactionDto toDto(AdminWalletTransaction entity);

    List<AdminWalletTransactionDto> toDtoList(List<AdminWalletTransaction> entities);
}
