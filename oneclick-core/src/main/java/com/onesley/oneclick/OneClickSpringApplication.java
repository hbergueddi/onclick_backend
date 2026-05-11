package com.onesley.oneclick;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

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

}
