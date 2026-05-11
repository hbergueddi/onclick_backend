package com.onesley.oneclick.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Microservice payment — Phase 2 §21 spec senior dev (3e service extrait).
 *
 * <p>Port 8086. Isolation PCI : ce service est volontairement séparé pour
 * faciliter le scoping de l'audit sécurité (real money flows).
 * Tables : payments, payment_methods, refunds, payment_transactions.
 */
@SpringBootApplication
@EnableJpaAuditing
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
