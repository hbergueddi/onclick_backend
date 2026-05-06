package com.onesley.oneclick.service.loyalty;

import com.onesley.oneclick.dto.loyalty.ClientLoyaltySummaryViewDto;
import com.onesley.oneclick.mapper.loyalty.ClientLoyaltySummaryViewMapper;
import com.onesley.oneclick.repository.loyalty.ClientLoyaltySummaryViewRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link ClientLoyaltySummaryView} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class ClientLoyaltySummaryViewService {

    private final ClientLoyaltySummaryViewRepository repository;
    private final ClientLoyaltySummaryViewMapper mapper;

    public ClientLoyaltySummaryViewService(ClientLoyaltySummaryViewRepository repository, ClientLoyaltySummaryViewMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public List<ClientLoyaltySummaryViewDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
