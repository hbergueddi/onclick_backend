-- ════════════════════════════════════════════════════════════════════
-- V3 — Audit niveau 1 sur les tables business critiques
-- ════════════════════════════════════════════════════════════════════
-- Contexte
--   Le brief senior demande l'audit complet (createdBy/createdAt + modifiedBy
--   /modifiedAt) sur tout ce qui change. Le schéma legacy Supabase n'a que
--   created_at + updated_at. On ajoute created_by + modified_by aux tables
--   où l'identité de l'auteur a un sens métier.
--
-- Sélection des tables (14)
--   Critère : tables qui changent souvent ET dont les changements ont un
--   impact business (workflow, monnaie, conformité, contrats).
--
-- Colonnes ajoutées
--   created_by uuid     — UUID de auth.users.id, alimenté par
--                         SecurityContextAuditorAware.
--   modified_by uuid    — Idem, mis à jour à chaque UPDATE.
--
--   FK vers auth.users(id) ON DELETE SET NULL — si l'auteur est supprimé,
--   l'audit conserve l'historique avec NULL plutôt que de cascader.
--
-- Idempotence
--   IF NOT EXISTS sur les colonnes (PG 9.6+).
-- ════════════════════════════════════════════════════════════════════

-- Macro PL/pgSQL pour appliquer le pattern à plusieurs tables d'un coup
DO $$
DECLARE
    t TEXT;
    business_tables TEXT[] := ARRAY[
        'reservations',
        'loyalty_points',
        'scanned_tickets',
        'partner_contracts',
        'offers',
        'support_tickets',
        'restaurants',
        'restaurant_staff',
        'gain_rules',
        'tenant_announcements',
        'no_show_disputes',
        'resource_bookings',
        'admin_audit_log',
        'referrals'
    ];
BEGIN
    FOREACH t IN ARRAY business_tables LOOP
        EXECUTE format(
            'ALTER TABLE public.%I ADD COLUMN IF NOT EXISTS created_by uuid',
            t
        );
        EXECUTE format(
            'ALTER TABLE public.%I ADD COLUMN IF NOT EXISTS modified_by uuid',
            t
        );
        -- FK vers auth.users uniquement si la contrainte n'existe pas déjà
        EXECUTE format($f$
            DO $inner$
            BEGIN
                IF NOT EXISTS (
                    SELECT 1 FROM information_schema.table_constraints
                    WHERE table_schema = 'public'
                      AND table_name = '%1$s'
                      AND constraint_name = '%1$s_created_by_fkey'
                ) THEN
                    ALTER TABLE public.%1$I
                      ADD CONSTRAINT %1$s_created_by_fkey
                      FOREIGN KEY (created_by)
                      REFERENCES auth.users(id) ON DELETE SET NULL;
                END IF;
                IF NOT EXISTS (
                    SELECT 1 FROM information_schema.table_constraints
                    WHERE table_schema = 'public'
                      AND table_name = '%1$s'
                      AND constraint_name = '%1$s_modified_by_fkey'
                ) THEN
                    ALTER TABLE public.%1$I
                      ADD CONSTRAINT %1$s_modified_by_fkey
                      FOREIGN KEY (modified_by)
                      REFERENCES auth.users(id) ON DELETE SET NULL;
                END IF;
            END $inner$;
        $f$, t);
    END LOOP;
END $$;

-- Tables qui ont déjà created_by mais pas modified_by → on complète
ALTER TABLE public.tenants ADD COLUMN IF NOT EXISTS modified_by uuid;
ALTER TABLE public.custom_roles ADD COLUMN IF NOT EXISTS modified_by uuid;
ALTER TABLE public.rule_templates ADD COLUMN IF NOT EXISTS modified_by uuid;
ALTER TABLE public.explore_featured ADD COLUMN IF NOT EXISTS modified_by uuid;

-- Verification : count des tables avec les 4 colonnes audit complètes
DO $$
DECLARE
    count_complete INT;
BEGIN
    SELECT count(*) INTO count_complete
    FROM information_schema.columns c1
    WHERE c1.table_schema = 'public'
      AND c1.column_name = 'created_at'
      AND EXISTS (SELECT 1 FROM information_schema.columns c2
                  WHERE c2.table_schema = c1.table_schema
                    AND c2.table_name = c1.table_name
                    AND c2.column_name = 'updated_at')
      AND EXISTS (SELECT 1 FROM information_schema.columns c2
                  WHERE c2.table_schema = c1.table_schema
                    AND c2.table_name = c1.table_name
                    AND c2.column_name = 'created_by')
      AND EXISTS (SELECT 1 FROM information_schema.columns c2
                  WHERE c2.table_schema = c1.table_schema
                    AND c2.table_name = c1.table_name
                    AND c2.column_name = 'modified_by');
    RAISE NOTICE 'V3 done: % tables now have full 4-column audit', count_complete;
END $$;
