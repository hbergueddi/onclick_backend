package com.onesley.oneclick.modules.restaurant.internal;

import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.restaurant.api.ExploreFeaturedDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class ExploreFeaturedService {

    private final ExploreFeaturedRepository repo;

    /** Flux public Explore : uniquement les featured activés, triés par rang. */
    @Transactional(readOnly = true)
    public List<ExploreFeaturedDto> findAllEnabled() {
        return repo.findAllEnabledOrdered().stream().map(ExploreFeaturedDto::from).toList();
    }

    /** Vue admin (Pilotage Explore) : tous les featured, désactivés inclus. */
    @Transactional(readOnly = true)
    public List<ExploreFeaturedDto> findAll() {
        return repo.findAllByOrderByRankAsc().stream().map(ExploreFeaturedDto::from).toList();
    }

    public ExploreFeaturedDto upsert(ExploreFeaturedCreateDto dto) {
        ExploreFeatured f = repo.findByRestaurant(dto.restaurantId()).orElseGet(ExploreFeatured::new);
        f.setRestaurantId(dto.restaurantId());
        f.setRank(dto.rank() == null ? 0 : dto.rank());
        if (dto.enabled() != null) f.setEnabled(dto.enabled());
        f.setStartsAt(dto.startsAt());
        f.setEndsAt(dto.endsAt());
        f.setLabel(dto.label());
        f.setNotes(dto.notes());
        // created_by = auteur initial : renseigné une seule fois, jamais écrasé par
        // un éditeur ultérieur (l'upsert front renvoie le user courant à chaque save).
        if (f.getCreatedBy() == null && dto.createdBy() != null) f.setCreatedBy(dto.createdBy());
        return ExploreFeaturedDto.from(repo.save(f));
    }

    public void delete(UUID id) {
        ExploreFeatured f = repo.findById(id).orElseThrow(() -> new NotFoundException("ExploreFeatured", id));
        repo.delete(f);
    }
}
