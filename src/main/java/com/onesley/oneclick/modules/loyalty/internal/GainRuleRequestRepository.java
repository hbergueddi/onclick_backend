package com.onesley.oneclick.modules.loyalty.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GainRuleRequestRepository
        extends JpaRepository<GainRuleRequest, UUID>,
                JpaSpecificationExecutor<GainRuleRequest> {

    List<GainRuleRequest> findAllByRestaurantIdAndDeletedAtIsNull(UUID restaurantId);

    List<GainRuleRequest> findAllByStatusAndDeletedAtIsNull(String status);
}
