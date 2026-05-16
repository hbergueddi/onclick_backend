-- ════════════════════════════════════════════════════════════════════
-- V25 — Cleanup cuisine/budget/tags noise + import 120-seed curated
-- ════════════════════════════════════════════════════════════════════
--
-- Migration en 3 étapes :
--  1. Remove 'Restaurant' générique des tags (noise Google) — affecte ~589 restos
--  2. Cuisine='Restaurant' générique → NULL (366 restos) — le front masque automatiquement
--  3. Override avec 120 entrées curatées de l'ancien seed Supabase 
--     (cuisine + budget de qualité humaine, beats Google sur AZAR Casa,
--      Le Petit Rocher, Brasserie La Tour, etc.)
--
-- Tracé Sprint K hot fix — voir CLAUDE.md règle migrations.

BEGIN;

-- ─── Étape 0 : Relax CHECK budget pour autoriser €€€€ ───
-- Google retourne PRICE_LEVEL_VERY_EXPENSIVE → '€€€€' (mapPriceLevel),
-- et le seed legacy avait des €€€€ (Le Cabestan, La Maison Bleue). L'ancienne
-- contrainte ARRAY['€','€€','€€€'] bloquait l'insertion.
ALTER TABLE restaurants DROP CONSTRAINT IF EXISTS restaurants_budget_check;
ALTER TABLE restaurants ADD CONSTRAINT restaurants_budget_check
  CHECK (budget IS NULL OR budget = ANY (ARRAY['€','€€','€€€','€€€€']));

-- ─── Étape 1 : Cleanup tags 'Restaurant' ───
UPDATE restaurants
   SET tags = array_remove(tags, 'Restaurant'),
       updated_at = NOW()
 WHERE 'Restaurant' = ANY(tags);

-- ─── Étape 2 : Cuisine 'Restaurant' générique → NULL ───
UPDATE restaurants
   SET cuisine = NULL,
       updated_at = NOW()
 WHERE cuisine = 'Restaurant';

-- ─── Étape 3 : Override avec données curatées 120-seed ───
-- 120 UPDATE — match par (name, city) pour éviter collisions cross-city

UPDATE restaurants SET cuisine='Marocaine', budget='€€', updated_at=NOW() WHERE name='La Sqala' AND city='Casablanca';
UPDATE restaurants SET cuisine='Internationale', budget='€€€', updated_at=NOW() WHERE name='Rick''s Café' AND city='Casablanca';
UPDATE restaurants SET cuisine='Fruits de mer', budget='€€€', updated_at=NOW() WHERE name='Le Petit Rocher' AND city='Casablanca';
UPDATE restaurants SET cuisine='Turque', budget='€€', updated_at=NOW() WHERE name='Basmane' AND city='Casablanca';
UPDATE restaurants SET cuisine='Française', budget='€€€', updated_at=NOW() WHERE name='Brasserie La Tour' AND city='Casablanca';
UPDATE restaurants SET cuisine='Burger', budget='€€', updated_at=NOW() WHERE name='Blend Gourmet Burger' AND city='Casablanca';
UPDATE restaurants SET cuisine='Japonaise', budget='€€€', updated_at=NOW() WHERE name='Nikkei' AND city='Casablanca';
UPDATE restaurants SET cuisine='Libanaise', budget='€€€', updated_at=NOW() WHERE name='AZAR Casa' AND city='Casablanca';
UPDATE restaurants SET cuisine='Espagnole', budget='€€', updated_at=NOW() WHERE name='La Bodega' AND city='Casablanca';
UPDATE restaurants SET cuisine='Végétarienne', budget='€', updated_at=NOW() WHERE name='Veggie' AND city='Casablanca';
UPDATE restaurants SET cuisine='Grecque', budget='€€', updated_at=NOW() WHERE name='Mykonos' AND city='Casablanca';
UPDATE restaurants SET cuisine='Fruits de mer', budget='€€€€', updated_at=NOW() WHERE name='Le Cabestan Ocean View' AND city='Casablanca';
UPDATE restaurants SET cuisine='Internationale', budget='€€€', updated_at=NOW() WHERE name='Le Dhow' AND city='Rabat';
UPDATE restaurants SET cuisine='Marocaine', budget='€€', updated_at=NOW() WHERE name='Dar Zaki' AND city='Rabat';
UPDATE restaurants SET cuisine='Française', budget='€€€', updated_at=NOW() WHERE name='Cosmopolitan' AND city='Rabat';
UPDATE restaurants SET cuisine='Crêperie', budget='€', updated_at=NOW() WHERE name='Ty Potes' AND city='Rabat';
UPDATE restaurants SET cuisine='Boulangerie-café', budget='€€', updated_at=NOW() WHERE name='Paul Rabat' AND city='Rabat';
UPDATE restaurants SET cuisine='Marocaine', budget='€', updated_at=NOW() WHERE name='Dar Naji' AND city='Rabat';
UPDATE restaurants SET cuisine='Japonaise', budget='€€€', updated_at=NOW() WHERE name='Matsuri' AND city='Rabat';
UPDATE restaurants SET cuisine='Steakhouse', budget='€€', updated_at=NOW() WHERE name='L''Entrecôte' AND city='Rabat';
UPDATE restaurants SET cuisine='Poisson', budget='€', updated_at=NOW() WHERE name='Amoud' AND city='Rabat';
UPDATE restaurants SET cuisine='Gastronomique', budget='€€€€', updated_at=NOW() WHERE name='Hélène Darroze au Royal Mansour' AND city='Rabat';
UPDATE restaurants SET cuisine='Libanaise', budget='€€', updated_at=NOW() WHERE name='Beyrouth' AND city='Rabat';
UPDATE restaurants SET cuisine='Italienne', budget='€€', updated_at=NOW() WHERE name='Giardino' AND city='Rabat';
UPDATE restaurants SET cuisine='Marocaine', budget='€€€', updated_at=NOW() WHERE name='Al Fassia' AND city='Marrakech';
UPDATE restaurants SET cuisine='Marocaine moderne', budget='€€', updated_at=NOW() WHERE name='Nomad' AND city='Marrakech';
UPDATE restaurants SET cuisine='Méditerranéenne', budget='€€', updated_at=NOW() WHERE name='Le Jardin' AND city='Marrakech';
UPDATE restaurants SET cuisine='Café', budget='€', updated_at=NOW() WHERE name='Café des Épices' AND city='Marrakech';
UPDATE restaurants SET cuisine='Marocaine gastronomique', budget='€€€€', updated_at=NOW() WHERE name='La Mamounia - Le Marocain' AND city='Marrakech';
UPDATE restaurants SET cuisine='Marocaine', budget='€', updated_at=NOW() WHERE name='Amal' AND city='Marrakech';
UPDATE restaurants SET cuisine='Asiatique fusion', budget='€€€', updated_at=NOW() WHERE name='Bo-Zin' AND city='Marrakech';
UPDATE restaurants SET cuisine='Italienne', budget='€€€', updated_at=NOW() WHERE name='Pepe Nero' AND city='Marrakech';
UPDATE restaurants SET cuisine='Marocaine', budget='€€€', updated_at=NOW() WHERE name='Le Comptoir Darna' AND city='Marrakech';
UPDATE restaurants SET cuisine='Café brunch', budget='€€', updated_at=NOW() WHERE name='KAOWA' AND city='Marrakech';
UPDATE restaurants SET cuisine='Internationale', budget='€€€', updated_at=NOW() WHERE name='Le Lotus Club' AND city='Marrakech';
UPDATE restaurants SET cuisine='Française', budget='€€€', updated_at=NOW() WHERE name='Latitude 31' AND city='Marrakech';
UPDATE restaurants SET cuisine='Marocaine gastronomique', budget='€€€', updated_at=NOW() WHERE name='Dar Roumana' AND city='Fès';
UPDATE restaurants SET cuisine='Méditerranéenne', budget='€€', updated_at=NOW() WHERE name='The Ruined Garden' AND city='Fès';
UPDATE restaurants SET cuisine='Marocaine fusion', budget='€', updated_at=NOW() WHERE name='Café Clock' AND city='Fès';
UPDATE restaurants SET cuisine='Marocaine', budget='€€€€', updated_at=NOW() WHERE name='Riad Fès - Restaurant' AND city='Fès';
UPDATE restaurants SET cuisine='Italienne', budget='€€', updated_at=NOW() WHERE name='Made in Sud' AND city='Fès';
UPDATE restaurants SET cuisine='Marocaine', budget='€', updated_at=NOW() WHERE name='Chez Rachid' AND city='Fès';
UPDATE restaurants SET cuisine='Marocaine', budget='€€', updated_at=NOW() WHERE name='Dar Hatim' AND city='Fès';
UPDATE restaurants SET cuisine='Française', budget='€€€', updated_at=NOW() WHERE name='L''Amandier Fès' AND city='Fès';
UPDATE restaurants SET cuisine='Café', budget='€', updated_at=NOW() WHERE name='Nagham Café' AND city='Fès';
UPDATE restaurants SET cuisine='Marocaine gastronomique', budget='€€€', updated_at=NOW() WHERE name='Nur' AND city='Fès';
UPDATE restaurants SET cuisine='Marocaine', budget='€', updated_at=NOW() WHERE name='Restaurant Bouayad' AND city='Fès';
UPDATE restaurants SET cuisine='Marocaine', budget='€€€', updated_at=NOW() WHERE name='Palais de Fès Dar Tazi' AND city='Fès';
UPDATE restaurants SET cuisine='Marocaine', budget='€€€', updated_at=NOW() WHERE name='El Morocco Club' AND city='Tanger';
UPDATE restaurants SET cuisine='Poisson', budget='€€', updated_at=NOW() WHERE name='Saveurs de Poisson' AND city='Tanger';
UPDATE restaurants SET cuisine='Marocaine', budget='€€', updated_at=NOW() WHERE name='Le Salon Bleu' AND city='Tanger';
UPDATE restaurants SET cuisine='Italienne', budget='€€', updated_at=NOW() WHERE name='Anna & Paolo' AND city='Tanger';
UPDATE restaurants SET cuisine='Marocaine', budget='€€€', updated_at=NOW() WHERE name='Dar Nour' AND city='Tanger';
UPDATE restaurants SET cuisine='Poisson', budget='€', updated_at=NOW() WHERE name='Populaire Saveur de Poisson' AND city='Tanger';
UPDATE restaurants SET cuisine='Indienne', budget='€€', updated_at=NOW() WHERE name='Le Nabab' AND city='Tanger';
UPDATE restaurants SET cuisine='Café', budget='€', updated_at=NOW() WHERE name='Café Hafa' AND city='Tanger';
UPDATE restaurants SET cuisine='Brunch', budget='€€', updated_at=NOW() WHERE name='La Fabrique' AND city='Tanger';
UPDATE restaurants SET cuisine='Espagnole', budget='€€', updated_at=NOW() WHERE name='El Tangerino' AND city='Tanger';
UPDATE restaurants SET cuisine='Marocaine', budget='€', updated_at=NOW() WHERE name='Ahlen' AND city='Tanger';
UPDATE restaurants SET cuisine='Internationale', budget='€€', updated_at=NOW() WHERE name='Ocean Vagabond' AND city='Tanger';
UPDATE restaurants SET cuisine='Internationale', budget='€€€', updated_at=NOW() WHERE name='Pure Passion' AND city='Agadir';
UPDATE restaurants SET cuisine='Marocaine', budget='€€', updated_at=NOW() WHERE name='Le Jardin d''Eau' AND city='Agadir';
UPDATE restaurants SET cuisine='Italienne', budget='€€€', updated_at=NOW() WHERE name='La Scala' AND city='Agadir';
UPDATE restaurants SET cuisine='Poisson', budget='€', updated_at=NOW() WHERE name='Le Petit Pêcheur' AND city='Agadir';
UPDATE restaurants SET cuisine='Méditerranéenne', budget='€€', updated_at=NOW() WHERE name='Mezzo Mezzo' AND city='Agadir';
UPDATE restaurants SET cuisine='Internationale', budget='€€', updated_at=NOW() WHERE name='Taros' AND city='Agadir';
UPDATE restaurants SET cuisine='Grillades', budget='€', updated_at=NOW() WHERE name='Chez Mimi La Brochette' AND city='Agadir';
UPDATE restaurants SET cuisine='Internationale', budget='€€€€', updated_at=NOW() WHERE name='So Lounge' AND city='Agadir';
UPDATE restaurants SET cuisine='Café brunch', budget='€', updated_at=NOW() WHERE name='Jour et Nuit' AND city='Agadir';
UPDATE restaurants SET cuisine='Pub', budget='€€', updated_at=NOW() WHERE name='English Pub Agadir' AND city='Agadir';
UPDATE restaurants SET cuisine='Marocaine gastronomique', budget='€€€', updated_at=NOW() WHERE name='Le Riad Villa Blanche' AND city='Agadir';
UPDATE restaurants SET cuisine='Marocaine', budget='€', updated_at=NOW() WHERE name='Tafarnout' AND city='Agadir';
UPDATE restaurants SET cuisine='Marocaine', budget='€€', updated_at=NOW() WHERE name='Riad Bahia' AND city='Meknès';
UPDATE restaurants SET cuisine='Marocaine', budget='€', updated_at=NOW() WHERE name='Restaurant Zitouna' AND city='Meknès';
UPDATE restaurants SET cuisine='Marocaine gastronomique', budget='€€€', updated_at=NOW() WHERE name='Palais Terrab' AND city='Meknès';
UPDATE restaurants SET cuisine='Marocaine', budget='€', updated_at=NOW() WHERE name='Aïssi' AND city='Meknès';
UPDATE restaurants SET cuisine='Méditerranéenne', budget='€€', updated_at=NOW() WHERE name='Chez Dimitri' AND city='Meknès';
UPDATE restaurants SET cuisine='Grillades', budget='€€', updated_at=NOW() WHERE name='La Grillardière' AND city='Meknès';
UPDATE restaurants SET cuisine='Marocaine', budget='€€€', updated_at=NOW() WHERE name='Le Collier de la Colombe' AND city='Meknès';
UPDATE restaurants SET cuisine='Française', budget='€€€', updated_at=NOW() WHERE name='Le Dauphin' AND city='Meknès';
UPDATE restaurants SET cuisine='Marocaine', budget='€€', updated_at=NOW() WHERE name='Ryad El Ma' AND city='Meknès';
UPDATE restaurants SET cuisine='Italienne', budget='€', updated_at=NOW() WHERE name='Pizza Roma' AND city='Meknès';
UPDATE restaurants SET cuisine='Café', budget='€', updated_at=NOW() WHERE name='Café Les Arcades' AND city='Meknès';
UPDATE restaurants SET cuisine='Marocaine fusion', budget='€€', updated_at=NOW() WHERE name='Kenza' AND city='Meknès';
UPDATE restaurants SET cuisine='Française', budget='€€€', updated_at=NOW() WHERE name='Le Dauphin Oujda' AND city='Oujda';
UPDATE restaurants SET cuisine='Italienne', budget='€€', updated_at=NOW() WHERE name='Restaurant Como' AND city='Oujda';
UPDATE restaurants SET cuisine='Marocaine', budget='€', updated_at=NOW() WHERE name='Le Rif' AND city='Oujda';
UPDATE restaurants SET cuisine='Internationale', budget='€€', updated_at=NOW() WHERE name='Le Garden' AND city='Oujda';
UPDATE restaurants SET cuisine='Marocaine', budget='€', updated_at=NOW() WHERE name='Sidi Yahia' AND city='Oujda';
UPDATE restaurants SET cuisine='Marocaine gastronomique', budget='€€€', updated_at=NOW() WHERE name='Mamounia Palace Oujda' AND city='Oujda';
UPDATE restaurants SET cuisine='Snack', budget='€', updated_at=NOW() WHERE name='Snack Bab Sidi Abdelwahab' AND city='Oujda';
UPDATE restaurants SET cuisine='Café', budget='€', updated_at=NOW() WHERE name='Café Bab El Gharbi' AND city='Oujda';
UPDATE restaurants SET cuisine='Poisson', budget='€€', updated_at=NOW() WHERE name='La Fontaine Bleue' AND city='Oujda';
UPDATE restaurants SET cuisine='Internationale', budget='€€', updated_at=NOW() WHERE name='Royal Air Maroc Restaurant' AND city='Oujda';
UPDATE restaurants SET cuisine='Grillades', budget='€€', updated_at=NOW() WHERE name='Grill House Oujda' AND city='Oujda';
UPDATE restaurants SET cuisine='Marocaine', budget='€€', updated_at=NOW() WHERE name='Le Jasmin' AND city='Oujda';
UPDATE restaurants SET cuisine='Française', budget='€€€', updated_at=NOW() WHERE name='La Maison Blanche' AND city='Kénitra';
UPDATE restaurants SET cuisine='Marocaine', budget='€€', updated_at=NOW() WHERE name='L''Oliveraie' AND city='Kénitra';
UPDATE restaurants SET cuisine='Poisson', budget='€€', updated_at=NOW() WHERE name='Plage des Nations' AND city='Kénitra';
UPDATE restaurants SET cuisine='Marocaine', budget='€', updated_at=NOW() WHERE name='Le Gharb' AND city='Kénitra';
UPDATE restaurants SET cuisine='Café', budget='€', updated_at=NOW() WHERE name='Café de la Gare' AND city='Kénitra';
UPDATE restaurants SET cuisine='Italienne', budget='€', updated_at=NOW() WHERE name='Pizza Del Sol' AND city='Kénitra';
UPDATE restaurants SET cuisine='Marocaine', budget='€€', updated_at=NOW() WHERE name='Le Kasbah Mehdiya' AND city='Kénitra';
UPDATE restaurants SET cuisine='Grillades', budget='€€', updated_at=NOW() WHERE name='Grill Master' AND city='Kénitra';
UPDATE restaurants SET cuisine='Japonaise', budget='€€', updated_at=NOW() WHERE name='Sushi Time Kénitra' AND city='Kénitra';
UPDATE restaurants SET cuisine='Marocaine gastronomique', budget='€€€', updated_at=NOW() WHERE name='Riad Al Gharb' AND city='Kénitra';
UPDATE restaurants SET cuisine='Marocaine', budget='€', updated_at=NOW() WHERE name='Chez Hamid' AND city='Kénitra';
UPDATE restaurants SET cuisine='Marocaine', budget='€€', updated_at=NOW() WHERE name='La Mamounia Kénitra' AND city='Kénitra';
UPDATE restaurants SET cuisine='Poisson', budget='€€', updated_at=NOW() WHERE name='Restaurant Restinga' AND city='Tétouan';
UPDATE restaurants SET cuisine='Méditerranéenne', budget='€€€', updated_at=NOW() WHERE name='Blanco Riad' AND city='Tétouan';
UPDATE restaurants SET cuisine='Marocaine', budget='€€€', updated_at=NOW() WHERE name='Riad El Reducto' AND city='Tétouan';
UPDATE restaurants SET cuisine='Espagnole', budget='€€', updated_at=NOW() WHERE name='El Rincón Andaluz' AND city='Tétouan';
UPDATE restaurants SET cuisine='Pâtisserie', budget='€', updated_at=NOW() WHERE name='Pâtisserie Rahmouni' AND city='Tétouan';
UPDATE restaurants SET cuisine='Marocaine', budget='€€', updated_at=NOW() WHERE name='Al Mandari' AND city='Tétouan';
UPDATE restaurants SET cuisine='Poisson', budget='€€€', updated_at=NOW() WHERE name='Marina Smir' AND city='Tétouan';
UPDATE restaurants SET cuisine='Café', budget='€', updated_at=NOW() WHERE name='Café Sáfir' AND city='Tétouan';
UPDATE restaurants SET cuisine='Marocaine fusion', budget='€€', updated_at=NOW() WHERE name='Le Zellij' AND city='Tétouan';
UPDATE restaurants SET cuisine='Internationale', budget='€€', updated_at=NOW() WHERE name='La Terrasse' AND city='Tétouan';
UPDATE restaurants SET cuisine='Marocaine', budget='€', updated_at=NOW() WHERE name='Chez Abdou' AND city='Tétouan';
UPDATE restaurants SET cuisine='Internationale', budget='€€€', updated_at=NOW() WHERE name='Club Cabo Negro' AND city='Tétouan';

COMMIT;
