package com.onesley.oneclick.service.admin;

import com.onesley.oneclick.dto.admin.AdminWalletTransactionDto;
import com.onesley.oneclick.mapper.admin.AdminWalletTransactionMapper;
import com.onesley.oneclick.repository.admin.AdminWalletTransactionRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link AdminWalletTransaction} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class AdminWalletTransactionService {

    private final AdminWalletTransactionRepository repository;
    private final AdminWalletTransactionMapper mapper;

    public AdminWalletTransactionService(AdminWalletTransactionRepository repository, AdminWalletTransactionMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<AdminWalletTransactionDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<AdminWalletTransactionDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
