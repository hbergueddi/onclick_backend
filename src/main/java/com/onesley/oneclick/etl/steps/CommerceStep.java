package com.onesley.oneclick.etl.steps;

import com.onesley.oneclick.etl.EtlStep;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Step 7 — Commerce : Offers + Contracts + Invoices + InvoiceLines + WalletTransactions.
 *
 * <p>Transformations clés :
 * <ul>
 *   <li>{@code offers} : legacy.pts (points) → ignored ; legacy.is_active → enabled ;
 *       starts_at NULL → use now() ; expires_at NULL → use 1 year from now ;
 *       check expires_at > starts_at (skip si dates malformées)</li>
 *   <li>{@code partner_contracts} → {@code contracts} : status FR → EN ;
 *       contract_start/end → starts_at/ends_at ; on garde uniquement les colonnes core</li>
 *   <li>{@code oneclick_hi_invoices} → {@code invoices} : period_month → period_start/end
 *       (1 mois) ; total_ca → subtotal ; status FR → EN</li>
 *   <li>{@code legacy.invoice_lines} → {@code invoice_lines} : unit_price_ht → unit_price ;
 *       drop total_ht (calculé en GENERATED col) + description</li>
 *   <li>{@code admin_wallet_transactions} → {@code wallet_transactions} : amount>0 = credit,
 *       reason "commission_scan" → type="commission"</li>
 * </ul>
 */
@Component
@Profile("etl")
public class CommerceStep extends EtlStep.AbstractEtlStep {

    public CommerceStep(JdbcTemplate jdbc, TransactionTemplate tx) {
        super(jdbc, tx);
    }

    @Override public String getName() { return "commerce (offers+contracts+invoices+wallet_tx)"; }

    @Override
    public String[] getTargetTables() {
        return new String[] { "wallet_transactions", "invoice_lines", "invoices", "contracts", "offers" };
    }

    @Override
    public long migrate() {
        long total = 0;

        // ─── offers (84) ─────────────────────────────────────────────────────
        // CHECK : expires_at > starts_at
        long offers = jdbc.update("""
            INSERT INTO offers (id, restaurant_id, title, description, starts_at, expires_at, enabled, created_at, updated_at)
            SELECT
              o.id,
              o.restaurant_id,
              COALESCE(NULLIF(o.title, ''), 'Offre'),
              o.description,
              COALESCE(o.starts_at, now() - interval '1 day'),
              COALESCE(o.expires_at, now() + interval '1 year'),
              COALESCE(o.is_active, true),
              COALESCE(o.created_at, now()),
              COALESCE(o.updated_at, now())
            FROM legacy.offers o
            WHERE EXISTS (SELECT 1 FROM restaurants r WHERE r.id = o.restaurant_id)
              AND COALESCE(o.expires_at, now() + interval '1 year') > COALESCE(o.starts_at, now() - interval '1 day')
            """);
        log.info("  offers : {} rows", offers);
        total += offers;

        // ─── contracts (31) ──────────────────────────────────────────────────
        // legacy.status FR → enterprise EN ; CHECK : ends_at IS NULL OR ends_at >= starts_at
        long contracts = jdbc.update("""
            INSERT INTO contracts (id, restaurant_id, contract_number, commission_rate, starts_at, ends_at,
                                   status, created_at, updated_at)
            SELECT
              pc.id,
              pc.restaurant_id,
              COALESCE(NULLIF(pc.contract_number, ''), 'CTR-' || substring(pc.id::text, 1, 8)),
              GREATEST(LEAST(COALESCE(pc.commission_rate, 3.0), 100), 0),  -- clamp [0,100]
              COALESCE(pc.contract_start, current_date),
              CASE
                WHEN pc.contract_end IS NULL THEN NULL
                WHEN pc.contract_end >= COALESCE(pc.contract_start, current_date) THEN pc.contract_end
                ELSE NULL  -- skip dates aberrantes
              END,
              CASE pc.status
                WHEN 'actif'          THEN 'active'
                WHEN 'prospect'       THEN 'draft'
                WHEN 'en_négociation' THEN 'draft'
                WHEN 'renouvellement' THEN 'active'
                WHEN 'paused'         THEN 'paused'
                WHEN 'terminated'     THEN 'terminated'
                ELSE 'draft'
              END,
              COALESCE(pc.created_at, now()),
              COALESCE(pc.updated_at, now())
            FROM legacy.partner_contracts pc
            WHERE EXISTS (SELECT 1 FROM restaurants r WHERE r.id = pc.restaurant_id)
            """);
        log.info("  contracts : {} rows", contracts);
        total += contracts;

        // ─── invoices (25) ───────────────────────────────────────────────────
        // legacy.period_month (1 date) → enterprise.period_start/end (1 mois)
        long invoices = jdbc.update("""
            INSERT INTO invoices (id, restaurant_id, invoice_number, period_start, period_end,
                                  subtotal, tva_amount, total_ttc, status, paid_at, created_at, updated_at)
            SELECT
              hi.id,
              hi.restaurant_id,
              'INV-' || to_char(hi.period_month, 'YYYY-MM') || '-' || substring(hi.id::text, 1, 4),
              hi.period_month,
              (hi.period_month + interval '1 month - 1 day')::date,
              COALESCE(hi.total_ca, 0),
              COALESCE(hi.commission_2pct, 0) * 0.20,    -- TVA 20% sur commission
              COALESCE(hi.commission_2pct, 0) * 1.20,    -- TTC = commission + TVA
              CASE hi.status
                WHEN 'payé'      THEN 'paid'
                WHEN 'brouillon' THEN 'draft'
                WHEN 'envoyée'   THEN 'sent'
                WHEN 'en_retard' THEN 'overdue'
                WHEN 'annulée'   THEN 'cancelled'
                ELSE 'draft'
              END,
              hi.paid_at,
              now(),
              now()
            FROM legacy.oneclick_hi_invoices hi
            WHERE EXISTS (SELECT 1 FROM restaurants r WHERE r.id = hi.restaurant_id)
              AND hi.period_month IS NOT NULL
            """);
        log.info("  invoices : {} rows", invoices);
        total += invoices;

        // ─── invoice_lines (27) ──────────────────────────────────────────────
        // line_total est une GENERATED column en enterprise → on n'insère pas
        long lines = jdbc.update("""
            INSERT INTO invoice_lines (id, invoice_id, label, quantity, unit_price, sort_order)
            SELECT
              il.id,
              il.invoice_id,
              COALESCE(NULLIF(il.label, ''), 'Ligne'),
              COALESCE(il.quantity, 1),
              COALESCE(il.unit_price_ht, 0),
              COALESCE(il.sort_order, 0)
            FROM legacy.invoice_lines il
            WHERE EXISTS (SELECT 1 FROM invoices i WHERE i.id = il.invoice_id)
            """);
        log.info("  invoice_lines : {} rows", lines);
        total += lines;

        // ─── wallet_transactions (50) ────────────────────────────────────────
        // legacy : amount (signed), reason text → enterprise : amount + type (credit/debit/commission/...)
        long wtx = jdbc.update("""
            INSERT INTO wallet_transactions (id, restaurant_id, type, amount, reason, created_at, created_by)
            SELECT
              awt.id,
              awt.restaurant_id,
              CASE
                WHEN awt.reason ILIKE '%commission%' THEN 'commission'
                WHEN awt.reason ILIKE '%payout%' THEN 'payout'
                WHEN awt.reason ILIKE '%ajust%' THEN 'adjustment'
                WHEN awt.amount > 0 THEN 'credit'
                WHEN awt.amount < 0 THEN 'debit'
                ELSE 'adjustment'
              END,
              awt.amount,
              awt.reason,
              COALESCE(awt.created_at, now()),
              CASE WHEN EXISTS (SELECT 1 FROM users u WHERE u.id = awt.admin_id) THEN awt.admin_id ELSE NULL END
            FROM legacy.admin_wallet_transactions awt
            WHERE EXISTS (SELECT 1 FROM restaurants r WHERE r.id = awt.restaurant_id)
            """);
        log.info("  wallet_transactions : {} rows", wtx);
        total += wtx;

        return total;
    }
}
