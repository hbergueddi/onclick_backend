package com.onesley.oneclick.service.restaurant;

import com.onesley.oneclick.dto.restaurant.RestaurantDto;
import com.onesley.oneclick.mapper.restaurant.RestaurantMapper;
import com.onesley.oneclick.repository.restaurant.RestaurantRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class RestaurantService {

    private final RestaurantRepository repository;
    private final RestaurantMapper mapper;

    public RestaurantService(RestaurantRepository repository, RestaurantMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<RestaurantDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public Page<RestaurantDto> findByCity(String city, int page, int size) {
        return repository.findAllByCity(city, PageRequest.of(page, size))
            .map(mapper::toDto);
    }

    public List<RestaurantDto> findActiveByTenant(UUID tenantId) {
        return mapper.toDtoList(repository.findAllByTenantId(tenantId));
    }

    public long countByCity(String city) {
        return repository.countByCity(city);
    }
}
