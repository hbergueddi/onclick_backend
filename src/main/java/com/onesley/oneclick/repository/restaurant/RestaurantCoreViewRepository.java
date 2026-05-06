package com.onesley.oneclick.repository.restaurant;

import com.onesley.oneclick.entity.restaurant.RestaurantCoreView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository read-only pour la vue {@link RestaurantCoreView}.
 *
 * <p>On hérite directement de {@link Repository} (interface basique) plutôt que
 * de {@code JpaRepository} pour <i>retirer du contrat les méthodes d'écriture</i>
 * ({@code save}, {@code delete}). Sécurité : on ne peut pas accidentellement appeler
 * un mutator sur une entité issue d'une vue, ce qui éviterait des erreurs runtime
 * type "cannot insert into a view".
 *
 * <p>Pattern recommandé pour toutes les vues du schéma (7 vues + 1 matview).
 */
public interface RestaurantCoreViewRepository extends Repository<RestaurantCoreView, UUID> {

    Page<RestaurantCoreView> findAllByCity(String city, Pageable pageable);

    List<RestaurantCoreView> findAllByStatus(String status);

    long count();
}
