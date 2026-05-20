package com.onesley.oneclick.audit;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import lombok.Getter;

/**
 * Audit niveau minimal — uniquement {@code created_at} (sans {@code updated_at}).
 *
 * <p>Couvre les ~28 tables du schéma legacy Supabase qui ont {@code created_at}
 * mais <b>pas</b> {@code updated_at} (ex: logs, audit append-only, événements
 * historiques). Pour ces tables, il n'y a pas de notion d'update — la row est
 * créée puis immuable, ou ses modifications ne sont pas trackées au timestamp
 * (mais peuvent l'être au niveau diff dans une autre table).
 *
 * <h3>Tables ciblées</h3>
 *
 * <p>Identifiées par audit DB ({@code C---}) :
 * action_logs, admin_notifications, admin_wallet_transactions, ai_usage,
 * ai_usage_bypass, chat_messages, contract_disabled_articles, contract_history,
 * contract_template_articles, elite_rsvps, expired_points, friend_group_members,
 * lifecycle_events, monitor_logs, notifications, point_distributions,
 * point_gifts, quota_change_logs, redemption_events, redemption_otp_requests,
 * reservation_guests, restaurant_expired_pool, system_alerts, system_health_checks,
 * team_invitations, tenant_admins, user_favorites.
 *
 * <h3>ddl-auto=validate</h3>
 *
 * <p>Pour qu'une entité étende {@code CreatedAtEntity}, sa table SQL doit posséder
 * la colonne {@code created_at timestamptz NOT NULL}.
 *
 * <h3>Hiérarchie</h3>
 *
 * <pre>
 * CreatedAtEntity (created_at) ← cette classe
 * ├── CreatedAuthorEntity (+ created_by) ← document_versions
 * │ └── CreatedAuditedEntity (+ modified_by) ← admin_audit_log, scanned_tickets, …
 * └── TimestampedEntity (+ updated_at)
 * └── AuditedEntity (+ created_by + modified_by)
 * </pre>
 *
 * @see CreatedAuthorEntity
 * @see TimestampedEntity
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
public abstract class CreatedAtEntity {

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
