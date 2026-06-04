-- ════════════════════════════════════════════════════════════════════
-- V68 — « Ma Famille » (PCC Lot 5) — liens famille entre membres d'un tenant
-- ════════════════════════════════════════════════════════════════════
-- Un membre (A) ajoute des proches (B) du MÊME tenant à sa liste famille, puis
-- consulte le total de points fidélité restants de chacun et l'historique
-- complet des points d'un proche. Limite 10 membres par caller.
--
-- Port fidèle du legacy Supabase (20260427150000_pcc_family_members.sql + 4 RPCs
-- SECURITY DEFINER add/list/points-history/remove) vers un module Spring propre.
--
-- DIFFÉRENCE GÉNÉRIQUE vs legacy (PCC-only hardcodé tenant=palmeraie) :
--   Ici le scope tenant est celui DU CALLER (résolu via UserDirectoryApi côté
--   service), pas un UUID palmeraie en dur. Réutilisable par n'importe quel
--   tenant whitelabel (HOMU, Restopro, …) sans modification.
--
-- Relations UNIDIRECTIONNELLES (A→B). UNIQUE(member_id, related_member_id),
-- CHECK(member_id <> related_member_id). DELETE permis aux 2 parties (ABAC service).
--
-- RBAC v2 senior strict : @PreAuthorize("hasAuthority('VERB:FAMILY')") partout
-- (jamais isAuthenticated/hasRole). Le self-scope (caller = A) et le scope tenant
-- sont gérés dans PccFamilyService (ABAC).
--
-- Grants (calque V66/V67) :
--   • CLIENT                                       → VIEW + CREATE + DELETE:FAMILY
--       (le membre gère SA liste famille — self-scope forcé côté service)
--   • STAFF + RESTAURATEUR + GROUP_ADMIN
--     + SUPERADMIN                                 → VIEW + CREATE + DELETE:FAMILY
--       (cohérence avec BOOKINGS/PUNCH_CARDS : les rôles de gestion ont le même
--        accès ; le scope tenant/caller reste porté par le service)
--
-- Aucun rôle ne reçoit UPDATE:FAMILY : un lien famille naît (add) et meurt
-- (remove), il ne se « met à jour » pas (la relation textuelle est fixée à l'ajout).
--
-- Anti-régression : table dédiée, 0 impact OneClick/HOMU/Restopro.
--
-- ⚠️ Après application : flusher le cache userDetails (redis-cli FLUSHDB) sinon les
-- sessions en cache n'ont pas les nouvelles autorités avant le TTL 1 h.
-- ════════════════════════════════════════════════════════════════════

-- ── 1. Table pcc_family_members ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS pcc_family_members (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id          uuid        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    related_member_id  uuid        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    relation           varchar(64),
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pcc_family_no_self  CHECK (member_id <> related_member_id),
    CONSTRAINT pcc_family_unique   UNIQUE (member_id, related_member_id)
);

-- Lecture « ma liste famille » (caller = member_id) + retrait côté B (related_member_id).
CREATE INDEX IF NOT EXISTS idx_pcc_family_member_id ON pcc_family_members (member_id);
CREATE INDEX IF NOT EXISTS idx_pcc_family_related_id ON pcc_family_members (related_member_id);

-- ── 2. Ressource RBAC FAMILY (feuille sous la catégorie Social) ─────────────
INSERT INTO menus (code, name, parent_id, sort_order)
SELECT 'FAMILY', 'Ma Famille',
       (SELECT id FROM menus WHERE code = 'SOCIAL_HUB'), 54
WHERE NOT EXISTS (SELECT 1 FROM menus WHERE code = 'FAMILY');

-- ── 3. CLIENT → VIEW + CREATE + DELETE ──────────────────────────────────────
INSERT INTO permissions (role_id, menu_id, action_id)
SELECT r.id, m.id, a.id
FROM roles r CROSS JOIN menus m CROSS JOIN actions a
WHERE r.code = 'CLIENT'
  AND m.code = 'FAMILY'
  AND a.code IN ('VIEW', 'CREATE', 'DELETE')
  AND NOT EXISTS (SELECT 1 FROM permissions p WHERE p.role_id=r.id AND p.menu_id=m.id AND p.action_id=a.id);

-- ── 4. STAFF + RESTAURATEUR + GROUP_ADMIN + SUPERADMIN → VIEW + CREATE + DELETE ──
INSERT INTO permissions (role_id, menu_id, action_id)
SELECT r.id, m.id, a.id
FROM roles r CROSS JOIN menus m CROSS JOIN actions a
WHERE r.code IN ('STAFF', 'RESTAURATEUR', 'GROUP_ADMIN', 'SUPERADMIN')
  AND m.code = 'FAMILY'
  AND a.code IN ('VIEW', 'CREATE', 'DELETE')
  AND NOT EXISTS (SELECT 1 FROM permissions p WHERE p.role_id=r.id AND p.menu_id=m.id AND p.action_id=a.id);

-- ════════════════════════════════════════════════════════════════════
-- 5. Seed démo (idempotent) : member1 ajoute member2 (Conjoint·e) + member3 (Enfant)
--    Utile pour démontrer une liste famille non-vide. ON CONFLICT DO NOTHING
--    (UNIQUE member_id+related_member_id) → ré-exécution sûre.
-- ════════════════════════════════════════════════════════════════════
INSERT INTO pcc_family_members (member_id, related_member_id, relation)
SELECT m1.id, m2.id, 'Conjoint·e'
FROM (SELECT id FROM users WHERE email = 'member1@palmeraie.com') m1
CROSS JOIN (SELECT id FROM users WHERE email = 'member2@palmeraie.com') m2
ON CONFLICT (member_id, related_member_id) DO NOTHING;

INSERT INTO pcc_family_members (member_id, related_member_id, relation)
SELECT m1.id, m3.id, 'Enfant'
FROM (SELECT id FROM users WHERE email = 'member1@palmeraie.com') m1
CROSS JOIN (SELECT id FROM users WHERE email = 'member3@palmeraie.com') m3
ON CONFLICT (member_id, related_member_id) DO NOTHING;
