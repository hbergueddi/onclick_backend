-- V50 — enrichissement plan de salle ProDesk (/prodesk/zones).
--
-- Les entités RestaurantZone / RestaurantTable étaient minimalistes (zone = name ;
-- table = number + seats), alors que l'UI ProDesk édite type/description/capacité/statut
-- de zone et forme/position/statut de table. Sans ces colonnes, l'édition était bloquée
-- (les hooks front jetaient "V2 backend"). On ajoute les colonnes + endpoints PATCH.
--
-- Idempotent (ADD COLUMN IF NOT EXISTS). Défauts non-destructifs alignés sur les
-- valeurs front (zone status 'active', table status 'disponible').
ALTER TABLE restaurant_zones
    ADD COLUMN IF NOT EXISTS type        varchar(64)  NOT NULL DEFAULT 'salle',
    ADD COLUMN IF NOT EXISTS description varchar(512),
    ADD COLUMN IF NOT EXISTS capacity    integer      NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS status      varchar(64)  NOT NULL DEFAULT 'active';

ALTER TABLE restaurant_tables
    ADD COLUMN IF NOT EXISTS shape    varchar(64)  NOT NULL DEFAULT 'carree',
    ADD COLUMN IF NOT EXISTS position varchar(128),
    ADD COLUMN IF NOT EXISTS status   varchar(64)  NOT NULL DEFAULT 'disponible';
