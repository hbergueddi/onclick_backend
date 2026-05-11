package com.onesley.oneclick.etl.steps;

import com.onesley.oneclick.etl.EtlStep;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Step 8 — Phase 13.B : tables secondaires (notifications, device_tokens,
 * friendships, referrals, support_tickets, events, event_participations,
 * resources, resource_bookings, audit_logs).
 *
 * <p>Volume estimé : ~50k rows (essentiellement notifications 40019 + audit 3875 + friendships 1300).
 *
 * <p>Transformations majeures :
 * <ul>
 *   <li>Friendships : legacy {@code requester_id/addressee_id} → enterprise {@code user1_id/user2_id}
 *       avec swap pour respecter CHECK {@code user1_id < user2_id}</li>
 *   <li>Notifications : refonte importante (legacy a 8 cols, enterprise 10 — drop {@code read} bool
 *       au profit de {@code read_at} timestamp)</li>
 *   <li>tenant_events → events : {@code event_date} → {@code event_at}, rename + filter rsvp_enabled</li>
 *   <li>event_rsvps → event_participations : {@code attending} → {@code going}</li>
 *   <li>bookable_resources → resources : drop pricing jsonb (placé dans resource_pricings séparé),
 *       resource_type enum → text</li>
 *   <li>action_logs + admin_audit_log → audit_logs (concat de 2 sources avec UNION)</li>
 * </ul>
 */
@Component
@Profile("etl")
public class PhaseBStep extends EtlStep.AbstractEtlStep {

    public PhaseBStep(JdbcTemplate jdbc, TransactionTemplate tx) {
        super(jdbc, tx);
    }

    @Override public String getName() { return "phaseB (notifications+social+events+resources+audit)"; }

    @Override
    public String[] getTargetTables() {
        return new String[] {
            "audit_logs",
            "resource_bookings",
            "resources",
            "event_participations",
            "events",
            "support_tickets",
            "referrals",
            "friendships",
            "device_tokens",
            "notifications"
        };
    }

    @Override
    public long migrate() {
        long total = 0;

        // ─── notifications (40019) ───────────────────────────────────────────
        // legacy : user_id, title, message, type, read (bool), link, restaurant_id
        // enterprise : recipient_user_id, type (CHECK), channel (CHECK), title, body, link,
        //              metadata jsonb, read_at timestamp
        long notifs = jdbc.update("""
            INSERT INTO notifications (id, recipient_user_id, type, channel, title, body, link, metadata, read_at, created_at)
            SELECT
              n.id,
              n.user_id,
              CASE
                WHEN n.type ILIKE '%reservation%' OR n.type ILIKE '%resa%' THEN 'reservation'
                WHEN n.type ILIKE '%loyalty%' OR n.type ILIKE '%point%' THEN 'loyalty'
                WHEN n.type ILIKE '%promo%' OR n.type ILIKE '%offer%' THEN 'promotion'
                WHEN n.type ILIKE '%community%' OR n.type ILIKE '%friend%' THEN 'community'
                WHEN n.type ILIKE '%support%' OR n.type ILIKE '%ticket%' THEN 'support'
                WHEN n.type ILIKE '%announce%' THEN 'announcement'
                ELSE 'system'
              END AS type,
              'inapp' AS channel,
              COALESCE(NULLIF(n.title, ''), 'Notification'),
              COALESCE(NULLIF(n.message, ''), ''),
              n.link,
              CASE WHEN n.restaurant_id IS NOT NULL
                   THEN jsonb_build_object('restaurant_id', n.restaurant_id::text)
                   ELSE '{}'::jsonb END,
              CASE WHEN COALESCE(n.read, false) THEN COALESCE(n.created_at, now()) ELSE NULL END,
              COALESCE(n.created_at, now())
            FROM legacy.notifications n
            WHERE EXISTS (SELECT 1 FROM users u WHERE u.id = n.user_id)
            """);
        log.info("  notifications : {} rows", notifs);
        total += notifs;

        // ─── device_tokens (109) — UNIQUE(token), dédup ROW_NUMBER ───────────
        long dt = jdbc.update("""
            WITH ranked AS (
              SELECT
                dt.id, dt.user_id, dt.token, dt.platform, dt.app_id, dt.created_at, dt.updated_at,
                ROW_NUMBER() OVER (PARTITION BY dt.token ORDER BY dt.created_at NULLS LAST) AS rn
              FROM legacy.device_tokens dt
              WHERE EXISTS (SELECT 1 FROM users u WHERE u.id = dt.user_id)
                AND dt.token IS NOT NULL AND dt.token != ''
            )
            INSERT INTO device_tokens (id, user_id, token, platform, app_id, created_at, updated_at)
            SELECT
              id, user_id, token,
              CASE WHEN platform IN ('ios','android','web') THEN platform ELSE 'ios' END,
              app_id,
              COALESCE(created_at, now()),
              COALESCE(updated_at, now())
            FROM ranked
            WHERE rn = 1
            """);
        log.info("  device_tokens : {} rows", dt);
        total += dt;

        // ─── friendships (1300) — canonical user1 < user2 ─────────────────
        // legacy : requester_id, addressee_id, status (accepted/pending/refused)
        // enterprise CHECK : user1_id < user2_id (string compare = byte-wise PG)
        long fs = jdbc.update("""
            INSERT INTO friendships (id, user1_id, user2_id, status, accepted_at, created_at, updated_at)
            SELECT
              f.id,
              LEAST(f.requester_id::text, f.addressee_id::text)::uuid,
              GREATEST(f.requester_id::text, f.addressee_id::text)::uuid,
              CASE f.status
                WHEN 'accepted' THEN 'accepted'
                WHEN 'pending'  THEN 'pending'
                WHEN 'refused'  THEN 'declined'
                WHEN 'blocked'  THEN 'blocked'
                ELSE 'pending'
              END,
              CASE WHEN f.status = 'accepted' THEN COALESCE(f.updated_at, f.created_at) ELSE NULL END,
              COALESCE(f.created_at, now()),
              COALESCE(f.updated_at, now())
            FROM legacy.friendships f
            WHERE EXISTS (SELECT 1 FROM users u WHERE u.id = f.requester_id)
              AND EXISTS (SELECT 1 FROM users u WHERE u.id = f.addressee_id)
              AND f.requester_id != f.addressee_id
            """);
        log.info("  friendships : {} rows", fs);
        total += fs;

        // ─── referrals (12) ──────────────────────────────────────────────────
        long ref = jdbc.update("""
            INSERT INTO referrals (id, referrer_id, referred_user_id, referral_code, status, activated_at, created_at, updated_at)
            SELECT
              r.id,
              r.referrer_id,
              r.referred_user_id,
              -- Generate code from id if NULL (legacy n'a pas le code en colonne)
              COALESCE(NULLIF(r.referred_phone, ''), 'REF-' || substring(r.id::text, 1, 8)),
              CASE r.status
                WHEN 'actif'      THEN 'activated'
                WHEN 'en_attente' THEN 'pending'
                WHEN 'expired'    THEN 'expired'
                ELSE 'pending'
              END,
              r.activated_at,
              COALESCE(r.created_at, now()),
              COALESCE(r.created_at, now())
            FROM legacy.referrals r
            WHERE EXISTS (SELECT 1 FROM users u WHERE u.id = r.referrer_id)
            """);
        log.info("  referrals : {} rows", ref);
        total += ref;

        // ─── support_tickets (4) ─────────────────────────────────────────────
        // legacy : client_id → opened_by, status 'ouvert' → 'open'
        long st = jdbc.update("""
            INSERT INTO support_tickets (id, opened_by, category, priority, status, subject, resolved_at, closed_at,
                                          assigned_to, created_at, updated_at)
            SELECT
              t.id,
              t.client_id,
              COALESCE(NULLIF(t.category, ''), 'general'),
              CASE COALESCE(t.priority, 'normal')
                WHEN 'low'    THEN 'low'
                WHEN 'normal' THEN 'normal'
                WHEN 'high'   THEN 'high'
                WHEN 'urgent' THEN 'urgent'
                ELSE 'normal'
              END,
              CASE COALESCE(t.status, 'open')
                WHEN 'ouvert'      THEN 'open'
                WHEN 'open'        THEN 'open'
                WHEN 'in_progress' THEN 'in_progress'
                WHEN 'resolved'    THEN 'resolved'
                WHEN 'closed'      THEN 'closed'
                ELSE 'open'
              END,
              COALESCE(NULLIF(t.subject, ''), 'Sans sujet'),
              NULL,
              NULL,
              NULL,
              COALESCE(t.created_at, now()),
              COALESCE(t.updated_at, now())
            FROM legacy.support_tickets t
            WHERE EXISTS (SELECT 1 FROM users u WHERE u.id = t.client_id)
            """);
        log.info("  support_tickets : {} rows", st);
        total += st;

        // ─── events (18) — depuis tenant_events ──────────────────────────────
        // CHECK enterprise.events.capacity > 0
        long ev = jdbc.update("""
            INSERT INTO events (id, tenant_id, title, description, event_type, event_at, capacity, created_at, updated_at)
            SELECT
              te.id,
              te.tenant_id,
              COALESCE(NULLIF(te.title, ''), 'Événement'),
              te.description,
              te.category,
              COALESCE(te.event_date, now() + interval '1 month'),
              GREATEST(COALESCE(te.capacity, 1), 1),
              COALESCE(te.created_at, now()),
              COALESCE(te.updated_at, now())
            FROM legacy.tenant_events te
            WHERE EXISTS (SELECT 1 FROM tenants t WHERE t.id = te.tenant_id)
            """);
        log.info("  events : {} rows", ev);
        total += ev;

        // ─── event_participations (2) — depuis event_rsvps ───────────────────
        long ep = jdbc.update("""
            INSERT INTO event_participations (id, event_id, user_id, status, created_at, updated_at)
            SELECT
              er.id,
              er.event_id,
              er.user_id,
              CASE er.status
                WHEN 'attending' THEN 'going'
                WHEN 'maybe'     THEN 'maybe'
                WHEN 'declined'  THEN 'declined'
                WHEN 'attended'  THEN 'attended'
                ELSE 'going'
              END,
              COALESCE(er.created_at, now()),
              COALESCE(er.updated_at, now())
            FROM legacy.event_rsvps er
            WHERE EXISTS (SELECT 1 FROM events e WHERE e.id = er.event_id)
              AND EXISTS (SELECT 1 FROM users u WHERE u.id = er.user_id)
            """);
        log.info("  event_participations : {} rows", ep);
        total += ep;

        // ─── resources (33) — depuis bookable_resources ──────────────────────
        long res = jdbc.update("""
            INSERT INTO resources (id, tenant_id, resource_type, name, capacity, enabled, created_at, updated_at)
            SELECT
              br.id,
              br.tenant_id,
              br.resource_type::text,
              COALESCE(NULLIF(br.name, ''), 'Ressource'),
              br.capacity,
              COALESCE(br.enabled, true),
              COALESCE(br.created_at, now()),
              COALESCE(br.updated_at, now())
            FROM legacy.bookable_resources br
            WHERE EXISTS (SELECT 1 FROM tenants t WHERE t.id = br.tenant_id)
            """);
        log.info("  resources : {} rows", res);
        total += res;

        // ─── resource_bookings (76) ──────────────────────────────────────────
        // legacy.status enum FR → text EN, CHECK end_at > start_at
        long rb = jdbc.update("""
            INSERT INTO resource_bookings (id, resource_id, organizer_id, start_at, end_at, status, notes, created_at, updated_at)
            SELECT
              rb.id,
              rb.resource_id,
              rb.organizer_id,
              rb.start_at,
              rb.end_at,
              CASE rb.status::text
                WHEN 'demandee'  THEN 'pending'
                WHEN 'confirmee' THEN 'confirmed'
                WHEN 'honoree'   THEN 'completed'
                WHEN 'no_show'   THEN 'no_show'
                WHEN 'annulee'   THEN 'cancelled'
                ELSE 'pending'
              END,
              rb.notes,
              COALESCE(rb.created_at, now()),
              COALESCE(rb.updated_at, now())
            FROM legacy.resource_bookings rb
            WHERE EXISTS (SELECT 1 FROM resources r WHERE r.id = rb.resource_id)
              AND EXISTS (SELECT 1 FROM users u WHERE u.id = rb.organizer_id)
              AND rb.end_at > rb.start_at
            """);
        log.info("  resource_bookings : {} rows", rb);
        total += rb;

        // ─── audit_logs : concat de action_logs (3847) + admin_audit_log (28) ─
        // Sources legacy DIFFÉRENTES, on les concatène en 1 seule table enterprise.
        long al1 = jdbc.update("""
            INSERT INTO audit_logs (id, user_id, entity_type, entity_id, action, diff, ip_address, created_at)
            SELECT
              al.id,
              al.user_id,
              COALESCE(al.type, 'unknown'),
              al.restaurant_id,
              COALESCE(al.action, 'event'),
              CASE WHEN al.details IS NOT NULL AND al.details != ''
                   THEN jsonb_build_object('details', al.details, 'member_name', al.member_name)
                   ELSE '{}'::jsonb END,
              al.ip,
              COALESCE(al.created_at, now())
            FROM legacy.action_logs al
            WHERE EXISTS (SELECT 1 FROM users u WHERE u.id = al.user_id)
            """);
        log.info("  audit_logs (from action_logs) : {} rows", al1);
        total += al1;

        long al2 = jdbc.update("""
            INSERT INTO audit_logs (id, user_id, tenant_id, entity_type, entity_id, action, diff, ip_address, user_agent, created_at)
            SELECT
              aal.id,
              aal.actor_id,
              aal.tenant_id,
              COALESCE(aal.entity_type, 'unknown'),
              aal.entity_id,
              aal.action,
              COALESCE(aal.diff, '{}'::jsonb),
              aal.ip,
              aal.user_agent,
              COALESCE(aal.created_at, now())
            FROM legacy.admin_audit_log aal
            WHERE EXISTS (SELECT 1 FROM users u WHERE u.id = aal.actor_id)
            """);
        log.info("  audit_logs (from admin_audit_log) : {} rows", al2);
        total += al2;

        return total;
    }

    @Override
    public void validate() {
        // Sanity check basique
        long notifs = countTarget("notifications");
        long legacyNotifs = countLegacy("notifications");
        if (notifs < legacyNotifs * 95 / 100) {
            throw new IllegalStateException(
                "notifications : enterprise=" + notifs + " vs legacy=" + legacyNotifs + " (perte > 5%)");
        }
    }
}
