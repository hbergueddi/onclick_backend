package com.onesley.oneclick.loyalty;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Microservice loyalty — Phase 2 §21 spec senior dev (2e service extrait).
 *
 * <p>Port 8085. DB partagée {@code oneclick_enterprise} (tables loyalty_accounts,
 * loyalty_transactions, redemptions, tiers, loyalty_rules).
 */
@SpringBootApplication
@EnableJpaAuditing
public class LoyaltyServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoyaltyServiceApplication.class, args);
    }
}
