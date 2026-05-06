package com.onesley.oneclick.audit;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Active le système d'audit JPA Spring Data (annotations {@code @CreatedDate},
 * {@code @CreatedBy}, {@code @LastModifiedDate}, {@code @LastModifiedBy}).
 *
 * <p>L'attribut {@code auditorAwareRef} pointe vers le bean
 * {@link SecurityContextAuditorAware} (bean {@code "auditorAware"}) qui fournit
 * l'UUID du user authentifié.
 *
 * <p>Activé pour toute entité héritant de {@link BaseEntity}.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaAuditingConfig {
}
