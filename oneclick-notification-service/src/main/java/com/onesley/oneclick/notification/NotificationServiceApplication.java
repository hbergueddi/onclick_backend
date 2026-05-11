package com.onesley.oneclick.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Microservice notification — Phase 2 §21 spec senior dev.
 *
 * <p>Port 8084. Consume Kafka topic {@code reservation.created}.
 * DB partagée avec oneclick-core sur {@code oneclick_enterprise}.
 */
@SpringBootApplication
@EnableKafka
@EnableJpaAuditing
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
