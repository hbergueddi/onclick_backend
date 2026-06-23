-- V96 — Planning des ressources bookables : opening_hours / slot_duration_minutes / max_invitees
--
-- CONTEXTE
--   Le module resource_booking (V5) ne portait que id/tenant/type/name/description/capacity/enabled.
--   L'écran de réservation (8 modules PCC : Padel/Spa/Golf/Coiffeur/Palm Gym/Tennis/Foot/Basket) a
--   besoin, pour générer la grille de créneaux d'un jour, des HORAIRES d'ouverture, de la DURÉE d'un
--   créneau et du nombre MAX d'invités — exactement les champs que le legacy portait dans la table
--   Supabase `bookable_resources` (opening_hours JSONB, slot_duration_minutes, max_invitees) mais qui
--   n'avaient PAS été migrés vers Spring. Sans eux, les clients (Android natif ET iOS) tombent sur des
--   défauts (60 min / 0 invité / horaires vides) → AUCUN créneau réservable.
--
--   Cette migration aligne le contrat Spring sur le besoin réel : elle ajoute les 3 colonnes puis
--   les renseigne par `resource_type` à partir des valeurs canoniques du seed legacy
--   (supabase/migrations PCC : padel 2026-04-24, spa/golf 2026-04-24, coiffeur 2026-04-27,
--   palm gym 2026-04-27, golf override 60 min 2026-04-27, tennis/foot/basket 2026-05-04).
--
-- PORTÉE
--   Les resource_type ci-dessous sont EXCLUSIFS au tenant palmeraie (aucun autre tenant n'en possède),
--   donc un backfill `WHERE resource_type = 'x'` est naturellement scopé palmeraie (cf seed legacy).
--   Idempotent de fait : Flyway n'exécute la migration qu'une fois ; les valeurs sont déterministes.

ALTER TABLE resources
    ADD COLUMN IF NOT EXISTS opening_hours          jsonb,
    ADD COLUMN IF NOT EXISTS slot_duration_minutes  integer,
    ADD COLUMN IF NOT EXISTS max_invitees           integer;

COMMENT ON COLUMN resources.opening_hours IS 'Horaires d''ouverture par jour {mon..sun:["HH:MM-HH:MM"]} — génération des créneaux (§10 booking)';
COMMENT ON COLUMN resources.slot_duration_minutes IS 'Durée d''un créneau en minutes (padel 90, golf/tennis/foot/basket/coach 60, spa 60/75/90, coiffeur 30)';
COMMENT ON COLUMN resources.max_invitees IS 'Nombre max d''invités en plus de l''organisateur (padel/golf/tennis 3, foot/basket 9, spa/coiffeur/coach 0)';

-- ── Backfill par type (valeurs canoniques du legacy bookable_resources) ───────────────────────────

-- Padel : 6 terrains · 90 min · max 4 joueurs (3 invités) · 08:00-22:00 tous les jours.
UPDATE resources SET slot_duration_minutes = 90, max_invitees = 3,
    opening_hours = '{"mon":["08:00-22:00"],"tue":["08:00-22:00"],"wed":["08:00-22:00"],"thu":["08:00-22:00"],"fri":["08:00-22:00"],"sat":["08:00-22:00"],"sun":["08:00-22:00"]}'::jsonb
    WHERE resource_type = 'padel_court';

-- Golf : tee times 60 min (override legacy 27/04, ex-180) · max 4 joueurs (3 invités) · 07:00-17:00.
UPDATE resources SET slot_duration_minutes = 60, max_invitees = 3,
    opening_hours = '{"mon":["07:00-17:00"],"tue":["07:00-17:00"],"wed":["07:00-17:00"],"thu":["07:00-17:00"],"fri":["07:00-17:00"],"sat":["07:00-17:00"],"sun":["07:00-17:00"]}'::jsonb
    WHERE resource_type = 'golf_tee';

-- Coiffeur : RDV individuel 30 min · 0 invité · lun-sam 09:00-19:00 (fermé dimanche).
UPDATE resources SET slot_duration_minutes = 30, max_invitees = 0,
    opening_hours = '{"mon":["09:00-19:00"],"tue":["09:00-19:00"],"wed":["09:00-19:00"],"thu":["09:00-19:00"],"fri":["09:00-19:00"],"sat":["09:00-19:00"]}'::jsonb
    WHERE resource_type = 'barber_chair';

-- Palm Gym : séance coach 60 min · 0 invité · lun-ven 07:00-21:00, sam 08:00-19:00 (représentatif).
UPDATE resources SET slot_duration_minutes = 60, max_invitees = 0,
    opening_hours = '{"mon":["07:00-21:00"],"tue":["07:00-21:00"],"wed":["07:00-21:00"],"thu":["07:00-21:00"],"fri":["07:00-21:00"],"sat":["08:00-19:00"]}'::jsonb
    WHERE resource_type = 'coach_session';

-- Tennis : 5 courts · 60 min · max 4 (3 invités) · lun-ven 09:00-22:00, sam 09:00-21:00, dim 09:00-20:00.
UPDATE resources SET slot_duration_minutes = 60, max_invitees = 3,
    opening_hours = '{"mon":["09:00-22:00"],"tue":["09:00-22:00"],"wed":["09:00-22:00"],"thu":["09:00-22:00"],"fri":["09:00-22:00"],"sat":["09:00-21:00"],"sun":["09:00-20:00"]}'::jsonb
    WHERE resource_type = 'tennis_court';

-- Foot : 5 terrains 5v5 · 60 min · max 10 (9 invités) · mêmes horaires que tennis.
UPDATE resources SET slot_duration_minutes = 60, max_invitees = 9,
    opening_hours = '{"mon":["09:00-22:00"],"tue":["09:00-22:00"],"wed":["09:00-22:00"],"thu":["09:00-22:00"],"fri":["09:00-22:00"],"sat":["09:00-21:00"],"sun":["09:00-20:00"]}'::jsonb
    WHERE resource_type = 'football_field';

-- Basket : 5 terrains 5v5 · 60 min · max 10 (9 invités) · mêmes horaires que foot.
UPDATE resources SET slot_duration_minutes = 60, max_invitees = 9,
    opening_hours = '{"mon":["09:00-22:00"],"tue":["09:00-22:00"],"wed":["09:00-22:00"],"thu":["09:00-22:00"],"fri":["09:00-22:00"],"sat":["09:00-21:00"],"sun":["09:00-20:00"]}'::jsonb
    WHERE resource_type = 'basketball_court';

-- Spa : soins individuels · 0 invité · 10:00-19:00 tous les jours. Durée par défaut 60 min, puis
-- override par soin (legacy : Massage Suédois 60, Hammam & Gommage 90, Soin Signature Argan 75).
UPDATE resources SET slot_duration_minutes = 60, max_invitees = 0,
    opening_hours = '{"mon":["10:00-19:00"],"tue":["10:00-19:00"],"wed":["10:00-19:00"],"thu":["10:00-19:00"],"fri":["10:00-19:00"],"sat":["10:00-19:00"],"sun":["10:00-19:00"]}'::jsonb
    WHERE resource_type = 'spa_room';
UPDATE resources SET slot_duration_minutes = 90 WHERE resource_type = 'spa_room' AND name = 'Hammam & Gommage';
UPDATE resources SET slot_duration_minutes = 75 WHERE resource_type = 'spa_room' AND name = 'Soin Signature Argan';

-- Piscines (V71) : créneaux 60 min · 0 invité · 08:00-20:00 tous les jours (défaut raisonnable —
-- le legacy ne les configurait pas en bookable ; ces valeurs les rendent réservables proprement).
UPDATE resources SET slot_duration_minutes = 60, max_invitees = 0,
    opening_hours = '{"mon":["08:00-20:00"],"tue":["08:00-20:00"],"wed":["08:00-20:00"],"thu":["08:00-20:00"],"fri":["08:00-20:00"],"sat":["08:00-20:00"],"sun":["08:00-20:00"]}'::jsonb
    WHERE resource_type IN ('indoor_pool', 'olympic_pool');
