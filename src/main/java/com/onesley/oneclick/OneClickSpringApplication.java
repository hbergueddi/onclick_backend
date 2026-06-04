package com.onesley.oneclick;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * Boot principal oneclick-core (Phase 4 spec senior dev §1+§21).
 *
 * <p>{@link EnableScheduling} active les jobs {@code @Scheduled} qui seront
 * automatiquement audités par {@code JobExecutionAspect} (§15 spec — table
 * {@code job_executions}).
 */
@SpringBootApplication
@EnableScheduling
public class OneClickSpringApplication {

    public static void main(String[] args) {
        SpringApplication.run(OneClickSpringApplication.class, args);
    }

    /**
     * Horloge applicative injectable (UTC) — permet aux services temporels (ex:
     * {@code NoShowDisputeService}, fenêtres de contestation) d'être testables en
     * substituant un {@code Clock.fixed(...)}. Défini sur la classe racine (hors
     * frontière de module Modulith) car partagé par plusieurs modules.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

}
