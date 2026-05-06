-- ════════════════════════════════════════════════════════════════════
-- V4 — Matrice de permissions au niveau application (app_role × menu × action)
-- ════════════════════════════════════════════════════════════════════
-- Contexte
--   Le brief senior (item #8) demande des permissions fines par menu :
--     READ / WRITE / UPDATE / DELETE / SHOW / UPLOAD / DOWNLOAD
--
--   Deux systèmes de rôles coexistent dans OneClick :
--     1. app_role (4)        : admin, restaurateur, client, tenant_admin
--                              → identité applicative haute (utilisée par
--                                @PreAuthorize côté Spring controllers)
--     2. staff_role (5+)     : owner, manager, directeur, responsable_resa,
--                              serveur — rôle dans UN restaurant donné
--                              → permissions fines déjà gérées par la table
--                                existante `staff_role_permissions` (260 rows)
--
--   V4 crée la matrice du SYSTÈME 1 (app_permissions) sans toucher au 2.
--   Les deux systèmes restent parallèles et complémentaires :
--     - app_permissions : qui peut accéder au menu (gros coup de filtre)
--     - staff_role_permissions : qui peut faire quoi à l'intérieur d'un
--                                restaurant (filtre fin)
--
-- Format
--   Format `permission_id = "<menu>.<action>"` aligné avec
--   staff_role_permissions pour cohérence (ex: "reservations.delete").
--
-- Actions (7 du brief)
--   READ, WRITE, UPDATE, DELETE, SHOW, UPLOAD, DOWNLOAD
--
-- Menus (12 groupes métier alignés avec scripts/scaffold/naming.mjs)
--   auth, tenant, admin, contract, support, restaurant, reservation,
--   loyalty, marketing, pcc + le pseudo-menu "system" (settings, etc.)
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS public.app_permissions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    app_role text NOT NULL,
    menu text NOT NULL,
    action text NOT NULL,
    granted boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT app_permissions_role_check
        CHECK (app_role IN ('admin', 'restaurateur', 'client', 'tenant_admin')),
    CONSTRAINT app_permissions_action_check
        CHECK (action IN ('READ', 'WRITE', 'UPDATE', 'DELETE', 'SHOW', 'UPLOAD', 'DOWNLOAD')),
    CONSTRAINT app_permissions_unique
        UNIQUE (app_role, menu, action)
);

CREATE INDEX IF NOT EXISTS idx_app_permissions_role ON public.app_permissions(app_role);
CREATE INDEX IF NOT EXISTS idx_app_permissions_menu ON public.app_permissions(menu);

COMMENT ON TABLE public.app_permissions IS
    'Permissions matrix at the application level (app_role × menu × action). '
    'Distinct from staff_role_permissions which concerns intra-restaurant '
    'staff roles (owner/manager/etc). Loaded into memory by Spring at boot, '
    'evaluated by @RequirePermission annotation.';

-- ── Seed initial — politique conservatrice ────────────────────────────
-- Rôle admin : full access (toutes actions sur tous menus)
-- Rôles métier : actions selon le menu (cf brief Phase 5.2 + extension fine)

INSERT INTO public.app_permissions (app_role, menu, action) VALUES
    -- admin : full access sur tous les menus
    ('admin', 'auth', 'READ'),         ('admin', 'auth', 'WRITE'),         ('admin', 'auth', 'UPDATE'),         ('admin', 'auth', 'DELETE'),         ('admin', 'auth', 'SHOW'),
    ('admin', 'tenant', 'READ'),       ('admin', 'tenant', 'WRITE'),       ('admin', 'tenant', 'UPDATE'),       ('admin', 'tenant', 'DELETE'),       ('admin', 'tenant', 'SHOW'),
    ('admin', 'admin', 'READ'),        ('admin', 'admin', 'WRITE'),        ('admin', 'admin', 'UPDATE'),        ('admin', 'admin', 'DELETE'),        ('admin', 'admin', 'SHOW'),        ('admin', 'admin', 'DOWNLOAD'),
    ('admin', 'contract', 'READ'),     ('admin', 'contract', 'WRITE'),     ('admin', 'contract', 'UPDATE'),     ('admin', 'contract', 'DELETE'),     ('admin', 'contract', 'SHOW'),     ('admin', 'contract', 'DOWNLOAD'),     ('admin', 'contract', 'UPLOAD'),
    ('admin', 'support', 'READ'),      ('admin', 'support', 'WRITE'),      ('admin', 'support', 'UPDATE'),      ('admin', 'support', 'DELETE'),      ('admin', 'support', 'SHOW'),
    ('admin', 'restaurant', 'READ'),   ('admin', 'restaurant', 'WRITE'),   ('admin', 'restaurant', 'UPDATE'),   ('admin', 'restaurant', 'DELETE'),   ('admin', 'restaurant', 'SHOW'),   ('admin', 'restaurant', 'UPLOAD'),
    ('admin', 'reservation', 'READ'),  ('admin', 'reservation', 'WRITE'),  ('admin', 'reservation', 'UPDATE'),  ('admin', 'reservation', 'DELETE'),  ('admin', 'reservation', 'SHOW'),
    ('admin', 'loyalty', 'READ'),      ('admin', 'loyalty', 'WRITE'),      ('admin', 'loyalty', 'UPDATE'),      ('admin', 'loyalty', 'DELETE'),      ('admin', 'loyalty', 'SHOW'),
    ('admin', 'marketing', 'READ'),    ('admin', 'marketing', 'WRITE'),    ('admin', 'marketing', 'UPDATE'),    ('admin', 'marketing', 'DELETE'),    ('admin', 'marketing', 'SHOW'),    ('admin', 'marketing', 'UPLOAD'),
    ('admin', 'pcc', 'READ'),          ('admin', 'pcc', 'WRITE'),          ('admin', 'pcc', 'UPDATE'),          ('admin', 'pcc', 'DELETE'),          ('admin', 'pcc', 'SHOW'),

    -- restaurateur : peut gérer son restaurant + ses réservations
    ('restaurateur', 'restaurant', 'READ'),     ('restaurateur', 'restaurant', 'UPDATE'),     ('restaurateur', 'restaurant', 'SHOW'),     ('restaurateur', 'restaurant', 'UPLOAD'),
    ('restaurateur', 'reservation', 'READ'),    ('restaurateur', 'reservation', 'WRITE'),    ('restaurateur', 'reservation', 'UPDATE'),    ('restaurateur', 'reservation', 'SHOW'),
    ('restaurateur', 'loyalty', 'READ'),        ('restaurateur', 'loyalty', 'WRITE'),        ('restaurateur', 'loyalty', 'SHOW'),
    ('restaurateur', 'marketing', 'READ'),      ('restaurateur', 'marketing', 'WRITE'),      ('restaurateur', 'marketing', 'UPDATE'),      ('restaurateur', 'marketing', 'SHOW'),
    ('restaurateur', 'pcc', 'READ'),            ('restaurateur', 'pcc', 'WRITE'),            ('restaurateur', 'pcc', 'UPDATE'),            ('restaurateur', 'pcc', 'SHOW'),

    -- client : lecture restaurants/promos + écriture sur ses réservations + ses points
    ('client', 'restaurant', 'READ'),    ('client', 'restaurant', 'SHOW'),
    ('client', 'reservation', 'READ'),   ('client', 'reservation', 'WRITE'),   ('client', 'reservation', 'UPDATE'),   ('client', 'reservation', 'DELETE'),   ('client', 'reservation', 'SHOW'),
    ('client', 'loyalty', 'READ'),       ('client', 'loyalty', 'WRITE'),       ('client', 'loyalty', 'SHOW'),
    ('client', 'marketing', 'READ'),     ('client', 'marketing', 'SHOW'),
    ('client', 'pcc', 'READ'),           ('client', 'pcc', 'WRITE'),           ('client', 'pcc', 'SHOW'),
    ('client', 'support', 'READ'),       ('client', 'support', 'WRITE'),       ('client', 'support', 'SHOW'),
    ('client', 'auth', 'READ'),          ('client', 'auth', 'UPDATE'),          ('client', 'auth', 'SHOW'),     -- son propre profil

    -- tenant_admin : accès complet sur son tenant (PCC, HOMU, etc.)
    ('tenant_admin', 'tenant', 'READ'),       ('tenant_admin', 'tenant', 'UPDATE'),       ('tenant_admin', 'tenant', 'SHOW'),
    ('tenant_admin', 'restaurant', 'READ'),   ('tenant_admin', 'restaurant', 'WRITE'),   ('tenant_admin', 'restaurant', 'UPDATE'),   ('tenant_admin', 'restaurant', 'SHOW'),   ('tenant_admin', 'restaurant', 'UPLOAD'),
    ('tenant_admin', 'reservation', 'READ'),  ('tenant_admin', 'reservation', 'WRITE'),  ('tenant_admin', 'reservation', 'UPDATE'),  ('tenant_admin', 'reservation', 'SHOW'),
    ('tenant_admin', 'pcc', 'READ'),          ('tenant_admin', 'pcc', 'WRITE'),          ('tenant_admin', 'pcc', 'UPDATE'),          ('tenant_admin', 'pcc', 'DELETE'),          ('tenant_admin', 'pcc', 'SHOW'),
    ('tenant_admin', 'marketing', 'READ'),    ('tenant_admin', 'marketing', 'WRITE'),    ('tenant_admin', 'marketing', 'UPDATE'),    ('tenant_admin', 'marketing', 'SHOW')
ON CONFLICT (app_role, menu, action) DO NOTHING;

DO $$
DECLARE
    cnt INT;
BEGIN
    SELECT count(*) INTO cnt FROM public.app_permissions;
    RAISE NOTICE 'V4 done: % rows in app_permissions', cnt;
END $$;
