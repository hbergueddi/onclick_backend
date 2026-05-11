-- ============================================================================
-- V11 — RBAC seed matrix (Phase 4 §2 spec senior dev)
-- ============================================================================
-- Le schéma identity/auth est déjà en place (V1). Cette migration POPULE la
-- matrice Role × Menu × Action attendue par §2 :
--
--   User "*" --> "1" Role : has_single_role
--   Role "*" -- "*" Permission : has_permissions
--   Menu "1" --o "*" Permission
--   Action "1" --o "*" Permission
--
-- Convention :
--   - 12 menus (1 par module métier) avec sort_order = position dans le UI
--   - 7 actions de base : READ, CREATE, UPDATE, DELETE, SHOW, UPLOAD, DOWNLOAD
--   - Permissions = grants par (role × menu × action), denormalisés en 1 row
--   - UUID fixes (préfixe par type) → idempotence cross-environnement
-- ============================================================================

-- ─── 12 menus (1 par module métier §1) ──────────────────────────────────────
INSERT INTO menus (id, code, name, icon, path, parent_id, sort_order) VALUES
    ('20000000-0000-0000-0000-000000000001', 'identity',         'Utilisateurs',         'users',         '/admin/users',          NULL,  10),
    ('20000000-0000-0000-0000-000000000002', 'tenant',           'Tenants & Branding',   'building',      '/admin/tenants',        NULL,  20),
    ('20000000-0000-0000-0000-000000000003', 'restaurant',       'Restaurants',          'utensils',      '/restaurants',          NULL,  30),
    ('20000000-0000-0000-0000-000000000004', 'reservation',      'Réservations',         'calendar',      '/reservations',         NULL,  40),
    ('20000000-0000-0000-0000-000000000005', 'loyalty',          'Fidélité',             'star',          '/loyalty',              NULL,  50),
    ('20000000-0000-0000-0000-000000000006', 'promotion',        'Promotions',           'megaphone',     '/promotions',           NULL,  60),
    ('20000000-0000-0000-0000-000000000007', 'community',        'Communauté',           'users-2',       '/community',            NULL,  70),
    ('20000000-0000-0000-0000-000000000008', 'event',            'Événements',           'sparkles',      '/events',               NULL,  80),
    ('20000000-0000-0000-0000-000000000009', 'resource_booking', 'Resources (PCC)',      'tennis-ball',   '/bookings',             NULL,  90),
    ('20000000-0000-0000-0000-00000000000a', 'financial',        'Contrats & Factures',  'receipt',       '/financial',            NULL, 100),
    ('20000000-0000-0000-0000-00000000000b', 'payment',          'Paiements',            'credit-card',   '/payments',             NULL, 110),
    ('20000000-0000-0000-0000-00000000000c', 'support',          'Support & Tickets',    'help-circle',   '/support',              NULL, 120)
ON CONFLICT (code) DO UPDATE SET
    name       = EXCLUDED.name,
    icon       = EXCLUDED.icon,
    path       = EXCLUDED.path,
    sort_order = EXCLUDED.sort_order,
    updated_at = now();

-- ─── 7 actions de base (CRUD + permissions fines item #8 brief) ─────────────
INSERT INTO actions (id, code, name, module) VALUES
    ('30000000-0000-0000-0000-000000000001', 'READ',     'Lire (liste)',          'rbac'),
    ('30000000-0000-0000-0000-000000000002', 'SHOW',     'Voir détail',           'rbac'),
    ('30000000-0000-0000-0000-000000000003', 'CREATE',   'Créer',                 'rbac'),
    ('30000000-0000-0000-0000-000000000004', 'UPDATE',   'Modifier',              'rbac'),
    ('30000000-0000-0000-0000-000000000005', 'DELETE',   'Supprimer (soft)',      'rbac'),
    ('30000000-0000-0000-0000-000000000006', 'UPLOAD',   'Téléverser un fichier', 'rbac'),
    ('30000000-0000-0000-0000-000000000007', 'DOWNLOAD', 'Télécharger',           'rbac')
ON CONFLICT (code) DO UPDATE SET
    name   = EXCLUDED.name,
    module = EXCLUDED.module;

-- ─── Matrice de permissions par rôle ────────────────────────────────────────
-- Stratégie :
--   - SUPERADMIN  : full (toutes actions × tous menus)
--   - GROUP_ADMIN : tout sauf delete sur identity/tenant, full sur les modules métier
--   - RESTAURATEUR: full sur restaurant/reservation/loyalty/promotion/event/resource_booking,
--                   READ+SHOW+UPDATE sur financial/payment, READ+SHOW sur support
--   - STAFF       : READ+SHOW+UPDATE sur restaurant/reservation, READ sur loyalty/promotion
--   - CLIENT      : READ+SHOW sur restaurant/loyalty/promotion, full sur reservation (les siennes)
--                   + READ/SHOW sur event/resource_booking, full sur support (ses tickets)
--
-- Helpers : on précalcule role_id constants (cf V1 seed).
DO $$
DECLARE
    r_superadmin   uuid := '10000000-0000-0000-0000-000000000005';
    r_group_admin  uuid := '10000000-0000-0000-0000-000000000004';
    r_restaurateur uuid := '10000000-0000-0000-0000-000000000003';
    r_staff        uuid := '10000000-0000-0000-0000-000000000002';
    r_client       uuid := '10000000-0000-0000-0000-000000000001';

    a_read     uuid := '30000000-0000-0000-0000-000000000001';
    a_show     uuid := '30000000-0000-0000-0000-000000000002';
    a_create   uuid := '30000000-0000-0000-0000-000000000003';
    a_update   uuid := '30000000-0000-0000-0000-000000000004';
    a_delete   uuid := '30000000-0000-0000-0000-000000000005';
    a_upload   uuid := '30000000-0000-0000-0000-000000000006';
    a_download uuid := '30000000-0000-0000-0000-000000000007';

    m_identity         uuid := '20000000-0000-0000-0000-000000000001';
    m_tenant           uuid := '20000000-0000-0000-0000-000000000002';
    m_restaurant       uuid := '20000000-0000-0000-0000-000000000003';
    m_reservation      uuid := '20000000-0000-0000-0000-000000000004';
    m_loyalty          uuid := '20000000-0000-0000-0000-000000000005';
    m_promotion        uuid := '20000000-0000-0000-0000-000000000006';
    m_community        uuid := '20000000-0000-0000-0000-000000000007';
    m_event            uuid := '20000000-0000-0000-0000-000000000008';
    m_resource_booking uuid := '20000000-0000-0000-0000-000000000009';
    m_financial        uuid := '20000000-0000-0000-0000-00000000000a';
    m_payment          uuid := '20000000-0000-0000-0000-00000000000b';
    m_support          uuid := '20000000-0000-0000-0000-00000000000c';

    m   uuid;
    a   uuid;
    role_uuid uuid;
BEGIN
    -- ── SUPERADMIN : tout sur tout les 12 menus × 7 actions = 84 grants ────
    FOR m IN SELECT id FROM menus LOOP
        FOR a IN SELECT id FROM actions LOOP
            INSERT INTO permissions (role_id, menu_id, action_id)
                VALUES (r_superadmin, m, a)
                ON CONFLICT (role_id, menu_id, action_id) DO NOTHING;
        END LOOP;
    END LOOP;

    -- ── GROUP_ADMIN : tout sauf DELETE sur identity & tenant ────────────────
    FOR m IN SELECT id FROM menus LOOP
        FOR a IN SELECT id FROM actions LOOP
            -- Skip DELETE sur identity & tenant
            IF (m IN (m_identity, m_tenant)) AND (a = a_delete) THEN
                CONTINUE;
            END IF;
            INSERT INTO permissions (role_id, menu_id, action_id)
                VALUES (r_group_admin, m, a)
                ON CONFLICT (role_id, menu_id, action_id) DO NOTHING;
        END LOOP;
    END LOOP;

    -- ── RESTAURATEUR : full sur 6 modules métier + READ/SHOW/UPDATE financial+payment ──
    FOR m IN
        SELECT id FROM menus WHERE id IN (m_restaurant, m_reservation, m_loyalty, m_promotion, m_event, m_resource_booking, m_community)
    LOOP
        FOR a IN SELECT id FROM actions LOOP
            INSERT INTO permissions (role_id, menu_id, action_id)
                VALUES (r_restaurateur, m, a)
                ON CONFLICT (role_id, menu_id, action_id) DO NOTHING;
        END LOOP;
    END LOOP;
    -- READ + SHOW + UPDATE + DOWNLOAD sur financial & payment
    FOREACH m IN ARRAY ARRAY[m_financial, m_payment] LOOP
        FOREACH a IN ARRAY ARRAY[a_read, a_show, a_update, a_download] LOOP
            INSERT INTO permissions (role_id, menu_id, action_id)
                VALUES (r_restaurateur, m, a)
                ON CONFLICT (role_id, menu_id, action_id) DO NOTHING;
        END LOOP;
    END LOOP;
    -- READ + SHOW + CREATE + UPDATE sur support (ouvrir tickets)
    FOREACH a IN ARRAY ARRAY[a_read, a_show, a_create, a_update] LOOP
        INSERT INTO permissions (role_id, menu_id, action_id)
            VALUES (r_restaurateur, m_support, a)
            ON CONFLICT (role_id, menu_id, action_id) DO NOTHING;
    END LOOP;

    -- ── STAFF : restreint à son resto ───────────────────────────────────────
    -- READ + SHOW + UPDATE sur restaurant & reservation
    FOREACH m IN ARRAY ARRAY[m_restaurant, m_reservation] LOOP
        FOREACH a IN ARRAY ARRAY[a_read, a_show, a_update] LOOP
            INSERT INTO permissions (role_id, menu_id, action_id)
                VALUES (r_staff, m, a)
                ON CONFLICT (role_id, menu_id, action_id) DO NOTHING;
        END LOOP;
    END LOOP;
    -- READ + SHOW sur loyalty + promotion + event + resource_booking
    FOREACH m IN ARRAY ARRAY[m_loyalty, m_promotion, m_event, m_resource_booking] LOOP
        FOREACH a IN ARRAY ARRAY[a_read, a_show] LOOP
            INSERT INTO permissions (role_id, menu_id, action_id)
                VALUES (r_staff, m, a)
                ON CONFLICT (role_id, menu_id, action_id) DO NOTHING;
        END LOOP;
    END LOOP;

    -- ── CLIENT : ses propres données ────────────────────────────────────────
    -- READ + SHOW sur restaurant (catalogue) + loyalty + promotion + event + community
    FOREACH m IN ARRAY ARRAY[m_restaurant, m_loyalty, m_promotion, m_event, m_community, m_resource_booking] LOOP
        FOREACH a IN ARRAY ARRAY[a_read, a_show] LOOP
            INSERT INTO permissions (role_id, menu_id, action_id)
                VALUES (r_client, m, a)
                ON CONFLICT (role_id, menu_id, action_id) DO NOTHING;
        END LOOP;
    END LOOP;
    -- Full sur reservation (les siennes) + support (ses tickets)
    FOREACH m IN ARRAY ARRAY[m_reservation, m_support] LOOP
        FOREACH a IN ARRAY ARRAY[a_read, a_show, a_create, a_update, a_delete] LOOP
            INSERT INTO permissions (role_id, menu_id, action_id)
                VALUES (r_client, m, a)
                ON CONFLICT (role_id, menu_id, action_id) DO NOTHING;
        END LOOP;
    END LOOP;
    -- UPLOAD sur support (joindre une PJ au ticket)
    INSERT INTO permissions (role_id, menu_id, action_id)
        VALUES (r_client, m_support, a_upload)
        ON CONFLICT (role_id, menu_id, action_id) DO NOTHING;

END $$;

-- ─── Vérification ──────────────────────────────────────────────────────────
-- Pour debug : SELECT COUNT(*) FROM permissions; SHOULD return ~250

COMMENT ON TABLE permissions IS
    'Matrice Role × Menu × Action (Phase 4 §2 spec senior dev — V11 seeded ~250 grants)';
