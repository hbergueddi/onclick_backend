package com.onesley.oneclick.service.restaurant;

import com.onesley.oneclick.dto.restaurant.RestaurantCoreDto;
import com.onesley.oneclick.mapper.restaurant.RestaurantCoreMapper;
import com.onesley.oneclick.repository.restaurant.RestaurantCoreViewRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class RestaurantCoreService {

    private final RestaurantCoreViewRepository repository;
    private final RestaurantCoreMapper mapper;

    public RestaurantCoreService(RestaurantCoreViewRepository repository,
                                 RestaurantCoreMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Page<RestaurantCoreDto> findByCity(String city, int page, int size) {
        return repository.findAllByCity(city, PageRequest.of(page, size))
            .map(mapper::toDto);
    }

    public List<RestaurantCoreDto> findActive() {
        return mapper.toDtoList(repository.findAllByStatus("actif"));
    }

    public long countAll() {
        return repository.count();
    }
}
