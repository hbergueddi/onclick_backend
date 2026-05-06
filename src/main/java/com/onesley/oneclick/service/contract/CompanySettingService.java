package com.onesley.oneclick.service.contract;

import com.onesley.oneclick.dto.contract.CompanySettingDto;
import com.onesley.oneclick.mapper.contract.CompanySettingMapper;
import com.onesley.oneclick.repository.contract.CompanySettingRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link CompanySetting} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class CompanySettingService {

    private final CompanySettingRepository repository;
    private final CompanySettingMapper mapper;

    public CompanySettingService(CompanySettingRepository repository, CompanySettingMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<CompanySettingDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<CompanySettingDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
