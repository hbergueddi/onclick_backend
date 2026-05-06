package com.onesley.oneclick.service.contract;

import com.onesley.oneclick.dto.contract.OneclickHiInvoiceDto;
import com.onesley.oneclick.mapper.contract.OneclickHiInvoiceMapper;
import com.onesley.oneclick.repository.contract.OneclickHiInvoiceRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link OneclickHiInvoice} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class OneclickHiInvoiceService {

    private final OneclickHiInvoiceRepository repository;
    private final OneclickHiInvoiceMapper mapper;

    public OneclickHiInvoiceService(OneclickHiInvoiceRepository repository, OneclickHiInvoiceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<OneclickHiInvoiceDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<OneclickHiInvoiceDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
