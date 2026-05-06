-- ════════════════════════════════════════════════════════════════════
-- OneClick Backend — Flyway baseline migration V1
-- ════════════════════════════════════════════════════════════════════
-- Generated from: oneclick_local (Postgres 17.x), via pg_dump --schema-only
-- Source database: backup of Supabase prod project vghevjywcbllzhhzzezf
-- Generated on: 2026-05-06
--
-- Stripped blocks (Supabase-specific, replaced by Spring Security in Java):
--   - 20 CREATE POLICY ... (RLS authorization → Spring Security @PreAuthorize)
--   - 93 ALTER TABLE ... ENABLE ROW LEVEL SECURITY (RLS opt-in)
--   - GRANT TO authenticated|service_role|anon (Supabase-only roles)
--
-- Added: auth schema compatibility shim
--   - auth.uid()/role()/jwt() : NULL-returning stubs for legacy SECURITY DEFINER
--     functions (115+ references). Authorization enforced in Spring Security.
--   - auth.users : table replicated 1:1 from Supabase Auth schema.
--     Required for FK constraints in profiles/user_roles/etc.
--     When Spring auth module is built, FKs will migrate to public.users.
--
-- Note: This baseline is reference-only on databases already at this state
-- (baseline-on-migrate=true, baseline-version=0 in application.yml).
-- It is executed only on a fresh Postgres database.
-- ════════════════════════════════════════════════════════════════════

-- ─── Auth schema compatibility shim ──────────────────────────────────

CREATE SCHEMA IF NOT EXISTS auth;

-- auth.uid/role/jwt : NULL stubs for legacy SECURITY DEFINER functions.
-- Real authorization is moved to Spring Security at the service layer.
CREATE OR REPLACE FUNCTION auth.uid()
RETURNS uuid LANGUAGE sql STABLE AS $$ SELECT NULL::uuid $$;

CREATE OR REPLACE FUNCTION auth.role()
RETURNS text LANGUAGE sql STABLE AS $$ SELECT NULL::text $$;

CREATE OR REPLACE FUNCTION auth.jwt()
RETURNS jsonb LANGUAGE sql STABLE AS $$ SELECT '{}'::jsonb $$;

-- auth.users : 1:1 copy of Supabase Auth users table structure.
-- Kept as-is so FK constraints from public schema continue to work.
-- Will be replaced by public.users when Spring auth module is implemented.
CREATE TABLE IF NOT EXISTS auth.users (
    instance_id                  uuid,
    id                           uuid NOT NULL,
    aud                          character varying(255),
    role                         character varying(255),
    email                        character varying(255),
    encrypted_password           character varying(255),
    email_confirmed_at           timestamp with time zone,
    invited_at                   timestamp with time zone,
    confirmation_token           character varying(255),
    confirmation_sent_at         timestamp with time zone,
    recovery_token               character varying(255),
    recovery_sent_at             timestamp with time zone,
    email_change_token_new       character varying(255),
    email_change                 character varying(255),
    email_change_sent_at         timestamp with time zone,
    last_sign_in_at              timestamp with time zone,
    raw_app_meta_data            jsonb,
    raw_user_meta_data           jsonb,
    is_super_admin               boolean,
    created_at                   timestamp with time zone,
    updated_at                   timestamp with time zone,
    phone                        text DEFAULT NULL::character varying,
    phone_confirmed_at           timestamp with time zone,
    phone_change                 text DEFAULT ''::character varying,
    phone_change_token           character varying(255) DEFAULT ''::character varying,
    phone_change_sent_at         timestamp with time zone,
    confirmed_at                 timestamp with time zone GENERATED ALWAYS AS (LEAST(email_confirmed_at, phone_confirmed_at)) STORED,
    email_change_token_current   character varying(255) DEFAULT ''::character varying,
    email_change_confirm_status  smallint DEFAULT 0,
    banned_until                 timestamp with time zone,
    reauthentication_token       character varying(255) DEFAULT ''::character varying,
    reauthentication_sent_at     timestamp with time zone,
    is_sso_user                  boolean DEFAULT false NOT NULL,
    deleted_at                   timestamp with time zone,
    is_anonymous                 boolean DEFAULT false NOT NULL,
    CONSTRAINT users_email_change_confirm_status_check
        CHECK (email_change_confirm_status >= 0 AND email_change_confirm_status <= 2),
    CONSTRAINT users_pkey PRIMARY KEY (id),
    CONSTRAINT users_phone_key UNIQUE (phone)
);

CREATE UNIQUE INDEX IF NOT EXISTS confirmation_token_idx
  ON auth.users (confirmation_token) WHERE ((confirmation_token)::text !~ '^[0-9 ]*$'::text);
CREATE UNIQUE INDEX IF NOT EXISTS email_change_token_current_idx
  ON auth.users (email_change_token_current) WHERE ((email_change_token_current)::text !~ '^[0-9 ]*$'::text);
CREATE UNIQUE INDEX IF NOT EXISTS email_change_token_new_idx
  ON auth.users (email_change_token_new) WHERE ((email_change_token_new)::text !~ '^[0-9 ]*$'::text);
CREATE UNIQUE INDEX IF NOT EXISTS reauthentication_token_idx
  ON auth.users (reauthentication_token) WHERE ((reauthentication_token)::text !~ '^[0-9 ]*$'::text);
CREATE UNIQUE INDEX IF NOT EXISTS recovery_token_idx
  ON auth.users (recovery_token) WHERE ((recovery_token)::text !~ '^[0-9 ]*$'::text);
CREATE UNIQUE INDEX IF NOT EXISTS users_email_partial_key
  ON auth.users (email) WHERE (is_sso_user = false);
CREATE INDEX IF NOT EXISTS users_instance_id_email_idx
  ON auth.users (instance_id, lower((email)::text));
CREATE INDEX IF NOT EXISTS users_instance_id_idx ON auth.users (instance_id);
CREATE INDEX IF NOT EXISTS users_is_anonymous_idx ON auth.users (is_anonymous);

-- ─── End of auth shim. Public schema dump follows. ───────────────────


--
-- PostgreSQL database dump
--

\restrict 0YxiKgvX5wMLzN9pNhW3BUFmYlY6DwjFER1IYoOdrZUndawMEbHzbmd4aRTQO99

-- Dumped from database version 17.9 (Homebrew)
-- Dumped by pg_dump version 17.9 (Homebrew)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: public; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA IF NOT EXISTS public;

--
-- Name: SCHEMA public; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON SCHEMA public IS 'standard public schema';

--
-- Name: announcement_priority; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.announcement_priority AS ENUM (
    'urgent',
    'permanent'
);

--
-- Name: app_role; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.app_role AS ENUM (
    'admin',
    'restaurateur',
    'client',
    'tenant_admin'
);

--
-- Name: bookable_payment_mode; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.bookable_payment_mode AS ENUM (
    'on_site',
    'upfront'
);

--
-- Name: bookable_resource_type; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.bookable_resource_type AS ENUM (
    'padel_court',
    'spa_room',
    'golf_tee',
    'seminar_room',
    'restaurant_table',
    'barber_chair',
    'coach_session',
    'tennis_court',
    'football_field',
    'basketball_court'
);

--
-- Name: contract_status; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.contract_status AS ENUM (
    'prospect',
    'en_négociation',
    'envoyé',
    'actif',
    'renouvellement',
    'expiré',
    'résilié'
);

--
-- Name: distribution_source; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.distribution_source AS ENUM (
    'wallet_admin',
    'restaurant_credit',
    'bonus_system',
    'recycled_expired'
);

--
-- Name: invitation_status; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.invitation_status AS ENUM (
    'en_attente',
    'acceptée',
    'refusée',
    'expirée',
    'annulée'
);

--
-- Name: offer_type; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.offer_type AS ENUM (
    'promo',
    'bonus',
    'reco'
);

--
-- Name: punch_card_activity; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.punch_card_activity AS ENUM (
    'padel',
    'spa',
    'golf',
    'seminar',
    'barber',
    'gym'
);

--
-- Name: reservation_status; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.reservation_status AS ENUM (
    'demandée',
    'confirmée',
    'placée',
    'terminée',
    'annulée',
    'honorée',
    'no_show',
    'refusée',
    'contre_proposition',
    'en_attente'
);

--
-- Name: resource_booking_status; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.resource_booking_status AS ENUM (
    'demandee',
    'confirmee',
    'honoree',
    'no_show',
    'annulee'
);

--
-- Name: restaurant_status; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.restaurant_status AS ENUM (
    'actif',
    'inactif',
    'suspendu'
);

--
-- Name: staff_role; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.staff_role AS ENUM (
    'owner',
    'manager',
    'waiter',
    'serveur',
    'chef_de_rang',
    'barman',
    'caissier',
    'controleur',
    'responsable_resa',
    'directeur'
);

--
-- Name: ticket_status; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.ticket_status AS ENUM (
    'ouvert',
    'en_cours',
    'en_attente',
    'résolu',
    'fermé'
);

--
-- Name: activate_referral_by_code(text, uuid, uuid); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.activate_referral_by_code(_code text, _filleul_id uuid, _restaurant_id uuid) RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  _referrer_id uuid;
BEGIN
  -- Find the referrer by code
  SELECT id INTO _referrer_id FROM public.profiles WHERE referral_code = upper(_code);
  IF _referrer_id IS NULL THEN
    RAISE EXCEPTION 'Code parrain invalide';
  END IF;
  IF _referrer_id = _filleul_id THEN
    RAISE EXCEPTION 'Vous ne pouvez pas vous parrainer vous-même';
  END IF;
  -- Check if already referred by this person
  IF EXISTS (
    SELECT 1 FROM public.referrals
    WHERE referrer_id = _referrer_id AND referred_user_id = _filleul_id
  ) THEN
    RAISE EXCEPTION 'Parrainage déjà enregistré';
  END IF;

  -- Create the referral and set it active immediately
  INSERT INTO public.referrals (
    referrer_id, referred_user_id, referred_phone, referred_name,
    restaurant_id, status
  )
  SELECT
    _referrer_id, _filleul_id,
    COALESCE(p.phone, ''),
    CONCAT(p.first_name, ' ', p.last_name),
    _restaurant_id, 'actif'
  FROM public.profiles p
  WHERE p.id = _filleul_id;

  -- ─── Sprint 04/05 FIX REGRESSION : Auto-friendship ───────────────
  -- Le parrainage établit une relation amicale (cohérence métier).
  -- Direction : filleul = requester (initiative), referrer = addressee.
  -- Status accepted direct (la relation est déjà validée par le code).
  -- Idempotent via ON CONFLICT (UNIQUE requester_id, addressee_id).
  INSERT INTO public.friendships (requester_id, addressee_id, status)
  VALUES (_filleul_id, _referrer_id, 'accepted')
  ON CONFLICT (requester_id, addressee_id) DO NOTHING;

  -- Si une demande pending existe déjà dans l'autre sens (ex: referrer
  -- avait envoyé une demande au filleul avant), passer à accepted.
  UPDATE public.friendships
     SET status = 'accepted', updated_at = NOW()
   WHERE requester_id = _referrer_id
     AND addressee_id = _filleul_id
     AND status = 'pending';
END;
$$;

--
-- Name: add_pcc_family_member(text, text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.add_pcc_family_member(p_identifier text, p_relation text DEFAULT NULL::text) RETURNS json
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  v_caller UUID := auth.uid();
  v_target UUID;
  v_target_first TEXT;
  v_target_last TEXT;
  v_clean TEXT;
  v_palmeraie_id UUID := '0cccc000-0000-4000-8000-000000000001'::UUID;
  v_relation_id UUID;
BEGIN
  IF v_caller IS NULL THEN
    RAISE EXCEPTION 'NOT_AUTHENTICATED';
  END IF;

  v_clean := TRIM(p_identifier);
  IF v_clean IS NULL OR LENGTH(v_clean) = 0 THEN
    RAISE EXCEPTION 'IDENTIFIER_REQUIRED';
  END IF;

  -- Auto-detect format : email | code OC-XXX | téléphone
  IF v_clean LIKE '%@%' THEN
    SELECT id, first_name, last_name INTO v_target, v_target_first, v_target_last
    FROM public.profiles
    WHERE LOWER(email) = LOWER(v_clean) AND tenant_id = v_palmeraie_id
    LIMIT 1;
  ELSIF UPPER(v_clean) LIKE 'OC-%' THEN
    SELECT id, first_name, last_name INTO v_target, v_target_first, v_target_last
    FROM public.profiles
    WHERE UPPER(referral_code) = UPPER(v_clean) AND tenant_id = v_palmeraie_id
    LIMIT 1;
  ELSE
    -- Téléphone : nettoie espaces / tirets pour comparaison
    SELECT id, first_name, last_name INTO v_target, v_target_first, v_target_last
    FROM public.profiles
    WHERE REPLACE(REPLACE(REPLACE(phone, ' ', ''), '-', ''), '.', '') = REPLACE(REPLACE(REPLACE(v_clean, ' ', ''), '-', ''), '.', '')
      AND tenant_id = v_palmeraie_id
    LIMIT 1;
  END IF;

  IF v_target IS NULL THEN
    RAISE EXCEPTION 'TARGET_NOT_FOUND' USING HINT = 'Aucun membre Palmeraie trouvé avec cet identifiant';
  END IF;

  IF v_target = v_caller THEN
    RAISE EXCEPTION 'CANNOT_ADD_SELF';
  END IF;

  -- Idempotent
  SELECT id INTO v_relation_id
  FROM public.pcc_family_members
  WHERE member_id = v_caller AND related_member_id = v_target;

  IF v_relation_id IS NOT NULL THEN
    RETURN json_build_object(
      'status', 'already_added',
      'relation_id', v_relation_id,
      'target_id', v_target,
      'target_name', COALESCE(v_target_first || ' ' || v_target_last, '')
    );
  END IF;

  INSERT INTO public.pcc_family_members (member_id, related_member_id, relation)
  VALUES (v_caller, v_target, p_relation)
  RETURNING id INTO v_relation_id;

  RETURN json_build_object(
    'status', 'added',
    'relation_id', v_relation_id,
    'target_id', v_target,
    'target_name', COALESCE(v_target_first || ' ' || v_target_last, '')
  );
END $$;

--
-- Name: assign_rule_to_restaurants(uuid, uuid[]); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.assign_rule_to_restaurants(p_rule_id uuid, p_restaurant_ids uuid[]) RETURNS TABLE(inserted_count integer, updated_count integer, skipped_count integer)
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  v_rule public.gain_rules%ROWTYPE;
  v_inserted INTEGER := 0;
  v_updated  INTEGER := 0;
  v_skipped  INTEGER := 0;
  v_resto_id UUID;
BEGIN
  IF NOT has_role(auth.uid(), 'admin'::app_role) THEN
    RAISE EXCEPTION 'not authorized';
  END IF;

  SELECT * INTO v_rule FROM public.gain_rules WHERE id = p_rule_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'gain_rule % not found', p_rule_id;
  END IF;

  IF p_restaurant_ids IS NULL OR array_length(p_restaurant_ids, 1) IS NULL THEN
    RETURN QUERY SELECT 0, 0, 0;
    RETURN;
  END IF;

  FOREACH v_resto_id IN ARRAY p_restaurant_ids LOOP
    -- UPSERT sur la contrainte unique (restaurant_id, source_rule_id)
    INSERT INTO public.restaurant_gain_rules (
      restaurant_id, source_rule_id, name, description, type,
      taux_conversion, min_ticket, max_points_par_ticket,
      point_value_mad, enabled
    )
    VALUES (
      v_resto_id, p_rule_id, v_rule.name, v_rule.description, v_rule.type,
      v_rule.taux_conversion, v_rule.min_ticket, v_rule.max_points_par_ticket,
      v_rule.point_value_mad, v_rule.enabled
    )
    ON CONFLICT (restaurant_id, source_rule_id)
    WHERE source_rule_id IS NOT NULL
    DO UPDATE SET
      name                  = EXCLUDED.name,
      description           = EXCLUDED.description,
      type                  = EXCLUDED.type,
      taux_conversion       = EXCLUDED.taux_conversion,
      min_ticket            = EXCLUDED.min_ticket,
      max_points_par_ticket = EXCLUDED.max_points_par_ticket,
      point_value_mad       = EXCLUDED.point_value_mad,
      enabled               = EXCLUDED.enabled,
      updated_at            = now();

    -- Différencier insert vs update via xmax = 0 (insert) ou non (update)
    -- Pas exposé via INSERT … ON CONFLICT sans RETURNING, on approxime :
    IF FOUND THEN
      -- on compte comme "update or insert" indifféremment, car l'UX ne
      -- distingue pas. On laisse 0 sur skipped.
      v_inserted := v_inserted + 1;
    END IF;
  END LOOP;

  RETURN QUERY SELECT v_inserted, v_updated, v_skipped;
END;
$$;

--
-- Name: FUNCTION assign_rule_to_restaurants(p_rule_id uuid, p_restaurant_ids uuid[]); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.assign_rule_to_restaurants(p_rule_id uuid, p_restaurant_ids uuid[]) IS 'Admin-only: upsert restaurant_gain_rules rows from a source gain_rule for each target restaurant. Idempotent.';

--
-- Name: bulk_update_restitutions_status(uuid[], text, text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.bulk_update_restitutions_status(p_ids uuid[], p_status text, p_notes text DEFAULT NULL::text) RETURNS TABLE(updated_count integer, total_points bigint, restitutions jsonb)
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  v_admin_id UUID := auth.uid();
  v_count INTEGER := 0;
  v_sum BIGINT := 0;
  v_rows JSONB;
  v_restitution RECORD;
BEGIN
  -- Autorisation
  IF NOT has_role(v_admin_id, 'admin'::app_role) THEN
    RAISE EXCEPTION 'not authorized';
  END IF;

  -- Validation du statut cible
  IF p_status NOT IN ('en_attente', 'validee', 'versee', 'annulee') THEN
    RAISE EXCEPTION 'invalid status: %', p_status;
  END IF;

  IF p_ids IS NULL OR array_length(p_ids, 1) IS NULL THEN
    RETURN QUERY SELECT 0::INTEGER, 0::BIGINT, '[]'::jsonb;
    RETURN;
  END IF;

  -- Update idempotent : on set processed_at uniquement quand status = 'versee'.
  -- Les notes ne sont appliquées QUE si passées (NULL = on garde l'existant).
  WITH updated AS (
    UPDATE public.restaurant_restitutions
    SET
      status       = p_status,
      processed_by = CASE WHEN p_status = 'versee' THEN v_admin_id ELSE processed_by END,
      processed_at = CASE WHEN p_status = 'versee' THEN now() ELSE NULL END,
      notes        = COALESCE(p_notes, notes),
      updated_at   = now()
    WHERE id = ANY(p_ids)
    RETURNING id, restaurant_id, period_month, total_points
  )
  SELECT
    COUNT(*)::INTEGER,
    COALESCE(SUM(total_points), 0)::BIGINT,
    COALESCE(jsonb_agg(jsonb_build_object(
      'id', id,
      'restaurant_id', restaurant_id,
      'period_month', period_month,
      'total_points', total_points
    )), '[]'::jsonb)
  INTO v_count, v_sum, v_rows
  FROM updated;

  -- ===== FIX #4 (22/04) — Notif staff resto quand status='versee' =====
  -- Pour chaque restitution versée, notifie tout le staff actif du resto concerné.
  -- Cible volontairement large : owner/manager/directeur/serveur tous notifiés
  -- (1x/mois max, pas spam). Fail-safe : si notif échoue, ne bloque pas l'update.
  IF p_status = 'versee' AND v_count > 0 THEN
    BEGIN
      FOR v_restitution IN
        SELECT
          r.restaurant_id,
          r.period_month,
          r.total_points,
          rest.name AS restaurant_name
        FROM public.restaurant_restitutions r
        LEFT JOIN public.restaurants rest ON rest.id = r.restaurant_id
        WHERE r.id = ANY(p_ids) AND r.status = 'versee'
      LOOP
        INSERT INTO public.notifications (user_id, title, message, type, link, restaurant_id)
        SELECT
          rs.user_id,
          'Restitution versée',
          format(
            'Votre restitution mensuelle de %s points pour %s%s a été versée. Consultez le détail dans votre espace.',
            v_restitution.total_points,
            to_char(v_restitution.period_month, 'TMMonth YYYY'),
            CASE WHEN v_restitution.restaurant_name IS NOT NULL
                 THEN ' (' || v_restitution.restaurant_name || ')'
                 ELSE '' END
          ),
          'restitution',
          '/prodesk/oneclickhi',
          v_restitution.restaurant_id
        FROM public.restaurant_staff rs
        WHERE rs.restaurant_id = v_restitution.restaurant_id
          AND rs.status = 'actif';
      END LOOP;
    EXCEPTION WHEN OTHERS THEN
      RAISE NOTICE 'Notif restitution failed: %', SQLERRM;
    END;
  END IF;

  -- Audit : trace dans admin_notifications si >= 5 versées en masse
  IF p_status = 'versee' AND v_count >= 5 THEN
    INSERT INTO public.admin_notifications (type, severity, title, description, target_url, metadata)
    VALUES (
      'system',
      'info',
      format('%s restitutions marquées versées', v_count),
      format('Un total de %s points a été versé en une opération par un admin.', v_sum),
      '/forge/restitutions',
      jsonb_build_object(
        'bulk_count', v_count,
        'bulk_total_points', v_sum,
        'by_admin', v_admin_id
      )
    );
  END IF;

  RETURN QUERY SELECT v_count, v_sum, v_rows;
END;
$$;

--
-- Name: FUNCTION bulk_update_restitutions_status(p_ids uuid[], p_status text, p_notes text); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.bulk_update_restitutions_status(p_ids uuid[], p_status text, p_notes text) IS 'Admin-only: change status of many restitutions in one atomic call. Returns updated_count, total_points, and the touched rows as JSONB. Emits a system notification on bulk-payment (>=5 versee in one call).';

--
-- Name: bump_body_version_reset_reads(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.bump_body_version_reset_reads() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  IF NEW.body IS DISTINCT FROM OLD.body THEN
    NEW.body_version = OLD.body_version + 1;
    -- Reset reads : tous les staff doivent re-acknowledger la nouvelle version
    DELETE FROM public.announcement_reads WHERE announcement_id = NEW.id;
  END IF;
  RETURN NEW;
END $$;

--
-- Name: cancel_resource_booking(uuid); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.cancel_resource_booking(p_booking_id uuid) RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  v_booking public.resource_bookings;
  v_resource public.bookable_resources;
  v_is_staff BOOLEAN;
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Non authentifié'; END IF;

  SELECT * INTO v_booking FROM public.resource_bookings WHERE id = p_booking_id;
  IF NOT FOUND THEN RAISE EXCEPTION 'Réservation introuvable'; END IF;

  SELECT * INTO v_resource FROM public.bookable_resources WHERE id = v_booking.resource_id;

  v_is_staff := public.has_role(auth.uid(), 'admin'::app_role)
    OR EXISTS (
      SELECT 1 FROM public.restaurant_staff
      WHERE user_id = auth.uid()
        AND restaurant_id = v_resource.restaurant_id
        AND status = 'actif'
    );

  IF v_booking.organizer_id <> auth.uid() AND NOT v_is_staff THEN
    RAISE EXCEPTION 'Accès refusé';
  END IF;

  IF v_booking.organizer_id = auth.uid() AND NOT v_is_staff THEN
    IF v_booking.start_at - NOW() < INTERVAL '2 hours' THEN
      RAISE EXCEPTION 'Annulation impossible moins de 2h avant la réservation';
    END IF;
  END IF;

  IF v_booking.status IN ('honoree', 'no_show', 'annulee') THEN
    RAISE EXCEPTION 'Réservation déjà clôturée (status=%)', v_booking.status;
  END IF;

  UPDATE public.resource_bookings SET status = 'annulee', updated_at = NOW() WHERE id = p_booking_id;
END;
$$;

--
-- Name: check_and_increment_ai_usage(uuid, integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.check_and_increment_ai_usage(p_user_id uuid, p_max integer DEFAULT 10) RETURNS jsonb
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  v_now timestamptz := now();
  v_reset_window interval := interval '24 hours';
  v_row ai_usage%ROWTYPE;
  v_new_count int;
BEGIN
  -- Bypass whitelist : QA/test accounts → quota infini, pas de tracking
  IF EXISTS (SELECT 1 FROM public.ai_usage_bypass WHERE user_id = p_user_id) THEN
    RETURN jsonb_build_object(
      'allowed', true,
      'remaining', 999999,
      'reset_at', v_now + v_reset_window,
      'bypass', true
    );
  END IF;

  SELECT * INTO v_row FROM public.ai_usage
    WHERE user_id = p_user_id
    FOR UPDATE;

  IF NOT FOUND THEN
    INSERT INTO public.ai_usage (user_id, prompt_count, last_prompt_at)
      VALUES (p_user_id, 1, v_now);
    RETURN jsonb_build_object(
      'allowed', true,
      'remaining', GREATEST(p_max - 1, 0),
      'reset_at', v_now + v_reset_window
    );
  END IF;

  IF v_row.last_prompt_at IS NULL OR (v_now - v_row.last_prompt_at) >= v_reset_window THEN
    UPDATE public.ai_usage
      SET prompt_count = 1, last_prompt_at = v_now
      WHERE user_id = p_user_id;
    RETURN jsonb_build_object(
      'allowed', true,
      'remaining', GREATEST(p_max - 1, 0),
      'reset_at', v_now + v_reset_window
    );
  END IF;

  IF v_row.prompt_count >= p_max THEN
    RETURN jsonb_build_object(
      'allowed', false,
      'remaining', 0,
      'reset_at', v_row.last_prompt_at + v_reset_window
    );
  END IF;

  v_new_count := v_row.prompt_count + 1;
  UPDATE public.ai_usage
    SET prompt_count = v_new_count, last_prompt_at = v_now
    WHERE user_id = p_user_id;
  RETURN jsonb_build_object(
    'allowed', true,
    'remaining', GREATEST(p_max - v_new_count, 0),
    'reset_at', v_row.last_prompt_at + v_reset_window
  );
END;
$$;

--
-- Name: check_dispute_count(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.check_dispute_count() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  v_count int;
BEGIN
  SELECT COUNT(*) INTO v_count FROM public.no_show_disputes
    WHERE reservation_id = NEW.reservation_id;
  IF v_count >= 2 THEN
    RAISE EXCEPTION 'Max 2 contestations par réservation atteint' USING ERRCODE = 'check_violation';
  END IF;
  IF v_count = 1 AND NOT NEW.is_recontestation THEN
    RAISE EXCEPTION 'La 2e contestation doit être marquée comme re-contestation (avec photo)' USING ERRCODE = 'check_violation';
  END IF;
  IF v_count = 1 AND NEW.photo_url IS NULL THEN
    RAISE EXCEPTION 'La re-contestation nécessite une photo' USING ERRCODE = 'check_violation';
  END IF;
  RETURN NEW;
END;
$$;

--
-- Name: check_guest_invited_by_owner(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.check_guest_invited_by_owner() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM public.reservations
    WHERE id = NEW.reservation_id AND client_id = NEW.invited_by
  ) THEN
    RAISE EXCEPTION 'Seul le propriétaire de la réservation peut inviter des convives.';
  END IF;
  RETURN NEW;
END;
$$;

--
-- Name: check_max_active_staff(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.check_max_active_staff() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  _count integer;
  _max integer;
BEGIN
  IF NEW.status = 'actif' THEN
    SELECT max_staff INTO _max FROM public.restaurants WHERE id = NEW.restaurant_id;
    _max := COALESCE(_max, 20);

    SELECT COUNT(*) INTO _count
    FROM public.restaurant_staff
    WHERE restaurant_id = NEW.restaurant_id
      AND status = 'actif'
      AND id IS DISTINCT FROM NEW.id;

    IF _count >= _max THEN
      RAISE EXCEPTION 'Limite atteinte : maximum % membres actifs par restaurant. Contactez le support pour augmenter cette limite.', _max;
    END IF;
  END IF;

  RETURN NEW;
END;
$$;

--
-- Name: check_max_guests(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.check_max_guests() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
BEGIN
  IF NEW.couverts > 8 THEN
    RAISE EXCEPTION 'Le nombre maximum de convives est de 8 personnes.';
  END IF;
  IF NEW.couverts < 1 THEN
    RAISE EXCEPTION 'Le nombre minimum de convives est de 1 personne.';
  END IF;
  RETURN NEW;
END;
$$;

--
-- Name: check_max_reservations_per_day(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.check_max_reservations_per_day() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  _count integer;
BEGIN
  SELECT COUNT(*) INTO _count
  FROM public.reservations
  WHERE client_id = NEW.client_id
    AND restaurant_id = NEW.restaurant_id
    AND date = NEW.date
    AND status NOT IN ('annulée', 'refusée', 'no_show')
    AND id IS DISTINCT FROM NEW.id;

  IF _count >= 2 THEN
    RAISE EXCEPTION 'Limite atteinte : maximum 2 réservations actives par jour et par restaurant.';
  END IF;

  RETURN NEW;
END;
$$;

--
-- Name: check_pcc_family_max_10(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.check_pcc_family_max_10() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  v_count INT;
BEGIN
  SELECT COUNT(*) INTO v_count
  FROM public.pcc_family_members
  WHERE member_id = NEW.member_id;
  IF v_count >= 10 THEN
    RAISE EXCEPTION 'PCC_FAMILY_MAX_REACHED' USING HINT = 'Limite de 10 membres famille atteinte';
  END IF;
  RETURN NEW;
END $$;

--
-- Name: check_reservation_not_in_past(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.check_reservation_not_in_past() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
BEGIN
  IF (NEW.date + NEW.heure::time) < now() THEN
    RAISE EXCEPTION 'Impossible de réserver un créneau déjà passé.';
  END IF;
  RETURN NEW;
END;
$$;

--
-- Name: check_reservation_update_valid(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.check_reservation_update_valid() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
BEGIN
  -- When setting a counter-proposal, validate the new date is not in the past
  IF NEW.status = 'contre_proposition' AND NEW.date < CURRENT_DATE THEN
    RAISE EXCEPTION 'La date de contre-proposition ne peut pas être dans le passé';
  END IF;

  -- Also validate when date changes on any status that is not terminal
  IF NEW.date IS DISTINCT FROM OLD.date
     AND NEW.date < CURRENT_DATE
     AND NEW.status NOT IN ('annulée', 'terminée', 'honorée', 'no_show', 'refusée') THEN
    RAISE EXCEPTION 'La date de réservation ne peut pas être dans le passé';
  END IF;

  RETURN NEW;
END;
$$;

--
-- Name: cleanup_old_monitor_logs(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.cleanup_old_monitor_logs() RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER
    AS $$
BEGIN
  DELETE FROM public.monitor_logs WHERE created_at < now() - interval '30 days';
END;
$$;

--
-- Name: compute_client_score(uuid); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.compute_client_score(p_client_id uuid) RETURNS numeric
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  _score NUMERIC(3,1);
  _honorees INTEGER;
  _no_shows INTEGER;
  _total INTEGER;
BEGIN
  SELECT
    COUNT(*) FILTER (WHERE status = 'honorée'),
    COUNT(*) FILTER (WHERE status = 'no_show'),
    COUNT(*)
  INTO _honorees, _no_shows, _total
  FROM public.reservations
  WHERE client_id = p_client_id
    AND status IN ('honorée', 'no_show')
    AND date >= (CURRENT_DATE - INTERVAL '6 months');

  IF _total = 0 THEN
    _score := NULL;
  ELSE
    _score := ROUND((_honorees::NUMERIC / _total) * 5, 1);
  END IF;

  -- Cache in profiles
  UPDATE public.profiles
    SET reliability_score = _score,
        score_updated_at = now()
    WHERE id = p_client_id;

  RETURN _score;
END;
$$;

--
-- Name: confirm_resource_booking(uuid); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.confirm_resource_booking(p_booking_id uuid) RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  v_booking public.resource_bookings;
  v_resource public.bookable_resources;
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Non authentifié'; END IF;

  SELECT * INTO v_booking FROM public.resource_bookings WHERE id = p_booking_id;
  IF NOT FOUND THEN RAISE EXCEPTION 'Réservation introuvable'; END IF;

  SELECT * INTO v_resource FROM public.bookable_resources WHERE id = v_booking.resource_id;

  IF NOT (
    public.has_role(auth.uid(), 'admin'::app_role)
    OR EXISTS (
      SELECT 1 FROM public.restaurant_staff
      WHERE user_id = auth.uid()
        AND restaurant_id = v_resource.restaurant_id
        AND status = 'actif'
    )
  ) THEN
    RAISE EXCEPTION 'Accès refusé : staff requis';
  END IF;

  IF v_booking.status NOT IN ('demandee') THEN
    RAISE EXCEPTION 'Réservation non confirmable (status=%)', v_booking.status;
  END IF;

  UPDATE public.resource_bookings SET status = 'confirmee', updated_at = NOW() WHERE id = p_booking_id;
END;
$$;

--
-- Name: consolidate_monthly_restitutions(date); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.consolidate_monthly_restitutions(p_period_month date DEFAULT NULL::date) RETURNS integer
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  v_period DATE;
  v_count INTEGER := 0;
  r RECORD;
  v_restitution_id UUID;
BEGIN
  -- Default: previous calendar month (first day)
  v_period := COALESCE(
    p_period_month,
    (date_trunc('month', now() - INTERVAL '1 month'))::DATE
  );
  -- Normalise to first day of month
  v_period := date_trunc('month', v_period)::DATE;

  -- Aggregate unconsolidated pool rows created during that month.
  -- We group by restaurant_id only; the period is the one passed in.
  FOR r IN
    SELECT restaurant_id, SUM(points)::INTEGER AS total_points
    FROM public.restaurant_expired_pool
    WHERE consolidated_at IS NULL
      AND created_at >= v_period
      AND created_at < (v_period + INTERVAL '1 month')::DATE
    GROUP BY restaurant_id
    HAVING SUM(points) > 0
  LOOP
    -- Upsert the restitution row (one per (restaurant, period))
    INSERT INTO public.restaurant_restitutions (
      restaurant_id, period_month, total_points, status
    ) VALUES (
      r.restaurant_id, v_period, r.total_points, 'en_attente'
    )
    ON CONFLICT (restaurant_id, period_month) DO UPDATE
      SET total_points = public.restaurant_restitutions.total_points + EXCLUDED.total_points,
          updated_at = now()
    RETURNING id INTO v_restitution_id;

    -- Mark pool rows as consolidated and link them to the restitution
    UPDATE public.restaurant_expired_pool
    SET consolidated_at = now(),
        restitution_id = v_restitution_id
    WHERE restaurant_id = r.restaurant_id
      AND consolidated_at IS NULL
      AND created_at >= v_period
      AND created_at < (v_period + INTERVAL '1 month')::DATE;

    v_count := v_count + 1;
  END LOOP;

  RETURN v_count;
END;
$$;

--
-- Name: FUNCTION consolidate_monthly_restitutions(p_period_month date); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.consolidate_monthly_restitutions(p_period_month date) IS 'Rolls up unconsolidated restaurant_expired_pool rows into restaurant_restitutions for the given month (default: previous month). Idempotent. Returns number of restitutions touched.';

--
-- Name: consume_points_fifo(uuid, uuid[], integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.consume_points_fifo(p_client_id uuid, p_restaurant_ids uuid[], p_points_to_consume integer) RETURNS integer
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  _total_consumed INTEGER := 0;
  _remaining INTEGER := p_points_to_consume;
  _row RECORD;
BEGIN
  IF p_points_to_consume <= 0 THEN
    RETURN 0;
  END IF;

  -- Use a cursor ordered by earned_at ASC (FIFO: oldest first)
  -- Only consider non-expired points with remaining balance
  FOR _row IN
    SELECT id, remaining_points
    FROM public.loyalty_points
    WHERE client_id = p_client_id
      AND restaurant_id = ANY(p_restaurant_ids)
      AND remaining_points > 0
      AND points > 0
      AND (expires_at IS NULL OR expires_at > now())
    ORDER BY earned_at ASC
    FOR UPDATE SKIP LOCKED  -- Prevent concurrent consumption race
  LOOP
    EXIT WHEN _remaining <= 0;

    IF _row.remaining_points >= _remaining THEN
      -- This row covers the rest
      UPDATE public.loyalty_points
        SET remaining_points = remaining_points - _remaining
        WHERE id = _row.id;
      _total_consumed := _total_consumed + _remaining;
      _remaining := 0;
    ELSE
      -- Consume entire row, continue to next
      UPDATE public.loyalty_points
        SET remaining_points = 0
        WHERE id = _row.id;
      _total_consumed := _total_consumed + _row.remaining_points;
      _remaining := _remaining - _row.remaining_points;
    END IF;
  END LOOP;

  RETURN _total_consumed;
END;
$$;

--
-- Name: consume_points_fifo(uuid, uuid, integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.consume_points_fifo(_client_id uuid, _restaurant_id uuid, _points_to_consume integer) RETURNS integer
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  _remaining integer := _points_to_consume;
  _row RECORD;
  _deducted integer;
BEGIN
  -- Get group_id for the restaurant (for group point pooling)
  -- Consume oldest non-expired points first
  FOR _row IN
    SELECT lp.id, lp.remaining_points
    FROM public.loyalty_points lp
    WHERE lp.client_id = _client_id
      AND lp.points > 0
      AND lp.remaining_points > 0
      AND (lp.expires_at IS NULL OR lp.expires_at > now())
      AND (
        lp.restaurant_id = _restaurant_id
        OR lp.restaurant_id IN (
          SELECT r2.id FROM public.restaurants r2
          WHERE r2.group_id IS NOT NULL
            AND r2.group_id = (SELECT r1.group_id FROM public.restaurants r1 WHERE r1.id = _restaurant_id)
        )
      )
    ORDER BY lp.earned_at ASC
  LOOP
    EXIT WHEN _remaining <= 0;

    _deducted := LEAST(_row.remaining_points, _remaining);
    UPDATE public.loyalty_points SET remaining_points = remaining_points - _deducted WHERE id = _row.id;
    _remaining := _remaining - _deducted;
  END LOOP;

  RETURN _points_to_consume - _remaining; -- actual points consumed
END;
$$;

--
-- Name: create_bookable_resource(uuid, public.bookable_resource_type, text, integer, integer, integer, jsonb, public.bookable_payment_mode, jsonb, text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.create_bookable_resource(p_restaurant_id uuid, p_resource_type public.bookable_resource_type, p_name text, p_capacity integer DEFAULT 1, p_slot_duration_minutes integer DEFAULT 60, p_max_invitees integer DEFAULT 0, p_opening_hours jsonb DEFAULT NULL::jsonb, p_payment_mode public.bookable_payment_mode DEFAULT 'on_site'::public.bookable_payment_mode, p_pricing jsonb DEFAULT NULL::jsonb, p_image_url text DEFAULT NULL::text) RETURNS uuid
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  v_resource_id UUID;
  v_tenant_id UUID;
BEGIN
  -- Auth check
  IF NOT (
    has_role((SELECT auth.uid()), 'admin'::app_role)
    OR EXISTS (
      SELECT 1 FROM restaurant_staff rs
      WHERE rs.user_id = (SELECT auth.uid())
        AND rs.restaurant_id = p_restaurant_id
        AND rs.status = 'actif'
    )
  ) THEN
    RAISE EXCEPTION 'Accès refusé : vous n''êtes pas staff actif de ce restaurant';
  END IF;

  -- Validations
  IF p_name IS NULL OR length(trim(p_name)) = 0 THEN
    RAISE EXCEPTION 'Le nom de la ressource est obligatoire';
  END IF;
  IF p_capacity < 1 THEN
    RAISE EXCEPTION 'La capacité doit être supérieure ou égale à 1';
  END IF;
  IF p_slot_duration_minutes < 15 THEN
    RAISE EXCEPTION 'La durée d''un créneau doit être au moins 15 minutes';
  END IF;
  IF p_max_invitees < 0 THEN
    RAISE EXCEPTION 'Le nombre maximum d''invités doit être positif ou nul';
  END IF;

  -- Dériver tenant_id depuis le restaurant
  SELECT tenant_id INTO v_tenant_id FROM restaurants WHERE id = p_restaurant_id;

  -- INSERT bookable_resource
  INSERT INTO bookable_resources (
    tenant_id, restaurant_id, resource_type, name, capacity,
    slot_duration_minutes, max_invitees, opening_hours, payment_mode, pricing, image_url, enabled
  ) VALUES (
    v_tenant_id, p_restaurant_id, p_resource_type, trim(p_name), p_capacity,
    p_slot_duration_minutes, p_max_invitees, p_opening_hours, p_payment_mode, p_pricing,
    NULLIF(trim(COALESCE(p_image_url, '')), ''), true
  ) RETURNING id INTO v_resource_id;

  -- Audit log (vrai schéma : restaurant_id NOT NULL + action TEXT)
  INSERT INTO public.action_logs (restaurant_id, user_id, member_name, action, type, details)
  VALUES (
    p_restaurant_id,
    (SELECT auth.uid()),
    '',
    'create_bookable_resource',
    'admin_action',
    format('Resource %s (%s) créée — id=%s', trim(p_name), p_resource_type::text, v_resource_id)
  );

  RETURN v_resource_id;
END;
$$;

--
-- Name: create_default_booking_rules(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.create_default_booking_rules() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
BEGIN
  INSERT INTO public.booking_rules (restaurant_id, name, description, category, value, enabled)
  VALUES
    (NEW.id, 'Confirmation automatique', 'Les réservations sont confirmées automatiquement si des places sont disponibles', 'confirmation', 'auto', true),
    (NEW.id, 'Délai d''annulation', 'Le client peut annuler sans pénalité jusqu''à 2h avant', 'annulation', '2h', true),
    (NEW.id, 'Pénalité no-show', 'Un no-show impacte le score de fiabilité du client', 'annulation', '-0.5 pts', true),
    (NEW.id, 'Limite de couverts', 'Maximum 8 personnes par réservation OneClick', 'capacité', '8 couverts', true),
    (NEW.id, 'Réservations par jour', 'Maximum 2 réservations actives par client par jour', 'capacité', '2 max/jour', true),
    (NEW.id, 'Rappel de réservation', 'Notification envoyée 2h avant la réservation', 'notification', '2h avant', true),
    (NEW.id, 'Notification au restaurant', 'Le restaurant est notifié immédiatement de chaque nouvelle réservation', 'notification', 'temps réel', true);
  RETURN NEW;
END;
$$;

--
-- Name: create_default_services(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.create_default_services() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
BEGIN
  INSERT INTO public.restaurant_services (restaurant_id, name, type, heure_debut, heure_fin, capacite_max, clickgo_quota, status, jours_actifs)
  VALUES
    (NEW.id, 'Brunch', 'brunch', '10:00', '11:30', 50, 20, 'actif', ARRAY['Lun','Mar','Mer','Jeu','Ven','Sam','Dim']),
    (NEW.id, 'Déjeuner', 'déjeuner', '12:00', '14:30', 50, 50, 'actif', ARRAY['Lun','Mar','Mer','Jeu','Ven','Sam','Dim']),
    (NEW.id, 'Dîner', 'dîner', '19:00', '23:30', 50, 50, 'actif', ARRAY['Lun','Mar','Mer','Jeu','Ven','Sam','Dim']);
  RETURN NEW;
END;
$$;

--
-- Name: create_resource_booking(uuid, timestamp with time zone, integer, jsonb, text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.create_resource_booking(p_resource_id uuid, p_start_at timestamp with time zone, p_party_size integer DEFAULT 1, p_invitees jsonb DEFAULT '[]'::jsonb, p_notes text DEFAULT NULL::text) RETURNS uuid
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  v_resource public.bookable_resources;
  v_end_at TIMESTAMPTZ;
  v_booking_id UUID;
  v_invitee_count INT;
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'Non authentifié' USING ERRCODE = '42501';
  END IF;

  SELECT * INTO v_resource FROM public.bookable_resources WHERE id = p_resource_id AND enabled = true;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Ressource introuvable ou désactivée';
  END IF;

  v_invitee_count := jsonb_array_length(COALESCE(p_invitees, '[]'::jsonb));

  IF v_invitee_count > v_resource.max_invitees THEN
    RAISE EXCEPTION 'Trop d''invités (max % autorisés sur cette ressource)', v_resource.max_invitees;
  END IF;

  IF p_party_size < 1 OR p_party_size > (1 + v_resource.max_invitees) THEN
    RAISE EXCEPTION 'Nombre de personnes invalide (1 à % max)', 1 + v_resource.max_invitees;
  END IF;

  v_end_at := p_start_at + (v_resource.slot_duration_minutes || ' minutes')::INTERVAL;

  IF p_start_at <= NOW() THEN
    RAISE EXCEPTION 'Date de réservation dans le passé';
  END IF;

  IF EXISTS (
    SELECT 1 FROM public.resource_bookings
    WHERE resource_id = p_resource_id
      AND status IN ('demandee', 'confirmee')
      AND tstzrange(start_at, end_at) && tstzrange(p_start_at, v_end_at)
  ) THEN
    RAISE EXCEPTION 'Créneau déjà réservé sur cette ressource';
  END IF;

  INSERT INTO public.resource_bookings (
    resource_id, organizer_id, start_at, end_at,
    party_size, invitees, status, notes
  )
  VALUES (
    p_resource_id, auth.uid(), p_start_at, v_end_at,
    p_party_size, COALESCE(p_invitees, '[]'::jsonb), 'demandee', p_notes
  )
  RETURNING id INTO v_booking_id;

  RETURN v_booking_id;
END;
$$;

--
-- Name: create_seminar_request(text, text, text, text, integer, date, date, text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.create_seminar_request(p_company_name text, p_contact_name text, p_contact_email text, p_contact_phone text DEFAULT NULL::text, p_expected_attendees integer DEFAULT 10, p_preferred_date_start date DEFAULT NULL::date, p_preferred_date_end date DEFAULT NULL::date, p_needs_text text DEFAULT NULL::text) RETURNS uuid
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  v_request_id UUID;
  v_tenant_id UUID;
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'Non authentifié';
  END IF;

  -- Récupère tenant_id du caller (via profiles)
  SELECT tenant_id INTO v_tenant_id FROM public.profiles WHERE id = auth.uid();

  IF p_company_name IS NULL OR TRIM(p_company_name) = '' THEN
    RAISE EXCEPTION 'Nom entreprise requis';
  END IF;
  IF p_contact_email IS NULL OR p_contact_email NOT LIKE '%@%' THEN
    RAISE EXCEPTION 'Email contact invalide';
  END IF;
  IF p_expected_attendees < 1 THEN
    RAISE EXCEPTION 'Nombre de personnes invalide';
  END IF;
  IF p_preferred_date_end IS NOT NULL AND p_preferred_date_start IS NOT NULL
    AND p_preferred_date_end < p_preferred_date_start THEN
    RAISE EXCEPTION 'Date fin avant date début';
  END IF;

  INSERT INTO public.seminar_requests (
    tenant_id, organizer_id, company_name, contact_name, contact_email,
    contact_phone, expected_attendees, preferred_date_start,
    preferred_date_end, needs_text, status
  )
  VALUES (
    v_tenant_id, auth.uid(), TRIM(p_company_name), TRIM(p_contact_name), LOWER(TRIM(p_contact_email)),
    p_contact_phone, p_expected_attendees, p_preferred_date_start,
    p_preferred_date_end, p_needs_text, 'demandee'
  )
  RETURNING id INTO v_request_id;

  RETURN v_request_id;
END;
$$;

--
-- Name: credit_referral_points(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.credit_referral_points() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  _admin_id uuid;
  _total_pts integer;
  _resto_name text;
  _referrer_first_name text;
  _filleul_first_name text;
BEGIN
  -- Only fire when status changes to 'actif'
  IF NEW.status = 'actif' AND (OLD.status IS NULL OR OLD.status <> 'actif') THEN
    -- Ensure restaurant_id is set
    IF NEW.restaurant_id IS NULL THEN
      RAISE EXCEPTION 'Le restaurant doit être choisi pour le parrainage.';
    END IF;

    -- Credit 50 points to referrer
    INSERT INTO public.loyalty_points (client_id, restaurant_id, points, amount_ttc, reason, credited_by)
    VALUES (NEW.referrer_id, NEW.restaurant_id, 50, 0, 'parrainage_parrain', NEW.referrer_id);

    -- Credit 50 points to referred user
    _total_pts := 50;
    IF NEW.referred_user_id IS NOT NULL THEN
      INSERT INTO public.loyalty_points (client_id, restaurant_id, points, amount_ttc, reason, credited_by)
      VALUES (NEW.referred_user_id, NEW.restaurant_id, 50, 0, 'parrainage_filleul', NEW.referrer_id);
      _total_pts := 100;
    END IF;

    -- Set pts_awarded
    NEW.pts_awarded := 50;
    NEW.activated_at := now();

    -- Fetch restaurant name + first names for notification content
    SELECT name INTO _resto_name FROM public.restaurants WHERE id = NEW.restaurant_id;
    SELECT first_name INTO _referrer_first_name FROM public.profiles WHERE id = NEW.referrer_id;
    IF NEW.referred_user_id IS NOT NULL THEN
      SELECT first_name INTO _filleul_first_name FROM public.profiles WHERE id = NEW.referred_user_id;
    END IF;

    -- Debit restaurant: credit admin wallet (restaurant cost)
    SELECT ur.user_id INTO _admin_id
    FROM public.user_roles ur
    WHERE ur.role = 'admin'
    LIMIT 1;

    IF _admin_id IS NOT NULL THEN
      INSERT INTO public.admin_wallet_transactions (admin_id, restaurant_id, amount, reason, details)
      VALUES (
        _admin_id,
        NEW.restaurant_id,
        _total_pts,
        'referral_commission',
        'Parrainage : ' || _total_pts || ' pts débités — ' || COALESCE(_resto_name, 'Restaurant')
      );
    END IF;

    -- NEW (session 28): in-app notifications for referrer + referred user
    INSERT INTO public.notifications (user_id, title, message, type, link, read)
    VALUES (
      NEW.referrer_id,
      'Parrainage réussi 🎁',
      COALESCE(_filleul_first_name, 'Un ami') || ' a rejoint OneClick grâce à vous. +50 points offerts !',
      'referral',
      '/pocket/referral',
      false
    );

    IF NEW.referred_user_id IS NOT NULL THEN
      INSERT INTO public.notifications (user_id, title, message, type, link, read)
      VALUES (
        NEW.referred_user_id,
        'Bienvenue sur OneClick 🎁',
        'Vous avez été parrainé par ' || COALESCE(_referrer_first_name, 'un ami') || '. +50 points offerts sur votre premier restaurant !',
        'referral',
        '/pocket/profile',
        false
      );
    END IF;
  END IF;

  RETURN NEW;
END;
$$;

--
-- Name: delete_bookable_resource(uuid); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.delete_bookable_resource(p_resource_id uuid) RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  v_restaurant_id UUID;
  v_booking_count INT;
  v_resource_name TEXT;
BEGIN
  SELECT restaurant_id, name INTO v_restaurant_id, v_resource_name
  FROM bookable_resources WHERE id = p_resource_id;
  IF v_restaurant_id IS NULL THEN
    RAISE EXCEPTION 'Ressource introuvable';
  END IF;

  IF NOT (
    has_role((SELECT auth.uid()), 'admin'::app_role)
    OR EXISTS (
      SELECT 1 FROM restaurant_staff rs
      WHERE rs.user_id = (SELECT auth.uid())
        AND rs.restaurant_id = v_restaurant_id
        AND rs.status = 'actif'
    )
  ) THEN
    RAISE EXCEPTION 'Accès refusé : vous n''êtes pas staff actif de ce restaurant';
  END IF;

  SELECT COUNT(*) INTO v_booking_count
  FROM resource_bookings
  WHERE resource_id = p_resource_id;

  IF v_booking_count > 0 THEN
    RAISE EXCEPTION 'Impossible de supprimer : % réservation(s) historique(s) existent. Désactivez la ressource à la place pour empêcher de nouvelles réservations.', v_booking_count;
  END IF;

  -- Audit log AVANT delete (sinon on perd l'id)
  INSERT INTO public.action_logs (restaurant_id, user_id, member_name, action, type, details)
  VALUES (
    v_restaurant_id,
    (SELECT auth.uid()),
    '',
    'delete_bookable_resource',
    'admin_action',
    format('Resource %s (id=%s) supprimée', v_resource_name, p_resource_id)
  );

  DELETE FROM bookable_resources WHERE id = p_resource_id;
END;
$$;

--
-- Name: enforce_max_pinned_per_priority(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.enforce_max_pinned_per_priority() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  IF NEW.is_pinned = true AND NEW.archived_at IS NULL AND NEW.deleted_at IS NULL THEN
    UPDATE public.tenant_announcements
    SET is_pinned = false
    WHERE tenant_id = NEW.tenant_id
      AND priority = NEW.priority
      AND id != NEW.id
      AND is_pinned = true
      AND archived_at IS NULL
      AND deleted_at IS NULL;
  END IF;
  RETURN NEW;
END $$;

--
-- Name: enroll_member(uuid, uuid, integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.enroll_member(p_restaurant_id uuid, p_client_id uuid, p_welcome_points integer) RETURNS TABLE(client_id uuid, points_granted integer, loyalty_id uuid)
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  v_staff_id UUID;
  v_max_points INT;
  v_lp_id UUID;
BEGIN
  -- 1. Caller doit être authentifié
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'Non authentifié' USING ERRCODE = '42501';
  END IF;

  -- 2. Caller doit être staff actif du restaurant (ou super-admin)
  IF NOT (
    public.has_role(auth.uid(), 'admin'::app_role)
    OR EXISTS (
      SELECT 1 FROM restaurant_staff
      WHERE user_id = auth.uid()
        AND restaurant_id = p_restaurant_id
        AND status = 'actif'
    )
  ) THEN
    RAISE EXCEPTION 'Accès refusé : vous n''êtes pas staff de ce restaurant'
      USING ERRCODE = '42501';
  END IF;

  -- 3. Client existe ?
  IF NOT EXISTS (SELECT 1 FROM profiles WHERE id = p_client_id) THEN
    RAISE EXCEPTION 'Client introuvable';
  END IF;

  -- 4. Récupère le plafond welcome_points_max du restaurant
  SELECT COALESCE(welcome_points_max, 500) INTO v_max_points
  FROM restaurant_gain_rules
  WHERE restaurant_id = p_restaurant_id AND enabled = true
  ORDER BY created_at DESC
  LIMIT 1;

  -- Si pas de règle, fallback 500
  v_max_points := COALESCE(v_max_points, 500);

  IF p_welcome_points < 0 OR p_welcome_points > v_max_points THEN
    RAISE EXCEPTION 'Bonus hors limite : doit être entre 0 et % points', v_max_points;
  END IF;

  v_staff_id := auth.uid();

  -- 5. Insert loyalty_points reason='welcome'
  IF p_welcome_points > 0 THEN
    INSERT INTO loyalty_points (
      client_id, restaurant_id, points, amount_ttc, reason, earned_at, credited_by
    ) VALUES (
      p_client_id, p_restaurant_id, p_welcome_points, 0, 'welcome', NOW(), v_staff_id
    ) RETURNING id INTO v_lp_id;
  END IF;

  -- 6. Retour
  client_id := p_client_id;
  points_granted := p_welcome_points;
  loyalty_id := v_lp_id;
  RETURN NEXT;
END;
$$;

--
-- Name: FUNCTION enroll_member(p_restaurant_id uuid, p_client_id uuid, p_welcome_points integer); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.enroll_member(p_restaurant_id uuid, p_client_id uuid, p_welcome_points integer) IS 'Inscrit un client au programme de fidélité d''un restaurant avec points de bienvenue.
   Réservé aux staff actifs du resto + super-admin. Vérifie le plafond welcome_points_max
   défini par l''admin. Trace l''action via loyalty_points.credited_by.
   Sprint 9 whitelabel — enrollment feature.';

--
-- Name: ensure_client_rating(uuid); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.ensure_client_rating(p_client_id uuid) RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
BEGIN
  INSERT INTO public.client_ratings (client_id, rating, is_new)
  VALUES (p_client_id, 5.0, true)
  ON CONFLICT (client_id) DO NOTHING;
END;
$$;

--
-- Name: event_rsvps_set_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.event_rsvps_set_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN NEW.updated_at = now(); RETURN NEW; END $$;

--
-- Name: export_no_show_stats_csv(uuid, date, date); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.export_no_show_stats_csv(p_restaurant_id uuid, p_start_date date, p_end_date date) RETURNS TABLE(membre_nom text, membre_email text, total_bookings bigint, honorees bigint, no_shows bigint, annulations bigint, taux_no_show_pct numeric, dernier_no_show timestamp with time zone)
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
BEGIN
  IF NOT (
    has_role((SELECT auth.uid()), 'admin'::app_role)
    OR EXISTS (
      SELECT 1 FROM restaurant_staff rs
      WHERE rs.user_id = (SELECT auth.uid())
        AND rs.restaurant_id = p_restaurant_id
        AND rs.status = 'actif'
    )
  ) THEN
    RAISE EXCEPTION 'Accès refusé : vous n''êtes pas staff actif de ce restaurant';
  END IF;

  INSERT INTO public.action_logs (restaurant_id, user_id, member_name, action, type, details)
  VALUES (
    p_restaurant_id,
    (SELECT auth.uid()),
    '',
    'export_no_show_stats_csv',
    'admin_action',
    format('Export no-show stats du %s au %s', p_start_date, p_end_date)
  );

  RETURN QUERY
  SELECT
    coalesce(p.first_name || ' ' || p.last_name, '') AS membre_nom,
    coalesce(p.email, '') AS membre_email,
    count(*) AS total_bookings,
    count(*) FILTER (WHERE rb.status = 'honoree') AS honorees,
    count(*) FILTER (WHERE rb.status = 'no_show') AS no_shows,
    count(*) FILTER (WHERE rb.status = 'annulee') AS annulations,
    -- Taux no-show = no_shows / (honorees + no_shows) (les annulations exclues du dénominateur)
    CASE
      WHEN count(*) FILTER (WHERE rb.status IN ('honoree', 'no_show')) > 0 THEN
        round(
          100.0 * count(*) FILTER (WHERE rb.status = 'no_show')
          / count(*) FILTER (WHERE rb.status IN ('honoree', 'no_show')),
          1
        )
      ELSE 0
    END AS taux_no_show_pct,
    max(rb.start_at) FILTER (WHERE rb.status = 'no_show') AS dernier_no_show
  FROM resource_bookings rb
  JOIN bookable_resources br ON br.id = rb.resource_id
  LEFT JOIN profiles p ON p.id = rb.organizer_id
  WHERE br.restaurant_id = p_restaurant_id
    AND rb.start_at::date >= p_start_date
    AND rb.start_at::date <= p_end_date
  GROUP BY p.id, p.first_name, p.last_name, p.email
  HAVING count(*) > 0
  ORDER BY count(*) FILTER (WHERE rb.status = 'no_show') DESC, total_bookings DESC;
END;
$$;

--
-- Name: export_punch_cards_csv(uuid, text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.export_punch_cards_csv(p_restaurant_id uuid, p_activity_type text DEFAULT NULL::text) RETURNS TABLE(membre_nom text, membre_email text, activite text, count_punched integer, threshold integer, redeemed_count integer, derniere_punch timestamp with time zone, derniere_redemption timestamp with time zone)
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  v_tenant_id UUID;
BEGIN
  -- Auth check : admin OR staff actif du restaurant
  IF NOT (
    has_role((SELECT auth.uid()), 'admin'::app_role)
    OR EXISTS (
      SELECT 1 FROM restaurant_staff rs
      WHERE rs.user_id = (SELECT auth.uid())
        AND rs.restaurant_id = p_restaurant_id
        AND rs.status = 'actif'
    )
  ) THEN
    RAISE EXCEPTION 'Accès refusé : vous n''êtes pas staff actif de ce restaurant';
  END IF;

  -- Dériver tenant_id (punch_cards sont au niveau tenant pas resto)
  SELECT tenant_id INTO v_tenant_id FROM restaurants WHERE id = p_restaurant_id;
  IF v_tenant_id IS NULL THEN
    RAISE EXCEPTION 'Restaurant introuvable ou pas associé à un tenant whitelabel';
  END IF;

  -- Audit log
  INSERT INTO public.action_logs (restaurant_id, user_id, member_name, action, type, details)
  VALUES (
    p_restaurant_id,
    (SELECT auth.uid()),
    '',
    'export_punch_cards_csv',
    'admin_action',
    format('Export punch cards tenant %s (activity=%s)', v_tenant_id, coalesce(p_activity_type, 'all'))
  );

  RETURN QUERY
  SELECT
    coalesce(p.first_name || ' ' || p.last_name, '') AS membre_nom,
    coalesce(p.email, '') AS membre_email,
    pc.activity_type::text AS activite,
    pc.count_punched AS count_punched,
    pc.threshold AS threshold,
    pc.redeemed_count AS redeemed_count,
    pc.last_punched_at AS derniere_punch,
    pc.last_redeemed_at AS derniere_redemption
  FROM loyalty_punch_cards pc
  LEFT JOIN profiles p ON p.id = pc.client_id
  WHERE pc.tenant_id = v_tenant_id
    AND (p_activity_type IS NULL OR pc.activity_type::text = p_activity_type)
  ORDER BY pc.last_punched_at DESC NULLS LAST;
END;
$$;

--
-- Name: export_reservations_csv(uuid, date, date); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.export_reservations_csv(p_restaurant_id uuid, p_start_date date, p_end_date date) RETURNS TABLE(date_resa date, heure time without time zone, couverts integer, service text, client_nom text, client_email text, client_telephone text, statut text, notes text, cree_le timestamp with time zone)
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
BEGIN
  IF NOT (
    has_role((SELECT auth.uid()), 'admin'::app_role)
    OR EXISTS (
      SELECT 1 FROM restaurant_staff rs
      WHERE rs.user_id = (SELECT auth.uid())
        AND rs.restaurant_id = p_restaurant_id
        AND rs.status = 'actif'
    )
  ) THEN
    RAISE EXCEPTION 'Accès refusé : vous n''êtes pas staff actif de ce restaurant';
  END IF;

  INSERT INTO public.action_logs (restaurant_id, user_id, member_name, action, type, details)
  VALUES (
    p_restaurant_id,
    (SELECT auth.uid()),
    '',
    'export_reservations_csv',
    'admin_action',
    format('Export réservations du %s au %s', p_start_date, p_end_date)
  );

  RETURN QUERY
  SELECT
    r.date AS date_resa,
    r.heure AS heure,
    coalesce(r.couverts, 0) AS couverts,
    coalesce(r.service, '') AS service,
    coalesce(p.first_name || ' ' || p.last_name, '') AS client_nom,
    coalesce(p.email, '') AS client_email,
    coalesce(p.phone, '') AS client_telephone,
    r.status::text AS statut,
    coalesce(r.notes, '') AS notes,
    r.created_at AS cree_le
  FROM reservations r
  LEFT JOIN profiles p ON p.id = r.client_id
  WHERE r.restaurant_id = p_restaurant_id
    AND r.date >= p_start_date
    AND r.date <= p_end_date
  ORDER BY r.date ASC, r.heure ASC;
END;
$$;

--
-- Name: export_resource_bookings_csv(uuid, date, date); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.export_resource_bookings_csv(p_restaurant_id uuid, p_start_date date, p_end_date date) RETURNS TABLE(date_resa date, heure_debut time without time zone, heure_fin time without time zone, ressource text, type_ressource text, organisateur_nom text, organisateur_email text, organisateur_telephone text, invites_count integer, statut text, notes text, cree_le timestamp with time zone)
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
BEGIN
  -- Auth check
  IF NOT (
    has_role((SELECT auth.uid()), 'admin'::app_role)
    OR EXISTS (
      SELECT 1 FROM restaurant_staff rs
      WHERE rs.user_id = (SELECT auth.uid())
        AND rs.restaurant_id = p_restaurant_id
        AND rs.status = 'actif'
    )
  ) THEN
    RAISE EXCEPTION 'Accès refusé : vous n''êtes pas staff actif de ce restaurant';
  END IF;

  -- Audit log (génération export)
  INSERT INTO public.action_logs (restaurant_id, user_id, member_name, action, type, details)
  VALUES (
    p_restaurant_id,
    (SELECT auth.uid()),
    '',
    'export_resource_bookings_csv',
    'admin_action',
    format('Export bookings du %s au %s', p_start_date, p_end_date)
  );

  RETURN QUERY
  SELECT
    rb.start_at::date AS date_resa,
    rb.start_at::time AS heure_debut,
    rb.end_at::time AS heure_fin,
    br.name AS ressource,
    br.resource_type::text AS type_ressource,
    coalesce(p.first_name || ' ' || p.last_name, '') AS organisateur_nom,
    coalesce(p.email, '') AS organisateur_email,
    coalesce(p.phone, '') AS organisateur_telephone,
    coalesce(jsonb_array_length(rb.invitees), 0) AS invites_count,
    rb.status::text AS statut,
    coalesce(rb.notes, '') AS notes,
    rb.created_at AS cree_le
  FROM resource_bookings rb
  JOIN bookable_resources br ON br.id = rb.resource_id
  LEFT JOIN profiles p ON p.id = rb.organizer_id
  WHERE br.restaurant_id = p_restaurant_id
    AND rb.start_at::date >= p_start_date
    AND rb.start_at::date <= p_end_date
  ORDER BY rb.start_at ASC;
END;
$$;

--
-- Name: find_client_by_code(text, uuid); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.find_client_by_code(p_code text, p_restaurant_id uuid) RETURNS TABLE(client_id uuid, full_name text, phone text, today_reservations_count integer, honored_unscanned_count integer)
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
  SELECT
    p.id AS client_id,
    TRIM(COALESCE(p.first_name, '') || ' ' || COALESCE(p.last_name, '')) AS full_name,
    p.phone,
    (
      SELECT COUNT(*)::integer FROM public.reservations r
      WHERE r.client_id = p.id
        AND r.restaurant_id = p_restaurant_id
        AND r.date = CURRENT_DATE
    ) AS today_reservations_count,
    (
      SELECT COUNT(*)::integer FROM public.reservations r
      WHERE r.client_id = p.id
        AND r.restaurant_id = p_restaurant_id
        AND r.date = CURRENT_DATE
        AND r.status = 'honorée'
        AND NOT EXISTS (
          SELECT 1 FROM public.scanned_tickets st
          WHERE st.reservation_id = r.id
        )
    ) AS honored_unscanned_count
  FROM public.profiles p
  WHERE p.referral_code = UPPER(TRIM(p_code))
  LIMIT 1;
$$;

--
-- Name: find_profile_by_email(text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.find_profile_by_email(p_email text) RETURNS TABLE(id uuid, first_name text, last_name text)
    LANGUAGE sql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
  SELECT p.id, p.first_name, p.last_name
  FROM public.profiles p
  WHERE LOWER(p.email) = LOWER(TRIM(p_email))
    AND auth.uid() IS NOT NULL  -- Caller must be authenticated
    AND p.id != auth.uid()       -- Ne retourne pas son propre profil
  LIMIT 1;
$$;

--
-- Name: FUNCTION find_profile_by_email(p_email text); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.find_profile_by_email(p_email text) IS 'Recherche un profil par email. Retourne uniquement id/nom pour minimiser
   la surface RGPD. Case-insensitive + trim. Utilisé par le flow "Ajouter
   un ami par email" dans OneClickCircle / HOMU Win. Sprint 9 whitelabel.';

--
-- Name: find_profile_by_phone(text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.find_profile_by_phone(p_phone text) RETURNS TABLE(id uuid, first_name text, last_name text)
    LANGUAGE sql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
  SELECT p.id, p.first_name, p.last_name
  FROM public.profiles p
  WHERE p.phone = p_phone
    AND auth.uid() IS NOT NULL  -- Caller must be authenticated
    AND p.id != auth.uid()       -- Ne retourne pas son propre profil (évite l'abus)
  LIMIT 1;
$$;

--
-- Name: FUNCTION find_profile_by_phone(p_phone text); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.find_profile_by_phone(p_phone text) IS 'Recherche un profil par numéro de téléphone. Retourne uniquement id/nom pour
   minimiser la surface d''exposition RGPD. Remplace la policy permissive
   "Authenticated users can search profiles by phone" (droppée Sprint 9).';

--
-- Name: fix_null_auth_columns(uuid); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fix_null_auth_columns(_user_id uuid) RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
BEGIN
  UPDATE auth.users
  SET
    confirmation_token = COALESCE(confirmation_token, ''),
    recovery_token = COALESCE(recovery_token, ''),
    email_change_token_new = COALESCE(email_change_token_new, ''),
    email_change_token_current = COALESCE(email_change_token_current, ''),
    email_change = COALESCE(email_change, ''),
    reauthentication_token = COALESCE(reauthentication_token, '')
  WHERE id = _user_id;
END;
$$;

--
-- Name: generate_referral_code(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.generate_referral_code() RETURNS text
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  _code text;
  _exists boolean;
BEGIN
  LOOP
    _code := 'OC-' || upper(substr(md5(random()::text || clock_timestamp()::text), 1, 6));
    SELECT EXISTS (SELECT 1 FROM public.profiles WHERE referral_code = _code) INTO _exists;
    EXIT WHEN NOT _exists;
  END LOOP;
  RETURN _code;
END;
$$;

--
-- Name: generate_restaurant_referral_code(text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.generate_restaurant_referral_code(p_city text) RETURNS text
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  v_prefix text;
  v_random text;
  v_code text;
  v_exists boolean;
  v_attempts int := 0;
BEGIN
  -- Préfixe 3 lettres basé sur la ville (fallback ONE)
  v_prefix := UPPER(LEFT(
    COALESCE(regexp_replace(p_city, '[^a-zA-Z]', '', 'g'), 'ONE'),
    3
  ));
  IF LENGTH(v_prefix) < 3 THEN
    v_prefix := RPAD(v_prefix, 3, 'X');
  END IF;

  -- Boucle jusqu'à trouver un code unique (max 10 tentatives)
  LOOP
    v_attempts := v_attempts + 1;
    -- 4 caractères alphanumériques (sans ambigus O/0, I/1)
    -- Note : gen_random_bytes vit dans le schéma `extensions` sur Supabase,
    -- pas `public`. Préfixer pour respecter le SET search_path = public.
    v_random := UPPER(SUBSTRING(
      translate(encode(extensions.gen_random_bytes(4), 'hex'), '01', 'HJ'),
      1, 4
    ));
    v_code := v_prefix || '-' || v_random;

    SELECT EXISTS (SELECT 1 FROM public.restaurants WHERE referral_code = v_code)
      INTO v_exists;
    EXIT WHEN NOT v_exists OR v_attempts >= 10;
  END LOOP;

  RETURN v_code;
END;
$$;

--
-- Name: get_client_current_tier(uuid); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.get_client_current_tier(p_client_id uuid) RETURNS TABLE(tier_name text, total_points integer, sort_order integer, gain_bonus_pct numeric, taux_conversion_override numeric, point_value_mad_override numeric, min_ticket_override numeric, max_points_per_ticket_override integer, benefit_duration_days_override integer)
    LANGUAGE plpgsql STABLE SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
BEGIN
  RETURN QUERY
  WITH qualifying AS (
    -- Pour chaque tier activé, on calcule les points gagnés dans sa fenêtre
    -- ET les dépenses dans sa fenêtre (spend). Un client qualifie si
    --   points_earned_in_window >= points_required
    --   OR (spend > 0 dans sa fenêtre >= min_spend)
    SELECT
      t.tier_name,
      t.sort_order,
      t.gain_bonus_pct,
      t.taux_conversion_override,
      t.point_value_mad_override,
      t.min_ticket_override,
      t.max_points_per_ticket_override,
      t.benefit_duration_days_override,
      COALESCE((
        SELECT SUM(lp.points)::INTEGER
        FROM public.loyalty_points lp
        WHERE lp.client_id = p_client_id
          AND lp.points > 0
          AND lp.earned_at >= now() - (COALESCE(t.period_days, 180) || ' days')::interval
      ), 0) AS pts_earned,
      COALESCE((
        SELECT SUM(st.montant)
        FROM public.scanned_tickets st
        WHERE st.client_id = p_client_id
          AND st.created_at >= now() - (COALESCE(t.period_days, 180) || ' days')::interval
      ), 0) AS spent
    FROM public.tier_thresholds t
    WHERE t.enabled = true
  )
  SELECT
    q.tier_name,
    q.pts_earned AS total_points,
    q.sort_order,
    q.gain_bonus_pct,
    q.taux_conversion_override,
    q.point_value_mad_override,
    q.min_ticket_override,
    q.max_points_per_ticket_override,
    q.benefit_duration_days_override
  FROM qualifying q, public.tier_thresholds tt
  WHERE tt.tier_name = q.tier_name
    AND (
      tt.points_required <= q.pts_earned
      OR (tt.min_spend > 0 AND tt.min_spend <= q.spent)
    )
  ORDER BY q.sort_order DESC
  LIMIT 1;
END;
$$;

--
-- Name: get_client_score(uuid); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.get_client_score(_client_id uuid) RETURNS TABLE(stars numeric, total_reservations bigint, honorees bigint, no_shows bigint, label text)
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  _rating NUMERIC(2,1);
  _is_new BOOLEAN;
  _honored INTEGER;
  _noshow INTEGER;
  _total BIGINT;
  _label TEXT;
BEGIN
  -- Ensure rating record exists
  PERFORM ensure_client_rating(_client_id);

  -- Get from client_ratings
  SELECT cr.rating, cr.is_new, cr.total_honored, cr.total_no_show
  INTO _rating, _is_new, _honored, _noshow
  FROM client_ratings cr WHERE cr.client_id = _client_id;

  _total := _honored + _noshow;

  -- Determine label
  IF _is_new OR _total = 0 THEN
    _label := 'Nouveau';
    _rating := COALESCE(_rating, 5.0);
  ELSIF _rating >= 4.5 THEN
    _label := 'Excellent';
  ELSIF _rating >= 3.5 THEN
    _label := 'Fiable';
  ELSIF _rating >= 2.0 THEN
    _label := 'Moyen';
  ELSE
    _label := 'Peu fiable';
  END IF;

  RETURN QUERY SELECT _rating::NUMERIC, _total, _honored::BIGINT, _noshow::BIGINT, _label;
END;
$$;

--
-- Name: get_client_today_reservations(uuid, uuid); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.get_client_today_reservations(p_client_id uuid, p_restaurant_id uuid) RETURNS TABLE(reservation_id uuid, heure text, service text, couverts integer, status public.reservation_status, has_scanned_ticket boolean)
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
  SELECT
    r.id AS reservation_id,
    r.heure,
    r.service,
    r.couverts,
    r.status,
    EXISTS (
      SELECT 1 FROM public.scanned_tickets st
      WHERE st.reservation_id = r.id
    ) AS has_scanned_ticket
  FROM public.reservations r
  WHERE r.client_id = p_client_id
    AND r.restaurant_id = p_restaurant_id
    AND r.date = CURRENT_DATE
  ORDER BY r.heure ASC;
$$;

--
-- Name: get_contact_import_quota(integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.get_contact_import_quota(_tz_offset_minutes integer DEFAULT 0) RETURNS TABLE(imports_today integer, friends_total integer, quota_per_day integer, friends_cap integer)
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  _user_id uuid := auth.uid();
  _now_user timestamptz := now() + (_tz_offset_minutes || ' minutes')::interval;
  _today_start timestamptz := date_trunc('day', _now_user) - (_tz_offset_minutes || ' minutes')::interval;
BEGIN
  IF _user_id IS NULL THEN
    RAISE EXCEPTION 'Authentication required';
  END IF;

  RETURN QUERY
  SELECT
    COALESCE((
      SELECT COUNT(*)::int FROM public.contact_import_events
      WHERE user_id = _user_id AND imported_at >= _today_start
    ), 0) AS imports_today,
    COALESCE((
      SELECT COUNT(*)::int FROM public.friendships
      WHERE status = 'accepted'
        AND (requester_id = _user_id OR addressee_id = _user_id)
    ), 0) AS friends_total,
    10 AS quota_per_day,
    50 AS friends_cap;
END;
$$;

--
-- Name: get_cross_tenant_stats(integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.get_cross_tenant_stats(p_days integer DEFAULT 30) RETURNS TABLE(tenant_id uuid, tenant_slug text, tenant_name text, tenant_status text, restaurants_total bigint, restaurants_actifs bigint, tickets_period bigint, ca_period numeric, ca_previous numeric, reservations_period bigint, clients_actifs bigint, offers_actives bigint, last_activity_at timestamp with time zone)
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  v_since TIMESTAMPTZ;
  v_prev_since TIMESTAMPTZ;
BEGIN
  -- Garde : super-admin uniquement
  IF NOT public.has_role(auth.uid(), 'admin'::app_role) THEN
    RAISE EXCEPTION 'Access denied: super-admin only';
  END IF;

  v_since := NOW() - (p_days || ' days')::INTERVAL;
  v_prev_since := NOW() - (p_days * 2 || ' days')::INTERVAL;

  RETURN QUERY
  SELECT
    t.id AS tenant_id,
    t.slug AS tenant_slug,
    t.name AS tenant_name,
    t.status::TEXT AS tenant_status,
    -- Totaux restaurants
    COALESCE((SELECT COUNT(*) FROM restaurants r WHERE r.tenant_id = t.id), 0)::BIGINT AS restaurants_total,
    COALESCE((SELECT COUNT(*) FROM restaurants r WHERE r.tenant_id = t.id AND r.status = 'actif'), 0)::BIGINT AS restaurants_actifs,
    -- Tickets période
    COALESCE((
      SELECT COUNT(*)
      FROM scanned_tickets st
      JOIN restaurants r ON r.id = st.restaurant_id
      WHERE r.tenant_id = t.id AND st.created_at >= v_since
    ), 0)::BIGINT AS tickets_period,
    -- CA période
    COALESCE((
      SELECT SUM(st.montant)
      FROM scanned_tickets st
      JOIN restaurants r ON r.id = st.restaurant_id
      WHERE r.tenant_id = t.id AND st.created_at >= v_since
    ), 0)::NUMERIC AS ca_period,
    -- CA période précédente (pour delta)
    COALESCE((
      SELECT SUM(st.montant)
      FROM scanned_tickets st
      JOIN restaurants r ON r.id = st.restaurant_id
      WHERE r.tenant_id = t.id
        AND st.created_at >= v_prev_since
        AND st.created_at < v_since
    ), 0)::NUMERIC AS ca_previous,
    -- Réservations période
    COALESCE((
      SELECT COUNT(*)
      FROM reservations res
      JOIN restaurants r ON r.id = res.restaurant_id
      WHERE r.tenant_id = t.id AND res.created_at >= v_since
    ), 0)::BIGINT AS reservations_period,
    -- Clients actifs période (DISTINCT)
    COALESCE((
      SELECT COUNT(DISTINCT client_id)
      FROM (
        SELECT st.client_id
        FROM scanned_tickets st
        JOIN restaurants r ON r.id = st.restaurant_id
        WHERE r.tenant_id = t.id AND st.created_at >= v_since AND st.client_id IS NOT NULL
        UNION
        SELECT res.client_id
        FROM reservations res
        JOIN restaurants r ON r.id = res.restaurant_id
        WHERE r.tenant_id = t.id AND res.created_at >= v_since AND res.client_id IS NOT NULL
      ) AS clients
    ), 0)::BIGINT AS clients_actifs,
    -- Offres actives
    COALESCE((
      SELECT COUNT(*)
      FROM offers o
      WHERE o.tenant_id = t.id AND o.is_active = true
    ), 0)::BIGINT AS offers_actives,
    -- Dernière activité (dernier ticket ou résa)
    (
      SELECT MAX(activity_ts) FROM (
        SELECT MAX(st.created_at) AS activity_ts
        FROM scanned_tickets st
        JOIN restaurants r ON r.id = st.restaurant_id
        WHERE r.tenant_id = t.id
        UNION ALL
        SELECT MAX(res.created_at)
        FROM reservations res
        JOIN restaurants r ON r.id = res.restaurant_id
        WHERE r.tenant_id = t.id
      ) AS activity
    ) AS last_activity_at
  FROM tenants t
  ORDER BY ca_period DESC NULLS LAST;
END;
$$;

--
-- Name: FUNCTION get_cross_tenant_stats(p_days integer); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.get_cross_tenant_stats(p_days integer) IS 'Retourne les stats agrégées par tenant sur p_days jours (défaut 30).
   Accessible uniquement aux super-admins (role=admin).
   Utilisé par le dashboard /super-admin/tenants pour le pilotage cross-tenant.
   Sprint 9 whitelabel.';

--
-- Name: get_event_rsvp_counts(uuid); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.get_event_rsvp_counts(p_event_id uuid) RETURNS json
    LANGUAGE plpgsql STABLE SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  v_result json;
BEGIN
  SELECT json_build_object(
    'attending',     COALESCE(SUM(CASE WHEN status = 'attending'     THEN 1 ELSE 0 END), 0)::int,
    'maybe',         COALESCE(SUM(CASE WHEN status = 'maybe'         THEN 1 ELSE 0 END), 0)::int,
    'not_attending', COALESCE(SUM(CASE WHEN status = 'not_attending' THEN 1 ELSE 0 END), 0)::int
  ) INTO v_result
  FROM public.event_rsvps
  WHERE event_id = p_event_id;

  RETURN v_result;
END $$;

--
-- Name: get_expiring_points_due_for_notification(text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.get_expiring_points_due_for_notification(p_window text) RETURNS TABLE(client_id uuid, total_points integer, earliest_expiration timestamp with time zone, row_ids uuid[])
    LANGUAGE plpgsql STABLE SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
BEGIN
  IF p_window = '7d' THEN
    RETURN QUERY
      SELECT
        lp.client_id,
        SUM(lp.remaining_points)::INTEGER AS total_points,
        MIN(lp.expires_at) AS earliest_expiration,
        array_agg(lp.id) AS row_ids
      FROM public.loyalty_points lp
      WHERE lp.remaining_points > 0
        AND lp.expires_at IS NOT NULL
        AND lp.expires_at > now()
        AND lp.expires_at <= now() + INTERVAL '7 days 12 hours'
        AND lp.expires_at >= now() + INTERVAL '6 days 12 hours'
        AND lp.notified_7d_at IS NULL
      GROUP BY lp.client_id;
  ELSIF p_window = '1d' THEN
    RETURN QUERY
      SELECT
        lp.client_id,
        SUM(lp.remaining_points)::INTEGER AS total_points,
        MIN(lp.expires_at) AS earliest_expiration,
        array_agg(lp.id) AS row_ids
      FROM public.loyalty_points lp
      WHERE lp.remaining_points > 0
        AND lp.expires_at IS NOT NULL
        AND lp.expires_at > now()
        AND lp.expires_at <= now() + INTERVAL '1 day 12 hours'
        AND lp.notified_1d_at IS NULL
      GROUP BY lp.client_id;
  ELSE
    RAISE EXCEPTION 'invalid window: %', p_window;
  END IF;
END;
$$;

--
-- Name: get_family_member_points_history(uuid); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.get_family_member_points_history(p_target_id uuid) RETURNS TABLE(id uuid, restaurant_id uuid, restaurant_name text, points integer, amount_ttc numeric, reason text, earned_at timestamp with time zone, remaining_points integer, expires_at timestamp with time zone)
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  v_caller UUID := auth.uid();
  v_palmeraie_id UUID := '0cccc000-0000-4000-8000-000000000001'::UUID;
  v_relation_exists BOOLEAN;
BEGIN
  IF v_caller IS NULL THEN
    RAISE EXCEPTION 'NOT_AUTHENTICATED';
  END IF;

  SELECT EXISTS (
    SELECT 1 FROM public.pcc_family_members
    WHERE member_id = v_caller AND related_member_id = p_target_id
  ) INTO v_relation_exists;

  IF NOT v_relation_exists THEN
    RAISE EXCEPTION 'NO_FAMILY_RELATION' USING HINT = 'Vous n''êtes pas autorisé à voir cet historique';
  END IF;

  RETURN QUERY
  SELECT
    lp.id, lp.restaurant_id, r.name AS restaurant_name,
    lp.points, lp.amount_ttc, lp.reason, lp.earned_at,
    lp.remaining_points, lp.expires_at
  FROM public.loyalty_points lp
  JOIN public.restaurants r ON r.id = lp.restaurant_id
  WHERE lp.client_id = p_target_id
    AND r.tenant_id = v_palmeraie_id
  ORDER BY lp.earned_at DESC;
END $$;

--
-- Name: get_loyalty_alerts(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.get_loyalty_alerts() RETURNS TABLE(id text, severity text, category text, title text, description text, target_url text, count integer)
    LANGUAGE plpgsql STABLE SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  v_active_restos_without_rule INTEGER;
  v_pending_rule_requests      INTEGER;
  v_pending_restitutions       INTEGER;
  v_overdue_restitutions       INTEGER;
  v_suspicious_redemptions_7d  INTEGER;
  v_rejected_redemptions_24h   INTEGER;
  v_empty_tier_count           INTEGER;
BEGIN
  -- 1) Active restaurants without a dedicated gain rule (they fall back to Standard).
  --    Informative only — not a problem, but admin may want to set one.
  SELECT COUNT(*) INTO v_active_restos_without_rule
  FROM restaurants r
  LEFT JOIN restaurant_gain_rules rgr
    ON rgr.restaurant_id = r.id AND rgr.enabled = true
  WHERE r.status = 'actif' AND rgr.id IS NULL;

  -- 2) Pending rule requests waiting for admin review
  BEGIN
    SELECT COUNT(*) INTO v_pending_rule_requests
    FROM rule_requests
    WHERE status = 'en_attente';
  EXCEPTION WHEN undefined_table THEN
    v_pending_rule_requests := 0;
  END;

  -- 3) Pending restitutions (en_attente for any period)
  SELECT COUNT(*) INTO v_pending_restitutions
  FROM restaurant_restitutions
  WHERE status = 'en_attente';

  -- 4) Restitutions pending for more than 30 days → overdue
  SELECT COUNT(*) INTO v_overdue_restitutions
  FROM restaurant_restitutions
  WHERE status = 'en_attente'
    AND created_at < now() - INTERVAL '30 days';

  -- 5) Suspicious redemptions in last 7d (flagged or rejected)
  BEGIN
    SELECT COUNT(*) INTO v_suspicious_redemptions_7d
    FROM redemption_events
    WHERE created_at >= now() - INTERVAL '7 days'
      AND (flag_ratio_high OR flag_daily_near_cap OR flag_large_absolute OR NOT accepted);
  EXCEPTION WHEN undefined_table THEN
    v_suspicious_redemptions_7d := 0;
  END;

  -- 6) Rejected redemptions in last 24h (spike detection)
  BEGIN
    SELECT COUNT(*) INTO v_rejected_redemptions_24h
    FROM redemption_events
    WHERE created_at >= now() - INTERVAL '24 hours' AND NOT accepted;
  EXCEPTION WHEN undefined_table THEN
    v_rejected_redemptions_24h := 0;
  END;

  -- 7) Enabled tiers with 0 clients
  WITH tier_counts AS (
    SELECT t.tier_name, COUNT(DISTINCT ct.client_id) AS n
    FROM tier_thresholds t
    LEFT JOIN (
      SELECT
        lp.client_id,
        (
          SELECT tt.tier_name FROM tier_thresholds tt
          WHERE tt.enabled = true AND tt.points_required <= SUM(lp.remaining_points)
          ORDER BY tt.sort_order DESC LIMIT 1
        ) AS tier
      FROM loyalty_points lp
      WHERE lp.remaining_points > 0
        AND (lp.expires_at IS NULL OR lp.expires_at > now())
      GROUP BY lp.client_id
    ) ct ON ct.tier = t.tier_name
    WHERE t.enabled = true
    GROUP BY t.tier_name
  )
  SELECT COUNT(*) INTO v_empty_tier_count FROM tier_counts WHERE n = 0;

  -- Build the result set — only emit rows with non-zero counts
  IF v_active_restos_without_rule > 0 THEN
    RETURN QUERY SELECT
      'restos_no_rule'::TEXT,
      'info'::TEXT,
      'rules'::TEXT,
      format('%s restaurant%s sans règle dédiée', v_active_restos_without_rule,
             CASE WHEN v_active_restos_without_rule > 1 THEN 's' ELSE '' END)::TEXT,
      'Ces restaurants utilisent la règle Standard globale. Assignez-leur une règle spécifique si nécessaire.'::TEXT,
      '/forge/regles'::TEXT,
      v_active_restos_without_rule;
  END IF;

  IF v_pending_rule_requests > 0 THEN
    RETURN QUERY SELECT
      'pending_rule_requests'::TEXT,
      'warning'::TEXT,
      'rules'::TEXT,
      format('%s demande%s de règle en attente', v_pending_rule_requests,
             CASE WHEN v_pending_rule_requests > 1 THEN 's' ELSE '' END)::TEXT,
      'Des restaurateurs attendent votre validation pour leurs règles personnalisées.'::TEXT,
      '/forge/demandes-regles'::TEXT,
      v_pending_rule_requests;
  END IF;

  IF v_overdue_restitutions > 0 THEN
    RETURN QUERY SELECT
      'overdue_restitutions'::TEXT,
      'danger'::TEXT,
      'restitutions'::TEXT,
      format('%s restitution%s en attente depuis plus de 30 jours', v_overdue_restitutions,
             CASE WHEN v_overdue_restitutions > 1 THEN 's' ELSE '' END)::TEXT,
      'Des restitutions n''ont pas été traitées dans le délai habituel. À prioriser.'::TEXT,
      '/forge/restitutions'::TEXT,
      v_overdue_restitutions;
  ELSIF v_pending_restitutions > 0 THEN
    RETURN QUERY SELECT
      'pending_restitutions'::TEXT,
      'info'::TEXT,
      'restitutions'::TEXT,
      format('%s restitution%s en attente', v_pending_restitutions,
             CASE WHEN v_pending_restitutions > 1 THEN 's' ELSE '' END)::TEXT,
      'À consolider et verser aux restaurants partenaires.'::TEXT,
      '/forge/restitutions'::TEXT,
      v_pending_restitutions;
  END IF;

  IF v_suspicious_redemptions_7d > 0 THEN
    RETURN QUERY SELECT
      'suspicious_redemptions'::TEXT,
      CASE WHEN v_suspicious_redemptions_7d > 10 THEN 'danger' ELSE 'warning' END::TEXT,
      'redemption'::TEXT,
      format('%s redemption%s signalée%s sur 7 jours', v_suspicious_redemptions_7d,
             CASE WHEN v_suspicious_redemptions_7d > 1 THEN 's' ELSE '' END,
             CASE WHEN v_suspicious_redemptions_7d > 1 THEN 's' ELSE '' END)::TEXT,
      'À examiner dans l''audit pour détecter d''éventuelles fraudes.'::TEXT,
      '/forge/redemption-audit?tab=suspicious&period=7d'::TEXT,
      v_suspicious_redemptions_7d;
  END IF;

  IF v_rejected_redemptions_24h >= 5 THEN
    RETURN QUERY SELECT
      'spike_rejected_redemptions'::TEXT,
      'warning'::TEXT,
      'redemption'::TEXT,
      format('Pic de %s redemption%s refusée%s en 24h', v_rejected_redemptions_24h,
             CASE WHEN v_rejected_redemptions_24h > 1 THEN 's' ELSE '' END,
             CASE WHEN v_rejected_redemptions_24h > 1 THEN 's' ELSE '' END)::TEXT,
      'Un seuil est peut-être trop restrictif. Vérifiez les motifs de refus.'::TEXT,
      '/forge/redemption-audit?tab=rejected&period=24h'::TEXT,
      v_rejected_redemptions_24h;
  END IF;

  IF v_empty_tier_count > 0 THEN
    RETURN QUERY SELECT
      'empty_tier'::TEXT,
      'info'::TEXT,
      'tiers'::TEXT,
      format('%s palier%s sans aucun client', v_empty_tier_count,
             CASE WHEN v_empty_tier_count > 1 THEN 's' ELSE '' END)::TEXT,
      'Les seuils sont peut-être trop élevés. Revoyez les critères d''éligibilité.'::TEXT,
      '/forge/regles'::TEXT,
      v_empty_tier_count;
  END IF;

  RETURN;
END;
$$;

--
-- Name: get_loyalty_kpis(integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.get_loyalty_kpis(p_days integer DEFAULT 30) RETURNS TABLE(active_clients integer, active_clients_prev integer, points_circulating bigint, points_expiring_30d bigint, points_earned_window bigint, points_earned_window_prev bigint, points_redeemed_window bigint, points_redeemed_window_prev bigint, revenue_window_mad numeric, revenue_window_prev_mad numeric, redemption_rate_pct numeric)
    LANGUAGE plpgsql STABLE SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  v_from TIMESTAMPTZ := now() - (p_days || ' days')::interval;
  v_from_prev TIMESTAMPTZ := now() - (2 * p_days || ' days')::interval;
BEGIN
  RETURN QUERY
  WITH
    -- Active clients = clients with at least one positive loyalty_points entry in window
    active AS (
      SELECT COUNT(DISTINCT client_id)::INTEGER AS n
      FROM loyalty_points
      WHERE points > 0 AND earned_at >= v_from
    ),
    active_prev AS (
      SELECT COUNT(DISTINCT client_id)::INTEGER AS n
      FROM loyalty_points
      WHERE points > 0 AND earned_at >= v_from_prev AND earned_at < v_from
    ),
    -- Currently circulating = sum of non-expired remaining_points
    circulating AS (
      SELECT COALESCE(SUM(remaining_points), 0)::BIGINT AS n
      FROM loyalty_points
      WHERE remaining_points > 0
        AND (expires_at IS NULL OR expires_at > now())
    ),
    -- Expiring in 30 days (bookkeeping reminder)
    expiring AS (
      SELECT COALESCE(SUM(remaining_points), 0)::BIGINT AS n
      FROM loyalty_points
      WHERE remaining_points > 0
        AND expires_at IS NOT NULL
        AND expires_at > now()
        AND expires_at <= now() + INTERVAL '30 days'
    ),
    -- Earned in window
    earned AS (
      SELECT COALESCE(SUM(points), 0)::BIGINT AS n
      FROM loyalty_points
      WHERE points > 0 AND earned_at >= v_from
    ),
    earned_prev AS (
      SELECT COALESCE(SUM(points), 0)::BIGINT AS n
      FROM loyalty_points
      WHERE points > 0 AND earned_at >= v_from_prev AND earned_at < v_from
    ),
    -- Redeemed in window (negative conversion rows, absolute value)
    redeemed AS (
      SELECT COALESCE(SUM(-points), 0)::BIGINT AS n
      FROM loyalty_points
      WHERE points < 0 AND reason = 'conversion' AND earned_at >= v_from
    ),
    redeemed_prev AS (
      SELECT COALESCE(SUM(-points), 0)::BIGINT AS n
      FROM loyalty_points
      WHERE points < 0 AND reason = 'conversion' AND earned_at >= v_from_prev AND earned_at < v_from
    ),
    -- Restaurant revenue loyal-driven = sum of scanned_tickets.montant in window
    revenue AS (
      SELECT COALESCE(SUM(montant), 0) AS n
      FROM scanned_tickets
      WHERE created_at >= v_from
    ),
    revenue_prev AS (
      SELECT COALESCE(SUM(montant), 0) AS n
      FROM scanned_tickets
      WHERE created_at >= v_from_prev AND created_at < v_from
    )
  SELECT
    (SELECT n FROM active),
    (SELECT n FROM active_prev),
    (SELECT n FROM circulating),
    (SELECT n FROM expiring),
    (SELECT n FROM earned),
    (SELECT n FROM earned_prev),
    (SELECT n FROM redeemed),
    (SELECT n FROM redeemed_prev),
    (SELECT n FROM revenue),
    (SELECT n FROM revenue_prev),
    CASE WHEN (SELECT n FROM earned) > 0
      THEN ROUND(((SELECT n FROM redeemed)::NUMERIC / (SELECT n FROM earned)::NUMERIC) * 100, 2)
      ELSE 0 END;
END;
$$;

--
-- Name: get_loyalty_monthly_flows(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.get_loyalty_monthly_flows() RETURNS TABLE(month_start date, points_earned bigint, points_redeemed bigint, points_expired bigint)
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
  WITH months AS (
    SELECT generate_series(
      date_trunc('month', now() - INTERVAL '11 months')::DATE,
      date_trunc('month', now())::DATE,
      INTERVAL '1 month'
    )::DATE AS m
  )
  SELECT
    m.m AS month_start,
    COALESCE((
      SELECT SUM(points)
      FROM loyalty_points
      WHERE points > 0
        AND earned_at >= m.m
        AND earned_at < (m.m + INTERVAL '1 month')::DATE
    ), 0)::BIGINT AS points_earned,
    COALESCE((
      SELECT SUM(-points)
      FROM loyalty_points
      WHERE points < 0 AND reason = 'conversion'
        AND earned_at >= m.m
        AND earned_at < (m.m + INTERVAL '1 month')::DATE
    ), 0)::BIGINT AS points_redeemed,
    COALESCE((
      SELECT SUM(points_expired)
      FROM expired_points
      WHERE expired_at >= m.m
        AND expired_at < (m.m + INTERVAL '1 month')::DATE
    ), 0)::BIGINT AS points_expired
  FROM months m
  ORDER BY m.m;
$$;

--
-- Name: get_loyalty_tier_distribution(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.get_loyalty_tier_distribution() RETURNS TABLE(tier_name text, sort_order integer, client_count integer, total_points bigint)
    LANGUAGE plpgsql STABLE SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
BEGIN
  RETURN QUERY
  WITH
    -- Tous les clients ayant eu au moins 1 crédit ou 1 ticket dans les 365 derniers jours
    active_clients AS (
      SELECT DISTINCT client_id
      FROM public.loyalty_points
      WHERE points > 0 AND earned_at >= now() - INTERVAL '365 days'
      UNION
      SELECT DISTINCT client_id
      FROM public.scanned_tickets
      WHERE created_at >= now() - INTERVAL '365 days'
    ),
    client_tier AS (
      SELECT
        ac.client_id,
        (
          SELECT t.tier_name
          FROM public.tier_thresholds t
          WHERE t.enabled = true
            AND (
              t.points_required <= COALESCE((
                SELECT SUM(lp.points)::INTEGER
                FROM public.loyalty_points lp
                WHERE lp.client_id = ac.client_id
                  AND lp.points > 0
                  AND lp.earned_at >= now() - (t.period_days || ' days')::interval
              ), 0)
              OR (t.min_spend > 0 AND t.min_spend <= COALESCE((
                SELECT SUM(st.montant)
                FROM public.scanned_tickets st
                WHERE st.client_id = ac.client_id
                  AND st.created_at >= now() - (t.period_days || ' days')::interval
              ), 0))
            )
          ORDER BY t.sort_order DESC
          LIMIT 1
        ) AS tier,
        COALESCE((
          SELECT SUM(lp.remaining_points)::INTEGER
          FROM public.loyalty_points lp
          WHERE lp.client_id = ac.client_id
            AND lp.remaining_points > 0
            AND (lp.expires_at IS NULL OR lp.expires_at > now())
        ), 0) AS cur_balance
      FROM active_clients ac
    )
  SELECT
    t.tier_name,
    t.sort_order,
    COALESCE(COUNT(ct.client_id) FILTER (WHERE ct.tier = t.tier_name), 0)::INTEGER,
    COALESCE(SUM(ct.cur_balance) FILTER (WHERE ct.tier = t.tier_name), 0)::BIGINT
  FROM public.tier_thresholds t
  LEFT JOIN client_tier ct ON true
  WHERE t.enabled = true
  GROUP BY t.tier_name, t.sort_order
  UNION ALL
  SELECT
    'Sans statut'::TEXT,
    0,
    COUNT(*)::INTEGER,
    COALESCE(SUM(cur_balance), 0)::BIGINT
  FROM client_tier
  WHERE tier IS NULL
  ORDER BY sort_order;
END;
$$;

--
-- Name: get_loyalty_top_restaurants(integer, integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.get_loyalty_top_restaurants(p_days integer DEFAULT 30, p_limit integer DEFAULT 10) RETURNS TABLE(restaurant_id uuid, restaurant_name text, revenue_mad numeric, ticket_count integer, points_earned bigint, unique_clients integer, avg_ticket_mad numeric)
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
  SELECT
    r.id AS restaurant_id,
    r.name AS restaurant_name,
    COALESCE(SUM(st.montant), 0)::NUMERIC AS revenue_mad,
    COUNT(st.id)::INTEGER AS ticket_count,
    COALESCE(SUM(st.points_credites), 0)::BIGINT AS points_earned,
    COUNT(DISTINCT st.client_id)::INTEGER AS unique_clients,
    CASE WHEN COUNT(st.id) > 0
      THEN ROUND(SUM(st.montant) / COUNT(st.id)::NUMERIC, 2)
      ELSE 0 END AS avg_ticket_mad
  FROM restaurants r
  LEFT JOIN scanned_tickets st
    ON st.restaurant_id = r.id
    AND st.created_at >= now() - (p_days || ' days')::interval
  WHERE r.status = 'actif'
  GROUP BY r.id, r.name
  HAVING COUNT(st.id) > 0
  ORDER BY revenue_mad DESC
  LIMIT p_limit;
$$;

--
-- Name: get_my_restaurant_referrals(uuid); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.get_my_restaurant_referrals(p_restaurant_id uuid) RETURNS TABLE(id uuid, name text, city text, status text, referred_activated_at timestamp with time zone, created_at timestamp with time zone)
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
BEGIN
  -- Guard : l'appelant doit être staff du resto
  IF NOT EXISTS (
    SELECT 1 FROM public.restaurant_staff
    WHERE user_id = auth.uid()
      AND restaurant_id = p_restaurant_id
      AND status = 'actif'
      AND staff_role IN ('owner', 'manager', 'directeur')
  ) AND NOT public.has_role(auth.uid(), 'admin') THEN
    RAISE EXCEPTION 'Non autorisé' USING ERRCODE = '42501';
  END IF;

  RETURN QUERY
  SELECT r.id, r.name, r.city, r.status, r.referred_activated_at, r.created_at
  FROM public.restaurants r
  WHERE r.referred_by_id = p_restaurant_id
  ORDER BY r.created_at DESC;
END;
$$;

--
-- Name: get_point_value_mad(uuid); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.get_point_value_mad(p_restaurant_id uuid) RETURNS numeric
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
  SELECT COALESCE(
    -- 1st: restaurant-specific override if defined and enabled
    (SELECT point_value_mad
     FROM restaurant_gain_rules
     WHERE restaurant_id = p_restaurant_id
       AND enabled = true
       AND point_value_mad IS NOT NULL
     ORDER BY created_at DESC
     LIMIT 1),
    -- 2nd: best enabled global rule
    (SELECT point_value_mad
     FROM gain_rules
     WHERE enabled = true
     ORDER BY min_ticket DESC
     LIMIT 1),
    -- 3rd: safety fallback
    1.00
  );
$$;

--
-- Name: FUNCTION get_point_value_mad(p_restaurant_id uuid); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.get_point_value_mad(p_restaurant_id uuid) IS 'Returns the effective MAD value of 1 point for a given restaurant, following inheritance.';

--
-- Name: get_points_redeemed_last_24h(uuid); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.get_points_redeemed_last_24h(p_client_id uuid) RETURNS integer
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
  SELECT COALESCE(SUM(-points), 0)::INTEGER
  FROM public.loyalty_points
  WHERE client_id = p_client_id
    AND reason = 'conversion'
    AND points < 0
    AND earned_at >= now() - INTERVAL '24 hours';
$$;

--
-- Name: FUNCTION get_points_redeemed_last_24h(p_client_id uuid); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.get_points_redeemed_last_24h(p_client_id uuid) IS 'Returns the sum of points this client has redeemed in the last 24 hours (absolute value).';

--
-- Name: get_restitution_pool_breakdown(uuid); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.get_restitution_pool_breakdown(p_restitution_id uuid) RETURNS TABLE(client_id uuid, total_points integer, rows_count integer, first_expired_at timestamp with time zone, last_expired_at timestamp with time zone)
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
  SELECT
    rep.client_id,
    SUM(rep.points)::INTEGER AS total_points,
    COUNT(*)::INTEGER AS rows_count,
    MIN(rep.created_at) AS first_expired_at,
    MAX(rep.created_at) AS last_expired_at
  FROM public.restaurant_expired_pool rep
  WHERE rep.restitution_id = p_restitution_id
  GROUP BY rep.client_id
  ORDER BY total_points DESC;
$$;

--
-- Name: get_restitutions_export(uuid[]); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.get_restitutions_export(p_ids uuid[] DEFAULT NULL::uuid[]) RETURNS TABLE(id uuid, restaurant_id uuid, restaurant_name text, period_month date, total_points integer, status text, processed_at timestamp with time zone, notes text, created_at timestamp with time zone)
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
  SELECT
    r.id,
    r.restaurant_id,
    COALESCE(rest.name, '—') AS restaurant_name,
    r.period_month,
    r.total_points,
    r.status,
    r.processed_at,
    r.notes,
    r.created_at
  FROM public.restaurant_restitutions r
  LEFT JOIN public.restaurants rest ON rest.id = r.restaurant_id
  WHERE (p_ids IS NULL OR array_length(p_ids, 1) IS NULL OR r.id = ANY(p_ids))
  ORDER BY r.period_month DESC, rest.name ASC;
$$;

--
-- Name: FUNCTION get_restitutions_export(p_ids uuid[]); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.get_restitutions_export(p_ids uuid[]) IS 'Admin-only: returns restitutions joined with restaurant name, filtered by optional id list. Used by PDF/CSV export.';

--
-- Name: get_rule_assignments(uuid); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.get_rule_assignments(p_rule_id uuid) RETURNS TABLE(restaurant_id uuid, restaurant_name text, city text, enabled boolean, assigned_at timestamp with time zone)
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
  SELECT
    rgr.restaurant_id,
    r.name AS restaurant_name,
    r.city,
    rgr.enabled,
    rgr.created_at AS assigned_at
  FROM public.restaurant_gain_rules rgr
  JOIN public.restaurants r ON r.id = rgr.restaurant_id
  WHERE rgr.source_rule_id = p_rule_id
  ORDER BY r.city NULLS LAST, r.name;
$$;

--
-- Name: get_top_restaurants_by_reservations(timestamp with time zone, text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.get_top_restaurants_by_reservations(p_since timestamp with time zone, p_status text DEFAULT NULL::text) RETURNS TABLE(restaurant_id uuid, reservation_count bigint)
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
  SELECT r.restaurant_id, COUNT(*)::BIGINT AS reservation_count
  FROM public.reservations r
  WHERE r.created_at >= p_since
    AND r.restaurant_id IS NOT NULL
    AND (p_status IS NULL OR r.status::text = p_status)
  GROUP BY r.restaurant_id
  ORDER BY reservation_count DESC;
$$;

--
-- Name: FUNCTION get_top_restaurants_by_reservations(p_since timestamp with time zone, p_status text); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.get_top_restaurants_by_reservations(p_since timestamp with time zone, p_status text) IS 'Aggregation server-side des reservations par restaurant_id pour Top KPIs admin. Transfert agrege au lieu de rows brutes (perf + scaling-safe).';

--
-- Name: get_user_tenant_id(uuid); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.get_user_tenant_id(p_user_id uuid DEFAULT auth.uid()) RETURNS uuid
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
  SELECT tenant_id
  FROM public.tenant_admins
  WHERE user_id = p_user_id
  LIMIT 1;
$$;

--
-- Name: FUNCTION get_user_tenant_id(p_user_id uuid); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.get_user_tenant_id(p_user_id uuid) IS 'Retourne le tenant géré par l''utilisateur. NULL = pas tenant_admin (super-admin ou autre).';

--
-- Name: handle_new_user(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.handle_new_user() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
BEGIN
  INSERT INTO profiles (id, first_name, last_name, email, city, phone)
  VALUES (
    NEW.id,
    COALESCE(NEW.raw_user_meta_data->>'first_name', ''),
    COALESCE(NEW.raw_user_meta_data->>'last_name', ''),
    NEW.email,
    NULLIF(COALESCE(NEW.raw_user_meta_data->>'city', ''), ''),
    NULLIF(COALESCE(NEW.raw_user_meta_data->>'phone', ''), '')
  )
  ON CONFLICT (id) DO UPDATE SET
    email = EXCLUDED.email,
    first_name = COALESCE(NULLIF(EXCLUDED.first_name, ''), profiles.first_name),
    last_name = COALESCE(NULLIF(EXCLUDED.last_name, ''), profiles.last_name),
    city = COALESCE(EXCLUDED.city, profiles.city),
    phone = COALESCE(EXCLUDED.phone, profiles.phone);

  -- Always assign client role (prevent privilege escalation)
  INSERT INTO user_roles (user_id, role)
  VALUES (NEW.id, 'client')
  ON CONFLICT (user_id, role) DO NOTHING;

  -- Initialize client rating
  PERFORM ensure_client_rating(NEW.id);

  RETURN NEW;
END;
$$;

--
-- Name: has_role(uuid, public.app_role); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.has_role(_user_id uuid, _role public.app_role) RETURNS boolean
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.user_roles
    WHERE user_id = _user_id AND role = _role
  )
$$;

--
-- Name: has_tenant_access(uuid, uuid); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.has_tenant_access(p_tenant_id uuid, p_user_id uuid DEFAULT auth.uid()) RETURNS boolean
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
  SELECT
    public.has_role(p_user_id, 'admin'::app_role) -- super-admin voit tout
    OR public.is_tenant_admin_of(p_user_id, p_tenant_id);
$$;

--
-- Name: increment_elite_places(uuid); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.increment_elite_places(_event_id uuid) RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
BEGIN
  UPDATE public.elite_events
  SET places_taken = places_taken + 1
  WHERE id = _event_id;
END;
$$;

--
-- Name: increment_elite_places(uuid, integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.increment_elite_places(event_id uuid, delta integer) RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER
    AS $$
BEGIN
  UPDATE elite_events
  SET remaining_places = remaining_places + delta
  WHERE id = event_id;
END;
$$;

--
-- Name: is_email_suppressed(text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.is_email_suppressed(p_email text) RETURNS boolean
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.email_bounces
    WHERE email = LOWER(TRIM(p_email))
      AND is_suppressed = true
  );
$$;

--
-- Name: FUNCTION is_email_suppressed(p_email text); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.is_email_suppressed(p_email text) IS 'Helper pour EFs : retourne true si email a bounced permanent (skip send recommande).';

--
-- Name: is_group_owner_of(uuid, uuid); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.is_group_owner_of(_user_id uuid, _restaurant_id uuid) RETURNS boolean
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
  SELECT EXISTS (
    SELECT 1
    FROM public.restaurants r
    JOIN public.restaurant_groups rg ON rg.id = r.group_id
    WHERE r.id = _restaurant_id
      AND rg.owner_user_id = _user_id
  )
$$;

--
-- Name: is_guest_of_reservation(uuid, uuid); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.is_guest_of_reservation(p_reservation_id uuid, p_user_id uuid) RETURNS boolean
    LANGUAGE sql STABLE SECURITY DEFINER
    AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.reservation_guests
    WHERE reservation_id = p_reservation_id
    AND guest_user_id = p_user_id
  );
$$;

--
-- Name: is_limited_history_staff(uuid, uuid); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.is_limited_history_staff(_user_id uuid, _restaurant_id uuid) RETURNS boolean
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.restaurant_staff
    WHERE user_id = _user_id
      AND restaurant_id = _restaurant_id
      AND staff_role IN ('serveur', 'waiter', 'barman', 'caissier', 'chef_de_rang', 'controleur', 'responsable_resa')
      AND status = 'actif'
  )
  -- Group owners are never limited
  AND NOT EXISTS (
    SELECT 1
    FROM public.restaurants r
    JOIN public.restaurant_groups rg ON rg.id = r.group_id
    WHERE r.id = _restaurant_id
      AND rg.owner_user_id = _user_id
  )
$$;

--
-- Name: is_senior_staff_of(uuid, uuid); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.is_senior_staff_of(_user_id uuid, _restaurant_id uuid) RETURNS boolean
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.restaurant_staff
    WHERE user_id = _user_id 
      AND restaurant_id = _restaurant_id
      AND staff_role IN ('owner', 'manager', 'directeur')
      AND status = 'actif'
  )
  OR
  -- Group owner is always considered senior staff
  EXISTS (
    SELECT 1
    FROM public.restaurants r
    JOIN public.restaurant_groups rg ON rg.id = r.group_id
    WHERE r.id = _restaurant_id
      AND rg.owner_user_id = _user_id
  )
$$;

--
-- Name: is_staff_of(uuid, uuid); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.is_staff_of(_user_id uuid, _restaurant_id uuid) RETURNS boolean
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.restaurant_staff
    WHERE user_id = _user_id AND restaurant_id = _restaurant_id
  )
  OR
  -- Group owner has access to all restaurants in their group
  EXISTS (
    SELECT 1
    FROM public.restaurants r
    JOIN public.restaurant_groups rg ON rg.id = r.group_id
    WHERE r.id = _restaurant_id
      AND rg.owner_user_id = _user_id
  )
$$;

--
-- Name: is_tenant_admin_of(uuid, uuid); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.is_tenant_admin_of(p_user_id uuid, p_tenant_id uuid) RETURNS boolean
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.tenant_admins
    WHERE user_id = p_user_id AND tenant_id = p_tenant_id
  );
$$;

--
-- Name: FUNCTION is_tenant_admin_of(p_user_id uuid, p_tenant_id uuid); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.is_tenant_admin_of(p_user_id uuid, p_tenant_id uuid) IS 'Vérifie si user est tenant_admin du tenant p_tenant_id. Utilisé dans RLS additives.';

--
-- Name: link_guest_by_phone(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.link_guest_by_phone() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
BEGIN
  IF NEW.phone IS NOT NULL AND NEW.phone <> '' THEN
    UPDATE public.reservation_guests
    SET guest_user_id = NEW.id, status = 'lié'
    WHERE guest_phone = NEW.phone AND guest_user_id IS NULL;
  END IF;
  RETURN NEW;
END;
$$;

--
-- Name: link_guest_on_profile_update(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.link_guest_on_profile_update() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
BEGIN
  -- Guard: only act if phone actually changed (prevents loop)
  IF OLD.phone IS NOT DISTINCT FROM NEW.phone THEN
    RETURN NEW;
  END IF;

  -- Skip if phone is null or empty
  IF NEW.phone IS NULL OR NEW.phone = '' THEN
    RETURN NEW;
  END IF;

  -- Link existing guest entries that match this phone
  -- Use a direct UPDATE without triggering other triggers
  UPDATE public.reservation_guests
  SET guest_user_id = NEW.id
  WHERE guest_phone = NEW.phone
    AND guest_user_id IS NULL;

  RETURN NEW;
END;
$$;

--
-- Name: link_reservation_guest_by_phone(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.link_reservation_guest_by_phone() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
BEGIN
  -- Try to find a profile matching the guest_phone
  IF NEW.guest_user_id IS NULL AND NEW.guest_phone IS NOT NULL AND NEW.guest_phone <> '' THEN
    SELECT id INTO NEW.guest_user_id
    FROM public.profiles
    WHERE phone = NEW.guest_phone
    LIMIT 1;
    
    IF NEW.guest_user_id IS NOT NULL THEN
      NEW.status := 'lié';
    END IF;
  END IF;
  RETURN NEW;
END;
$$;

--
-- Name: list_client_accounts(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.list_client_accounts() RETURNS TABLE(email text, first_name text, last_name text, tenant_group_id uuid, tenant_id uuid, tenant_slug text)
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
BEGIN
  RETURN QUERY
    SELECT
      p.email,
      p.first_name,
      p.last_name,
      p.tenant_group_id,
      p.tenant_id,
      t.slug AS tenant_slug
    FROM user_roles ur
    JOIN profiles p ON p.id = ur.user_id
    LEFT JOIN tenants t ON t.id = p.tenant_id
    WHERE ur.role = 'client'
      AND p.email IS NOT NULL
    ORDER BY p.email;
END;
$$;

--
-- Name: FUNCTION list_client_accounts(); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.list_client_accounts() IS 'Liste les comptes clients (first_name, last_name, email, tenant_group_id, tenant_id, tenant_slug).
   Utilisé par le sélecteur de comptes de test sur la page Login des apps Win whitelabel.
   Sprint 9 — ajout tenant_id + slug pour filtrer les clients par tenant.';

--
-- Name: list_my_bookings(integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.list_my_bookings(p_limit integer DEFAULT 20) RETURNS TABLE(id uuid, resource_id uuid, resource_name text, resource_type public.bookable_resource_type, start_at timestamp with time zone, end_at timestamp with time zone, party_size integer, invitees jsonb, status public.resource_booking_status, notes text, created_at timestamp with time zone)
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Non authentifié'; END IF;

  RETURN QUERY
  SELECT
    rb.id, rb.resource_id, br.name, br.resource_type,
    rb.start_at, rb.end_at, rb.party_size, rb.invitees,
    rb.status, rb.notes, rb.created_at
  FROM public.resource_bookings rb
  JOIN public.bookable_resources br ON br.id = rb.resource_id
  WHERE rb.organizer_id = auth.uid()
  ORDER BY rb.start_at DESC
  LIMIT p_limit;
END;
$$;

--
-- Name: list_my_event_rsvp(uuid); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.list_my_event_rsvp(p_event_id uuid) RETURNS text
    LANGUAGE plpgsql STABLE SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  v_status text;
BEGIN
  SELECT status INTO v_status
    FROM public.event_rsvps
    WHERE event_id = p_event_id AND user_id = auth.uid();
  RETURN v_status; -- null si pas de RSVP
END $$;

--
-- Name: list_my_punch_cards(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.list_my_punch_cards() RETURNS TABLE(id uuid, tenant_id uuid, tenant_slug text, activity_type public.punch_card_activity, count_punched integer, threshold integer, redeemed_count integer, next_redemption_at integer, last_punched_at timestamp with time zone)
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Non authentifié'; END IF;

  RETURN QUERY
  SELECT
    pc.id, pc.tenant_id, t.slug, pc.activity_type,
    pc.count_punched, pc.threshold, pc.redeemed_count,
    GREATEST(0, pc.threshold * (pc.redeemed_count + 1) - pc.count_punched)::INT AS next_redemption_at,
    pc.last_punched_at
  FROM public.loyalty_punch_cards pc
  JOIN public.tenants t ON t.id = pc.tenant_id
  WHERE pc.client_id = auth.uid()
  ORDER BY pc.activity_type;
END;
$$;

--
-- Name: list_pcc_family(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.list_pcc_family() RETURNS TABLE(relation_id uuid, member_id uuid, first_name text, last_name text, email text, avatar_url text, relation text, total_remaining_points integer, added_at timestamp with time zone)
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  v_caller UUID := auth.uid();
  v_palmeraie_id UUID := '0cccc000-0000-4000-8000-000000000001'::UUID;
BEGIN
  IF v_caller IS NULL THEN
    RAISE EXCEPTION 'NOT_AUTHENTICATED';
  END IF;

  RETURN QUERY
  SELECT
    f.id AS relation_id,
    f.related_member_id AS member_id,
    p.first_name,
    p.last_name,
    p.email,
    p.avatar_url,
    f.relation,
    COALESCE((
      SELECT SUM(lp.remaining_points)::INTEGER
      FROM public.loyalty_points lp
      JOIN public.restaurants r ON r.id = lp.restaurant_id
      WHERE lp.client_id = f.related_member_id
        AND r.tenant_id = v_palmeraie_id
        AND COALESCE(lp.remaining_points, 0) > 0
    ), 0) AS total_remaining_points,
    f.created_at AS added_at
  FROM public.pcc_family_members f
  JOIN public.profiles p ON p.id = f.related_member_id
  WHERE f.member_id = v_caller
  ORDER BY f.created_at DESC;
END $$;

--
-- Name: list_recent_enrollments(uuid, integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.list_recent_enrollments(p_restaurant_id uuid, p_limit integer DEFAULT 20) RETURNS TABLE(id uuid, client_id uuid, first_name text, last_name text, email text, phone text, points integer, enrolled_at timestamp with time zone, enrolled_by uuid)
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'Non authentifié';
  END IF;
  IF NOT (
    public.has_role(auth.uid(), 'admin'::app_role)
    OR EXISTS (
      SELECT 1 FROM restaurant_staff
      WHERE user_id = auth.uid()
        AND restaurant_id = p_restaurant_id
        AND status = 'actif'
    )
  ) THEN
    RAISE EXCEPTION 'Accès refusé';
  END IF;

  RETURN QUERY
  SELECT
    lp.id,
    lp.client_id,
    p.first_name,
    p.last_name,
    p.email,
    p.phone,
    lp.points::INT,
    lp.earned_at,
    lp.credited_by
  FROM loyalty_points lp
  JOIN profiles p ON p.id = lp.client_id
  WHERE lp.restaurant_id = p_restaurant_id
    AND lp.reason = 'welcome'
  ORDER BY lp.earned_at DESC
  LIMIT p_limit;
END;
$$;

--
-- Name: FUNCTION list_recent_enrollments(p_restaurant_id uuid, p_limit integer); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.list_recent_enrollments(p_restaurant_id uuid, p_limit integer) IS 'Liste les N derniers membres inscrits dans un restaurant (reason=welcome).
   Réservé aux staff + super-admin. Sprint 9 enrollment.';

--
-- Name: list_resource_bookings_for_staff(uuid, date, date); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.list_resource_bookings_for_staff(p_restaurant_id uuid DEFAULT NULL::uuid, p_date_start date DEFAULT CURRENT_DATE, p_date_end date DEFAULT (CURRENT_DATE + '7 days'::interval)) RETURNS TABLE(id uuid, resource_id uuid, resource_name text, resource_type public.bookable_resource_type, organizer_id uuid, organizer_name text, organizer_email text, start_at timestamp with time zone, end_at timestamp with time zone, party_size integer, invitees jsonb, status public.resource_booking_status, notes text, restaurant_id uuid)
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Non authentifié'; END IF;

  RETURN QUERY
  SELECT
    rb.id, rb.resource_id, br.name, br.resource_type,
    rb.organizer_id,
    TRIM(COALESCE(p.first_name, '') || ' ' || COALESCE(p.last_name, '')) AS organizer_name,
    p.email AS organizer_email,
    rb.start_at, rb.end_at, rb.party_size, rb.invitees,
    rb.status, rb.notes, br.restaurant_id
  FROM public.resource_bookings rb
  JOIN public.bookable_resources br ON br.id = rb.resource_id
  JOIN public.profiles p ON p.id = rb.organizer_id
  WHERE rb.start_at::date >= p_date_start
    AND rb.start_at::date <= p_date_end
    AND (p_restaurant_id IS NULL OR br.restaurant_id = p_restaurant_id)
    AND (
      public.has_role(auth.uid(), 'admin'::app_role)
      OR EXISTS (
        SELECT 1 FROM public.restaurant_staff rs
        WHERE rs.user_id = auth.uid()
          AND rs.restaurant_id = br.restaurant_id
          AND rs.status = 'actif'
      )
    )
  ORDER BY rb.start_at;
END;
$$;

--
-- Name: list_resource_busy_slots(uuid, date); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.list_resource_busy_slots(p_restaurant_id uuid, p_date date) RETURNS TABLE(resource_id uuid, start_at timestamp with time zone, end_at timestamp with time zone, status public.resource_booking_status)
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$ BEGIN IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Non authentifié'; END IF; RETURN QUERY SELECT rb.resource_id, rb.start_at, rb.end_at, rb.status FROM public.resource_bookings rb JOIN public.bookable_resources br ON br.id = rb.resource_id WHERE br.restaurant_id = p_restaurant_id AND rb.start_at::date = p_date AND rb.status IN ('demandee','confirmee') ORDER BY rb.start_at; END; $$;

--
-- Name: list_restaurant_staff_for_login(uuid, text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.list_restaurant_staff_for_login(p_restaurant_id uuid, p_role text DEFAULT NULL::text) RETURNS TABLE(email text, first_name text, last_name text, staff_role text)
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
BEGIN
  RETURN QUERY
  SELECT
    p.email,
    p.first_name,
    p.last_name,
    rs.staff_role::TEXT
  FROM restaurant_staff rs
  JOIN profiles p ON p.id = rs.user_id
  WHERE rs.restaurant_id = p_restaurant_id
    AND rs.status = 'actif'
    AND p.email IS NOT NULL
    AND (p_role IS NULL OR rs.staff_role::TEXT = p_role)
  ORDER BY
    CASE rs.staff_role::TEXT
      WHEN 'owner'            THEN 1
      WHEN 'manager'          THEN 2
      WHEN 'directeur'        THEN 3
      WHEN 'chef_de_rang'     THEN 4
      WHEN 'responsable_resa' THEN 5
      WHEN 'serveur'          THEN 6
      WHEN 'barman'           THEN 7
      WHEN 'caissier'         THEN 8
      WHEN 'controleur'       THEN 9
      WHEN 'waiter'           THEN 10
      ELSE 99
    END,
    p.email;
END;
$$;

--
-- Name: FUNCTION list_restaurant_staff_for_login(p_restaurant_id uuid, p_role text); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.list_restaurant_staff_for_login(p_restaurant_id uuid, p_role text) IS 'Retourne les staff emails d''un restaurant (pour le sélecteur Login).
   Utilisé par l''app Store whitelabel (HOMU, Restopro, etc.) pour
   remplacer le pattern hardcodé `{slug}@{slug}.com` par les vrais
   emails seedés. SECURITY DEFINER + GRANT anon (page Login publique).
   Filtrage par role optionnel (NULL = tous les staff du resto).
   Sprint 9 whitelabel.';

--
-- Name: log_admin_action(text, text, uuid, text, jsonb, jsonb); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.log_admin_action(p_action text, p_entity_type text DEFAULT NULL::text, p_entity_id uuid DEFAULT NULL::uuid, p_entity_label text DEFAULT NULL::text, p_diff jsonb DEFAULT NULL::jsonb, p_metadata jsonb DEFAULT NULL::jsonb) RETURNS uuid
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  v_id uuid;
  v_email text;
BEGIN
  -- Guard : admin uniquement
  IF NOT public.has_role(auth.uid(), 'admin') THEN
    RAISE EXCEPTION 'Only admin can write audit log' USING ERRCODE = '42501';
  END IF;

  -- Lookup email (fallback vide si introuvable)
  SELECT email INTO v_email FROM auth.users WHERE id = auth.uid();

  INSERT INTO public.admin_audit_log (
    actor_id, actor_email, action, entity_type, entity_id, entity_label, diff, metadata
  ) VALUES (
    auth.uid(), COALESCE(v_email, ''), p_action, p_entity_type, p_entity_id, p_entity_label, p_diff, p_metadata
  )
  RETURNING id INTO v_id;

  RETURN v_id;
END;
$$;

--
-- Name: FUNCTION log_admin_action(p_action text, p_entity_type text, p_entity_id uuid, p_entity_label text, p_diff jsonb, p_metadata jsonb); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.log_admin_action(p_action text, p_entity_type text, p_entity_id uuid, p_entity_label text, p_diff jsonb, p_metadata jsonb) IS 'SECURITY DEFINER — insère un log audit. Guard interne : admin uniquement.';

--
-- Name: log_contract_changes(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.log_contract_changes() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
BEGIN
  IF OLD.commission_rate IS DISTINCT FROM NEW.commission_rate THEN
    INSERT INTO contract_history (contract_id, field_changed, old_value, new_value)
    VALUES (NEW.id, 'commission_rate', OLD.commission_rate::text, NEW.commission_rate::text);
  END IF;
  IF OLD.status IS DISTINCT FROM NEW.status THEN
    INSERT INTO contract_history (contract_id, field_changed, old_value, new_value)
    VALUES (NEW.id, 'status', OLD.status, NEW.status);
  END IF;
  IF OLD.contract_end IS DISTINCT FROM NEW.contract_end THEN
    INSERT INTO contract_history (contract_id, field_changed, old_value, new_value)
    VALUES (NEW.id, 'contract_end', OLD.contract_end::text, NEW.contract_end::text);
  END IF;
  IF OLD.auto_renew IS DISTINCT FROM NEW.auto_renew THEN
    INSERT INTO contract_history (contract_id, field_changed, old_value, new_value)
    VALUES (NEW.id, 'auto_renew', OLD.auto_renew::text, NEW.auto_renew::text);
  END IF;
  IF OLD.payment_terms IS DISTINCT FROM NEW.payment_terms THEN
    INSERT INTO contract_history (contract_id, field_changed, old_value, new_value)
    VALUES (NEW.id, 'payment_terms', OLD.payment_terms, NEW.payment_terms);
  END IF;
  RETURN NEW;
END;
$$;

--
-- Name: log_new_reservation(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.log_new_reservation() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  _client_name text;
BEGIN
  SELECT CONCAT(first_name, ' ', last_name) INTO _client_name
  FROM public.profiles WHERE id = NEW.client_id;

  INSERT INTO public.action_logs (restaurant_id, user_id, member_name, action, type, details)
  VALUES (
    NEW.restaurant_id,
    NEW.client_id,
    COALESCE(_client_name, 'Client'),
    'Nouvelle réservation',
    'réservation',
    CONCAT(
      COALESCE(_client_name, 'Client'),
      ' — ', NEW.couverts, ' couverts, ',
      TO_CHAR(NEW.date, 'DD/MM/YYYY'), ' à ', NEW.heure,
      ' (', NEW.service, ')'
    )
  );

  RETURN NEW;
END;
$$;

--
-- Name: log_reservation_status_change(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.log_reservation_status_change() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  _client_name text;
  _action text;
  _details text;
BEGIN
  IF OLD.status = NEW.status THEN
    RETURN NEW;
  END IF;

  SELECT CONCAT(first_name, ' ', last_name) INTO _client_name
  FROM public.profiles WHERE id = NEW.client_id;

  _action := CASE NEW.status::text
    WHEN 'confirmée' THEN 'Réservation confirmée'
    WHEN 'refusée' THEN 'Réservation refusée'
    WHEN 'contre_proposition' THEN 'Contre-proposition envoyée'
    WHEN 'annulée' THEN 'Réservation annulée par le client'
    WHEN 'placée' THEN 'Client placé'
    WHEN 'honorée' THEN 'Réservation honorée'
    WHEN 'no_show' THEN 'No-show enregistré'
    WHEN 'terminée' THEN 'Réservation terminée'
    ELSE 'Statut réservation modifié'
  END;

  _details := CONCAT(
    COALESCE(_client_name, 'Client'),
    ' — ', NEW.couverts, ' couverts, ',
    TO_CHAR(NEW.date, 'DD/MM/YYYY'), ' à ', NEW.heure
  );

  IF NEW.status::text = 'refusée' AND NEW.refusal_reason IS NOT NULL THEN
    _details := _details || ' — Motif : ' || NEW.refusal_reason;
  END IF;

  IF NEW.status::text = 'contre_proposition' AND NEW.proposed_heure IS NOT NULL THEN
    _details := _details || ' — Proposition : ' || COALESCE(TO_CHAR(NEW.proposed_date, 'DD/MM/YYYY'), '') || ' à ' || NEW.proposed_heure;
  END IF;

  IF NEW.status::text = 'annulée' AND NEW.cancellation_reason IS NOT NULL THEN
    _details := _details || ' — Commentaire client : ' || NEW.cancellation_reason;
  END IF;

  INSERT INTO public.action_logs (restaurant_id, user_id, member_name, action, type, details)
  VALUES (
    NEW.restaurant_id,
    NEW.client_id,
    COALESCE(_client_name, 'Client'),
    _action,
    'réservation',
    _details
  );

  RETURN NEW;
END;
$$;

--
-- Name: mark_admin_notifications_read(uuid[]); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.mark_admin_notifications_read(p_ids uuid[] DEFAULT NULL::uuid[]) RETURNS integer
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  v_count INTEGER;
  v_admin_id UUID := auth.uid();
BEGIN
  IF NOT has_role(v_admin_id, 'admin'::app_role) THEN
    RAISE EXCEPTION 'not authorized';
  END IF;

  IF p_ids IS NULL OR array_length(p_ids, 1) IS NULL THEN
    UPDATE admin_notifications
    SET read_at = now()
    WHERE read_at IS NULL
      AND (admin_id IS NULL OR admin_id = v_admin_id);
  ELSE
    UPDATE admin_notifications
    SET read_at = now()
    WHERE id = ANY(p_ids)
      AND read_at IS NULL
      AND (admin_id IS NULL OR admin_id = v_admin_id);
  END IF;

  GET DIAGNOSTICS v_count = ROW_COUNT;
  RETURN v_count;
END;
$$;

--
-- Name: mark_booking_honoree(uuid); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.mark_booking_honoree(p_booking_id uuid) RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  v_booking public.resource_bookings;
  v_resource public.bookable_resources;
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Non authentifié'; END IF;
  SELECT * INTO v_booking FROM public.resource_bookings WHERE id = p_booking_id;
  IF NOT FOUND THEN RAISE EXCEPTION 'Réservation introuvable'; END IF;
  SELECT * INTO v_resource FROM public.bookable_resources WHERE id = v_booking.resource_id;

  IF NOT (
    public.has_role(auth.uid(), 'admin'::app_role)
    OR EXISTS (
      SELECT 1 FROM public.restaurant_staff
      WHERE user_id = auth.uid() AND restaurant_id = v_resource.restaurant_id AND status = 'actif'
    )
  ) THEN
    RAISE EXCEPTION 'Accès refusé : staff requis';
  END IF;

  IF v_booking.status NOT IN ('demandee', 'confirmee') THEN
    RAISE EXCEPTION 'Réservation non marquable honorée (status=%)', v_booking.status;
  END IF;

  UPDATE public.resource_bookings SET status = 'honoree', updated_at = NOW() WHERE id = p_booking_id;
END;
$$;

--
-- Name: mark_booking_no_show(uuid); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.mark_booking_no_show(p_booking_id uuid) RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  v_booking public.resource_bookings;
  v_resource public.bookable_resources;
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Non authentifié'; END IF;
  SELECT * INTO v_booking FROM public.resource_bookings WHERE id = p_booking_id;
  IF NOT FOUND THEN RAISE EXCEPTION 'Réservation introuvable'; END IF;
  SELECT * INTO v_resource FROM public.bookable_resources WHERE id = v_booking.resource_id;

  IF NOT (
    public.has_role(auth.uid(), 'admin'::app_role)
    OR EXISTS (
      SELECT 1 FROM public.restaurant_staff
      WHERE user_id = auth.uid() AND restaurant_id = v_resource.restaurant_id AND status = 'actif'
    )
  ) THEN
    RAISE EXCEPTION 'Accès refusé : staff requis';
  END IF;

  IF v_booking.status NOT IN ('demandee', 'confirmee') THEN
    RAISE EXCEPTION 'Réservation non marquable no_show (status=%)', v_booking.status;
  END IF;

  UPDATE public.resource_bookings SET status = 'no_show', updated_at = NOW() WHERE id = p_booking_id;
END;
$$;

--
-- Name: mark_loyalty_points_notified(uuid[], text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.mark_loyalty_points_notified(p_row_ids uuid[], p_window text) RETURNS integer
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  v_count INTEGER;
BEGIN
  IF p_window = '7d' THEN
    UPDATE public.loyalty_points
    SET notified_7d_at = now()
    WHERE id = ANY(p_row_ids) AND notified_7d_at IS NULL;
    GET DIAGNOSTICS v_count = ROW_COUNT;
  ELSIF p_window = '1d' THEN
    UPDATE public.loyalty_points
    SET notified_1d_at = now()
    WHERE id = ANY(p_row_ids) AND notified_1d_at IS NULL;
    GET DIAGNOSTICS v_count = ROW_COUNT;
  ELSE
    RAISE EXCEPTION 'invalid window: %', p_window;
  END IF;
  RETURN v_count;
END;
$$;

--
-- Name: nextval(text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.nextval(seq_name text) RETURNS bigint
    LANGUAGE sql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
  SELECT nextval(seq_name::regclass);
$$;

--
-- Name: notify_admin_new_elite_application(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.notify_admin_new_elite_application() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  v_name TEXT;
BEGIN
  -- Ne déclencher qu'à la création d'une candidature "en_attente"
  IF NEW.status <> 'en_attente' THEN
    RETURN NEW;
  END IF;

  -- Récupérer le nom lisible du candidat
  SELECT COALESCE(NULLIF(TRIM(first_name || ' ' || last_name), ''), 'Un client')
    INTO v_name
  FROM public.profiles
  WHERE id = NEW.user_id;

  INSERT INTO public.admin_notifications (type, severity, title, description, target_url, metadata)
  VALUES (
    'elite_application',
    'info',
    'Nouvelle candidature Elite',
    format('%s candidate au tier %s',
      COALESCE(v_name, NULLIF(NEW.full_name, ''), 'Un client'),
      COALESCE(NEW.preferred_tier, 'Elite')),
    '/forge/elite-club?tab=applications',
    jsonb_build_object(
      'application_id', NEW.id,
      'user_id', NEW.user_id,
      'preferred_tier', NEW.preferred_tier
    )
  );

  RETURN NEW;
END;
$$;

--
-- Name: FUNCTION notify_admin_new_elite_application(); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.notify_admin_new_elite_application() IS 'Emits an admin_notifications row when a new elite_applications row is inserted with status=en_attente. TICKET-026 / TICKET-029 Sprint 3.';

--
-- Name: notify_admin_new_rule_request(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.notify_admin_new_rule_request() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  v_resto_name TEXT;
BEGIN
  IF NEW.status <> 'en_attente' THEN
    RETURN NEW;
  END IF;

  SELECT name INTO v_resto_name FROM restaurants WHERE id = NEW.restaurant_id;

  INSERT INTO admin_notifications (type, severity, title, description, target_url, metadata)
  VALUES (
    'rule_request',
    'warning',
    'Nouvelle demande de règle',
    format('%s demande une règle "%s" (%s%%)',
      COALESCE(v_resto_name, 'Un restaurant'),
      NEW.name,
      ROUND(NEW.taux_conversion * 100)::text),
    format('/forge/demandes-regles?highlight=%s', NEW.id),
    jsonb_build_object(
      'request_id', NEW.id,
      'restaurant_id', NEW.restaurant_id,
      'restaurant_name', v_resto_name
    )
  );

  RETURN NEW;
END;
$$;

--
-- Name: notify_admin_on_redemption_reject(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.notify_admin_on_redemption_reject() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  v_24h_rejects INTEGER;
  v_last_notif TIMESTAMPTZ;
BEGIN
  IF NEW.accepted IS TRUE THEN
    RETURN NEW;
  END IF;

  SELECT COUNT(*) INTO v_24h_rejects
  FROM redemption_events
  WHERE accepted = false
    AND created_at >= now() - INTERVAL '24 hours';

  -- Trigger a notif only when we cross the threshold of 5 and we haven't
  -- already sent one in the last 12h.
  IF v_24h_rejects >= 5 THEN
    SELECT MAX(created_at) INTO v_last_notif
    FROM admin_notifications
    WHERE type = 'suspicious_redemption';

    IF v_last_notif IS NULL OR v_last_notif < now() - INTERVAL '12 hours' THEN
      INSERT INTO admin_notifications (type, severity, title, description, target_url, metadata)
      VALUES (
        'suspicious_redemption',
        'warning',
        format('%s redemptions refusées en 24h', v_24h_rejects),
        'Un pic a été détecté. Vérifiez les motifs de refus et les seuils.',
        '/forge/redemption-audit?tab=rejected&period=24h',
        jsonb_build_object('count_24h', v_24h_rejects)
      );
    END IF;
  END IF;

  RETURN NEW;
END;
$$;

--
-- Name: notify_pcc_family_added(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.notify_pcc_family_added() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  v_caller_name TEXT;
BEGIN
  SELECT COALESCE(NULLIF(TRIM(first_name || ' ' || COALESCE(last_name, '')), ''), email, 'Un membre Palmeraie')
    INTO v_caller_name
  FROM public.profiles
  WHERE id = NEW.member_id;

  INSERT INTO public.notifications (user_id, type, title, message, link, read, created_at)
  VALUES (
    NEW.related_member_id,
    'pcc_family_added',
    'Ajouté à une famille Palmeraie',
    COALESCE(v_caller_name, 'Un membre') || ' vous a ajouté à sa liste famille. Vous pouvez retirer ce lien depuis votre profil.',
    '/pocket/pcc/family',
    false,
    NOW()
  );
  RETURN NEW;
END $$;

--
-- Name: notify_reservation_change(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.notify_reservation_change() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public', 'net'
    AS $$
DECLARE
  _payload jsonb;
  _supabase_url text := 'https://vghevjywcbllzhhzzezf.supabase.co';
  _service_role_key text;
BEGIN
  -- Get service role key from vault (Supabase stores it in app settings)
  _service_role_key := current_setting('supabase.service_role_key', true);

  -- Determine trigger type and build payload
  IF TG_OP = 'INSERT' THEN
    _payload := jsonb_build_object(
      'source', 'db_webhook',
      'trigger_type', 'INSERT',
      'record', row_to_json(NEW)::jsonb
    );
  ELSIF TG_OP = 'UPDATE' THEN
    -- Only fire if status actually changed (redundant with WHEN clause but safe)
    IF OLD.status = NEW.status THEN
      RETURN NEW;
    END IF;
    _payload := jsonb_build_object(
      'source', 'db_webhook',
      'trigger_type', 'UPDATE',
      'record', row_to_json(NEW)::jsonb,
      'old_record', row_to_json(OLD)::jsonb
    );
  END IF;

  -- Fire async HTTP request to Edge Function via pg_net
  -- This returns immediately (non-blocking)
  PERFORM net.http_post(
    url := _supabase_url || '/functions/v1/send-reservation-push',
    body := _payload,
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'x-webhook-secret', 'oneclick-webhook-2026'
    )
  );

  RETURN NEW;
EXCEPTION
  WHEN OTHERS THEN
    -- Never let push notification failures block the reservation
    RAISE WARNING 'notify_reservation_change failed: %', SQLERRM;
    RETURN NEW;
END;
$$;

--
-- Name: FUNCTION notify_reservation_change(); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.notify_reservation_change() IS 'Auto-triggers push notifications via Edge Function when reservation is created or status changes';

--
-- Name: prevent_organizer_overlap(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.prevent_organizer_overlap() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  v_conflict_resource_name TEXT;
BEGIN
  IF NEW.status NOT IN ('demandee', 'confirmee') THEN
    RETURN NEW;
  END IF;

  SELECT br.name
    INTO v_conflict_resource_name
  FROM public.resource_bookings rb
  JOIN public.bookable_resources br ON br.id = rb.resource_id
  WHERE rb.organizer_id = NEW.organizer_id
    AND rb.id != NEW.id
    AND rb.status IN ('demandee', 'confirmee')
    AND tstzrange(rb.start_at, rb.end_at, '[)') &&
        tstzrange(NEW.start_at, NEW.end_at, '[)')
  LIMIT 1;

  IF v_conflict_resource_name IS NOT NULL THEN
    RAISE EXCEPTION
      'Vous avez deja une reservation a ce creneau (%)',
      v_conflict_resource_name
      USING ERRCODE = '23P01',
            HINT    = 'Annulez ou decalez votre reservation existante avant d''en creer une nouvelle.';
  END IF;

  RETURN NEW;
END;
$$;

--
-- Name: FUNCTION prevent_organizer_overlap(); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.prevent_organizer_overlap() IS 'Empeche un organizer d''avoir 2 bookings actifs chevauchants cross-modules. Sprint PCC bugfix 24/04.';

--
-- Name: process_expired_admin_wallet(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.process_expired_admin_wallet() RETURNS integer
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  _count integer := 0;
  _row RECORD;
BEGIN
  FOR _row IN
    SELECT id, admin_id, restaurant_id, remaining_amount, created_at, expires_at
    FROM public.admin_wallet_transactions
    WHERE amount > 0
      AND remaining_amount > 0
      AND expires_at IS NOT NULL
      AND expires_at <= now()
  LOOP
    -- Zero out remaining
    UPDATE public.admin_wallet_transactions
    SET remaining_amount = 0
    WHERE id = _row.id;

    -- Log the expiration as a negative entry
    INSERT INTO public.admin_wallet_transactions (admin_id, restaurant_id, amount, reason, details)
    VALUES (
      _row.admin_id,
      _row.restaurant_id,
      -_row.remaining_amount,
      'admin_wallet_expiration',
      'Expiration automatique après 3 ans — Transaction originale du ' || to_char(_row.created_at, 'DD/MM/YYYY')
    );

    _count := _count + 1;
  END LOOP;

  RETURN _count;
END;
$$;

--
-- Name: process_expired_points(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.process_expired_points() RETURNS integer
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  _count integer := 0;
  _row RECORD;
  _admin_id uuid;
  _admin_share integer;
  _restaurant_share integer;
  _expired_id uuid;
  _resto_name text;
BEGIN
  -- Get the first admin user for wallet attribution
  SELECT ur.user_id INTO _admin_id
  FROM public.user_roles ur
  WHERE ur.role = 'admin'
  LIMIT 1;

  FOR _row IN
    SELECT id, client_id, restaurant_id, remaining_points, earned_at, expires_at
    FROM public.loyalty_points
    WHERE points > 0
      AND remaining_points > 0
      AND expires_at IS NOT NULL
      AND expires_at <= now()
  LOOP
    -- Calculate shares (integer division: restaurant gets remainder)
    _admin_share := _row.remaining_points / 2;
    _restaurant_share := _row.remaining_points - _admin_share;

    -- Transfer to expired_points (full archive)
    INSERT INTO public.expired_points (client_id, restaurant_id, original_point_id, points_expired, earned_at, expired_at)
    VALUES (_row.client_id, _row.restaurant_id, _row.id, _row.remaining_points, _row.earned_at, _row.expires_at)
    RETURNING id INTO _expired_id;

    -- 50% → Admin wallet
    IF _admin_id IS NOT NULL AND _admin_share > 0 THEN
      INSERT INTO public.admin_wallet_transactions (admin_id, restaurant_id, amount, reason, details)
      VALUES (
        _admin_id,
        _row.restaurant_id,
        _admin_share,
        'expired_points_recovery',
        'Récupération 50% points expirés — Client ' || _row.client_id::text
      );
    END IF;

    -- 50% → Restaurant expired pool (for monthly restitution)
    IF _restaurant_share > 0 THEN
      INSERT INTO public.restaurant_expired_pool (restaurant_id, client_id, expired_point_id, points)
      VALUES (_row.restaurant_id, _row.client_id, _expired_id, _restaurant_share);
    END IF;

    -- Zero out remaining
    UPDATE public.loyalty_points SET remaining_points = 0 WHERE id = _row.id;

    -- ===== FIX #3 (22/04) — Notif client par lot expiré =====
    -- Lookup nom resto pour message lisible. Fail-safe : si lookup échoue, fallback générique.
    BEGIN
      SELECT name INTO _resto_name FROM public.restaurants WHERE id = _row.restaurant_id;
    EXCEPTION WHEN OTHERS THEN
      _resto_name := NULL;
    END;

    BEGIN
      INSERT INTO public.notifications (user_id, title, message, type, link, restaurant_id)
      VALUES (
        _row.client_id,
        'Points expirés',
        format(
          '%s points sont arrivés à expiration%s. Pensez à utiliser vos points avant leur date d''expiration pour ne pas les perdre.',
          _row.remaining_points,
          CASE WHEN _resto_name IS NOT NULL THEN ' chez ' || _resto_name ELSE '' END
        ),
        'points_expired',
        '/pocket/vault',
        _row.restaurant_id
      );
    EXCEPTION WHEN OTHERS THEN
      -- Notif failure must NEVER block expiration processing.
      RAISE NOTICE 'Notif expired points failed for client %: %', _row.client_id, SQLERRM;
    END;

    _count := _count + 1;
  END LOOP;

  RETURN _count;
END;
$$;

--
-- Name: promote_pending_client_ratings(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.promote_pending_client_ratings() RETURNS integer
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  v_count INTEGER;
BEGIN
  WITH promoted AS (
    UPDATE client_ratings
    SET visible_rating = pending_rating,
        visible_total_honored = pending_total_honored,
        visible_total_no_show = pending_total_no_show,
        visible_is_new = pending_is_new,
        pending_rating = NULL,
        pending_total_honored = NULL,
        pending_total_no_show = NULL,
        pending_is_new = NULL,
        pending_visible_at = NULL,
        updated_at = now()
    WHERE pending_visible_at IS NOT NULL
      AND pending_visible_at <= now()
    RETURNING 1
  )
  SELECT COUNT(*) INTO v_count FROM promoted;

  RETURN v_count;
END;
$$;

--
-- Name: propagate_cancellation_to_guests(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.propagate_cancellation_to_guests() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  _guest RECORD;
  _host_name text;
  _restaurant_name text;
  _is_late_cancel boolean;
BEGIN
  -- Fire on transition vers 'annulée' OU vers 'no_show' avec late_cancellation
  _is_late_cancel := NEW.status::text = 'no_show'
                  AND NEW.late_cancellation = true
                  AND (OLD.status::text <> 'no_show' OR OLD.late_cancellation = false);

  IF (NEW.status::text <> 'annulée' OR OLD.status::text = 'annulée')
     AND NOT _is_late_cancel THEN
    RETURN NEW;
  END IF;

  SELECT CONCAT(first_name, ' ', last_name) INTO _host_name
  FROM public.profiles WHERE id = NEW.client_id;

  SELECT name INTO _restaurant_name
  FROM public.restaurants WHERE id = NEW.restaurant_id;

  -- Update all non-refused guests to 'annulée'
  UPDATE public.reservation_guests
  SET status = 'annulée'
  WHERE reservation_id = NEW.id
    AND status NOT IN ('refusé');

  -- Send notification to each linked guest
  FOR _guest IN
    SELECT guest_user_id, guest_name
    FROM public.reservation_guests
    WHERE reservation_id = NEW.id
      AND guest_user_id IS NOT NULL
      AND status = 'annulée'
  LOOP
    INSERT INTO public.notifications (user_id, title, message, type, link)
    VALUES (
      _guest.guest_user_id,
      'Réservation annulée',
      CONCAT(
        'La réservation chez ', COALESCE(_restaurant_name, 'le restaurant'),
        ' du ', TO_CHAR(NEW.date, 'DD/MM/YYYY'), ' à ', NEW.heure,
        ' a été annulée par ', COALESCE(_host_name, 'l''hôte'), '.',
        CASE WHEN NEW.cancellation_reason IS NOT NULL AND NEW.cancellation_reason <> ''
          THEN CONCAT(' Motif : ', NEW.cancellation_reason)
          ELSE ''
        END
      ),
      'reservation',
      '/pocket/oneclick'
    );
  END LOOP;

  RETURN NEW;
END;
$$;

--
-- Name: FUNCTION propagate_cancellation_to_guests(); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.propagate_cancellation_to_guests() IS 'Propage status annulée (depuis annulée OU depuis late_cancellation no_show) aux invités. Notifie les invités liés. Aucune pénalité aux invités.';

--
-- Name: purge_old_action_logs(integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.purge_old_action_logs(p_days integer DEFAULT 365) RETURNS integer
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  v_deleted INTEGER := 0;
BEGIN
  IF NOT (has_role(auth.uid(), 'admin'::app_role) OR auth.role() = 'service_role') THEN
    RAISE EXCEPTION 'not authorized';
  END IF;

  WITH d AS (
    DELETE FROM public.action_logs
    WHERE created_at < now() - (p_days || ' days')::interval
    RETURNING id
  )
  SELECT COUNT(*)::INTEGER INTO v_deleted FROM d;

  RETURN v_deleted;
END;
$$;

--
-- Name: FUNCTION purge_old_action_logs(p_days integer); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.purge_old_action_logs(p_days integer) IS 'Supprime les action_logs plus anciens que p_days jours (defaut 365j). Admin/service_role only.';

--
-- Name: purge_old_notifications(integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.purge_old_notifications(p_days integer DEFAULT 180) RETURNS integer
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  v_deleted INTEGER := 0;
BEGIN
  IF NOT (has_role(auth.uid(), 'admin'::app_role) OR auth.role() = 'service_role') THEN
    RAISE EXCEPTION 'not authorized';
  END IF;

  WITH d AS (
    DELETE FROM public.notifications
    WHERE read = true
      AND created_at < now() - (p_days || ' days')::interval
    RETURNING id
  )
  SELECT COUNT(*)::INTEGER INTO v_deleted FROM d;

  RETURN v_deleted;
END;
$$;

--
-- Name: FUNCTION purge_old_notifications(p_days integer); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.purge_old_notifications(p_days integer) IS 'Supprime les notifications lues plus anciennes que p_days jours (defaut 180j). Admin/service_role only.';

--
-- Name: reconcile_loyalty_points(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.reconcile_loyalty_points() RETURNS TABLE(point_id uuid, client_id uuid, restaurant_id uuid, stored_remaining integer, computed_remaining integer, discrepancy integer)
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
BEGIN
  RETURN QUERY
  SELECT
    lp.id AS point_id,
    lp.client_id,
    lp.restaurant_id,
    lp.remaining_points AS stored_remaining,
    GREATEST(0, lp.points - COALESCE(consumed.total_consumed, 0)) AS computed_remaining,
    lp.remaining_points - GREATEST(0, lp.points - COALESCE(consumed.total_consumed, 0)) AS discrepancy
  FROM public.loyalty_points lp
  LEFT JOIN (
    -- Sum negative entries (conversions/redemptions) per client+restaurant
    SELECT
      lp2.client_id,
      lp2.restaurant_id,
      ABS(SUM(CASE WHEN lp2.points < 0 THEN lp2.points ELSE 0 END)) AS total_consumed
    FROM public.loyalty_points lp2
    GROUP BY lp2.client_id, lp2.restaurant_id
  ) consumed ON consumed.client_id = lp.client_id AND consumed.restaurant_id = lp.restaurant_id
  WHERE lp.points > 0
    AND lp.remaining_points != GREATEST(0, lp.points - COALESCE(consumed.total_consumed, 0));
END;
$$;

--
-- Name: record_email_bounce(text, text, text, text, jsonb); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.record_email_bounce(p_email text, p_bounce_type text, p_bounce_reason text DEFAULT NULL::text, p_source_ef text DEFAULT NULL::text, p_raw_event jsonb DEFAULT NULL::jsonb) RETURNS uuid
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  v_id UUID;
  v_normalized TEXT := LOWER(TRIM(p_email));
BEGIN
  INSERT INTO public.email_bounces (
    email, bounce_type, bounce_reason, is_suppressed,
    last_bounced_at, bounce_count, source_ef, raw_event
  )
  VALUES (
    v_normalized, p_bounce_type, p_bounce_reason,
    (p_bounce_type = 'permanent'),
    now(), 1, p_source_ef, p_raw_event
  )
  ON CONFLICT (email) DO UPDATE SET
    bounce_type     = EXCLUDED.bounce_type,
    bounce_reason   = COALESCE(EXCLUDED.bounce_reason, public.email_bounces.bounce_reason),
    is_suppressed   = (EXCLUDED.bounce_type = 'permanent') OR public.email_bounces.is_suppressed,
    last_bounced_at = now(),
    bounce_count    = public.email_bounces.bounce_count + 1,
    raw_event       = COALESCE(EXCLUDED.raw_event, public.email_bounces.raw_event)
  RETURNING id INTO v_id;
  RETURN v_id;
END;
$$;

--
-- Name: FUNCTION record_email_bounce(p_email text, p_bounce_type text, p_bounce_reason text, p_source_ef text, p_raw_event jsonb); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.record_email_bounce(p_email text, p_bounce_type text, p_bounce_reason text, p_source_ef text, p_raw_event jsonb) IS 'Helper pour EF resend-webhooks : enregistre un bounce avec auto-suppress si permanent.';

--
-- Name: redeem_punch_card(uuid, uuid); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.redeem_punch_card(p_card_id uuid, p_booking_id uuid) RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  v_card public.loyalty_punch_cards;
  v_booking public.resource_bookings;
  v_resource public.bookable_resources;
  v_eligible_count INT;
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Non authentifié'; END IF;

  SELECT * INTO v_card FROM public.loyalty_punch_cards WHERE id = p_card_id;
  IF NOT FOUND THEN RAISE EXCEPTION 'Carte de fidélité introuvable'; END IF;

  SELECT * INTO v_booking FROM public.resource_bookings WHERE id = p_booking_id;
  IF NOT FOUND THEN RAISE EXCEPTION 'Réservation introuvable'; END IF;
  SELECT * INTO v_resource FROM public.bookable_resources WHERE id = v_booking.resource_id;

  -- Staff scope check
  IF NOT (
    public.has_role(auth.uid(), 'admin'::app_role)
    OR EXISTS (
      SELECT 1 FROM public.restaurant_staff
      WHERE user_id = auth.uid() AND restaurant_id = v_resource.restaurant_id AND status = 'actif'
    )
  ) THEN
    RAISE EXCEPTION 'Accès refusé : staff requis';
  END IF;

  -- Card belongs to booking organizer
  IF v_card.client_id <> v_booking.organizer_id THEN
    RAISE EXCEPTION 'Carte ne correspond pas au membre';
  END IF;

  -- Card eligible : count_punched >= threshold * (redeemed_count + 1)
  v_eligible_count := v_card.threshold * (v_card.redeemed_count + 1);
  IF v_card.count_punched < v_eligible_count THEN
    RAISE EXCEPTION 'Carte pas encore éligible (compteur: % / requis: %)',
      v_card.count_punched, v_eligible_count;
  END IF;

  UPDATE public.loyalty_punch_cards SET
    redeemed_count = redeemed_count + 1,
    last_redeemed_at = NOW(),
    updated_at = NOW()
  WHERE id = p_card_id;

  -- Optionnel : update booking pricing pour marquer "offert"
  UPDATE public.resource_bookings SET
    pricing_snapshot = COALESCE(pricing_snapshot, '{}'::jsonb)
      || jsonb_build_object('redeemed_card_id', p_card_id, 'redeemed_at', NOW()),
    updated_at = NOW()
  WHERE id = p_booking_id;
END;
$$;

--
-- Name: redeem_restaurant_referral(uuid, text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.redeem_restaurant_referral(p_referee_id uuid, p_code text) RETURNS jsonb
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  v_referrer record;
  v_referee  record;
  v_clean_code text;
BEGIN
  -- Normalise le code
  v_clean_code := UPPER(TRIM(p_code));
  IF v_clean_code IS NULL OR LENGTH(v_clean_code) < 5 THEN
    RETURN jsonb_build_object('success', false, 'error', 'Code invalide');
  END IF;

  -- Lookup referrer
  SELECT id, name, referral_code, status
    INTO v_referrer
  FROM public.restaurants
  WHERE referral_code = v_clean_code
  LIMIT 1;

  IF v_referrer.id IS NULL THEN
    RETURN jsonb_build_object('success', false, 'error', 'Code introuvable');
  END IF;

  IF v_referrer.status <> 'actif' THEN
    RETURN jsonb_build_object('success', false, 'error', 'Le parrain n''est pas actif');
  END IF;

  -- Lookup referee
  SELECT id, referred_by_id, status
    INTO v_referee
  FROM public.restaurants
  WHERE id = p_referee_id
  LIMIT 1;

  IF v_referee.id IS NULL THEN
    RETURN jsonb_build_object('success', false, 'error', 'Restaurant introuvable');
  END IF;

  -- Un resto ne peut pas se parrainer lui-même
  IF v_referee.id = v_referrer.id THEN
    RETURN jsonb_build_object('success', false, 'error', 'Auto-parrainage interdit');
  END IF;

  -- Un resto ne peut être parrainé qu'une seule fois
  IF v_referee.referred_by_id IS NOT NULL THEN
    RETURN jsonb_build_object('success', false, 'error', 'Ce resto a déjà été parrainé');
  END IF;

  -- Enregistre le parrainage (sans activation immédiate)
  UPDATE public.restaurants
    SET referred_by_id = v_referrer.id
  WHERE id = v_referee.id;

  -- Si le referee est déjà actif, active tout de suite
  IF v_referee.status = 'actif' THEN
    UPDATE public.restaurants
      SET referred_activated_at = now()
    WHERE id = v_referee.id;
  END IF;

  RETURN jsonb_build_object(
    'success', true,
    'referrer_id', v_referrer.id,
    'referrer_name', v_referrer.name,
    'activated', v_referee.status = 'actif'
  );
END;
$$;

--
-- Name: refresh_reservations_summary(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.refresh_reservations_summary() RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
BEGIN
  REFRESH MATERIALIZED VIEW CONCURRENTLY public.mv_reservations_summary;
END;
$$;

--
-- Name: remove_pcc_family_member(uuid); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.remove_pcc_family_member(p_relation_id uuid) RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  v_caller UUID := auth.uid();
  v_member UUID;
  v_related UUID;
BEGIN
  IF v_caller IS NULL THEN
    RAISE EXCEPTION 'NOT_AUTHENTICATED';
  END IF;

  SELECT member_id, related_member_id INTO v_member, v_related
  FROM public.pcc_family_members
  WHERE id = p_relation_id;

  IF v_member IS NULL THEN
    RAISE EXCEPTION 'RELATION_NOT_FOUND';
  END IF;

  IF v_caller != v_member AND v_caller != v_related THEN
    RAISE EXCEPTION 'NOT_AUTHORIZED';
  END IF;

  DELETE FROM public.pcc_family_members WHERE id = p_relation_id;
END $$;

--
-- Name: request_redemption_otp(uuid, uuid, integer, numeric, numeric); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.request_redemption_otp(p_client_id uuid, p_restaurant_id uuid, p_points integer, p_montant numeric, p_discount_dh numeric) RETURNS TABLE(request_id uuid, expires_at timestamp with time zone)
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  v_code TEXT;
  v_code_hash TEXT;
  v_request_id UUID;
  v_expires_at TIMESTAMPTZ;
  v_resto_name TEXT;
BEGIN
  -- Security: caller must be staff of this restaurant (or admin)
  IF NOT is_staff_of(auth.uid(), p_restaurant_id)
     AND NOT has_role(auth.uid(), 'admin'::app_role) THEN
    RAISE EXCEPTION 'not authorized';
  END IF;

  -- Input validation
  IF p_points <= 0 OR p_montant < 0 OR p_discount_dh < 0 THEN
    RAISE EXCEPTION 'invalid parameters';
  END IF;

  -- Cancel any previous pending OTP for the same (client, restaurant)
  -- to avoid stacking codes.
  UPDATE redemption_otp_requests
  SET status = 'cancelled'
  WHERE client_id = p_client_id
    AND restaurant_id = p_restaurant_id
    AND status = 'pending';

  -- Generate a random 6-digit code
  v_code := lpad(floor(random() * 1000000)::TEXT, 6, '0');
  v_code_hash := encode(digest(v_code, 'sha256'), 'hex');
  v_expires_at := now() + INTERVAL '5 minutes';

  INSERT INTO redemption_otp_requests (
    client_id, restaurant_id, staff_id,
    points_requested, ticket_montant, estimated_discount_dh,
    code_hash, expires_at
  ) VALUES (
    p_client_id, p_restaurant_id, auth.uid(),
    p_points, p_montant, p_discount_dh,
    v_code_hash, v_expires_at
  )
  RETURNING id INTO v_request_id;

  -- Notify the client in-app with the code
  SELECT name INTO v_resto_name FROM restaurants WHERE id = p_restaurant_id;

  INSERT INTO notifications (user_id, title, message, type, link)
  VALUES (
    p_client_id,
    '🔒 Code de confirmation',
    'Code ' || v_code || ' — pour valider la conversion de ' || p_points::text ||
      ' pts (' || p_discount_dh::text || ' MAD) chez ' || COALESCE(v_resto_name, 'le restaurant') ||
      '. Valide 5 minutes.',
    'otp',
    '/pocket/vault'
  );

  RETURN QUERY SELECT v_request_id, v_expires_at;
END;
$$;

--
-- Name: FUNCTION request_redemption_otp(p_client_id uuid, p_restaurant_id uuid, p_points integer, p_montant numeric, p_discount_dh numeric); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.request_redemption_otp(p_client_id uuid, p_restaurant_id uuid, p_points integer, p_montant numeric, p_discount_dh numeric) IS 'Creates a 5-min OTP request for a large redemption; notifies the client in-app with the code.';

--
-- Name: reset_reservation_reminders_on_reschedule(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.reset_reservation_reminders_on_reschedule() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
BEGIN
  IF NEW.date IS DISTINCT FROM OLD.date
     OR NEW.heure IS DISTINCT FROM OLD.heure
     OR NEW.proposed_date IS DISTINCT FROM OLD.proposed_date
     OR NEW.proposed_heure IS DISTINCT FROM OLD.proposed_heure THEN
    NEW.reminder_j1_sent_at := NULL;
    NEW.reminder_h2_sent_at := NULL;
  END IF;
  RETURN NEW;
END;
$$;

--
-- Name: reset_resource_booking_reminders_on_reschedule(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.reset_resource_booking_reminders_on_reschedule() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
BEGIN
  IF NEW.start_at IS DISTINCT FROM OLD.start_at
     OR NEW.end_at IS DISTINCT FROM OLD.end_at THEN
    NEW.reminder_j1_sent_at := NULL;
    NEW.reminder_h2_sent_at := NULL;
  END IF;
  RETURN NEW;
END;
$$;

--
-- Name: rsvp_event(uuid, text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.rsvp_event(p_event_id uuid, p_status text) RETURNS uuid
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  v_user_id  uuid := auth.uid();
  v_enabled  boolean;
  v_event_date timestamptz;
  v_rsvp_id  uuid;
BEGIN
  IF v_user_id IS NULL THEN
    RAISE EXCEPTION 'Non authentifié';
  END IF;

  IF p_status NOT IN ('attending', 'maybe', 'not_attending') THEN
    RAISE EXCEPTION 'Status invalide (attending/maybe/not_attending)';
  END IF;

  -- Vérifier que l'event existe et accepte les RSVPs
  SELECT rsvp_enabled, event_date INTO v_enabled, v_event_date
    FROM public.tenant_events
    WHERE id = p_event_id AND status = 'actif';

  IF v_enabled IS NULL THEN
    RAISE EXCEPTION 'Événement introuvable ou inactif';
  END IF;
  IF v_enabled = false THEN
    RAISE EXCEPTION 'RSVP non activé pour cet événement';
  END IF;
  IF v_event_date < now() THEN
    RAISE EXCEPTION 'Cet événement est passé, impossible de répondre';
  END IF;

  -- UPSERT
  INSERT INTO public.event_rsvps (event_id, user_id, status)
    VALUES (p_event_id, v_user_id, p_status)
    ON CONFLICT (event_id, user_id) DO UPDATE SET
      status = EXCLUDED.status,
      updated_at = now()
    RETURNING id INTO v_rsvp_id;

  RETURN v_rsvp_id;
END $$;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: restaurants; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.restaurants (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name text NOT NULL,
    city text DEFAULT ''::text NOT NULL,
    cuisine text DEFAULT ''::text NOT NULL,
    budget text DEFAULT '€€'::text NOT NULL,
    rating numeric(2,1) DEFAULT 0 NOT NULL,
    reviews_count integer DEFAULT 0 NOT NULL,
    image text DEFAULT '🍽️'::text NOT NULL,
    phone text,
    address text,
    description text,
    open_now boolean DEFAULT true NOT NULL,
    tags text[] DEFAULT '{}'::text[],
    lounge_pts integer DEFAULT 100 NOT NULL,
    status text DEFAULT 'actif'::text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    max_staff integer DEFAULT 20 NOT NULL,
    group_id uuid,
    google_place_id text,
    opening_hours jsonb,
    website_url text,
    latitude numeric(9,6),
    longitude numeric(9,6),
    google_rating numeric(3,2),
    google_reviews_count integer DEFAULT 0,
    google_photos text[] DEFAULT '{}'::text[],
    google_updated_at timestamp with time zone,
    search_vector tsvector GENERATED ALWAYS AS ((((setweight(to_tsvector('french'::regconfig, COALESCE(name, ''::text)), 'A'::"char") || setweight(to_tsvector('french'::regconfig, COALESCE(city, ''::text)), 'B'::"char")) || setweight(to_tsvector('french'::regconfig, COALESCE(cuisine, ''::text)), 'C'::"char")) || setweight(to_tsvector('french'::regconfig, COALESCE(description, ''::text)), 'D'::"char"))) STORED,
    onboarding_completed_at timestamp with time zone,
    referral_code text,
    referred_by_id uuid,
    referred_activated_at timestamp with time zone,
    tenant_id uuid,
    CONSTRAINT restaurants_status_check CHECK ((status = ANY (ARRAY['actif'::text, 'inactif'::text, 'suspendu'::text, 'prospect'::text])))
);

--
-- Name: COLUMN restaurants.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.restaurants.status IS 'actif = visible clients. inactif = désactivé temporairement. suspendu = sanction admin. prospect = contrat signé mais pas encore onboardé (invisible clients).';

--
-- Name: COLUMN restaurants.onboarding_completed_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.restaurants.onboarding_completed_at IS 'Timestamp de complétion du wizard onboarding ProDesk. NULL = à faire.';

--
-- Name: COLUMN restaurants.referral_code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.restaurants.referral_code IS 'Code de parrainage unique auto-généré (ex: CAS-X3A9). Partageable via Marketing Kit.';

--
-- Name: COLUMN restaurants.referred_by_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.restaurants.referred_by_id IS 'Restaurant parrain (FK self). NULL si inscription sans code.';

--
-- Name: COLUMN restaurants.referred_activated_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.restaurants.referred_activated_at IS 'Timestamp d''activation du parrainage (quand ce resto devient actif → crédit parrain).';

--
-- Name: search_restaurants(text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.search_restaurants(query text) RETURNS SETOF public.restaurants
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
  SELECT *
  FROM public.restaurants
  WHERE search_vector @@ plainto_tsquery('french', query)
     OR name ILIKE '%' || query || '%'
     OR city ILIKE '%' || query || '%'
     OR cuisine ILIKE '%' || query || '%'
  ORDER BY
    ts_rank(search_vector, plainto_tsquery('french', query)) DESC,
    rating DESC
  LIMIT 50;
$$;

--
-- Name: set_admin_wallet_expiration(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_admin_wallet_expiration() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
BEGIN
  IF NEW.amount > 0 THEN
    NEW.remaining_amount := NEW.amount;
    NEW.expires_at := COALESCE(NEW.expires_at, NEW.created_at + interval '3 years');
  ELSE
    NEW.remaining_amount := 0;
    NEW.expires_at := NULL;
  END IF;
  RETURN NEW;
END;
$$;

--
-- Name: set_bookable_resource_enabled(uuid, boolean); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_bookable_resource_enabled(p_resource_id uuid, p_enabled boolean) RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  v_restaurant_id UUID;
BEGIN
  SELECT restaurant_id INTO v_restaurant_id FROM bookable_resources WHERE id = p_resource_id;
  IF v_restaurant_id IS NULL THEN
    RAISE EXCEPTION 'Ressource introuvable';
  END IF;

  IF NOT (
    has_role((SELECT auth.uid()), 'admin'::app_role)
    OR EXISTS (
      SELECT 1 FROM restaurant_staff rs
      WHERE rs.user_id = (SELECT auth.uid())
        AND rs.restaurant_id = v_restaurant_id
        AND rs.status = 'actif'
    )
  ) THEN
    RAISE EXCEPTION 'Accès refusé : vous n''êtes pas staff actif de ce restaurant';
  END IF;

  UPDATE bookable_resources
  SET enabled = p_enabled, updated_at = NOW()
  WHERE id = p_resource_id;

  INSERT INTO public.action_logs (restaurant_id, user_id, member_name, action, type, details)
  VALUES (
    v_restaurant_id,
    (SELECT auth.uid()),
    '',
    CASE WHEN p_enabled THEN 'enable_bookable_resource' ELSE 'disable_bookable_resource' END,
    'admin_action',
    format('Resource id=%s %s', p_resource_id, CASE WHEN p_enabled THEN 'activée' ELSE 'désactivée' END)
  );
END;
$$;

--
-- Name: set_bookable_resource_image(uuid, text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_bookable_resource_image(p_resource_id uuid, p_image_url text) RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  v_restaurant_id UUID;
  v_clean_url TEXT;
BEGIN
  SELECT restaurant_id INTO v_restaurant_id FROM bookable_resources WHERE id = p_resource_id;
  IF v_restaurant_id IS NULL THEN
    RAISE EXCEPTION 'Ressource introuvable';
  END IF;

  IF NOT (
    has_role((SELECT auth.uid()), 'admin'::app_role)
    OR EXISTS (
      SELECT 1 FROM restaurant_staff rs
      WHERE rs.user_id = (SELECT auth.uid())
        AND rs.restaurant_id = v_restaurant_id
        AND rs.status = 'actif'
    )
  ) THEN
    RAISE EXCEPTION 'Accès refusé : vous n''êtes pas staff actif de ce restaurant';
  END IF;

  v_clean_url := NULLIF(trim(COALESCE(p_image_url, '')), '');

  UPDATE bookable_resources
  SET image_url = v_clean_url, updated_at = NOW()
  WHERE id = p_resource_id;

  INSERT INTO public.action_logs (restaurant_id, user_id, member_name, action, type, details)
  VALUES (
    v_restaurant_id,
    (SELECT auth.uid()),
    '',
    CASE WHEN v_clean_url IS NULL THEN 'clear_bookable_resource_image' ELSE 'set_bookable_resource_image' END,
    'admin_action',
    format('Resource id=%s image %s', p_resource_id, CASE WHEN v_clean_url IS NULL THEN 'retirée' ELSE 'mise à jour' END)
  );
END;
$$;

--
-- Name: set_loyalty_expiration(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_loyalty_expiration() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  _duration INTEGER;
  _tier_override INTEGER;
BEGIN
  IF NEW.points > 0 THEN
    -- 1) Client-tier override (tier_thresholds.benefit_duration_days_override)
    SELECT t.benefit_duration_days_override
      INTO _tier_override
      FROM get_client_current_tier(NEW.client_id) AS t
      LIMIT 1;

    IF _tier_override IS NOT NULL AND _tier_override > 0 THEN
      _duration := _tier_override;
    ELSE
      -- 2) Fallback: best enabled global rule (same as before)
      SELECT COALESCE(MAX(benefit_duration_days), 180) INTO _duration
      FROM public.gain_rules WHERE enabled = true;
    END IF;

    NEW.remaining_points := NEW.points;
    NEW.expires_at := NEW.earned_at + (_duration || ' days')::interval;
  ELSE
    -- Negative entries (redemptions, gifts sent) keep the old behavior.
    NEW.remaining_points := 0;
    NEW.expires_at := NULL;
  END IF;

  RETURN NEW;
END;
$$;

--
-- Name: FUNCTION set_loyalty_expiration(); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.set_loyalty_expiration() IS 'Sets expires_at on new loyalty_points rows. Uses the client''s current tier override on benefit_duration_days when present, else falls back to the global enabled gain_rule.';

--
-- Name: set_no_show_marked_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_no_show_marked_at() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
BEGIN
  -- Marquage no_show (depuis un autre status) → set timestamp + reset penalty applied
  IF NEW.status = 'no_show' AND (OLD.status IS NULL OR OLD.status <> 'no_show') THEN
    NEW.no_show_marked_at := now();
    NEW.no_show_penalty_applied_at := NULL;
  END IF;
  -- Rollback no_show → autre status : on garde no_show_marked_at (historique audit)
  -- mais on reset applied_at à NULL pour éviter qu'un futur re-no_show réapplique pas
  IF OLD.status = 'no_show' AND NEW.status <> 'no_show' THEN
    NEW.no_show_penalty_applied_at := NULL;
  END IF;
  RETURN NEW;
END;
$$;

--
-- Name: set_onboarding_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_onboarding_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  NEW.updated_at := now();
  RETURN NEW;
END;
$$;

--
-- Name: set_pcc_feedbacks_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_pcc_feedbacks_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END;
$$;

--
-- Name: set_referral_code(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_referral_code() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
BEGIN
  IF NEW.referral_code IS NULL OR NEW.referral_code = '' THEN
    NEW.referral_code := public.generate_referral_code();
  END IF;
  RETURN NEW;
END;
$$;

--
-- Name: sync_gain_rule_to_tier(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.sync_gain_rule_to_tier() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  _tier_name text;
  _benefits text;
  _period_days integer;
  _taux_pct text;
  _max_pts text;
  _min_ticket text;
  _extra text;
BEGIN
  -- Map gain_rule name to tier_threshold tier_name
  _tier_name := CASE UPPER(NEW.name)
    WHEN 'RUBY' THEN 'Ruby'
    WHEN 'SAPPHIRE' THEN 'Sapphire'
    WHEN 'EMERAUDE' THEN 'Émeraude'
    ELSE NULL
  END;

  -- If no matching tier, skip
  IF _tier_name IS NULL THEN
    RETURN NEW;
  END IF;

  -- Calculate period_days from period_value + period_type
  _period_days := CASE NEW.period_type
    WHEN 'month' THEN NEW.period_value * 30
    WHEN 'week' THEN NEW.period_value * 7
    WHEN 'day' THEN NEW.period_value
    ELSE NEW.period_value * 30
  END;

  -- Build formatted benefits string
  _taux_pct := ROUND(NEW.taux_conversion * 100)::text;
  _max_pts := TO_CHAR(NEW.max_points_par_ticket, 'FM999 999');
  _min_ticket := TO_CHAR(NEW.min_ticket, 'FM999 999');

  -- Tier-specific perks
  _extra := CASE UPPER(NEW.name)
    WHEN 'RUBY' THEN ' + Accès aux offres exclusives'
    WHEN 'SAPPHIRE' THEN ' + Priorité réservation + Remises premium'
    WHEN 'EMERAUDE' THEN ' + Expériences VIP + Conciergerie dédiée + Accès illimité'
    ELSE ''
  END;

  _benefits := 'Taux de gain : ' || _taux_pct || '% du ticket'
    || ' + Plafond : ' || _max_pts || ' pts / ticket'
    || ' + Ticket minimum : ' || _min_ticket || ' MAD'
    || _extra;

  -- Upsert into tier_thresholds
  UPDATE public.tier_thresholds
  SET
    min_spend = NEW.min_spend_monthly,
    period_days = _period_days,
    benefits = _benefits,
    updated_at = now()
  WHERE tier_name = _tier_name;

  RETURN NEW;
END;
$$;

--
-- Name: sync_rule_to_restaurants(uuid); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.sync_rule_to_restaurants(p_rule_id uuid) RETURNS integer
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  v_rule public.gain_rules%ROWTYPE;
  v_updated INTEGER := 0;
BEGIN
  IF NOT has_role(auth.uid(), 'admin'::app_role) THEN
    RAISE EXCEPTION 'not authorized';
  END IF;

  SELECT * INTO v_rule FROM public.gain_rules WHERE id = p_rule_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'gain_rule % not found', p_rule_id;
  END IF;

  WITH u AS (
    UPDATE public.restaurant_gain_rules
    SET
      name                  = v_rule.name,
      description           = v_rule.description,
      type                  = v_rule.type,
      taux_conversion       = v_rule.taux_conversion,
      min_ticket            = v_rule.min_ticket,
      max_points_par_ticket = v_rule.max_points_par_ticket,
      point_value_mad       = v_rule.point_value_mad,
      enabled               = v_rule.enabled,
      updated_at            = now()
    WHERE source_rule_id = p_rule_id
    RETURNING id
  )
  SELECT COUNT(*)::INTEGER INTO v_updated FROM u;

  RETURN v_updated;
END;
$$;

--
-- Name: tenant_branding_touch_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.tenant_branding_touch_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  NEW.updated_at := now();
  RETURN NEW;
END;
$$;

--
-- Name: trg_activate_restaurant_referral(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.trg_activate_restaurant_referral() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'public'
    AS $$
BEGIN
  -- Si le statut passe à 'actif' ET qu'il y a un parrain ET pas encore activé
  IF NEW.status = 'actif'
     AND OLD.status <> 'actif'
     AND NEW.referred_by_id IS NOT NULL
     AND NEW.referred_activated_at IS NULL
  THEN
    NEW.referred_activated_at := now();

    -- Notifier le parrain via admin_notifications (broadcast all admins too)
    INSERT INTO public.admin_notifications (
      admin_id, type, severity, title, description, target_url, metadata
    ) VALUES (
      NULL,
      'system',
      'info',
      'Nouveau parrainage activé',
      'Restaurant "' || NEW.name || '" parrainé par un autre partenaire est maintenant actif.',
      '/restaurants?highlight=' || NEW.id::text,
      jsonb_build_object(
        'referee_id', NEW.id,
        'referrer_id', NEW.referred_by_id,
        'activation_type', 'auto'
      )
    );
  END IF;

  RETURN NEW;
END;
$$;

--
-- Name: trg_no_show_impact_rating(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.trg_no_show_impact_rating() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
BEGIN
  -- Honoree : incrémenter total_honored + pending
  IF NEW.status = 'honoree' AND (OLD.status IS NULL OR OLD.status <> 'honoree') THEN
    PERFORM public.ensure_client_rating(NEW.organizer_id);
    UPDATE public.client_ratings SET
      total_honored = total_honored + 1,
      pending_total_honored = pending_total_honored + 1,
      updated_at = NOW()
    WHERE client_id = NEW.organizer_id;
  END IF;

  -- No-show : incrémenter total_no_show + pending
  IF NEW.status = 'no_show' AND (OLD.status IS NULL OR OLD.status <> 'no_show') THEN
    PERFORM public.ensure_client_rating(NEW.organizer_id);
    UPDATE public.client_ratings SET
      total_no_show = total_no_show + 1,
      pending_total_no_show = pending_total_no_show + 1,
      updated_at = NOW()
    WHERE client_id = NEW.organizer_id;
  END IF;

  RETURN NEW;
END;
$$;

--
-- Name: trg_notify_admin_support_ticket(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.trg_notify_admin_support_ticket() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $_$
DECLARE
  v_client_name text;
  v_restaurant_name text;
  v_description text;
BEGIN
  -- Lookup client name (denormalized pour perf UI + resilience si profile supprimé)
  SELECT COALESCE(NULLIF(CONCAT_WS(' ', first_name, last_name), ''), email, 'Client inconnu')
    INTO v_client_name
  FROM public.profiles
  WHERE id = NEW.client_id;

  -- Lookup restaurant name si le ticket est lié à un resto (colonne optionnelle)
  BEGIN
    EXECUTE 'SELECT name FROM public.restaurants WHERE id = $1'
    INTO v_restaurant_name
    USING NEW.restaurant_id;
  EXCEPTION WHEN undefined_column THEN
    v_restaurant_name := NULL;
  END;

  -- Build description concise
  v_description := COALESCE(v_client_name, 'Client') ||
    CASE
      WHEN v_restaurant_name IS NOT NULL THEN ' · ' || v_restaurant_name
      ELSE ''
    END ||
    ' · cat: ' || COALESCE(NEW.category, 'autre');

  -- Insert notification (broadcast à tous les admins via admin_id = NULL)
  INSERT INTO public.admin_notifications (
    admin_id, type, severity, title, description, target_url, metadata
  ) VALUES (
    NULL,                        -- NULL = broadcast all admins
    'system',                    -- type système (ticket support = cat générale)
    CASE NEW.category
      WHEN 'ticket_rejete' THEN 'warning'
      WHEN 'reservation' THEN 'warning'
      ELSE 'info'
    END,
    'Nouveau ticket support : ' || LEFT(NEW.subject, 80),
    v_description,
    '/prodesk/support?ticket=' || NEW.id::text,
    jsonb_build_object(
      'ticket_id', NEW.id,
      'client_id', NEW.client_id,
      'category', NEW.category
    )
  );

  RETURN NEW;
EXCEPTION WHEN OTHERS THEN
  -- Don't block ticket creation if notification fails
  RAISE WARNING 'Failed to notify admins of support ticket %: %', NEW.id, SQLERRM;
  RETURN NEW;
END;
$_$;

--
-- Name: FUNCTION trg_notify_admin_support_ticket(); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.trg_notify_admin_support_ticket() IS 'Insère admin_notifications à chaque nouveau support_ticket. Realtime push à la cloche admin.';

--
-- Name: trg_punch_card_on_booking_honoree(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.trg_punch_card_on_booking_honoree() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  v_resource public.bookable_resources;
  v_tenant_id UUID;
  v_activity public.punch_card_activity;
BEGIN
  IF NEW.status = 'honoree' AND (OLD.status IS NULL OR OLD.status <> 'honoree') THEN
    SELECT * INTO v_resource FROM public.bookable_resources WHERE id = NEW.resource_id;

    -- Map resource_type → activity_type (skip pour restaurant_table)
    -- C.2 (29/04) : ajout barber_chair → barber et coach_session → gym
    v_activity := CASE v_resource.resource_type
      WHEN 'padel_court'    THEN 'padel'::public.punch_card_activity
      WHEN 'spa_room'       THEN 'spa'::public.punch_card_activity
      WHEN 'golf_tee'       THEN 'golf'::public.punch_card_activity
      WHEN 'seminar_room'   THEN 'seminar'::public.punch_card_activity
      WHEN 'barber_chair'   THEN 'barber'::public.punch_card_activity   -- C.2 (29/04)
      WHEN 'coach_session'  THEN 'gym'::public.punch_card_activity      -- C.2 (29/04)
      ELSE NULL
    END;

    IF v_activity IS NULL THEN
      RETURN NEW;  -- Pas de punch card pour restaurant_table ou type futur non mappé
    END IF;

    -- Get tenant_id (resource direct ou via restaurant)
    v_tenant_id := v_resource.tenant_id;
    IF v_tenant_id IS NULL AND v_resource.restaurant_id IS NOT NULL THEN
      SELECT tenant_id INTO v_tenant_id FROM public.restaurants WHERE id = v_resource.restaurant_id;
    END IF;

    IF v_tenant_id IS NULL THEN
      RETURN NEW;  -- Pas de tenant → skip
    END IF;

    -- Upsert punch card +1 pour organizer uniquement (pas les invités)
    INSERT INTO public.loyalty_punch_cards (
      tenant_id, client_id, activity_type, count_punched, last_punched_at
    )
    VALUES (v_tenant_id, NEW.organizer_id, v_activity, 1, NOW())
    ON CONFLICT (tenant_id, client_id, activity_type) DO UPDATE
      SET count_punched = public.loyalty_punch_cards.count_punched + 1,
          last_punched_at = NOW(),
          updated_at = NOW();
  END IF;
  RETURN NEW;
END;
$$;

--
-- Name: trg_set_restaurant_referral_code(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.trg_set_restaurant_referral_code() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'public'
    AS $$
BEGIN
  IF NEW.referral_code IS NULL THEN
    NEW.referral_code := public.generate_restaurant_referral_code(NEW.city);
  END IF;
  RETURN NEW;
END;
$$;

--
-- Name: trg_set_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.trg_set_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN NEW.updated_at = NOW(); RETURN NEW; END;
$$;

--
-- Name: trg_update_client_score(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.trg_update_client_score() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
BEGIN
  -- Only recalculate when status becomes honorée or no_show
  IF NEW.status IN ('honorée', 'no_show') AND (OLD.status IS NULL OR OLD.status != NEW.status) THEN
    PERFORM public.compute_client_score(NEW.client_id);
  END IF;
  RETURN NEW;
END;
$$;

--
-- Name: unassign_rule_from_restaurants(uuid, uuid[]); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.unassign_rule_from_restaurants(p_rule_id uuid, p_restaurant_ids uuid[]) RETURNS integer
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  v_deleted INTEGER := 0;
BEGIN
  IF NOT has_role(auth.uid(), 'admin'::app_role) THEN
    RAISE EXCEPTION 'not authorized';
  END IF;

  IF p_restaurant_ids IS NULL OR array_length(p_restaurant_ids, 1) IS NULL THEN
    RETURN 0;
  END IF;

  WITH d AS (
    DELETE FROM public.restaurant_gain_rules
    WHERE source_rule_id = p_rule_id
      AND restaurant_id = ANY(p_restaurant_ids)
    RETURNING id
  )
  SELECT COUNT(*)::INTEGER INTO v_deleted FROM d;

  RETURN v_deleted;
END;
$$;

--
-- Name: update_announcement_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.update_announcement_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END $$;

--
-- Name: update_bookable_resource(uuid, text, integer, integer, integer, jsonb, public.bookable_payment_mode, jsonb, text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.update_bookable_resource(p_resource_id uuid, p_name text DEFAULT NULL::text, p_capacity integer DEFAULT NULL::integer, p_slot_duration_minutes integer DEFAULT NULL::integer, p_max_invitees integer DEFAULT NULL::integer, p_opening_hours jsonb DEFAULT NULL::jsonb, p_payment_mode public.bookable_payment_mode DEFAULT NULL::public.bookable_payment_mode, p_pricing jsonb DEFAULT NULL::jsonb, p_image_url text DEFAULT NULL::text) RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  v_restaurant_id UUID;
  v_clean_image_url TEXT;
BEGIN
  SELECT restaurant_id INTO v_restaurant_id FROM bookable_resources WHERE id = p_resource_id;
  IF v_restaurant_id IS NULL THEN
    RAISE EXCEPTION 'Ressource introuvable';
  END IF;

  IF NOT (
    has_role((SELECT auth.uid()), 'admin'::app_role)
    OR EXISTS (
      SELECT 1 FROM restaurant_staff rs
      WHERE rs.user_id = (SELECT auth.uid())
        AND rs.restaurant_id = v_restaurant_id
        AND rs.status = 'actif'
    )
  ) THEN
    RAISE EXCEPTION 'Accès refusé : vous n''êtes pas staff actif de ce restaurant';
  END IF;

  -- Validations
  IF p_name IS NOT NULL AND length(trim(p_name)) = 0 THEN
    RAISE EXCEPTION 'Le nom de la ressource ne peut pas être vide';
  END IF;
  IF p_capacity IS NOT NULL AND p_capacity < 1 THEN
    RAISE EXCEPTION 'La capacité doit être supérieure ou égale à 1';
  END IF;
  IF p_slot_duration_minutes IS NOT NULL AND p_slot_duration_minutes < 15 THEN
    RAISE EXCEPTION 'La durée d''un créneau doit être au moins 15 minutes';
  END IF;
  IF p_max_invitees IS NOT NULL AND p_max_invitees < 0 THEN
    RAISE EXCEPTION 'Le nombre maximum d''invités doit être positif ou nul';
  END IF;

  v_clean_image_url := CASE
    WHEN p_image_url IS NULL THEN NULL
    ELSE NULLIF(trim(p_image_url), '')
  END;

  UPDATE bookable_resources SET
    name                  = COALESCE(trim(p_name), name),
    capacity              = COALESCE(p_capacity, capacity),
    slot_duration_minutes = COALESCE(p_slot_duration_minutes, slot_duration_minutes),
    max_invitees          = COALESCE(p_max_invitees, max_invitees),
    opening_hours         = COALESCE(p_opening_hours, opening_hours),
    payment_mode          = COALESCE(p_payment_mode, payment_mode),
    pricing               = COALESCE(p_pricing, pricing),
    image_url             = COALESCE(v_clean_image_url, image_url),
    updated_at            = NOW()
  WHERE id = p_resource_id;

  -- Audit log (vrai schéma)
  INSERT INTO public.action_logs (restaurant_id, user_id, member_name, action, type, details)
  VALUES (
    v_restaurant_id,
    (SELECT auth.uid()),
    '',
    'update_bookable_resource',
    'admin_action',
    format('Resource id=%s mise à jour', p_resource_id)
  );
END;
$$;

--
-- Name: update_client_rating_on_reservation(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.update_client_rating_on_reservation() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  v_current_rating NUMERIC(2,1);
  v_current_honored INTEGER;
  v_current_no_show INTEGER;
  v_rating_delta NUMERIC(3,1) := 0.0;
  v_honored_delta INTEGER := 0;
  v_no_show_delta INTEGER := 0;
  v_new_rating NUMERIC(2,1);
  v_new_honored INTEGER;
  v_new_no_show INTEGER;
  v_apply_immediate boolean := false;
BEGIN
  -- Only act on UPDATE with actual status change
  IF TG_OP <> 'UPDATE' OR OLD.status IS NOT DISTINCT FROM NEW.status THEN
    RETURN NEW;
  END IF;

  -- Skip si ni l'ancien ni le nouveau status n'a d'impact rating
  IF OLD.status NOT IN ('honorée', 'no_show') AND NEW.status NOT IN ('honorée', 'no_show') THEN
    RETURN NEW;
  END IF;

  PERFORM ensure_client_rating(NEW.client_id);

  -- Safety net : promote tout pending dont le délai 48h est écoulé
  -- (au cas où le cron ne tourne plus ou serait en retard)
  UPDATE client_ratings
  SET visible_rating = COALESCE(pending_rating, visible_rating),
      visible_total_honored = COALESCE(pending_total_honored, visible_total_honored),
      visible_total_no_show = COALESCE(pending_total_no_show, visible_total_no_show),
      visible_is_new = COALESCE(pending_is_new, visible_is_new),
      pending_rating = NULL,
      pending_total_honored = NULL,
      pending_total_no_show = NULL,
      pending_is_new = NULL,
      pending_visible_at = NULL,
      updated_at = now()
  WHERE client_id = NEW.client_id
    AND pending_visible_at IS NOT NULL
    AND pending_visible_at <= now();

  -- ── Rollback effet OLD.status (si applicable) ──
  IF OLD.status = 'honorée' THEN
    v_rating_delta := v_rating_delta - 0.1;
    v_honored_delta := v_honored_delta - 1;
  ELSIF OLD.status = 'no_show' THEN
    -- Rollback uniquement si la pénalité avait été effectivement appliquée
    IF OLD.no_show_penalty_applied_at IS NOT NULL THEN
      v_rating_delta := v_rating_delta + 0.5;
      v_no_show_delta := v_no_show_delta - 1;
    END IF;
  END IF;

  -- ── Apply effet NEW.status ──
  IF NEW.status = 'honorée' THEN
    v_rating_delta := v_rating_delta + 0.1;
    v_honored_delta := v_honored_delta + 1;
  ELSIF NEW.status = 'no_show' THEN
    -- Late cancellation : pénalité immédiate (pas de contestation possible)
    -- No_show classique : différé via cron, pas d'effet ici
    IF NEW.late_cancellation = true THEN
      v_rating_delta := v_rating_delta - 0.5;
      v_no_show_delta := v_no_show_delta + 1;
      v_apply_immediate := true;
    END IF;
  END IF;

  -- Skip si pas de delta
  IF v_rating_delta = 0.0 AND v_honored_delta = 0 AND v_no_show_delta = 0 THEN
    RETURN NEW;
  END IF;

  -- Get current values pour calculer les nouvelles
  SELECT rating, total_honored, total_no_show
    INTO v_current_rating, v_current_honored, v_current_no_show
  FROM client_ratings WHERE client_id = NEW.client_id;

  v_new_rating := GREATEST(0.0, LEAST(5.0, v_current_rating + v_rating_delta));
  v_new_honored := GREATEST(0, v_current_honored + v_honored_delta);
  v_new_no_show := GREATEST(0, v_current_no_show + v_no_show_delta);

  -- Cas 1 : late_cancellation OU rollback (no_show → honorée) : visible IMMÉDIAT
  --   Le rollback est un cas spécial : si admin/resto corrige un no_show en
  --   honorée, le client doit voir la correction tout de suite (cohérent).
  --   On considère "rollback" tout cas où OLD était no_show (le trigger ne
  --   fait rien sinon).
  IF v_apply_immediate OR (OLD.status = 'no_show' AND OLD.no_show_penalty_applied_at IS NOT NULL) THEN
    UPDATE client_ratings
    SET is_new = false,
        rating = v_new_rating,
        total_honored = v_new_honored,
        total_no_show = v_new_no_show,
        -- Visible immédiat
        visible_rating = v_new_rating,
        visible_total_honored = v_new_honored,
        visible_total_no_show = v_new_no_show,
        visible_is_new = false,
        -- Reset pending
        pending_rating = NULL,
        pending_total_honored = NULL,
        pending_total_no_show = NULL,
        pending_is_new = NULL,
        pending_visible_at = NULL,
        updated_at = now()
    WHERE client_id = NEW.client_id;
  ELSE
    -- Cas 2 : honorée classique → pending 48h (l'user voit dans 48h)
    UPDATE client_ratings
    SET is_new = false,
        rating = v_new_rating,
        total_honored = v_new_honored,
        total_no_show = v_new_no_show,
        -- Pending 48h
        pending_rating = v_new_rating,
        pending_total_honored = v_new_honored,
        pending_total_no_show = v_new_no_show,
        pending_is_new = false,
        pending_visible_at = now() + INTERVAL '48 hours',
        updated_at = now()
    WHERE client_id = NEW.client_id;
  END IF;

  -- Marquer no_show_penalty_applied_at si late_cancellation (cron skippera)
  IF v_apply_immediate THEN
    UPDATE reservations
      SET no_show_penalty_applied_at = now()
      WHERE id = NEW.id;
  END IF;

  RETURN NEW;
END;
$$;

--
-- Name: FUNCTION update_client_rating_on_reservation(); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.update_client_rating_on_reservation() IS 'Idempotent + delayed-visibility aware. honorée → pending 48h. no_show classique → différé cron. late_cancellation → visible immédiat. Rollback (correction no_show → honorée) → visible immédiat.';

--
-- Name: update_pcc_family_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.update_pcc_family_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  NEW.updated_at := NOW();
  RETURN NEW;
END $$;

--
-- Name: update_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.update_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'public'
    AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$;

--
-- Name: validate_gain_rule(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.validate_gain_rule() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
BEGIN
  IF NEW.taux_conversion <= 0 OR NEW.taux_conversion > 1 THEN
    RAISE EXCEPTION 'Le taux de conversion doit être entre 0.01 et 1.0 (1%% à 100%%)';
  END IF;
  IF NEW.min_ticket < 0 THEN
    RAISE EXCEPTION 'Le ticket minimum ne peut pas être négatif';
  END IF;
  IF NEW.max_points_par_ticket < 1 THEN
    RAISE EXCEPTION 'Le plafond de points doit être au moins 1';
  END IF;
  RETURN NEW;
END;
$$;

--
-- Name: verify_redemption_otp(uuid, text, uuid, uuid, integer, text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.verify_redemption_otp(p_request_id uuid, p_code text, p_client_id uuid, p_restaurant_id uuid, p_points integer, p_ticket_ref text) RETURNS text
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  v_row redemption_otp_requests;
  v_expected TEXT;
BEGIN
  SELECT * INTO v_row FROM redemption_otp_requests WHERE id = p_request_id;
  IF NOT FOUND THEN
    RETURN 'not_found';
  END IF;

  IF v_row.status = 'consumed' THEN
    RETURN 'already_consumed';
  END IF;

  IF v_row.expires_at <= now() THEN
    UPDATE redemption_otp_requests SET status = 'expired' WHERE id = p_request_id AND status = 'pending';
    RETURN 'expired';
  END IF;

  IF v_row.attempts >= 3 THEN
    UPDATE redemption_otp_requests SET status = 'cancelled' WHERE id = p_request_id AND status = 'pending';
    RETURN 'too_many_attempts';
  END IF;

  -- Coherence: request must match the current scan (client + restaurant + points not higher)
  IF v_row.client_id <> p_client_id OR v_row.restaurant_id <> p_restaurant_id THEN
    RETURN 'mismatch';
  END IF;
  IF p_points > v_row.points_requested THEN
    RETURN 'mismatch';
  END IF;

  v_expected := encode(digest(p_code, 'sha256'), 'hex');
  IF v_expected <> v_row.code_hash THEN
    UPDATE redemption_otp_requests
    SET attempts = attempts + 1
    WHERE id = p_request_id;
    RETURN 'wrong_code';
  END IF;

  UPDATE redemption_otp_requests
  SET status = 'consumed',
      consumed_at = now(),
      consumed_for_ticket_ref = p_ticket_ref
  WHERE id = p_request_id;

  RETURN 'ok';
END;
$$;

--
-- Name: FUNCTION verify_redemption_otp(p_request_id uuid, p_code text, p_client_id uuid, p_restaurant_id uuid, p_points integer, p_ticket_ref text); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.verify_redemption_otp(p_request_id uuid, p_code text, p_client_id uuid, p_restaurant_id uuid, p_points integer, p_ticket_ref text) IS 'Verifies an OTP code against a request; marks it consumed on success. Returns a diagnosis string.';

--
-- Name: action_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.action_logs (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    restaurant_id uuid NOT NULL,
    user_id uuid NOT NULL,
    member_name text DEFAULT ''::text NOT NULL,
    action text NOT NULL,
    type text DEFAULT 'action'::text NOT NULL,
    details text DEFAULT ''::text,
    ip text DEFAULT ''::text,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: admin_audit_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.admin_audit_log (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    actor_id uuid,
    actor_email text DEFAULT ''::text NOT NULL,
    action text NOT NULL,
    entity_type text,
    entity_id uuid,
    entity_label text,
    diff jsonb,
    metadata jsonb,
    ip text,
    user_agent text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    tenant_id uuid
);

--
-- Name: TABLE admin_audit_log; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.admin_audit_log IS 'Journal immutable des actions admin globales. RLS admin-only.';

--
-- Name: admin_notifications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.admin_notifications (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    admin_id uuid,
    type text NOT NULL,
    severity text DEFAULT 'info'::text NOT NULL,
    title text NOT NULL,
    description text,
    target_url text,
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
    read_at timestamp with time zone,
    dismissed_at timestamp with time zone,
    CONSTRAINT admin_notifications_severity_check CHECK ((severity = ANY (ARRAY['info'::text, 'warning'::text, 'danger'::text]))),
    CONSTRAINT admin_notifications_type_check CHECK ((type = ANY (ARRAY['rule_request'::text, 'elite_application'::text, 'suspicious_redemption'::text, 'restitution_overdue'::text, 'restos_without_rule'::text, 'loyalty_alert'::text, 'system'::text])))
);

--
-- Name: TABLE admin_notifications; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.admin_notifications IS 'In-app notification center for admins. Fed by triggers on source tables (rule_requests, elite_applications, redemption_events) and by the loyalty alerts cron.';

--
-- Name: admin_wallet_transactions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.admin_wallet_transactions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    admin_id uuid NOT NULL,
    restaurant_id uuid,
    amount integer DEFAULT 0 NOT NULL,
    reason text DEFAULT 'creation_restaurant'::text NOT NULL,
    details text DEFAULT ''::text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    expires_at timestamp with time zone DEFAULT (now() + '3 years'::interval),
    remaining_amount integer DEFAULT 0 NOT NULL
);

ALTER TABLE ONLY public.admin_wallet_transactions REPLICA IDENTITY FULL;

--
-- Name: ai_usage; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ai_usage (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    prompt_count integer DEFAULT 0 NOT NULL,
    last_prompt_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: ai_usage_bypass; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ai_usage_bypass (
    user_id uuid NOT NULL,
    reason text,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: announcement_reads; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.announcement_reads (
    announcement_id uuid NOT NULL,
    user_id uuid NOT NULL,
    body_version_read integer DEFAULT 1 NOT NULL,
    read_at timestamp with time zone DEFAULT now() NOT NULL
);

ALTER TABLE ONLY public.announcement_reads REPLICA IDENTITY FULL;

--
-- Name: app_documents; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.app_documents (
    id text DEFAULT 'plan'::text NOT NULL,
    content text DEFAULT ''::text NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    version text DEFAULT '1.0.0'::text NOT NULL,
    updated_by uuid
);

--
-- Name: bookable_resources; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.bookable_resources (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid,
    restaurant_id uuid,
    resource_type public.bookable_resource_type NOT NULL,
    name text NOT NULL,
    capacity integer DEFAULT 1 NOT NULL,
    slot_duration_minutes integer DEFAULT 60 NOT NULL,
    max_invitees integer DEFAULT 0 NOT NULL,
    opening_hours jsonb,
    payment_mode public.bookable_payment_mode DEFAULT 'on_site'::public.bookable_payment_mode NOT NULL,
    pricing jsonb,
    enabled boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    image_url text,
    CONSTRAINT bookable_resources_capacity_check CHECK ((capacity >= 1)),
    CONSTRAINT bookable_resources_max_invitees_check CHECK ((max_invitees >= 0)),
    CONSTRAINT bookable_resources_slot_duration_minutes_check CHECK ((slot_duration_minutes >= 15))
);

--
-- Name: TABLE bookable_resources; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.bookable_resources IS 'Ressources réservables (padel court, spa room, golf tee, seminar room).
   Modèle générique permettant à PCC et futurs tenants de gérer des
   créneaux sur ressources non-resto. Isolé de la table reservations.
   Sprint PCC Phase 6 (24/04/2026).';

--
-- Name: COLUMN bookable_resources.image_url; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.bookable_resources.image_url IS 'URL de l''image hero (Unsplash, Storage, etc.). Si NULL, fallback sur mapping hardcodé pcc-activity-photos.ts par name. Sprint B.1 round 2 (29/04).';

--
-- Name: booking_rules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.booking_rules (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    restaurant_id uuid NOT NULL,
    name text NOT NULL,
    description text DEFAULT ''::text,
    category text DEFAULT 'confirmation'::text NOT NULL,
    value text DEFAULT ''::text NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: chat_messages; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.chat_messages (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    ticket_id uuid NOT NULL,
    user_id uuid NOT NULL,
    role text DEFAULT 'user'::text NOT NULL,
    content text DEFAULT ''::text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: client_ratings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.client_ratings (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    rating numeric(2,1) DEFAULT 5.0 NOT NULL,
    total_honored integer DEFAULT 0 NOT NULL,
    total_no_show integer DEFAULT 0 NOT NULL,
    is_new boolean DEFAULT true NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    visible_rating numeric(2,1) DEFAULT 5.0 NOT NULL,
    visible_total_honored integer DEFAULT 0 NOT NULL,
    visible_total_no_show integer DEFAULT 0 NOT NULL,
    visible_is_new boolean DEFAULT true NOT NULL,
    pending_rating numeric(2,1),
    pending_total_honored integer,
    pending_total_no_show integer,
    pending_is_new boolean,
    pending_visible_at timestamp with time zone,
    CONSTRAINT client_ratings_rating_check CHECK (((rating >= 0.0) AND (rating <= 5.0)))
);

--
-- Name: COLUMN client_ratings.rating; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.client_ratings.rating IS 'Current rating (fresh). Visible to restaurants immediately. Clients see visible_rating.';

--
-- Name: COLUMN client_ratings.visible_rating; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.client_ratings.visible_rating IS 'Rating visible to the client. Frozen for 48h after each status update.';

--
-- Name: COLUMN client_ratings.pending_rating; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.client_ratings.pending_rating IS 'New rating awaiting 48h delay before being visible to the client.';

--
-- Name: COLUMN client_ratings.pending_visible_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.client_ratings.pending_visible_at IS 'Timestamp when pending_rating becomes visible to the client (48h after update).';

--
-- Name: client_score_config; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.client_score_config (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    min_reservations integer DEFAULT 3 NOT NULL,
    seuil_excellent numeric DEFAULT 95 NOT NULL,
    seuil_fiable numeric DEFAULT 80 NOT NULL,
    seuil_moyen numeric DEFAULT 60 NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    score_initial numeric DEFAULT 5.0 NOT NULL,
    penalite_no_show numeric DEFAULT 0.1 NOT NULL,
    honorees_pour_remonter integer DEFAULT 5 NOT NULL,
    gain_par_palier numeric DEFAULT 0.1 NOT NULL,
    fenetre_mois integer DEFAULT 6 NOT NULL
);

--
-- Name: client_visible_ratings; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.client_visible_ratings WITH (security_invoker='true') AS
 SELECT id,
    client_id,
        CASE
            WHEN ((pending_visible_at IS NOT NULL) AND (pending_visible_at <= now())) THEN pending_rating
            ELSE visible_rating
        END AS rating,
        CASE
            WHEN ((pending_visible_at IS NOT NULL) AND (pending_visible_at <= now())) THEN pending_total_honored
            ELSE visible_total_honored
        END AS total_honored,
        CASE
            WHEN ((pending_visible_at IS NOT NULL) AND (pending_visible_at <= now())) THEN pending_total_no_show
            ELSE visible_total_no_show
        END AS total_no_show,
        CASE
            WHEN ((pending_visible_at IS NOT NULL) AND (pending_visible_at <= now())) THEN pending_is_new
            ELSE visible_is_new
        END AS is_new,
    updated_at,
    ((pending_visible_at IS NOT NULL) AND (pending_visible_at > now())) AS has_pending,
    pending_visible_at
   FROM public.client_ratings cr;

--
-- Name: company_settings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.company_settings (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    raison_sociale text DEFAULT 'OneClick SAS'::text NOT NULL,
    forme_juridique text DEFAULT 'SAS'::text,
    capital_social text DEFAULT '100 000 MAD'::text,
    adresse text DEFAULT ''::text,
    ville text DEFAULT 'Casablanca'::text,
    pays text DEFAULT 'Maroc'::text,
    telephone text DEFAULT ''::text,
    email text DEFAULT ''::text,
    site_web text DEFAULT ''::text,
    numero_ice text DEFAULT ''::text,
    numero_if text DEFAULT ''::text,
    numero_rc text DEFAULT ''::text,
    numero_cnss text DEFAULT ''::text,
    rib text DEFAULT ''::text,
    banque text DEFAULT ''::text,
    logo_url text DEFAULT ''::text,
    tva_rate numeric DEFAULT 20 NOT NULL,
    payment_delay_days integer DEFAULT 5 NOT NULL,
    invoice_prefix text DEFAULT 'OC-HI'::text NOT NULL,
    invoice_footer_text text DEFAULT 'Merci pour votre confiance.'::text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    numero_tp text
);

--
-- Name: contact_import_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.contact_import_events (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    imported_at timestamp with time zone DEFAULT now() NOT NULL,
    phone_count integer DEFAULT 0 NOT NULL,
    matched_count integer DEFAULT 0 NOT NULL
);

--
-- Name: contract_disabled_articles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.contract_disabled_articles (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    contract_id uuid NOT NULL,
    article_id uuid NOT NULL,
    disabled_by uuid,
    reason text DEFAULT ''::text,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: contract_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.contract_history (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    contract_id uuid NOT NULL,
    field_changed text NOT NULL,
    old_value text,
    new_value text,
    changed_by uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: contract_number_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.contract_number_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: contract_template_articles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.contract_template_articles (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    template_id uuid NOT NULL,
    article_number integer DEFAULT 1 NOT NULL,
    title text NOT NULL,
    content text DEFAULT ''::text NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: contract_templates; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.contract_templates (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name text NOT NULL,
    version text DEFAULT '1.0'::text NOT NULL,
    preamble text DEFAULT ''::text NOT NULL,
    footer text DEFAULT ''::text NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    tenant_id uuid
);

--
-- Name: TABLE contract_templates; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.contract_templates IS 'Template de contrat partenaire OneClick. Version 3.0 (20/04/2026) — ajout articles 23/24/25 (RGPD, Confidentialité, Audit) + clean préambule avec placeholders COMPANY_* résolus depuis company_settings.';

--
-- Name: custom_roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.custom_roles (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name text NOT NULL,
    description text DEFAULT ''::text,
    permissions jsonb DEFAULT '[]'::jsonb NOT NULL,
    created_by uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: device_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.device_tokens (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    token text NOT NULL,
    platform text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    app_id text DEFAULT 'win'::text,
    CONSTRAINT device_tokens_platform_check CHECK ((platform = ANY (ARRAY['android'::text, 'ios'::text, 'web'::text])))
);

--
-- Name: COLUMN device_tokens.app_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.device_tokens.app_id IS 'App identifier: win, store, group, admin';

--
-- Name: document_versions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.document_versions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    document_id text DEFAULT 'plan'::text NOT NULL,
    version text NOT NULL,
    content text DEFAULT ''::text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    notes text DEFAULT ''::text
);

--
-- Name: elite_applications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.elite_applications (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    full_name text DEFAULT ''::text NOT NULL,
    email text DEFAULT ''::text NOT NULL,
    phone text DEFAULT ''::text,
    motivation text DEFAULT ''::text NOT NULL,
    preferred_tier text DEFAULT 'Sapphire'::text NOT NULL,
    sponsor_user_id uuid,
    sponsor_name text,
    status text DEFAULT 'en_attente'::text NOT NULL,
    admin_note text DEFAULT ''::text,
    reviewed_by uuid,
    reviewed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    age integer,
    profession text DEFAULT ''::text,
    company text DEFAULT ''::text,
    interests text[] DEFAULT '{}'::text[],
    has_other_clubs boolean DEFAULT false,
    other_clubs_details text DEFAULT ''::text,
    city text DEFAULT ''::text,
    annual_dining_budget text DEFAULT ''::text,
    referral_source text DEFAULT ''::text
);

--
-- Name: elite_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.elite_events (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    restaurant_id uuid,
    title text NOT NULL,
    description text,
    event_date date NOT NULL,
    event_time text,
    location text,
    max_places integer DEFAULT 50,
    remaining_places integer DEFAULT 50,
    min_tier text DEFAULT 'Ruby'::text,
    image text,
    is_active boolean DEFAULT true,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: elite_rsvps; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.elite_rsvps (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    event_id uuid NOT NULL,
    user_id uuid NOT NULL,
    status text DEFAULT 'confirmed'::text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT elite_rsvps_status_check CHECK ((status = ANY (ARRAY['confirmed'::text, 'cancelled'::text, 'waitlist'::text])))
);

--
-- Name: email_bounces; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.email_bounces (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    email text NOT NULL,
    bounce_type text NOT NULL,
    bounce_reason text,
    is_suppressed boolean DEFAULT false NOT NULL,
    last_bounced_at timestamp with time zone DEFAULT now() NOT NULL,
    bounce_count integer DEFAULT 1 NOT NULL,
    source_ef text,
    raw_event jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT email_bounces_bounce_type_check CHECK ((bounce_type = ANY (ARRAY['permanent'::text, 'transient'::text, 'complaint'::text])))
);

--
-- Name: TABLE email_bounces; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.email_bounces IS 'Tracking bounces emails Resend. Permet suppression list pre-send pour eviter retry permanent bounce.';

--
-- Name: COLUMN email_bounces.bounce_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.email_bounces.bounce_type IS 'permanent (mailbox inexistante, domaine invalide), transient (server down, quota), complaint (spam reported).';

--
-- Name: COLUMN email_bounces.is_suppressed; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.email_bounces.is_suppressed IS 'TRUE si permanent bounce ou complaint. Bloque tous les futurs sends vers cette adresse.';

--
-- Name: event_rsvps; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.event_rsvps (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    event_id uuid NOT NULL,
    user_id uuid NOT NULL,
    status text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT event_rsvps_status_check CHECK ((status = ANY (ARRAY['attending'::text, 'maybe'::text, 'not_attending'::text])))
);

--
-- Name: expired_points; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.expired_points (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    restaurant_id uuid NOT NULL,
    original_point_id uuid NOT NULL,
    points_expired integer DEFAULT 0 NOT NULL,
    earned_at timestamp with time zone NOT NULL,
    expired_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: explore_featured; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.explore_featured (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    restaurant_id uuid NOT NULL,
    "position" integer DEFAULT 1 NOT NULL,
    starts_at timestamp with time zone DEFAULT now() NOT NULL,
    expires_at timestamp with time zone DEFAULT (now() + '30 days'::interval) NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    label text DEFAULT ''::text,
    notes text DEFAULT ''::text,
    created_by uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: fraud_alerts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.fraud_alerts (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    type text DEFAULT 'doublon'::text NOT NULL,
    severity text DEFAULT 'moyenne'::text NOT NULL,
    restaurant_id uuid,
    client_id uuid,
    description text DEFAULT ''::text NOT NULL,
    status text DEFAULT 'ouverte'::text NOT NULL,
    montant numeric,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: friend_group_members; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.friend_group_members (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    group_id uuid NOT NULL,
    friend_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: friend_groups; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.friend_groups (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    owner_id uuid NOT NULL,
    name text NOT NULL,
    emoji text DEFAULT '👥'::text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: friendships; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.friendships (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    requester_id uuid NOT NULL,
    addressee_id uuid NOT NULL,
    status text DEFAULT 'pending'::text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT friendships_status_check CHECK ((status = ANY (ARRAY['pending'::text, 'accepted'::text, 'refused'::text])))
);

--
-- Name: gain_rule_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.gain_rule_requests (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    restaurant_id uuid NOT NULL,
    requested_by uuid NOT NULL,
    name text NOT NULL,
    description text DEFAULT ''::text,
    type text DEFAULT 'standard'::text NOT NULL,
    taux_conversion numeric DEFAULT 0.1 NOT NULL,
    min_ticket numeric DEFAULT 50 NOT NULL,
    max_points_par_ticket integer DEFAULT 500 NOT NULL,
    status text DEFAULT 'en_attente'::text NOT NULL,
    rejection_reason text,
    reviewed_by uuid,
    reviewed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: gain_rules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.gain_rules (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name text NOT NULL,
    description text DEFAULT ''::text,
    type text DEFAULT 'standard'::text NOT NULL,
    taux_conversion numeric DEFAULT 0.1 NOT NULL,
    min_ticket numeric DEFAULT 50 NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    max_points_par_ticket integer DEFAULT 500 NOT NULL,
    period_type text DEFAULT 'month'::text NOT NULL,
    period_value integer DEFAULT 30 NOT NULL,
    benefit_duration_days integer DEFAULT 90 NOT NULL,
    min_spend_monthly numeric DEFAULT 0 NOT NULL,
    point_value_mad numeric(6,2) DEFAULT 1.00 NOT NULL,
    max_redemption_per_24h integer DEFAULT 500 NOT NULL,
    max_redemption_ratio_pct numeric(5,2) DEFAULT 80 NOT NULL,
    otp_required_above_pts integer DEFAULT 200 NOT NULL,
    otp_required_above_ratio_pct numeric(5,2) DEFAULT 50 NOT NULL,
    tenant_id uuid,
    CONSTRAINT gain_rules_max_redemption_per_24h_check CHECK ((max_redemption_per_24h > 0)),
    CONSTRAINT gain_rules_max_redemption_ratio_pct_check CHECK (((max_redemption_ratio_pct > (0)::numeric) AND (max_redemption_ratio_pct <= (100)::numeric))),
    CONSTRAINT gain_rules_otp_required_above_pts_check CHECK ((otp_required_above_pts > 0)),
    CONSTRAINT gain_rules_otp_required_above_ratio_pct_check CHECK (((otp_required_above_ratio_pct > (0)::numeric) AND (otp_required_above_ratio_pct <= (100)::numeric))),
    CONSTRAINT gain_rules_point_value_mad_check CHECK ((point_value_mad > (0)::numeric))
);

--
-- Name: TABLE gain_rules; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.gain_rules IS 'Loyalty rules. One "Standard" row is the global default applied to any restaurant without an override in restaurant_gain_rules.';

--
-- Name: COLUMN gain_rules.point_value_mad; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.gain_rules.point_value_mad IS 'Monetary value of 1 point when redeemed (MAD). Default 1.00.';

--
-- Name: COLUMN gain_rules.max_redemption_per_24h; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.gain_rules.max_redemption_per_24h IS 'Max points a single client can redeem over a rolling 24h window.';

--
-- Name: COLUMN gain_rules.max_redemption_ratio_pct; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.gain_rules.max_redemption_ratio_pct IS 'Max share (%) of the ticket amount that can be paid in redeemed points.';

--
-- Name: COLUMN gain_rules.otp_required_above_pts; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.gain_rules.otp_required_above_pts IS 'Threshold in points above which an OTP is required to validate a redemption.';

--
-- Name: COLUMN gain_rules.otp_required_above_ratio_pct; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.gain_rules.otp_required_above_ratio_pct IS 'Threshold (% of ticket) above which an OTP is required.';

--
-- Name: invoice_lines; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.invoice_lines (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    invoice_id uuid NOT NULL,
    label text DEFAULT ''::text NOT NULL,
    description text,
    quantity numeric DEFAULT 1 NOT NULL,
    unit_price_ht numeric DEFAULT 0 NOT NULL,
    total_ht numeric GENERATED ALWAYS AS ((quantity * unit_price_ht)) STORED,
    sort_order integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: invoice_number_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.invoice_number_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: lifecycle_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lifecycle_events (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    restaurant_id uuid NOT NULL,
    event text DEFAULT 'inscription'::text NOT NULL,
    actor text DEFAULT ''::text NOT NULL,
    details text DEFAULT ''::text,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: loyalty_plafonds; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.loyalty_plafonds (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name text NOT NULL,
    description text DEFAULT ''::text,
    value numeric DEFAULT 0 NOT NULL,
    unit text DEFAULT 'points'::text NOT NULL,
    scope text DEFAULT 'client'::text NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: loyalty_points; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.loyalty_points (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    restaurant_id uuid NOT NULL,
    points integer DEFAULT 0 NOT NULL,
    reason text DEFAULT 'reservation'::text,
    earned_at timestamp with time zone DEFAULT now() NOT NULL,
    amount_ttc numeric DEFAULT 0,
    credited_by uuid,
    expires_at timestamp with time zone,
    remaining_points integer DEFAULT 0,
    notified_7d_at timestamp with time zone,
    notified_1d_at timestamp with time zone
);

--
-- Name: COLUMN loyalty_points.notified_7d_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.loyalty_points.notified_7d_at IS 'When the client was warned about this row expiring in 7 days (NULL = not yet).';

--
-- Name: COLUMN loyalty_points.notified_1d_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.loyalty_points.notified_1d_at IS 'When the client received a last-call warning (J-1). NULL = not yet.';

--
-- Name: loyalty_punch_cards; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.loyalty_punch_cards (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    client_id uuid NOT NULL,
    activity_type public.punch_card_activity NOT NULL,
    count_punched integer DEFAULT 0 NOT NULL,
    threshold integer DEFAULT 10 NOT NULL,
    redeemed_count integer DEFAULT 0 NOT NULL,
    last_punched_at timestamp with time zone,
    last_redeemed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT loyalty_punch_cards_count_punched_check CHECK ((count_punched >= 0)),
    CONSTRAINT loyalty_punch_cards_redeemed_count_check CHECK ((redeemed_count >= 0)),
    CONSTRAINT loyalty_punch_cards_threshold_check CHECK ((threshold >= 1))
);

ALTER TABLE ONLY public.loyalty_punch_cards REPLICA IDENTITY FULL;

--
-- Name: TABLE loyalty_punch_cards; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.loyalty_punch_cards IS 'Cartes de fidélité 10/1 par activité (padel, spa, golf, seminar).
   1 ligne par (tenant × client × activity). Auto-incrémentée par trigger
   sur booking honoree. Redeem manuel par staff via redeem_punch_card RPC.
   Seul l''organizer du booking gagne le punch (pas les invités). Sprint PCC Phase 6.';

--
-- Name: monitor_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.monitor_logs (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    source text NOT NULL,
    event_type text NOT NULL,
    status text DEFAULT 'info'::text,
    user_id uuid,
    platform text,
    metadata jsonb DEFAULT '{}'::jsonb,
    error_message text,
    duration_ms integer,
    ip_address text
);

--
-- Name: profiles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.profiles (
    id uuid NOT NULL,
    first_name text DEFAULT ''::text NOT NULL,
    last_name text DEFAULT ''::text NOT NULL,
    phone text,
    city text,
    avatar_url text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    referral_code text,
    reliability_score numeric(3,1),
    score_updated_at timestamp with time zone,
    email text,
    tenant_group_id uuid,
    tenant_id uuid,
    community_cover_url text,
    allergens text[] DEFAULT '{}'::text[] NOT NULL,
    language text
);

--
-- Name: COLUMN profiles.allergens; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.profiles.allergens IS 'Allergenes EU 1169/2011 Annex II : gluten lactose peanuts tree_nuts eggs soy fish shellfish sesame mustard celery sulfites lupin molluscs.';

--
-- Name: COLUMN profiles.language; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.profiles.language IS 'i18n V1 OneClick Win — preference langue UI (fr|en|ar). NULL = fallback FR (comportement legacy preserve). Validation cote frontend uniquement (pas de CHECK constraint pour flexibilite future).';

--
-- Name: reservation_guests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reservation_guests (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    reservation_id uuid NOT NULL,
    invited_by uuid NOT NULL,
    guest_name text,
    guest_phone text NOT NULL,
    guest_user_id uuid,
    status text DEFAULT 'invité'::text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    seen_by_host boolean DEFAULT true NOT NULL
);

--
-- Name: reservations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reservations (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    restaurant_id uuid NOT NULL,
    date date NOT NULL,
    heure text NOT NULL,
    couverts integer DEFAULT 2 NOT NULL,
    zone text,
    table_num integer,
    service text DEFAULT 'dîner'::text NOT NULL,
    status public.reservation_status DEFAULT 'demandée'::public.reservation_status NOT NULL,
    notes text DEFAULT ''::text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    refusal_reason text,
    proposed_date date,
    proposed_heure text,
    proposal_expires_at timestamp with time zone,
    cancellation_reason text,
    no_show_marked_at timestamp with time zone,
    no_show_penalty_applied_at timestamp with time zone,
    late_cancellation boolean DEFAULT false NOT NULL,
    reminder_j1_sent_at timestamp with time zone,
    reminder_h2_sent_at timestamp with time zone
);

--
-- Name: COLUMN reservations.no_show_marked_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.reservations.no_show_marked_at IS 'Timestamp du marquage no_show (set par trigger). Sert au cron pénalité différée 48h.';

--
-- Name: COLUMN reservations.no_show_penalty_applied_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.reservations.no_show_penalty_applied_at IS 'NULL = pénalité (-0.5 rating, +1 total_no_show) pas encore appliquée. Sinon, timestamp d''application par le cron.';

--
-- Name: COLUMN reservations.late_cancellation; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.reservations.late_cancellation IS 'true si le client a annulé < 1h avant la résa (contre proposed_date/heure si contre_proposition). Force status=no_show + pénalité immédiate + contestation impossible.';

--
-- Name: mv_reservations_summary; Type: MATERIALIZED VIEW; Schema: public; Owner: -
--

CREATE MATERIALIZED VIEW public.mv_reservations_summary AS
 SELECT r.id,
    r.restaurant_id,
    r.client_id,
    r.date,
    r.heure,
    r.couverts,
    r.status,
    r.service,
    r.zone,
    r.notes,
    r.created_at,
    rest.name AS restaurant_name,
    rest.city AS restaurant_city,
    rest.image AS restaurant_image,
    p.first_name AS client_first_name,
    p.last_name AS client_last_name,
    p.phone AS client_phone,
    COALESCE(gc.guest_count, (0)::bigint) AS guest_count
   FROM (((public.reservations r
     JOIN public.restaurants rest ON ((r.restaurant_id = rest.id)))
     LEFT JOIN public.profiles p ON ((r.client_id = p.id)))
     LEFT JOIN ( SELECT reservation_guests.reservation_id,
            count(*) AS guest_count
           FROM public.reservation_guests
          GROUP BY reservation_guests.reservation_id) gc ON ((gc.reservation_id = r.id)))
  WITH NO DATA;

--
-- Name: no_show_disputes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.no_show_disputes (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    reservation_id uuid NOT NULL,
    client_id uuid NOT NULL,
    description text NOT NULL,
    photo_url text,
    is_recontestation boolean DEFAULT false NOT NULL,
    status text DEFAULT 'pending'::text NOT NULL,
    escalation_phase text DEFAULT 'resto'::text NOT NULL,
    resolution_note text,
    resolved_at timestamp with time zone,
    resolved_by uuid,
    support_ticket_id uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT no_show_disputes_check CHECK (((NOT is_recontestation) OR (photo_url IS NOT NULL))),
    CONSTRAINT no_show_disputes_description_check CHECK (((length(description) >= 5) AND (length(description) <= 2000))),
    CONSTRAINT no_show_disputes_escalation_phase_check CHECK ((escalation_phase = ANY (ARRAY['resto'::text, 'support'::text]))),
    CONSTRAINT no_show_disputes_status_check CHECK ((status = ANY (ARRAY['pending'::text, 'accepted'::text, 'refused'::text])))
);

--
-- Name: TABLE no_show_disputes; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.no_show_disputes IS 'Contestations de no_show par les clients. Workflow 0-1h (resto) → 1-48h (admin/support) → 48h+ (pénalité auto). Cf migration 20260421110000.';

--
-- Name: notifications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notifications (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    title text NOT NULL,
    message text DEFAULT ''::text NOT NULL,
    type text DEFAULT 'info'::text NOT NULL,
    read boolean DEFAULT false NOT NULL,
    link text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    restaurant_id uuid
);

--
-- Name: offer_impressions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.offer_impressions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    offer_id uuid NOT NULL,
    user_id uuid NOT NULL,
    viewed_at timestamp with time zone DEFAULT now() NOT NULL,
    source text DEFAULT 'moments'::text NOT NULL
);

--
-- Name: offers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.offers (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    restaurant_id uuid NOT NULL,
    type text DEFAULT 'promo'::text NOT NULL,
    title text NOT NULL,
    description text,
    image text DEFAULT '🎁'::text,
    pts integer DEFAULT 0 NOT NULL,
    expires_at timestamp with time zone DEFAULT (now() + '30 days'::interval) NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    segments text[] DEFAULT '{tous}'::text[] NOT NULL,
    push_notify boolean DEFAULT true NOT NULL,
    campaign_id uuid,
    starts_at timestamp with time zone DEFAULT now() NOT NULL,
    tenant_id uuid,
    CONSTRAINT offers_type_check CHECK ((type = ANY (ARRAY['promo'::text, 'bonus'::text, 'reco'::text, 'plat_du_jour'::text, 'offre_flash'::text, 'evenement'::text, 'happy_hour'::text])))
);

--
-- Name: onboarding_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.onboarding_requests (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    status text DEFAULT 'en_attente'::text NOT NULL,
    restaurant_name text NOT NULL,
    city text NOT NULL,
    address text NOT NULL,
    phone text NOT NULL,
    cuisine text,
    budget text,
    description text,
    first_name text NOT NULL,
    last_name text NOT NULL,
    email text NOT NULL,
    contact_phone text NOT NULL,
    role text NOT NULL,
    ice text NOT NULL,
    if_number text,
    rc text,
    patente text,
    capacity integer NOT NULL,
    services text[] NOT NULL,
    reviewed_by uuid,
    reviewed_at timestamp with time zone,
    rejection_reason text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    tenant_id uuid
);

--
-- Name: COLUMN onboarding_requests.tenant_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.onboarding_requests.tenant_id IS 'Tenant whitelabel d''où vient la demande (ma.homu.store → tenant HOMU).
   NULL = demande via OneClick par défaut.
   Sprint 9 whitelabel onboarding.';

--
-- Name: oneclick_hi_invoices; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.oneclick_hi_invoices (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    restaurant_id uuid NOT NULL,
    period_month date NOT NULL,
    total_ca numeric DEFAULT 0 NOT NULL,
    credit_3pct numeric DEFAULT 0 NOT NULL,
    commission_2pct numeric DEFAULT 0 NOT NULL,
    status text DEFAULT 'en_attente'::text NOT NULL,
    paid_at timestamp with time zone,
    reminder_sent_at timestamp with time zone,
    notes text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    invoice_number text,
    tva_rate numeric DEFAULT 20 NOT NULL,
    tva_amount numeric DEFAULT 0 NOT NULL,
    total_ttc numeric DEFAULT 0 NOT NULL,
    due_date date,
    pdf_path text,
    validated_at timestamp with time zone,
    validated_by uuid,
    sent_at timestamp with time zone
);

--
-- Name: partner_contracts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.partner_contracts (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    restaurant_id uuid NOT NULL,
    commission_rate numeric DEFAULT 10 NOT NULL,
    contract_start date DEFAULT CURRENT_DATE NOT NULL,
    contract_end date DEFAULT (CURRENT_DATE + '1 year'::interval) NOT NULL,
    payment_terms text DEFAULT 'Net 30'::text NOT NULL,
    auto_renew boolean DEFAULT false NOT NULL,
    status text DEFAULT 'actif'::text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    represented_by text DEFAULT ''::text,
    represented_title text DEFAULT 'Gérant'::text,
    restaurant_address text DEFAULT ''::text,
    restaurant_city text DEFAULT ''::text,
    restaurant_phone text DEFAULT ''::text,
    client_commission_rate numeric DEFAULT 10 NOT NULL,
    wallet_admin_rate numeric DEFAULT 2 NOT NULL,
    oneclick_commission_rate numeric DEFAULT 3 NOT NULL,
    template_id uuid,
    contract_snapshot jsonb DEFAULT '{}'::jsonb,
    signed_at timestamp with time zone,
    signed_by text DEFAULT ''::text,
    contract_number text DEFAULT ''::text,
    expiration_notified_at timestamp with time zone,
    raison_sociale text DEFAULT ''::text,
    forme_juridique text DEFAULT ''::text,
    numero_rc text DEFAULT ''::text,
    numero_if text DEFAULT ''::text,
    numero_ice text DEFAULT ''::text,
    capital_social text DEFAULT ''::text,
    banque text DEFAULT ''::text,
    rib text DEFAULT ''::text,
    capacite_couverts integer DEFAULT 0,
    horaires_exploitation text DEFAULT ''::text,
    jours_fermeture text DEFAULT ''::text,
    duree_engagement_mois integer DEFAULT 12,
    preavis_resiliation_mois integer DEFAULT 3,
    penalite_resiliation numeric DEFAULT 0,
    plafond_commission_mensuel numeric DEFAULT 0,
    lieu_signature text DEFAULT ''::text,
    nombre_exemplaires integer DEFAULT 2,
    restaurant_name text DEFAULT ''::text,
    parent_contract_id uuid,
    renewal_number integer DEFAULT 0,
    expiration_notified_30d_at timestamp with time zone,
    expiration_notified_15d_at timestamp with time zone,
    expiration_notified_7d_at timestamp with time zone
);

--
-- Name: COLUMN partner_contracts.expiration_notified_30d_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.partner_contracts.expiration_notified_30d_at IS 'Timestamp envoi alerte J-30 avant contract_end (notify-expiring-contracts). NULL = pas encore envoyée.';

--
-- Name: COLUMN partner_contracts.expiration_notified_15d_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.partner_contracts.expiration_notified_15d_at IS 'Timestamp envoi alerte J-15 avant contract_end. NULL = pas encore envoyée.';

--
-- Name: COLUMN partner_contracts.expiration_notified_7d_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.partner_contracts.expiration_notified_7d_at IS 'Timestamp envoi alerte J-7 avant contract_end. NULL = pas encore envoyée.';

--
-- Name: pcc_family_members; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pcc_family_members (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    member_id uuid NOT NULL,
    related_member_id uuid NOT NULL,
    relation text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT pcc_family_no_self CHECK ((member_id <> related_member_id))
);

--
-- Name: pcc_feedbacks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pcc_feedbacks (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    member_id uuid NOT NULL,
    sentiment text NOT NULL,
    category text NOT NULL,
    comment text,
    reply_text text,
    reply_by uuid,
    reply_at timestamp with time zone,
    reply_read_by_member boolean DEFAULT false,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    target_restaurant_id uuid,
    CONSTRAINT pcc_feedbacks_sentiment_check CHECK ((sentiment = ANY (ARRAY['happy'::text, 'unhappy'::text])))
);

--
-- Name: COLUMN pcc_feedbacks.target_restaurant_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.pcc_feedbacks.target_restaurant_id IS 'Resto PCC cible de l''avis (Padel/Spa/Golf/etc.). NULL = avis général tenant-wide → tenant_admins seulement notifiés. Sprint Bug #1 30/04.';

--
-- Name: point_distributions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.point_distributions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    admin_id uuid NOT NULL,
    source_type public.distribution_source DEFAULT 'bonus_system'::public.distribution_source NOT NULL,
    restaurant_id uuid,
    client_id uuid,
    segment text,
    points integer DEFAULT 0 NOT NULL,
    reason text DEFAULT ''::text NOT NULL,
    campaign_id uuid,
    offer_id uuid,
    status text DEFAULT 'completed'::text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: point_gifts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.point_gifts (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    sender_id uuid NOT NULL,
    receiver_id uuid NOT NULL,
    restaurant_id uuid NOT NULL,
    points integer NOT NULL,
    message text DEFAULT ''::text,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: promo_notification_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.promo_notification_requests (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    offer_id uuid NOT NULL,
    restaurant_id uuid NOT NULL,
    requested_by uuid NOT NULL,
    status text DEFAULT 'en_attente'::text NOT NULL,
    message text DEFAULT ''::text,
    target_segments text[] DEFAULT '{tous}'::text[] NOT NULL,
    admin_note text DEFAULT ''::text,
    reviewed_by uuid,
    reviewed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    push_sent_at timestamp with time zone,
    push_sent_count integer DEFAULT 0,
    push_error text
);

--
-- Name: quota_change_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.quota_change_logs (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    restaurant_id uuid NOT NULL,
    service_type text NOT NULL,
    service_name text NOT NULL,
    old_quota integer DEFAULT 0 NOT NULL,
    new_quota integer DEFAULT 0 NOT NULL,
    changed_by uuid NOT NULL,
    changed_by_name text DEFAULT ''::text NOT NULL,
    change_source text DEFAULT 'restaurant'::text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: redemption_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.redemption_events (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    client_id uuid NOT NULL,
    restaurant_id uuid NOT NULL,
    scanned_by uuid,
    ticket_ref text,
    ticket_montant numeric NOT NULL,
    points_redeemed integer NOT NULL,
    discount_dh numeric NOT NULL,
    client_tier text,
    effective_point_value_mad numeric NOT NULL,
    accepted boolean NOT NULL,
    rejection_reason text,
    flag_ratio_high boolean DEFAULT false NOT NULL,
    flag_daily_near_cap boolean DEFAULT false NOT NULL,
    flag_first_redemption boolean DEFAULT false NOT NULL,
    flag_large_absolute boolean DEFAULT false NOT NULL
);

ALTER TABLE ONLY public.redemption_events REPLICA IDENTITY FULL;

--
-- Name: TABLE redemption_events; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.redemption_events IS 'Audit log of every redemption attempt (accepted or rejected). Used by /forge/redemption-audit to surface suspicious activity.';

--
-- Name: redemption_otp_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.redemption_otp_requests (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    expires_at timestamp with time zone DEFAULT (now() + '00:05:00'::interval) NOT NULL,
    client_id uuid NOT NULL,
    restaurant_id uuid NOT NULL,
    staff_id uuid NOT NULL,
    points_requested integer NOT NULL,
    ticket_montant numeric NOT NULL,
    estimated_discount_dh numeric NOT NULL,
    code_hash text NOT NULL,
    status text DEFAULT 'pending'::text NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    consumed_at timestamp with time zone,
    consumed_for_ticket_ref text,
    CONSTRAINT redemption_otp_requests_estimated_discount_dh_check CHECK ((estimated_discount_dh >= (0)::numeric)),
    CONSTRAINT redemption_otp_requests_points_requested_check CHECK ((points_requested > 0)),
    CONSTRAINT redemption_otp_requests_status_check CHECK ((status = ANY (ARRAY['pending'::text, 'consumed'::text, 'expired'::text, 'cancelled'::text]))),
    CONSTRAINT redemption_otp_requests_ticket_montant_check CHECK ((ticket_montant >= (0)::numeric))
);

--
-- Name: referrals; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.referrals (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    referrer_id uuid NOT NULL,
    referred_phone text NOT NULL,
    referred_name text,
    referred_user_id uuid,
    status text DEFAULT 'en_attente'::text NOT NULL,
    pts_awarded integer DEFAULT 50 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    activated_at timestamp with time zone,
    restaurant_id uuid,
    CONSTRAINT referrals_status_check CHECK ((status = ANY (ARRAY['en_attente'::text, 'actif'::text, 'expiré'::text])))
);

--
-- Name: resource_bookings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.resource_bookings (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    resource_id uuid NOT NULL,
    organizer_id uuid NOT NULL,
    start_at timestamp with time zone NOT NULL,
    end_at timestamp with time zone NOT NULL,
    party_size integer DEFAULT 1 NOT NULL,
    invitees jsonb DEFAULT '[]'::jsonb NOT NULL,
    status public.resource_booking_status DEFAULT 'demandee'::public.resource_booking_status NOT NULL,
    pricing_snapshot jsonb,
    notes text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    reminder_j1_sent_at timestamp with time zone,
    reminder_h2_sent_at timestamp with time zone,
    CONSTRAINT resource_bookings_check CHECK ((end_at > start_at)),
    CONSTRAINT resource_bookings_party_size_check CHECK ((party_size >= 1))
);

--
-- Name: TABLE resource_bookings; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.resource_bookings IS 'Réservations sur bookable_resources. Status workflow :
   demandee → confirmee → honoree (trigger punch +1) | no_show (trigger rating) | annulee.
   Annulation par organizer impossible H-2 avant. Sprint PCC Phase 6.';

--
-- Name: COLUMN resource_bookings.reminder_j1_sent_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.resource_bookings.reminder_j1_sent_at IS 'Timestamp de l''envoi du rappel J-1 (9h Maroc la veille). NULL = pas encore envoyé. Reset si start_at change.';

--
-- Name: COLUMN resource_bookings.reminder_h2_sent_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.resource_bookings.reminder_h2_sent_at IS 'Timestamp de l''envoi du rappel H-2 (~2h avant). NULL = pas encore envoyé. Reset si start_at change.';

--
-- Name: restaurant_expired_pool; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.restaurant_expired_pool (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    restaurant_id uuid NOT NULL,
    client_id uuid NOT NULL,
    expired_point_id uuid NOT NULL,
    points integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    consolidated_at timestamp with time zone,
    restitution_id uuid
);

--
-- Name: restaurant_gain_rules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.restaurant_gain_rules (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    restaurant_id uuid NOT NULL,
    name text NOT NULL,
    description text DEFAULT ''::text,
    type text DEFAULT 'standard'::text NOT NULL,
    taux_conversion numeric DEFAULT 0.1 NOT NULL,
    min_ticket numeric DEFAULT 50 NOT NULL,
    max_points_par_ticket integer DEFAULT 500 NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    point_value_mad numeric(6,2),
    source_rule_id uuid,
    welcome_points_default integer DEFAULT 100 NOT NULL,
    welcome_points_max integer DEFAULT 500 NOT NULL,
    CONSTRAINT restaurant_gain_rules_point_value_mad_check CHECK (((point_value_mad IS NULL) OR (point_value_mad > (0)::numeric))),
    CONSTRAINT welcome_points_default_positive CHECK ((welcome_points_default >= 0)),
    CONSTRAINT welcome_points_max_gte_default CHECK ((welcome_points_max >= welcome_points_default))
);

--
-- Name: COLUMN restaurant_gain_rules.point_value_mad; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.restaurant_gain_rules.point_value_mad IS 'Override of gain_rules.point_value_mad for this restaurant. NULL = inherit global.';

--
-- Name: COLUMN restaurant_gain_rules.welcome_points_default; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.restaurant_gain_rules.welcome_points_default IS 'Points offerts par défaut à l''inscription d''un nouveau membre (bonus de bienvenue).
   Valeur pré-remplie dans le formulaire staff.';

--
-- Name: COLUMN restaurant_gain_rules.welcome_points_max; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.restaurant_gain_rules.welcome_points_max IS 'Plafond absolu du bonus de bienvenue. Le staff ne peut pas dépasser cette valeur
   sans validation admin. Sert de garde-fou anti-abus.';

--
-- Name: restaurant_groups; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.restaurant_groups (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name text NOT NULL,
    description text DEFAULT ''::text,
    logo_url text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    owner_user_id uuid,
    tenant_id uuid
);

--
-- Name: restaurant_media; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.restaurant_media (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    restaurant_id uuid NOT NULL,
    type text DEFAULT 'photo'::text NOT NULL,
    name text NOT NULL,
    url text,
    status text DEFAULT 'en_attente'::text NOT NULL,
    uploaded_by uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: restaurant_restitutions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.restaurant_restitutions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    restaurant_id uuid NOT NULL,
    period_month date NOT NULL,
    total_points integer DEFAULT 0 NOT NULL,
    status text DEFAULT 'en_attente'::text NOT NULL,
    processed_by uuid,
    processed_at timestamp with time zone,
    notes text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: restaurant_services; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.restaurant_services (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    restaurant_id uuid NOT NULL,
    name text NOT NULL,
    type text DEFAULT 'déjeuner'::text NOT NULL,
    heure_debut text DEFAULT '12:00'::text NOT NULL,
    heure_fin text DEFAULT '15:00'::text NOT NULL,
    jours_actifs text[] DEFAULT '{Lun,Mar,Mer,Jeu,Ven,Sam,Dim}'::text[] NOT NULL,
    capacite_max integer DEFAULT 50 NOT NULL,
    status text DEFAULT 'actif'::text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    clickgo_quota integer DEFAULT 0 NOT NULL
);

--
-- Name: restaurant_staff; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.restaurant_staff (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    restaurant_id uuid NOT NULL,
    staff_role public.staff_role DEFAULT 'manager'::public.staff_role NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    status text DEFAULT 'actif'::text NOT NULL,
    start_date date
);

--
-- Name: COLUMN restaurant_staff.start_date; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.restaurant_staff.start_date IS 'Date de prise de poste du membre (NULL = inconnu, fallback created_at::date pour historique).';

--
-- Name: restaurant_tables; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.restaurant_tables (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    restaurant_id uuid NOT NULL,
    zone_id uuid NOT NULL,
    numero integer NOT NULL,
    capacite integer DEFAULT 2 NOT NULL,
    forme text DEFAULT 'carrée'::text NOT NULL,
    "position" text DEFAULT ''::text,
    status text DEFAULT 'disponible'::text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: restaurant_tier_config; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.restaurant_tier_config (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name text NOT NULL,
    slug text NOT NULL,
    min_ca numeric DEFAULT 0 NOT NULL,
    max_ca numeric,
    grace_period_months integer DEFAULT 0 NOT NULL,
    color text DEFAULT '#6366f1'::text NOT NULL,
    icon text DEFAULT '⭐'::text NOT NULL,
    "position" integer DEFAULT 1 NOT NULL,
    description text DEFAULT ''::text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: restaurant_tier_status; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.restaurant_tier_status (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    restaurant_id uuid NOT NULL,
    current_tier_id uuid,
    current_tier_slug text DEFAULT 'essentiel'::text NOT NULL,
    monthly_ca numeric DEFAULT 0 NOT NULL,
    tier_achieved_at timestamp with time zone,
    grace_expires_at timestamp with time zone,
    last_evaluated_at timestamp with time zone DEFAULT now(),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: restaurant_zones; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.restaurant_zones (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    restaurant_id uuid NOT NULL,
    name text NOT NULL,
    type text DEFAULT 'intérieur'::text NOT NULL,
    description text DEFAULT ''::text,
    capacite integer DEFAULT 0 NOT NULL,
    tables_count integer DEFAULT 0 NOT NULL,
    status text DEFAULT 'active'::text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: rule_templates; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.rule_templates (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    slug text NOT NULL,
    name text NOT NULL,
    description text,
    icon text DEFAULT 'Sparkles'::text,
    category text DEFAULT 'standard'::text NOT NULL,
    config jsonb DEFAULT '{}'::jsonb NOT NULL,
    is_builtin boolean DEFAULT false NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    created_by uuid
);

--
-- Name: TABLE rule_templates; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.rule_templates IS 'Pre-configured gain rule starting points. Admin picks one → form is pre-filled → tweak & save.';

--
-- Name: scanned_tickets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.scanned_tickets (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    restaurant_id uuid NOT NULL,
    client_id uuid NOT NULL,
    scanned_by uuid NOT NULL,
    ticket_ref text NOT NULL,
    montant numeric DEFAULT 0 NOT NULL,
    points_credites integer DEFAULT 0 NOT NULL,
    items jsonb DEFAULT '[]'::jsonb,
    status text DEFAULT 'validé'::text NOT NULL,
    photo_url text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    reservation_id uuid
);

--
-- Name: COLUMN scanned_tickets.reservation_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.scanned_tickets.reservation_id IS 'Réservation honorée à laquelle ce ticket est rattaché. NULL pour les tickets historiques (avant 04/2026) ou les scans sans résa explicite.';

--
-- Name: seminar_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.seminar_requests (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid,
    organizer_id uuid,
    company_name text NOT NULL,
    contact_name text NOT NULL,
    contact_email text NOT NULL,
    contact_phone text,
    expected_attendees integer DEFAULT 10 NOT NULL,
    preferred_date_start date,
    preferred_date_end date,
    needs_text text,
    status text DEFAULT 'demandee'::text NOT NULL,
    notes_internal text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT seminar_requests_expected_attendees_check CHECK ((expected_attendees >= 1))
);

--
-- Name: staff_notification_preferences; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.staff_notification_preferences (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    booking boolean DEFAULT true NOT NULL,
    reservation boolean DEFAULT true NOT NULL,
    feedback boolean DEFAULT true NOT NULL,
    loyalty boolean DEFAULT true NOT NULL,
    system boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: TABLE staff_notification_preferences; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.staff_notification_preferences IS 'Preferences de notifications par staff utilisateur. UNIQUE par user_id. Tenant-agnostic. RLS user-scoped via auth.uid().';

--
-- Name: COLUMN staff_notification_preferences.booking; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.staff_notification_preferences.booking IS 'Réservations activités : resource_booking + seminar_request + pcc_booking_* + pcc_seminar_* + pcc_reminder_*';

--
-- Name: COLUMN staff_notification_preferences.reservation; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.staff_notification_preferences.reservation IS 'Réservations table restaurants : type = "reservation" (table reservations classique)';

--
-- Name: COLUMN staff_notification_preferences.feedback; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.staff_notification_preferences.feedback IS 'Avis membres : pcc_feedback + pcc_feedback_sent + pcc_feedback_reply';

--
-- Name: COLUMN staff_notification_preferences.loyalty; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.staff_notification_preferences.loyalty IS 'Points fidélité : loyalty + points + tier_upgrade + promo';

--
-- Name: COLUMN staff_notification_preferences.system; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.staff_notification_preferences.system IS 'Alertes système : type = "system" (no-show, expire, etc.)';

--
-- Name: staff_role_permissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.staff_role_permissions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    staff_role text NOT NULL,
    permission_id text NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_by uuid
);

--
-- Name: support_tickets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.support_tickets (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    category text NOT NULL,
    subject text NOT NULL,
    message text NOT NULL,
    photos text[] DEFAULT '{}'::text[],
    status text DEFAULT 'ouvert'::text NOT NULL,
    last_reply text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    restaurant_id uuid,
    ticket_type text DEFAULT 'client'::text NOT NULL,
    escalated_to_admin boolean DEFAULT false NOT NULL,
    priority text DEFAULT 'normale'::text NOT NULL,
    resolution_level text DEFAULT 'pending'::text NOT NULL,
    ai_handled boolean DEFAULT false NOT NULL,
    ai_summary text,
    CONSTRAINT support_tickets_category_check CHECK ((category = ANY (ARRAY['ticket_rejete'::text, 'lounge_points'::text, 'reservation'::text, 'autre'::text]))),
    CONSTRAINT support_tickets_status_check CHECK ((status = ANY (ARRAY['ouvert'::text, 'en_attente'::text, 'en_cours'::text, 'résolu'::text])))
);

--
-- Name: system_alert_rules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_alert_rules (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name text NOT NULL,
    metric_name text NOT NULL,
    operator text DEFAULT 'gt'::text NOT NULL,
    threshold numeric DEFAULT 0 NOT NULL,
    severity text DEFAULT 'warning'::text NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    cooldown_minutes integer DEFAULT 30 NOT NULL,
    last_triggered_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: system_alerts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_alerts (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    rule_id uuid,
    metric_name text NOT NULL,
    metric_value numeric NOT NULL,
    threshold numeric NOT NULL,
    severity text DEFAULT 'warning'::text NOT NULL,
    message text DEFAULT ''::text NOT NULL,
    acknowledged boolean DEFAULT false NOT NULL,
    acknowledged_by uuid,
    acknowledged_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: system_health_checks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_health_checks (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    metric_name text NOT NULL,
    metric_value numeric DEFAULT 0 NOT NULL,
    metric_unit text DEFAULT 'ms'::text NOT NULL,
    status text DEFAULT 'ok'::text NOT NULL,
    details text DEFAULT ''::text,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: team_invitations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.team_invitations (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    restaurant_id uuid NOT NULL,
    invited_by uuid NOT NULL,
    first_name text DEFAULT ''::text NOT NULL,
    last_name text DEFAULT ''::text NOT NULL,
    phone text NOT NULL,
    role text DEFAULT 'serveur'::text NOT NULL,
    status text DEFAULT 'en_attente'::text NOT NULL,
    expires_at timestamp with time zone DEFAULT (now() + '7 days'::interval) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: tenant_admins; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tenant_admins (
    tenant_id uuid NOT NULL,
    user_id uuid NOT NULL,
    role text DEFAULT 'admin'::text NOT NULL,
    invited_by uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT tenant_admins_role_check CHECK ((role = ANY (ARRAY['owner'::text, 'admin'::text, 'viewer'::text])))
);

--
-- Name: TABLE tenant_admins; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.tenant_admins IS 'Assignation utilisateur → tenant. Un user peut être admin de plusieurs tenants (rare mais possible).';

--
-- Name: tenant_announcements; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tenant_announcements (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    author_id uuid NOT NULL,
    title text NOT NULL,
    body text NOT NULL,
    image_url text,
    priority public.announcement_priority DEFAULT 'permanent'::public.announcement_priority NOT NULL,
    is_pinned boolean DEFAULT true NOT NULL,
    publish_at timestamp with time zone DEFAULT now() NOT NULL,
    archived_at timestamp with time zone,
    deleted_at timestamp with time zone,
    body_version integer DEFAULT 1 NOT NULL,
    push_sent_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    target_restaurant_ids uuid[],
    CONSTRAINT tenant_announcements_body_check CHECK (((char_length(body) >= 1) AND (char_length(body) <= 1000))),
    CONSTRAINT tenant_announcements_title_check CHECK (((char_length(title) >= 1) AND (char_length(title) <= 100)))
);

ALTER TABLE ONLY public.tenant_announcements REPLICA IDENTITY FULL;

--
-- Name: COLUMN tenant_announcements.target_restaurant_ids; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.tenant_announcements.target_restaurant_ids IS 'Ciblage staff : NULL = tous staff du tenant (default), array = uniquement staff actif sur ces restaurants. Permet à un tenant_admin de cibler une annonce (ex: "Padel staff only").';

--
-- Name: tenant_branding; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tenant_branding (
    tenant_id uuid NOT NULL,
    logo_url text,
    logo_dark_url text,
    favicon_url text,
    primary_color text,
    accent_color text,
    background_color text,
    tagline text,
    app_name_win text,
    app_name_store text,
    custom_domain text,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: TABLE tenant_branding; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.tenant_branding IS 'Branding dynamique par tenant. Appliqué en CSS vars au boot via applyTenantTheme().';

--
-- Name: tenant_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tenant_events (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    title text NOT NULL,
    description text,
    photo_url text,
    category text,
    event_date timestamp with time zone NOT NULL,
    event_end_date timestamp with time zone,
    capacity integer,
    rsvp_enabled boolean DEFAULT false NOT NULL,
    status text DEFAULT 'actif'::text NOT NULL,
    display_order integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    visible_until timestamp with time zone,
    CONSTRAINT tenant_events_check CHECK (((event_end_date IS NULL) OR (event_end_date >= event_date)))
);

--
-- Name: COLUMN tenant_events.visible_until; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.tenant_events.visible_until IS 'TTL story-style : event masqué de la home quand visible_until <= NOW(). NULL = toujours visible (jusqu''à désactivation manuelle via status). Default côté admin pour stories : event_date + 24h.';

--
-- Name: tenant_features; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tenant_features (
    tenant_id uuid NOT NULL,
    feature_key text NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: TABLE tenant_features; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.tenant_features IS 'Feature flags par tenant. 13 features actuelles (ai_chat, referral, elite_club, …). Default true.';

--
-- Name: tenants; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tenants (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    slug text NOT NULL,
    name text NOT NULL,
    legal_name text,
    status text DEFAULT 'actif'::text NOT NULL,
    company_settings_id uuid,
    created_by uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    features jsonb DEFAULT '{}'::jsonb NOT NULL,
    CONSTRAINT tenants_status_check CHECK ((status = ANY (ARRAY['actif'::text, 'suspendu'::text, 'archivé'::text])))
);

--
-- Name: TABLE tenants; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.tenants IS 'Tenant = marque whitelabel. OneClick (default), Restopro, La Grillardière, futures. 1 ligne par marque.';

--
-- Name: COLUMN tenants.features; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.tenants.features IS 'Feature flags par tenant (JSONB). Ex: {"has_announcements": true, "has_pkpass": false}. Permet activation par tenant sans toucher au code.';

--
-- Name: tier_restaurant_offers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tier_restaurant_offers (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    restaurant_id uuid NOT NULL,
    tier_name text NOT NULL,
    offer_label text DEFAULT ''::text NOT NULL,
    offer_type text DEFAULT 'remise'::text NOT NULL,
    offer_value text DEFAULT ''::text NOT NULL,
    description text DEFAULT ''::text,
    enabled boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT tier_restaurant_offers_offer_type_check CHECK ((offer_type = ANY (ARRAY['remise'::text, 'cadeau'::text, 'priorite'::text, 'experience'::text, 'custom'::text]))),
    CONSTRAINT tier_restaurant_offers_tier_name_check CHECK ((tier_name = ANY (ARRAY['Ruby'::text, 'Sapphire'::text, 'Émeraude'::text])))
);

--
-- Name: tier_thresholds; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tier_thresholds (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tier_name text NOT NULL,
    period_days integer DEFAULT 90 NOT NULL,
    min_spend numeric DEFAULT 0 NOT NULL,
    points_required integer DEFAULT 0 NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    benefits text DEFAULT ''::text,
    enabled boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    gain_bonus_pct numeric(5,2) DEFAULT 0 NOT NULL,
    point_value_mad_override numeric(6,2),
    min_ticket_override numeric,
    max_points_per_ticket_override integer,
    benefit_duration_days_override integer,
    max_redemption_per_24h_override integer,
    max_redemption_ratio_pct_override numeric(5,2),
    otp_required_above_pts_override integer,
    otp_required_above_ratio_pct_override numeric(5,2),
    taux_conversion_override numeric(5,4),
    CONSTRAINT tier_thresholds_benefit_duration_days_override_check CHECK (((benefit_duration_days_override IS NULL) OR (benefit_duration_days_override > 0))),
    CONSTRAINT tier_thresholds_gain_bonus_pct_check CHECK (((gain_bonus_pct >= (0)::numeric) AND (gain_bonus_pct <= (500)::numeric))),
    CONSTRAINT tier_thresholds_max_points_per_ticket_override_check CHECK (((max_points_per_ticket_override IS NULL) OR (max_points_per_ticket_override > 0))),
    CONSTRAINT tier_thresholds_max_redemption_per_24h_override_check CHECK (((max_redemption_per_24h_override IS NULL) OR (max_redemption_per_24h_override > 0))),
    CONSTRAINT tier_thresholds_max_redemption_ratio_pct_override_check CHECK (((max_redemption_ratio_pct_override IS NULL) OR ((max_redemption_ratio_pct_override > (0)::numeric) AND (max_redemption_ratio_pct_override <= (100)::numeric)))),
    CONSTRAINT tier_thresholds_min_ticket_override_check CHECK (((min_ticket_override IS NULL) OR (min_ticket_override >= (0)::numeric))),
    CONSTRAINT tier_thresholds_otp_required_above_pts_override_check CHECK (((otp_required_above_pts_override IS NULL) OR (otp_required_above_pts_override > 0))),
    CONSTRAINT tier_thresholds_otp_required_above_ratio_pct_override_check CHECK (((otp_required_above_ratio_pct_override IS NULL) OR ((otp_required_above_ratio_pct_override > (0)::numeric) AND (otp_required_above_ratio_pct_override <= (100)::numeric)))),
    CONSTRAINT tier_thresholds_point_value_mad_override_check CHECK (((point_value_mad_override IS NULL) OR (point_value_mad_override > (0)::numeric))),
    CONSTRAINT tier_thresholds_taux_conversion_override_check CHECK (((taux_conversion_override IS NULL) OR ((taux_conversion_override > (0)::numeric) AND (taux_conversion_override <= (1)::numeric)))),
    CONSTRAINT tier_thresholds_tier_name_check CHECK ((tier_name = ANY (ARRAY['Ruby'::text, 'Sapphire'::text, 'Émeraude'::text, 'Black'::text])))
);

--
-- Name: COLUMN tier_thresholds.gain_bonus_pct; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.tier_thresholds.gain_bonus_pct IS 'Additive percentage on top of the base taux_conversion (e.g. 20 = +20%).';

--
-- Name: COLUMN tier_thresholds.point_value_mad_override; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.tier_thresholds.point_value_mad_override IS 'Overrides point_value_mad for clients at this tier (NULL = inherit global).';

--
-- Name: COLUMN tier_thresholds.min_ticket_override; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.tier_thresholds.min_ticket_override IS 'Overrides min_ticket for clients at this tier (NULL = inherit).';

--
-- Name: COLUMN tier_thresholds.max_points_per_ticket_override; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.tier_thresholds.max_points_per_ticket_override IS 'Overrides max_points_par_ticket for clients at this tier (NULL = inherit).';

--
-- Name: COLUMN tier_thresholds.benefit_duration_days_override; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.tier_thresholds.benefit_duration_days_override IS 'Overrides benefit_duration_days for points earned while at this tier (NULL = inherit).';

--
-- Name: COLUMN tier_thresholds.taux_conversion_override; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.tier_thresholds.taux_conversion_override IS 'Taux de conversion absolu pour ce tier (ex. 0.075 = 7,5%). NULL = utilise gain_bonus_pct additif sur le taux de base.';

--
-- Name: user_favorites; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_favorites (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    restaurant_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: user_roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_roles (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    role public.app_role NOT NULL
);

--
-- Name: v_admin_audit_log; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.v_admin_audit_log AS
 SELECT l.id,
    l.created_at,
    l.actor_id,
    COALESCE(NULLIF(l.actor_email, ''::text), p.email, 'Inconnu'::text) AS actor_email,
    COALESCE(NULLIF(concat_ws(' '::text, p.first_name, p.last_name), ''::text), l.actor_email, 'Inconnu'::text) AS actor_name,
    l.action,
    l.entity_type,
    l.entity_id,
    l.entity_label,
    l.diff,
    l.metadata
   FROM (public.admin_audit_log l
     LEFT JOIN public.profiles p ON ((p.id = l.actor_id)))
  ORDER BY l.created_at DESC;

--
-- Name: v_client_loyalty_summary; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.v_client_loyalty_summary AS
 SELECT p.id AS client_id,
    p.first_name,
    p.last_name,
    p.city,
    p.phone,
    p.created_at,
    COALESCE(lp.total_points, (0)::bigint) AS total_points_earned,
    COALESCE(lp.available_points, (0)::bigint) AS available_points,
    COALESCE(lp.total_spent, (0)::numeric) AS total_spent,
    COALESCE(lp.restaurant_count, (0)::bigint) AS restaurants_visited,
    COALESCE(rv.total_reservations, (0)::bigint) AS total_reservations,
    COALESCE(rv.honorees, (0)::bigint) AS reservations_honorees,
    COALESCE(rv.no_shows, (0)::bigint) AS reservations_no_show,
    COALESCE(st.ticket_count, (0)::bigint) AS tickets_scanned
   FROM ((((public.profiles p
     JOIN public.user_roles ur ON (((ur.user_id = p.id) AND (ur.role = 'client'::public.app_role))))
     LEFT JOIN ( SELECT loyalty_points.client_id,
            sum(
                CASE
                    WHEN (loyalty_points.points > 0) THEN loyalty_points.points
                    ELSE 0
                END) AS total_points,
            sum(
                CASE
                    WHEN (loyalty_points.remaining_points > 0) THEN loyalty_points.remaining_points
                    ELSE 0
                END) AS available_points,
            sum(
                CASE
                    WHEN (loyalty_points.amount_ttc > (0)::numeric) THEN loyalty_points.amount_ttc
                    ELSE (0)::numeric
                END) AS total_spent,
            count(DISTINCT loyalty_points.restaurant_id) AS restaurant_count
           FROM public.loyalty_points
          GROUP BY loyalty_points.client_id) lp ON ((lp.client_id = p.id)))
     LEFT JOIN ( SELECT reservations.client_id,
            count(*) AS total_reservations,
            count(*) FILTER (WHERE (reservations.status = 'honorée'::public.reservation_status)) AS honorees,
            count(*) FILTER (WHERE (reservations.status = 'no_show'::public.reservation_status)) AS no_shows
           FROM public.reservations
          GROUP BY reservations.client_id) rv ON ((rv.client_id = p.id)))
     LEFT JOIN ( SELECT scanned_tickets.client_id,
            count(*) AS ticket_count
           FROM public.scanned_tickets
          GROUP BY scanned_tickets.client_id) st ON ((st.client_id = p.id)));

--
-- Name: v_restaurant_kpis; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.v_restaurant_kpis AS
 SELECT r.id AS restaurant_id,
    r.name,
    r.city,
    r.group_id,
    COALESCE(st.total_ca, (0)::numeric) AS total_ca,
    COALESCE(st.ticket_count, (0)::bigint) AS ticket_count,
    COALESCE(lp.total_points_issued, (0)::bigint) AS total_points_issued,
    COALESCE(rv.total_reservations, (0)::bigint) AS total_reservations,
    COALESCE(rv.honorees, (0)::bigint) AS reservations_honorees,
    COALESCE(rv.no_shows, (0)::bigint) AS reservations_no_show,
    COALESCE(rv.active_reservations, (0)::bigint) AS active_reservations,
    COALESCE(staff.staff_count, (0)::bigint) AS staff_count
   FROM ((((public.restaurants r
     LEFT JOIN ( SELECT scanned_tickets.restaurant_id,
            sum(scanned_tickets.montant) AS total_ca,
            count(*) AS ticket_count
           FROM public.scanned_tickets
          GROUP BY scanned_tickets.restaurant_id) st ON ((st.restaurant_id = r.id)))
     LEFT JOIN ( SELECT loyalty_points.restaurant_id,
            sum(
                CASE
                    WHEN (loyalty_points.points > 0) THEN loyalty_points.points
                    ELSE 0
                END) AS total_points_issued
           FROM public.loyalty_points
          GROUP BY loyalty_points.restaurant_id) lp ON ((lp.restaurant_id = r.id)))
     LEFT JOIN ( SELECT reservations.restaurant_id,
            count(*) AS total_reservations,
            count(*) FILTER (WHERE (reservations.status = 'honorée'::public.reservation_status)) AS honorees,
            count(*) FILTER (WHERE (reservations.status = 'no_show'::public.reservation_status)) AS no_shows,
            count(*) FILTER (WHERE (reservations.status = ANY (ARRAY['demandée'::public.reservation_status, 'confirmée'::public.reservation_status, 'placée'::public.reservation_status]))) AS active_reservations
           FROM public.reservations
          GROUP BY reservations.restaurant_id) rv ON ((rv.restaurant_id = r.id)))
     LEFT JOIN ( SELECT restaurant_staff.restaurant_id,
            count(*) AS staff_count
           FROM public.restaurant_staff
          WHERE (restaurant_staff.status = 'actif'::text)
          GROUP BY restaurant_staff.restaurant_id) staff ON ((staff.restaurant_id = r.id)));

--
-- Name: v_restaurants_config; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.v_restaurants_config AS
 SELECT id,
    name,
    phone,
    address,
    description,
    tags,
    max_staff,
    open_now
   FROM public.restaurants;

--
-- Name: v_restaurants_core; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.v_restaurants_core AS
 SELECT id,
    name,
    city,
    cuisine,
    budget,
    rating,
    reviews_count,
    image,
    status,
    group_id,
    lounge_pts,
    created_at
   FROM public.restaurants;

--
-- Name: v_restaurants_google; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.v_restaurants_google AS
 SELECT id,
    name,
    google_place_id,
    google_rating,
    google_reviews_count,
    google_photos,
    website_url,
    latitude,
    longitude,
    opening_hours,
    google_updated_at
   FROM public.restaurants
  WHERE (google_place_id IS NOT NULL);

--
-- Name: v_restaurants_with_group; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.v_restaurants_with_group AS
 SELECT r.id,
    r.name,
    r.city,
    r.cuisine,
    r.budget,
    r.rating,
    r.image,
    r.status,
    r.group_id,
    r.lounge_pts,
    r.created_at,
    r.google_rating,
    g.name AS group_name,
    g.owner_user_id AS group_owner_id
   FROM (public.restaurants r
     LEFT JOIN public.restaurant_groups g ON ((r.group_id = g.id)));

--
-- Name: action_logs action_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.action_logs
    ADD CONSTRAINT action_logs_pkey PRIMARY KEY (id);

--
-- Name: admin_audit_log admin_audit_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_audit_log
    ADD CONSTRAINT admin_audit_log_pkey PRIMARY KEY (id);

--
-- Name: admin_notifications admin_notifications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_notifications
    ADD CONSTRAINT admin_notifications_pkey PRIMARY KEY (id);

--
-- Name: admin_wallet_transactions admin_wallet_transactions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_wallet_transactions
    ADD CONSTRAINT admin_wallet_transactions_pkey PRIMARY KEY (id);

--
-- Name: ai_usage_bypass ai_usage_bypass_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_usage_bypass
    ADD CONSTRAINT ai_usage_bypass_pkey PRIMARY KEY (user_id);

--
-- Name: ai_usage ai_usage_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_usage
    ADD CONSTRAINT ai_usage_pkey PRIMARY KEY (id);

--
-- Name: ai_usage ai_usage_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_usage
    ADD CONSTRAINT ai_usage_user_id_key UNIQUE (user_id);

--
-- Name: announcement_reads announcement_reads_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.announcement_reads
    ADD CONSTRAINT announcement_reads_pkey PRIMARY KEY (announcement_id, user_id);

--
-- Name: app_documents app_documents_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_documents
    ADD CONSTRAINT app_documents_pkey PRIMARY KEY (id);

--
-- Name: bookable_resources bookable_resources_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bookable_resources
    ADD CONSTRAINT bookable_resources_pkey PRIMARY KEY (id);

--
-- Name: booking_rules booking_rules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.booking_rules
    ADD CONSTRAINT booking_rules_pkey PRIMARY KEY (id);

--
-- Name: chat_messages chat_messages_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_messages
    ADD CONSTRAINT chat_messages_pkey PRIMARY KEY (id);

--
-- Name: client_ratings client_ratings_client_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.client_ratings
    ADD CONSTRAINT client_ratings_client_id_key UNIQUE (client_id);

--
-- Name: client_ratings client_ratings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.client_ratings
    ADD CONSTRAINT client_ratings_pkey PRIMARY KEY (id);

--
-- Name: client_score_config client_score_config_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.client_score_config
    ADD CONSTRAINT client_score_config_pkey PRIMARY KEY (id);

--
-- Name: company_settings company_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.company_settings
    ADD CONSTRAINT company_settings_pkey PRIMARY KEY (id);

--
-- Name: contact_import_events contact_import_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contact_import_events
    ADD CONSTRAINT contact_import_events_pkey PRIMARY KEY (id);

--
-- Name: contract_disabled_articles contract_disabled_articles_contract_id_article_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contract_disabled_articles
    ADD CONSTRAINT contract_disabled_articles_contract_id_article_id_key UNIQUE (contract_id, article_id);

--
-- Name: contract_disabled_articles contract_disabled_articles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contract_disabled_articles
    ADD CONSTRAINT contract_disabled_articles_pkey PRIMARY KEY (id);

--
-- Name: contract_history contract_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contract_history
    ADD CONSTRAINT contract_history_pkey PRIMARY KEY (id);

--
-- Name: contract_template_articles contract_template_articles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contract_template_articles
    ADD CONSTRAINT contract_template_articles_pkey PRIMARY KEY (id);

--
-- Name: contract_templates contract_templates_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contract_templates
    ADD CONSTRAINT contract_templates_pkey PRIMARY KEY (id);

--
-- Name: custom_roles custom_roles_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_roles
    ADD CONSTRAINT custom_roles_name_key UNIQUE (name);

--
-- Name: custom_roles custom_roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_roles
    ADD CONSTRAINT custom_roles_pkey PRIMARY KEY (id);

--
-- Name: device_tokens device_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.device_tokens
    ADD CONSTRAINT device_tokens_pkey PRIMARY KEY (id);

--
-- Name: device_tokens device_tokens_user_id_token_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.device_tokens
    ADD CONSTRAINT device_tokens_user_id_token_key UNIQUE (user_id, token);

--
-- Name: document_versions document_versions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.document_versions
    ADD CONSTRAINT document_versions_pkey PRIMARY KEY (id);

--
-- Name: elite_applications elite_applications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.elite_applications
    ADD CONSTRAINT elite_applications_pkey PRIMARY KEY (id);

--
-- Name: elite_events elite_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.elite_events
    ADD CONSTRAINT elite_events_pkey PRIMARY KEY (id);

--
-- Name: elite_rsvps elite_rsvps_event_id_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.elite_rsvps
    ADD CONSTRAINT elite_rsvps_event_id_user_id_key UNIQUE (event_id, user_id);

--
-- Name: elite_rsvps elite_rsvps_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.elite_rsvps
    ADD CONSTRAINT elite_rsvps_pkey PRIMARY KEY (id);

--
-- Name: email_bounces email_bounces_email_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_bounces
    ADD CONSTRAINT email_bounces_email_key UNIQUE (email);

--
-- Name: email_bounces email_bounces_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_bounces
    ADD CONSTRAINT email_bounces_pkey PRIMARY KEY (id);

--
-- Name: event_rsvps event_rsvps_event_id_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_rsvps
    ADD CONSTRAINT event_rsvps_event_id_user_id_key UNIQUE (event_id, user_id);

--
-- Name: event_rsvps event_rsvps_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_rsvps
    ADD CONSTRAINT event_rsvps_pkey PRIMARY KEY (id);

--
-- Name: expired_points expired_points_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.expired_points
    ADD CONSTRAINT expired_points_pkey PRIMARY KEY (id);

--
-- Name: explore_featured explore_featured_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.explore_featured
    ADD CONSTRAINT explore_featured_pkey PRIMARY KEY (id);

--
-- Name: explore_featured explore_featured_restaurant_id_starts_at_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.explore_featured
    ADD CONSTRAINT explore_featured_restaurant_id_starts_at_key UNIQUE (restaurant_id, starts_at);

--
-- Name: fraud_alerts fraud_alerts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fraud_alerts
    ADD CONSTRAINT fraud_alerts_pkey PRIMARY KEY (id);

--
-- Name: friend_group_members friend_group_members_group_id_friend_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.friend_group_members
    ADD CONSTRAINT friend_group_members_group_id_friend_id_key UNIQUE (group_id, friend_id);

--
-- Name: friend_group_members friend_group_members_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.friend_group_members
    ADD CONSTRAINT friend_group_members_pkey PRIMARY KEY (id);

--
-- Name: friend_groups friend_groups_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.friend_groups
    ADD CONSTRAINT friend_groups_pkey PRIMARY KEY (id);

--
-- Name: friendships friendships_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.friendships
    ADD CONSTRAINT friendships_pkey PRIMARY KEY (id);

--
-- Name: friendships friendships_requester_id_addressee_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.friendships
    ADD CONSTRAINT friendships_requester_id_addressee_id_key UNIQUE (requester_id, addressee_id);

--
-- Name: gain_rule_requests gain_rule_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.gain_rule_requests
    ADD CONSTRAINT gain_rule_requests_pkey PRIMARY KEY (id);

--
-- Name: gain_rules gain_rules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.gain_rules
    ADD CONSTRAINT gain_rules_pkey PRIMARY KEY (id);

--
-- Name: invoice_lines invoice_lines_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_lines
    ADD CONSTRAINT invoice_lines_pkey PRIMARY KEY (id);

--
-- Name: lifecycle_events lifecycle_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lifecycle_events
    ADD CONSTRAINT lifecycle_events_pkey PRIMARY KEY (id);

--
-- Name: loyalty_plafonds loyalty_plafonds_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.loyalty_plafonds
    ADD CONSTRAINT loyalty_plafonds_pkey PRIMARY KEY (id);

--
-- Name: loyalty_points loyalty_points_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.loyalty_points
    ADD CONSTRAINT loyalty_points_pkey PRIMARY KEY (id);

--
-- Name: loyalty_punch_cards loyalty_punch_cards_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.loyalty_punch_cards
    ADD CONSTRAINT loyalty_punch_cards_pkey PRIMARY KEY (id);

--
-- Name: loyalty_punch_cards loyalty_punch_cards_tenant_id_client_id_activity_type_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.loyalty_punch_cards
    ADD CONSTRAINT loyalty_punch_cards_tenant_id_client_id_activity_type_key UNIQUE (tenant_id, client_id, activity_type);

--
-- Name: oneclick_hi_invoices m3ak_hi_invoices_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.oneclick_hi_invoices
    ADD CONSTRAINT m3ak_hi_invoices_pkey PRIMARY KEY (id);

--
-- Name: oneclick_hi_invoices m3ak_hi_invoices_restaurant_id_period_month_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.oneclick_hi_invoices
    ADD CONSTRAINT m3ak_hi_invoices_restaurant_id_period_month_key UNIQUE (restaurant_id, period_month);

--
-- Name: monitor_logs monitor_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.monitor_logs
    ADD CONSTRAINT monitor_logs_pkey PRIMARY KEY (id);

--
-- Name: no_show_disputes no_show_disputes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.no_show_disputes
    ADD CONSTRAINT no_show_disputes_pkey PRIMARY KEY (id);

--
-- Name: notifications notifications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_pkey PRIMARY KEY (id);

--
-- Name: offer_impressions offer_impressions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer_impressions
    ADD CONSTRAINT offer_impressions_pkey PRIMARY KEY (id);

--
-- Name: offers offers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offers
    ADD CONSTRAINT offers_pkey PRIMARY KEY (id);

--
-- Name: onboarding_requests onboarding_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.onboarding_requests
    ADD CONSTRAINT onboarding_requests_pkey PRIMARY KEY (id);

--
-- Name: partner_contracts partner_contracts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.partner_contracts
    ADD CONSTRAINT partner_contracts_pkey PRIMARY KEY (id);

--
-- Name: pcc_family_members pcc_family_members_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pcc_family_members
    ADD CONSTRAINT pcc_family_members_pkey PRIMARY KEY (id);

--
-- Name: pcc_family_members pcc_family_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pcc_family_members
    ADD CONSTRAINT pcc_family_unique UNIQUE (member_id, related_member_id);

--
-- Name: pcc_feedbacks pcc_feedbacks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pcc_feedbacks
    ADD CONSTRAINT pcc_feedbacks_pkey PRIMARY KEY (id);

--
-- Name: point_distributions point_distributions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.point_distributions
    ADD CONSTRAINT point_distributions_pkey PRIMARY KEY (id);

--
-- Name: point_gifts point_gifts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.point_gifts
    ADD CONSTRAINT point_gifts_pkey PRIMARY KEY (id);

--
-- Name: profiles profiles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.profiles
    ADD CONSTRAINT profiles_pkey PRIMARY KEY (id);

--
-- Name: profiles profiles_referral_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.profiles
    ADD CONSTRAINT profiles_referral_code_key UNIQUE (referral_code);

--
-- Name: promo_notification_requests promo_notification_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.promo_notification_requests
    ADD CONSTRAINT promo_notification_requests_pkey PRIMARY KEY (id);

--
-- Name: quota_change_logs quota_change_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quota_change_logs
    ADD CONSTRAINT quota_change_logs_pkey PRIMARY KEY (id);

--
-- Name: redemption_events redemption_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.redemption_events
    ADD CONSTRAINT redemption_events_pkey PRIMARY KEY (id);

--
-- Name: redemption_otp_requests redemption_otp_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.redemption_otp_requests
    ADD CONSTRAINT redemption_otp_requests_pkey PRIMARY KEY (id);

--
-- Name: referrals referrals_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.referrals
    ADD CONSTRAINT referrals_pkey PRIMARY KEY (id);

--
-- Name: reservation_guests reservation_guests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservation_guests
    ADD CONSTRAINT reservation_guests_pkey PRIMARY KEY (id);

--
-- Name: reservation_guests reservation_guests_reservation_phone_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservation_guests
    ADD CONSTRAINT reservation_guests_reservation_phone_unique UNIQUE (reservation_id, guest_phone);

--
-- Name: reservations reservations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservations
    ADD CONSTRAINT reservations_pkey PRIMARY KEY (id);

--
-- Name: resource_bookings resource_bookings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.resource_bookings
    ADD CONSTRAINT resource_bookings_pkey PRIMARY KEY (id);

--
-- Name: restaurant_expired_pool restaurant_expired_pool_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.restaurant_expired_pool
    ADD CONSTRAINT restaurant_expired_pool_pkey PRIMARY KEY (id);

--
-- Name: restaurant_gain_rules restaurant_gain_rules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.restaurant_gain_rules
    ADD CONSTRAINT restaurant_gain_rules_pkey PRIMARY KEY (id);

--
-- Name: restaurant_groups restaurant_groups_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.restaurant_groups
    ADD CONSTRAINT restaurant_groups_pkey PRIMARY KEY (id);

--
-- Name: restaurant_media restaurant_media_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.restaurant_media
    ADD CONSTRAINT restaurant_media_pkey PRIMARY KEY (id);

--
-- Name: restaurant_restitutions restaurant_restitutions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.restaurant_restitutions
    ADD CONSTRAINT restaurant_restitutions_pkey PRIMARY KEY (id);

--
-- Name: restaurant_restitutions restaurant_restitutions_restaurant_id_period_month_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.restaurant_restitutions
    ADD CONSTRAINT restaurant_restitutions_restaurant_id_period_month_key UNIQUE (restaurant_id, period_month);

--
-- Name: restaurant_services restaurant_services_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.restaurant_services
    ADD CONSTRAINT restaurant_services_pkey PRIMARY KEY (id);

--
-- Name: restaurant_staff restaurant_staff_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.restaurant_staff
    ADD CONSTRAINT restaurant_staff_pkey PRIMARY KEY (id);

--
-- Name: restaurant_staff restaurant_staff_user_id_restaurant_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.restaurant_staff
    ADD CONSTRAINT restaurant_staff_user_id_restaurant_id_key UNIQUE (user_id, restaurant_id);

--
-- Name: restaurant_tables restaurant_tables_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.restaurant_tables
    ADD CONSTRAINT restaurant_tables_pkey PRIMARY KEY (id);

--
-- Name: restaurant_tier_config restaurant_tier_config_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.restaurant_tier_config
    ADD CONSTRAINT restaurant_tier_config_pkey PRIMARY KEY (id);

--
-- Name: restaurant_tier_config restaurant_tier_config_slug_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.restaurant_tier_config
    ADD CONSTRAINT restaurant_tier_config_slug_key UNIQUE (slug);

--
-- Name: restaurant_tier_status restaurant_tier_status_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.restaurant_tier_status
    ADD CONSTRAINT restaurant_tier_status_pkey PRIMARY KEY (id);

--
-- Name: restaurant_tier_status restaurant_tier_status_restaurant_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.restaurant_tier_status
    ADD CONSTRAINT restaurant_tier_status_restaurant_id_key UNIQUE (restaurant_id);

--
-- Name: restaurant_zones restaurant_zones_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.restaurant_zones
    ADD CONSTRAINT restaurant_zones_pkey PRIMARY KEY (id);

--
-- Name: restaurants restaurants_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.restaurants
    ADD CONSTRAINT restaurants_pkey PRIMARY KEY (id);

--
-- Name: restaurants restaurants_referral_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.restaurants
    ADD CONSTRAINT restaurants_referral_code_key UNIQUE (referral_code);

--
-- Name: rule_templates rule_templates_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rule_templates
    ADD CONSTRAINT rule_templates_pkey PRIMARY KEY (id);

--
-- Name: rule_templates rule_templates_slug_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rule_templates
    ADD CONSTRAINT rule_templates_slug_key UNIQUE (slug);

--
-- Name: scanned_tickets scanned_tickets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.scanned_tickets
    ADD CONSTRAINT scanned_tickets_pkey PRIMARY KEY (id);

--
-- Name: seminar_requests seminar_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seminar_requests
    ADD CONSTRAINT seminar_requests_pkey PRIMARY KEY (id);

--
-- Name: staff_notification_preferences staff_notification_preferences_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.staff_notification_preferences
    ADD CONSTRAINT staff_notification_preferences_pkey PRIMARY KEY (id);

--
-- Name: staff_notification_preferences staff_notification_preferences_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.staff_notification_preferences
    ADD CONSTRAINT staff_notification_preferences_user_id_key UNIQUE (user_id);

--
-- Name: staff_role_permissions staff_role_permissions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.staff_role_permissions
    ADD CONSTRAINT staff_role_permissions_pkey PRIMARY KEY (id);

--
-- Name: staff_role_permissions staff_role_permissions_staff_role_permission_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.staff_role_permissions
    ADD CONSTRAINT staff_role_permissions_staff_role_permission_id_key UNIQUE (staff_role, permission_id);

--
-- Name: support_tickets support_tickets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.support_tickets
    ADD CONSTRAINT support_tickets_pkey PRIMARY KEY (id);

--
-- Name: system_alert_rules system_alert_rules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_alert_rules
    ADD CONSTRAINT system_alert_rules_pkey PRIMARY KEY (id);

--
-- Name: system_alerts system_alerts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_alerts
    ADD CONSTRAINT system_alerts_pkey PRIMARY KEY (id);

--
-- Name: system_health_checks system_health_checks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_health_checks
    ADD CONSTRAINT system_health_checks_pkey PRIMARY KEY (id);

--
-- Name: team_invitations team_invitations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.team_invitations
    ADD CONSTRAINT team_invitations_pkey PRIMARY KEY (id);

--
-- Name: tenant_admins tenant_admins_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_admins
    ADD CONSTRAINT tenant_admins_pkey PRIMARY KEY (tenant_id, user_id);

--
-- Name: tenant_announcements tenant_announcements_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_announcements
    ADD CONSTRAINT tenant_announcements_pkey PRIMARY KEY (id);

--
-- Name: tenant_branding tenant_branding_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_branding
    ADD CONSTRAINT tenant_branding_pkey PRIMARY KEY (tenant_id);

--
-- Name: tenant_events tenant_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_events
    ADD CONSTRAINT tenant_events_pkey PRIMARY KEY (id);

--
-- Name: tenant_features tenant_features_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_features
    ADD CONSTRAINT tenant_features_pkey PRIMARY KEY (tenant_id, feature_key);

--
-- Name: tenants tenants_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenants
    ADD CONSTRAINT tenants_pkey PRIMARY KEY (id);

--
-- Name: tenants tenants_slug_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenants
    ADD CONSTRAINT tenants_slug_key UNIQUE (slug);

--
-- Name: tier_restaurant_offers tier_restaurant_offers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tier_restaurant_offers
    ADD CONSTRAINT tier_restaurant_offers_pkey PRIMARY KEY (id);

--
-- Name: tier_restaurant_offers tier_restaurant_offers_restaurant_id_tier_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tier_restaurant_offers
    ADD CONSTRAINT tier_restaurant_offers_restaurant_id_tier_name_key UNIQUE (restaurant_id, tier_name);

--
-- Name: tier_thresholds tier_thresholds_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tier_thresholds
    ADD CONSTRAINT tier_thresholds_pkey PRIMARY KEY (id);

--
-- Name: tier_thresholds tier_thresholds_tier_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tier_thresholds
    ADD CONSTRAINT tier_thresholds_tier_name_key UNIQUE (tier_name);

--
-- Name: user_favorites user_favorites_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_favorites
    ADD CONSTRAINT user_favorites_pkey PRIMARY KEY (id);

--
-- Name: user_favorites user_favorites_user_id_restaurant_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_favorites
    ADD CONSTRAINT user_favorites_user_id_restaurant_id_key UNIQUE (user_id, restaurant_id);

--
-- Name: user_roles user_roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT user_roles_pkey PRIMARY KEY (id);

--
-- Name: user_roles user_roles_user_id_role_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT user_roles_user_id_role_key UNIQUE (user_id, role);

--
-- Name: idx_action_logs_restaurant_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_action_logs_restaurant_created ON public.action_logs USING btree (restaurant_id, created_at DESC);

--
-- Name: idx_action_logs_type_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_action_logs_type_created ON public.action_logs USING btree (type, created_at DESC);

--
-- Name: idx_action_logs_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_action_logs_user_id ON public.action_logs USING btree (user_id);

--
-- Name: idx_admin_audit_log_actor; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_admin_audit_log_actor ON public.admin_audit_log USING btree (actor_id, created_at DESC) WHERE (actor_id IS NOT NULL);

--
-- Name: idx_admin_audit_log_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_admin_audit_log_created_at ON public.admin_audit_log USING btree (created_at DESC);

--
-- Name: idx_admin_audit_log_entity; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_admin_audit_log_entity ON public.admin_audit_log USING btree (entity_type, entity_id, created_at DESC) WHERE (entity_id IS NOT NULL);

--
-- Name: idx_admin_notif_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_admin_notif_created ON public.admin_notifications USING btree (created_at DESC);

--
-- Name: idx_admin_notif_unread; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_admin_notif_unread ON public.admin_notifications USING btree (admin_id, created_at DESC) WHERE ((read_at IS NULL) AND (dismissed_at IS NULL));

--
-- Name: idx_admin_wallet_admin_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_admin_wallet_admin_id ON public.admin_wallet_transactions USING btree (admin_id);

--
-- Name: idx_admin_wallet_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_admin_wallet_created_at ON public.admin_wallet_transactions USING btree (created_at DESC);

--
-- Name: idx_admin_wallet_reason; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_admin_wallet_reason ON public.admin_wallet_transactions USING btree (reason);

--
-- Name: idx_ai_usage_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ai_usage_user ON public.ai_usage USING btree (user_id);

--
-- Name: idx_alerts_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_alerts_created ON public.system_alerts USING btree (created_at DESC);

--
-- Name: idx_alerts_unack; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_alerts_unack ON public.system_alerts USING btree (acknowledged, created_at DESC);

--
-- Name: idx_announcement_reads_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_announcement_reads_user ON public.announcement_reads USING btree (user_id);

--
-- Name: idx_announcements_scheduled_pending; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_announcements_scheduled_pending ON public.tenant_announcements USING btree (publish_at) WHERE ((push_sent_at IS NULL) AND (deleted_at IS NULL));

--
-- Name: idx_announcements_target_restaurants; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_announcements_target_restaurants ON public.tenant_announcements USING gin (target_restaurant_ids) WHERE (target_restaurant_ids IS NOT NULL);

--
-- Name: idx_announcements_tenant_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_announcements_tenant_created ON public.tenant_announcements USING btree (tenant_id, created_at DESC) WHERE (deleted_at IS NULL);

--
-- Name: idx_announcements_tenant_pinned_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_announcements_tenant_pinned_active ON public.tenant_announcements USING btree (tenant_id, priority, is_pinned) WHERE ((archived_at IS NULL) AND (deleted_at IS NULL));

--
-- Name: idx_audit_log_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_log_tenant ON public.admin_audit_log USING btree (tenant_id) WHERE (tenant_id IS NOT NULL);

--
-- Name: idx_bookable_resources_resto; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bookable_resources_resto ON public.bookable_resources USING btree (restaurant_id, resource_type) WHERE (enabled = true);

--
-- Name: idx_booking_rules_restaurant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_booking_rules_restaurant ON public.booking_rules USING btree (restaurant_id);

--
-- Name: idx_chat_messages_ticket_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chat_messages_ticket_id ON public.chat_messages USING btree (ticket_id);

--
-- Name: idx_client_ratings_client_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_client_ratings_client_id ON public.client_ratings USING btree (client_id);

--
-- Name: idx_client_ratings_pending; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_client_ratings_pending ON public.client_ratings USING btree (client_id) WHERE (pending_rating IS NOT NULL);

--
-- Name: INDEX idx_client_ratings_pending; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON INDEX public.idx_client_ratings_pending IS 'perf-mid #16 (30/04) : speedup promote-pending-client-ratings cron (filter pending_rating IS NOT NULL).';

--
-- Name: idx_contact_import_events_user_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_contact_import_events_user_date ON public.contact_import_events USING btree (user_id, imported_at DESC);

--
-- Name: idx_contract_history_contract; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_contract_history_contract ON public.contract_history USING btree (contract_id, created_at DESC);

--
-- Name: idx_contract_template_articles_template; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_contract_template_articles_template ON public.contract_template_articles USING btree (template_id, sort_order);

--
-- Name: idx_contracts_pending_15d_alert; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_contracts_pending_15d_alert ON public.partner_contracts USING btree (contract_end) WHERE ((expiration_notified_15d_at IS NULL) AND (status = ANY (ARRAY['actif'::text, 'renouvellement'::text])));

--
-- Name: idx_contracts_pending_30d_alert; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_contracts_pending_30d_alert ON public.partner_contracts USING btree (contract_end) WHERE ((expiration_notified_30d_at IS NULL) AND (status = ANY (ARRAY['actif'::text, 'renouvellement'::text])));

--
-- Name: idx_contracts_pending_7d_alert; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_contracts_pending_7d_alert ON public.partner_contracts USING btree (contract_end) WHERE ((expiration_notified_7d_at IS NULL) AND (status = ANY (ARRAY['actif'::text, 'renouvellement'::text])));

--
-- Name: idx_device_tokens_updated_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_device_tokens_updated_at ON public.device_tokens USING btree (updated_at);

--
-- Name: INDEX idx_device_tokens_updated_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON INDEX public.idx_device_tokens_updated_at IS 'perf-mid #16 (30/04) : speedup cleanup-stale-push-tokens cron (WHERE updated_at < cutoff).';

--
-- Name: idx_device_tokens_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_device_tokens_user_id ON public.device_tokens USING btree (user_id);

--
-- Name: idx_document_versions_doc_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_document_versions_doc_id ON public.document_versions USING btree (document_id, created_at DESC);

--
-- Name: idx_elite_applications_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_elite_applications_status ON public.elite_applications USING btree (status, created_at DESC);

--
-- Name: idx_elite_events_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_elite_events_active ON public.elite_events USING btree (is_active, event_date DESC);

--
-- Name: idx_elite_rsvps_event; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_elite_rsvps_event ON public.elite_rsvps USING btree (event_id);

--
-- Name: idx_elite_rsvps_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_elite_rsvps_user ON public.elite_rsvps USING btree (user_id);

--
-- Name: idx_email_bounces_email_suppressed; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_email_bounces_email_suppressed ON public.email_bounces USING btree (email) WHERE (is_suppressed = true);

--
-- Name: idx_email_bounces_last_bounced; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_email_bounces_last_bounced ON public.email_bounces USING btree (last_bounced_at DESC);

--
-- Name: idx_event_rsvps_event_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_event_rsvps_event_id ON public.event_rsvps USING btree (event_id);

--
-- Name: idx_event_rsvps_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_event_rsvps_user_id ON public.event_rsvps USING btree (user_id);

--
-- Name: idx_expired_points_client; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_expired_points_client ON public.expired_points USING btree (client_id, expired_at DESC);

--
-- Name: idx_expired_pool_unconsolidated; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_expired_pool_unconsolidated ON public.restaurant_expired_pool USING btree (restaurant_id, created_at) WHERE (consolidated_at IS NULL);

--
-- Name: idx_explore_featured_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_explore_featured_active ON public.explore_featured USING btree (is_active, "position") WHERE (is_active = true);

--
-- Name: idx_fraud_alerts_restaurant_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_fraud_alerts_restaurant_status ON public.fraud_alerts USING btree (restaurant_id, status);

--
-- Name: idx_gain_rule_requests_restaurant_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_gain_rule_requests_restaurant_status ON public.gain_rule_requests USING btree (restaurant_id, status);

--
-- Name: idx_gain_rules_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_gain_rules_tenant ON public.gain_rules USING btree (tenant_id) WHERE (tenant_id IS NOT NULL);

--
-- Name: idx_health_checks_metric_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_health_checks_metric_created ON public.system_health_checks USING btree (metric_name, created_at DESC);

--
-- Name: idx_hi_invoices_restaurant_period; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_hi_invoices_restaurant_period ON public.oneclick_hi_invoices USING btree (restaurant_id, period_month);

--
-- Name: idx_hi_invoices_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_hi_invoices_status ON public.oneclick_hi_invoices USING btree (status);

--
-- Name: idx_invoice_lines_invoice_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_invoice_lines_invoice_id ON public.invoice_lines USING btree (invoice_id, sort_order);

--
-- Name: idx_lifecycle_events_restaurant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lifecycle_events_restaurant ON public.lifecycle_events USING btree (restaurant_id, created_at DESC);

--
-- Name: idx_loyalty_points_available; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_loyalty_points_available ON public.loyalty_points USING btree (client_id, restaurant_id) WHERE (remaining_points > 0);

--
-- Name: idx_loyalty_points_client; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_loyalty_points_client ON public.loyalty_points USING btree (client_id, earned_at DESC);

--
-- Name: idx_loyalty_points_client_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_loyalty_points_client_active ON public.loyalty_points USING btree (client_id, earned_at DESC) WHERE (remaining_points > 0);

--
-- Name: idx_loyalty_points_credited_by; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_loyalty_points_credited_by ON public.loyalty_points USING btree (credited_by);

--
-- Name: idx_loyalty_points_expiration; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_loyalty_points_expiration ON public.loyalty_points USING btree (client_id, expires_at) WHERE ((points > 0) AND (remaining_points > 0));

--
-- Name: idx_loyalty_points_expiration_scan; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_loyalty_points_expiration_scan ON public.loyalty_points USING btree (expires_at) WHERE ((remaining_points > 0) AND (expires_at IS NOT NULL));

--
-- Name: idx_loyalty_points_expires_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_loyalty_points_expires_active ON public.loyalty_points USING btree (expires_at) WHERE ((remaining_points > 0) AND (expires_at IS NOT NULL));

--
-- Name: idx_loyalty_points_expiry; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_loyalty_points_expiry ON public.loyalty_points USING btree (client_id, expires_at) WHERE (expires_at IS NOT NULL);

--
-- Name: idx_loyalty_points_fifo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_loyalty_points_fifo ON public.loyalty_points USING btree (client_id, earned_at) WHERE ((remaining_points > 0) AND (points > 0));

--
-- Name: idx_loyalty_points_reason; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_loyalty_points_reason ON public.loyalty_points USING btree (reason);

--
-- Name: idx_loyalty_points_restaurant_earned_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_loyalty_points_restaurant_earned_at ON public.loyalty_points USING btree (restaurant_id, earned_at);

--
-- Name: idx_m3ak_hi_invoices_period; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_m3ak_hi_invoices_period ON public.oneclick_hi_invoices USING btree (period_month DESC);

--
-- Name: idx_m3ak_hi_invoices_restaurant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_m3ak_hi_invoices_restaurant ON public.oneclick_hi_invoices USING btree (restaurant_id);

--
-- Name: idx_monitor_logs_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_monitor_logs_created_at ON public.monitor_logs USING btree (created_at DESC);

--
-- Name: idx_monitor_logs_event_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_monitor_logs_event_type ON public.monitor_logs USING btree (event_type, created_at DESC);

--
-- Name: idx_monitor_logs_source; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_monitor_logs_source ON public.monitor_logs USING btree (source, created_at DESC);

--
-- Name: idx_monitor_logs_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_monitor_logs_status ON public.monitor_logs USING btree (status) WHERE (status = 'error'::text);

--
-- Name: idx_mv_reservations_summary_client; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_mv_reservations_summary_client ON public.mv_reservations_summary USING btree (client_id, date DESC);

--
-- Name: idx_mv_reservations_summary_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX idx_mv_reservations_summary_id ON public.mv_reservations_summary USING btree (id);

--
-- Name: idx_mv_reservations_summary_restaurant_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_mv_reservations_summary_restaurant_date ON public.mv_reservations_summary USING btree (restaurant_id, date DESC);

--
-- Name: idx_no_show_disputes_client; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_no_show_disputes_client ON public.no_show_disputes USING btree (client_id, created_at DESC);

--
-- Name: idx_no_show_disputes_pending; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_no_show_disputes_pending ON public.no_show_disputes USING btree (status, escalation_phase) WHERE (status = 'pending'::text);

--
-- Name: idx_no_show_disputes_resa; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_no_show_disputes_resa ON public.no_show_disputes USING btree (reservation_id);

--
-- Name: idx_notifications_restaurant_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notifications_restaurant_id ON public.notifications USING btree (restaurant_id) WHERE (restaurant_id IS NOT NULL);

--
-- Name: idx_notifications_user_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notifications_user_created ON public.notifications USING btree (user_id, created_at DESC);

--
-- Name: idx_notifications_user_read; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notifications_user_read ON public.notifications USING btree (user_id, read, created_at DESC);

--
-- Name: idx_notifications_user_unread; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notifications_user_unread ON public.notifications USING btree (user_id, read) WHERE (read = false);

--
-- Name: idx_notifications_user_unread_recent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notifications_user_unread_recent ON public.notifications USING btree (user_id, created_at DESC) WHERE (read = false);

--
-- Name: idx_offer_impressions_offer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_offer_impressions_offer ON public.offer_impressions USING btree (offer_id);

--
-- Name: idx_offer_impressions_user_offer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_offer_impressions_user_offer ON public.offer_impressions USING btree (user_id, offer_id);

--
-- Name: idx_offers_active_dates; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_offers_active_dates ON public.offers USING btree (is_active, starts_at, expires_at) WHERE (is_active = true);

--
-- Name: idx_offers_active_restaurant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_offers_active_restaurant ON public.offers USING btree (restaurant_id, expires_at) WHERE (is_active = true);

--
-- Name: idx_offers_campaign_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_offers_campaign_id ON public.offers USING btree (campaign_id) WHERE (campaign_id IS NOT NULL);

--
-- Name: idx_offers_restaurant_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_offers_restaurant_active ON public.offers USING btree (restaurant_id, is_active) WHERE (is_active = true);

--
-- Name: idx_offers_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_offers_tenant ON public.offers USING btree (tenant_id) WHERE (tenant_id IS NOT NULL);

--
-- Name: idx_onboarding_requests_email; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_onboarding_requests_email ON public.onboarding_requests USING btree (email);

--
-- Name: idx_onboarding_requests_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_onboarding_requests_status ON public.onboarding_requests USING btree (status, created_at DESC);

--
-- Name: idx_onboarding_requests_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_onboarding_requests_tenant ON public.onboarding_requests USING btree (tenant_id) WHERE (tenant_id IS NOT NULL);

--
-- Name: idx_oneclick_hi_invoices_number; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX idx_oneclick_hi_invoices_number ON public.oneclick_hi_invoices USING btree (invoice_number) WHERE (invoice_number IS NOT NULL);

--
-- Name: idx_otp_client_pending; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_otp_client_pending ON public.redemption_otp_requests USING btree (client_id, created_at DESC) WHERE (status = 'pending'::text);

--
-- Name: idx_otp_restaurant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_otp_restaurant ON public.redemption_otp_requests USING btree (restaurant_id, created_at DESC);

--
-- Name: idx_partner_contracts_restaurant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_partner_contracts_restaurant ON public.partner_contracts USING btree (restaurant_id);

--
-- Name: idx_partner_contracts_restaurant_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_partner_contracts_restaurant_status ON public.partner_contracts USING btree (restaurant_id, status);

--
-- Name: idx_partner_contracts_template; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_partner_contracts_template ON public.partner_contracts USING btree (template_id);

--
-- Name: idx_pcc_family_member_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pcc_family_member_id ON public.pcc_family_members USING btree (member_id);

--
-- Name: idx_pcc_family_related_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pcc_family_related_id ON public.pcc_family_members USING btree (related_member_id);

--
-- Name: idx_pcc_feedbacks_member; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pcc_feedbacks_member ON public.pcc_feedbacks USING btree (member_id, created_at DESC);

--
-- Name: idx_pcc_feedbacks_pending; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pcc_feedbacks_pending ON public.pcc_feedbacks USING btree (created_at DESC) WHERE (reply_text IS NULL);

--
-- Name: idx_pcc_feedbacks_target_restaurant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pcc_feedbacks_target_restaurant ON public.pcc_feedbacks USING btree (target_restaurant_id) WHERE (target_restaurant_id IS NOT NULL);

--
-- Name: idx_pcc_feedbacks_unread_replies; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pcc_feedbacks_unread_replies ON public.pcc_feedbacks USING btree (member_id, reply_at DESC) WHERE ((reply_text IS NOT NULL) AND (reply_read_by_member = false));

--
-- Name: idx_point_distributions_admin; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_point_distributions_admin ON public.point_distributions USING btree (admin_id);

--
-- Name: idx_point_distributions_campaign; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_point_distributions_campaign ON public.point_distributions USING btree (campaign_id) WHERE (campaign_id IS NOT NULL);

--
-- Name: idx_point_distributions_client; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_point_distributions_client ON public.point_distributions USING btree (client_id);

--
-- Name: idx_point_distributions_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_point_distributions_created ON public.point_distributions USING btree (created_at DESC);

--
-- Name: idx_point_distributions_offer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_point_distributions_offer ON public.point_distributions USING btree (offer_id) WHERE (offer_id IS NOT NULL);

--
-- Name: idx_point_distributions_restaurant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_point_distributions_restaurant ON public.point_distributions USING btree (restaurant_id);

--
-- Name: idx_point_distributions_source_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_point_distributions_source_created ON public.point_distributions USING btree (source_type, created_at DESC);

--
-- Name: idx_profiles_allergens; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_profiles_allergens ON public.profiles USING gin (allergens) WHERE (array_length(allergens, 1) > 0);

--
-- Name: idx_profiles_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_profiles_created_at ON public.profiles USING btree (created_at DESC);

--
-- Name: idx_profiles_phone; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_profiles_phone ON public.profiles USING btree (phone) WHERE (phone IS NOT NULL);

--
-- Name: idx_profiles_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_profiles_tenant ON public.profiles USING btree (tenant_id) WHERE (tenant_id IS NOT NULL);

--
-- Name: idx_profiles_tenant_group_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_profiles_tenant_group_id ON public.profiles USING btree (tenant_group_id) WHERE (tenant_group_id IS NOT NULL);

--
-- Name: idx_punch_cards_client; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_punch_cards_client ON public.loyalty_punch_cards USING btree (client_id, activity_type);

--
-- Name: idx_quota_change_logs_restaurant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_quota_change_logs_restaurant ON public.quota_change_logs USING btree (restaurant_id, created_at DESC);

--
-- Name: idx_redemption_events_accepted_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_redemption_events_accepted_date ON public.redemption_events USING btree (accepted, created_at DESC);

--
-- Name: idx_redemption_events_client_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_redemption_events_client_id ON public.redemption_events USING btree (client_id, created_at DESC);

--
-- Name: idx_redemption_events_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_redemption_events_created_at ON public.redemption_events USING btree (created_at DESC);

--
-- Name: idx_redemption_events_restaurant_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_redemption_events_restaurant_id ON public.redemption_events USING btree (restaurant_id, created_at DESC);

--
-- Name: idx_redemption_events_suspicious; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_redemption_events_suspicious ON public.redemption_events USING btree (created_at DESC) WHERE (flag_ratio_high OR flag_daily_near_cap OR flag_large_absolute OR (NOT accepted));

--
-- Name: idx_referrals_referred_phone; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_referrals_referred_phone ON public.referrals USING btree (referred_phone);

--
-- Name: idx_referrals_referrer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_referrals_referrer ON public.referrals USING btree (referrer_id, status);

--
-- Name: idx_referrals_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_referrals_status ON public.referrals USING btree (status);

--
-- Name: idx_reservation_guests_guest_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reservation_guests_guest_user ON public.reservation_guests USING btree (guest_user_id) WHERE (guest_user_id IS NOT NULL);

--
-- Name: idx_reservation_guests_phone; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reservation_guests_phone ON public.reservation_guests USING btree (guest_phone);

--
-- Name: idx_reservation_guests_reservation; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reservation_guests_reservation ON public.reservation_guests USING btree (reservation_id);

--
-- Name: idx_reservation_guests_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reservation_guests_user ON public.reservation_guests USING btree (guest_user_id);

--
-- Name: idx_reservations_client_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reservations_client_date ON public.reservations USING btree (client_id, date DESC);

--
-- Name: idx_reservations_client_restaurant_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reservations_client_restaurant_date ON public.reservations USING btree (client_id, restaurant_id, date) WHERE (status <> ALL (ARRAY['annulée'::public.reservation_status, 'refusée'::public.reservation_status, 'no_show'::public.reservation_status]));

--
-- Name: idx_reservations_client_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reservations_client_status ON public.reservations USING btree (client_id, status);

--
-- Name: idx_reservations_created_at_desc; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reservations_created_at_desc ON public.reservations USING btree (created_at DESC) WHERE (restaurant_id IS NOT NULL);

--
-- Name: INDEX idx_reservations_created_at_desc; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON INDEX public.idx_reservations_created_at_desc IS 'perf-mid #17 (30/04) : speedup KPI admin Top reservations (filter created_at + GROUP BY restaurant_id). Anti-scaling 100K+ rows.';

--
-- Name: idx_reservations_no_show_pending; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reservations_no_show_pending ON public.reservations USING btree (no_show_marked_at) WHERE ((status = 'no_show'::public.reservation_status) AND (no_show_penalty_applied_at IS NULL));

--
-- Name: idx_reservations_reminders_pending; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reservations_reminders_pending ON public.reservations USING btree (date, heure) WHERE ((status = 'confirmée'::public.reservation_status) AND ((reminder_j1_sent_at IS NULL) OR (reminder_h2_sent_at IS NULL)));

--
-- Name: idx_reservations_restaurant_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reservations_restaurant_date ON public.reservations USING btree (restaurant_id, date DESC, heure);

--
-- Name: idx_reservations_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reservations_status ON public.reservations USING btree (status);

--
-- Name: idx_reservations_status_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reservations_status_created ON public.reservations USING btree (status, created_at DESC);

--
-- Name: idx_resource_bookings_organizer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_resource_bookings_organizer ON public.resource_bookings USING btree (organizer_id, start_at DESC);

--
-- Name: idx_resource_bookings_reminders_pending; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_resource_bookings_reminders_pending ON public.resource_bookings USING btree (start_at) WHERE ((status = 'confirmee'::public.resource_booking_status) AND ((reminder_j1_sent_at IS NULL) OR (reminder_h2_sent_at IS NULL)));

--
-- Name: idx_resource_bookings_resource_start; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_resource_bookings_resource_start ON public.resource_bookings USING btree (resource_id, start_at);

--
-- Name: idx_resource_bookings_status_start; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_resource_bookings_status_start ON public.resource_bookings USING btree (status, start_at);

--
-- Name: idx_restaurant_expired_pool_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_restaurant_expired_pool_created ON public.restaurant_expired_pool USING btree (created_at);

--
-- Name: idx_restaurant_expired_pool_restaurant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_restaurant_expired_pool_restaurant ON public.restaurant_expired_pool USING btree (restaurant_id);

--
-- Name: idx_restaurant_gain_rules_restaurant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_restaurant_gain_rules_restaurant ON public.restaurant_gain_rules USING btree (restaurant_id);

--
-- Name: idx_restaurant_gain_rules_restaurant_enabled; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_restaurant_gain_rules_restaurant_enabled ON public.restaurant_gain_rules USING btree (restaurant_id, enabled);

--
-- Name: idx_restaurant_gain_rules_source_rule; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_restaurant_gain_rules_source_rule ON public.restaurant_gain_rules USING btree (source_rule_id) WHERE (source_rule_id IS NOT NULL);

--
-- Name: idx_restaurant_groups_owner; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_restaurant_groups_owner ON public.restaurant_groups USING btree (owner_user_id) WHERE (owner_user_id IS NOT NULL);

--
-- Name: idx_restaurant_groups_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_restaurant_groups_tenant ON public.restaurant_groups USING btree (tenant_id) WHERE (tenant_id IS NOT NULL);

--
-- Name: idx_restaurant_media_restaurant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_restaurant_media_restaurant ON public.restaurant_media USING btree (restaurant_id);

--
-- Name: idx_restaurant_restitutions_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_restaurant_restitutions_status ON public.restaurant_restitutions USING btree (status);

--
-- Name: idx_restaurant_services_restaurant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_restaurant_services_restaurant ON public.restaurant_services USING btree (restaurant_id);

--
-- Name: idx_restaurant_staff_restaurant_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_restaurant_staff_restaurant_status ON public.restaurant_staff USING btree (restaurant_id, status);

--
-- Name: idx_restaurant_staff_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_restaurant_staff_user ON public.restaurant_staff USING btree (user_id, status);

--
-- Name: idx_restaurant_staff_user_role; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_restaurant_staff_user_role ON public.restaurant_staff USING btree (user_id, staff_role);

--
-- Name: idx_restaurant_tables_restaurant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_restaurant_tables_restaurant ON public.restaurant_tables USING btree (restaurant_id);

--
-- Name: idx_restaurant_tables_zone; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_restaurant_tables_zone ON public.restaurant_tables USING btree (zone_id);

--
-- Name: idx_restaurant_zones_restaurant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_restaurant_zones_restaurant ON public.restaurant_zones USING btree (restaurant_id);

--
-- Name: idx_restaurants_google_place_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_restaurants_google_place_id ON public.restaurants USING btree (google_place_id) WHERE (google_place_id IS NOT NULL);

--
-- Name: idx_restaurants_group; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_restaurants_group ON public.restaurants USING btree (group_id) WHERE (group_id IS NOT NULL);

--
-- Name: idx_restaurants_group_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_restaurants_group_status ON public.restaurants USING btree (group_id, status);

--
-- Name: idx_restaurants_onboarding_pending; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_restaurants_onboarding_pending ON public.restaurants USING btree (id) WHERE (onboarding_completed_at IS NULL);

--
-- Name: idx_restaurants_referral_code; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_restaurants_referral_code ON public.restaurants USING btree (referral_code) WHERE (referral_code IS NOT NULL);

--
-- Name: idx_restaurants_referred_by; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_restaurants_referred_by ON public.restaurants USING btree (referred_by_id) WHERE (referred_by_id IS NOT NULL);

--
-- Name: idx_restaurants_search_vector; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_restaurants_search_vector ON public.restaurants USING gin (search_vector);

--
-- Name: idx_restaurants_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_restaurants_status ON public.restaurants USING btree (status);

--
-- Name: idx_restaurants_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_restaurants_tenant ON public.restaurants USING btree (tenant_id) WHERE (tenant_id IS NOT NULL);

--
-- Name: idx_rgr_source_resto_unique; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX idx_rgr_source_resto_unique ON public.restaurant_gain_rules USING btree (restaurant_id, source_rule_id) WHERE (source_rule_id IS NOT NULL);

--
-- Name: idx_rule_templates_category; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_rule_templates_category ON public.rule_templates USING btree (category, enabled);

--
-- Name: idx_scanned_tickets_client; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_scanned_tickets_client ON public.scanned_tickets USING btree (client_id, created_at DESC);

--
-- Name: idx_scanned_tickets_client_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_scanned_tickets_client_date ON public.scanned_tickets USING btree (client_id, created_at DESC);

--
-- Name: idx_scanned_tickets_client_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_scanned_tickets_client_id ON public.scanned_tickets USING btree (client_id);

--
-- Name: idx_scanned_tickets_reservation; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_scanned_tickets_reservation ON public.scanned_tickets USING btree (reservation_id) WHERE (reservation_id IS NOT NULL);

--
-- Name: idx_scanned_tickets_restaurant_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_scanned_tickets_restaurant_created ON public.scanned_tickets USING btree (restaurant_id, created_at DESC);

--
-- Name: idx_scanned_tickets_restaurant_month; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_scanned_tickets_restaurant_month ON public.scanned_tickets USING btree (restaurant_id, created_at DESC);

--
-- Name: idx_scanned_tickets_resto_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_scanned_tickets_resto_date ON public.scanned_tickets USING btree (restaurant_id, created_at DESC);

--
-- Name: idx_scanned_tickets_scanned_by; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_scanned_tickets_scanned_by ON public.scanned_tickets USING btree (scanned_by);

--
-- Name: idx_scanned_tickets_ticket_ref; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_scanned_tickets_ticket_ref ON public.scanned_tickets USING btree (ticket_ref, restaurant_id);

--
-- Name: idx_scanned_tickets_unique_ref; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX idx_scanned_tickets_unique_ref ON public.scanned_tickets USING btree (ticket_ref, restaurant_id);

--
-- Name: idx_seminar_requests_tenant_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_seminar_requests_tenant_status ON public.seminar_requests USING btree (tenant_id, status, created_at DESC);

--
-- Name: idx_support_tickets_client; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_support_tickets_client ON public.support_tickets USING btree (client_id, status);

--
-- Name: idx_support_tickets_escalated; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_support_tickets_escalated ON public.support_tickets USING btree (escalated_to_admin) WHERE (escalated_to_admin = true);

--
-- Name: idx_support_tickets_priority; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_support_tickets_priority ON public.support_tickets USING btree (priority);

--
-- Name: idx_support_tickets_restaurant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_support_tickets_restaurant ON public.support_tickets USING btree (restaurant_id, status);

--
-- Name: idx_support_tickets_status_priority; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_support_tickets_status_priority ON public.support_tickets USING btree (status, created_at DESC);

--
-- Name: idx_support_tickets_type_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_support_tickets_type_status ON public.support_tickets USING btree (ticket_type, status);

--
-- Name: idx_team_invitations_restaurant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_team_invitations_restaurant ON public.team_invitations USING btree (restaurant_id, status);

--
-- Name: idx_tenant_admins_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tenant_admins_user ON public.tenant_admins USING btree (user_id);

--
-- Name: idx_tenant_events_tenant_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tenant_events_tenant_date ON public.tenant_events USING btree (tenant_id, event_date) WHERE (status = 'actif'::text);

--
-- Name: idx_tenant_events_visible; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tenant_events_visible ON public.tenant_events USING btree (tenant_id, event_date) WHERE (status = 'actif'::text);

--
-- Name: idx_tenants_slug; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tenants_slug ON public.tenants USING btree (slug);

--
-- Name: idx_tenants_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tenants_status ON public.tenants USING btree (status);

--
-- Name: idx_tier_offers_restaurant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tier_offers_restaurant ON public.tier_restaurant_offers USING btree (restaurant_id);

--
-- Name: idx_tier_offers_tier; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tier_offers_tier ON public.tier_restaurant_offers USING btree (tier_name);

--
-- Name: idx_user_favorites_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_favorites_user ON public.user_favorites USING btree (user_id);

--
-- Name: uniq_gain_rule_request_pending; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uniq_gain_rule_request_pending ON public.gain_rule_requests USING btree (restaurant_id, name) WHERE (status = 'en_attente'::text);

--
-- Name: uniq_scanned_ticket_reservation; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uniq_scanned_ticket_reservation ON public.scanned_tickets USING btree (reservation_id) WHERE (reservation_id IS NOT NULL);

--
-- Name: restaurant_staff enforce_max_active_staff; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER enforce_max_active_staff BEFORE INSERT OR UPDATE ON public.restaurant_staff FOR EACH ROW EXECUTE FUNCTION public.check_max_active_staff();

--
-- Name: reservations on_new_reservation; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER on_new_reservation AFTER INSERT ON public.reservations FOR EACH ROW EXECUTE FUNCTION public.log_new_reservation();

--
-- Name: reservations on_reservation_status_change; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER on_reservation_status_change AFTER UPDATE ON public.reservations FOR EACH ROW EXECUTE FUNCTION public.log_reservation_status_change();

--
-- Name: pcc_family_members pcc_family_max_10_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER pcc_family_max_10_trigger BEFORE INSERT ON public.pcc_family_members FOR EACH ROW EXECUTE FUNCTION public.check_pcc_family_max_10();

--
-- Name: pcc_family_members pcc_family_notify_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER pcc_family_notify_trigger AFTER INSERT ON public.pcc_family_members FOR EACH ROW EXECUTE FUNCTION public.notify_pcc_family_added();

--
-- Name: pcc_family_members pcc_family_updated_at_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER pcc_family_updated_at_trigger BEFORE UPDATE ON public.pcc_family_members FOR EACH ROW EXECUTE FUNCTION public.update_pcc_family_updated_at();

--
-- Name: tier_restaurant_offers set_tier_offers_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER set_tier_offers_updated_at BEFORE UPDATE ON public.tier_restaurant_offers FOR EACH ROW EXECUTE FUNCTION public.update_updated_at();

--
-- Name: tier_thresholds set_tier_thresholds_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER set_tier_thresholds_updated_at BEFORE UPDATE ON public.tier_thresholds FOR EACH ROW EXECUTE FUNCTION public.update_updated_at();

--
-- Name: restaurants trg_activate_restaurant_referral; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_activate_restaurant_referral BEFORE UPDATE OF status ON public.restaurants FOR EACH ROW EXECUTE FUNCTION public.trg_activate_restaurant_referral();

--
-- Name: tenant_announcements trg_announcement_body_version; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_announcement_body_version BEFORE UPDATE OF body ON public.tenant_announcements FOR EACH ROW EXECUTE FUNCTION public.bump_body_version_reset_reads();

--
-- Name: tenant_announcements trg_announcement_max_pinned; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_announcement_max_pinned AFTER INSERT OR UPDATE OF is_pinned, priority, archived_at, deleted_at ON public.tenant_announcements FOR EACH ROW EXECUTE FUNCTION public.enforce_max_pinned_per_priority();

--
-- Name: tenant_announcements trg_announcement_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_announcement_updated_at BEFORE UPDATE ON public.tenant_announcements FOR EACH ROW EXECUTE FUNCTION public.update_announcement_updated_at();

--
-- Name: bookable_resources trg_br_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_br_updated_at BEFORE UPDATE ON public.bookable_resources FOR EACH ROW EXECUTE FUNCTION public.trg_set_updated_at();

--
-- Name: no_show_disputes trg_check_dispute_count; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_check_dispute_count BEFORE INSERT ON public.no_show_disputes FOR EACH ROW EXECUTE FUNCTION public.check_dispute_count();

--
-- Name: reservation_guests trg_check_guest_invited_by_owner; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_check_guest_invited_by_owner BEFORE INSERT ON public.reservation_guests FOR EACH ROW EXECUTE FUNCTION public.check_guest_invited_by_owner();

--
-- Name: restaurant_staff trg_check_max_active_staff; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_check_max_active_staff BEFORE INSERT OR UPDATE ON public.restaurant_staff FOR EACH ROW EXECUTE FUNCTION public.check_max_active_staff();

--
-- Name: reservations trg_check_max_guests; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_check_max_guests BEFORE INSERT OR UPDATE OF couverts ON public.reservations FOR EACH ROW EXECUTE FUNCTION public.check_max_guests();

--
-- Name: reservations trg_check_max_reservations_per_day; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_check_max_reservations_per_day BEFORE INSERT ON public.reservations FOR EACH ROW EXECUTE FUNCTION public.check_max_reservations_per_day();

--
-- Name: reservations trg_check_reservation_not_in_past; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_check_reservation_not_in_past BEFORE INSERT ON public.reservations FOR EACH ROW EXECUTE FUNCTION public.check_reservation_not_in_past();

--
-- Name: reservations trg_check_reservation_update_valid; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_check_reservation_update_valid BEFORE UPDATE ON public.reservations FOR EACH ROW EXECUTE FUNCTION public.check_reservation_update_valid();

--
-- Name: partner_contracts trg_contracts_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_contracts_updated_at BEFORE UPDATE ON public.partner_contracts FOR EACH ROW EXECUTE FUNCTION public.update_updated_at();

--
-- Name: restaurants trg_create_default_booking_rules; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_create_default_booking_rules AFTER INSERT ON public.restaurants FOR EACH ROW EXECUTE FUNCTION public.create_default_booking_rules();

--
-- Name: restaurants trg_create_default_services; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_create_default_services AFTER INSERT ON public.restaurants FOR EACH ROW EXECUTE FUNCTION public.create_default_services();

--
-- Name: referrals trg_credit_referral_points; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_credit_referral_points BEFORE INSERT OR UPDATE ON public.referrals FOR EACH ROW EXECUTE FUNCTION public.credit_referral_points();

--
-- Name: email_bounces trg_email_bounces_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_email_bounces_updated_at BEFORE UPDATE ON public.email_bounces FOR EACH ROW EXECUTE FUNCTION public.update_updated_at();

--
-- Name: event_rsvps trg_event_rsvps_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_event_rsvps_updated_at BEFORE UPDATE ON public.event_rsvps FOR EACH ROW EXECUTE FUNCTION public.event_rsvps_set_updated_at();

--
-- Name: fraud_alerts trg_fraud_alerts_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_fraud_alerts_updated_at BEFORE UPDATE ON public.fraud_alerts FOR EACH ROW EXECUTE FUNCTION public.update_updated_at();

--
-- Name: friend_groups trg_friend_groups_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_friend_groups_updated_at BEFORE UPDATE ON public.friend_groups FOR EACH ROW EXECUTE FUNCTION public.update_updated_at();

--
-- Name: friendships trg_friendships_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_friendships_updated_at BEFORE UPDATE ON public.friendships FOR EACH ROW EXECUTE FUNCTION public.update_updated_at();

--
-- Name: gain_rules trg_gain_rules_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_gain_rules_updated_at BEFORE UPDATE ON public.gain_rules FOR EACH ROW EXECUTE FUNCTION public.update_updated_at();

--
-- Name: profiles trg_link_guest_on_profile_update; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_link_guest_on_profile_update AFTER INSERT OR UPDATE OF phone ON public.profiles FOR EACH ROW EXECUTE FUNCTION public.link_guest_by_phone();

--
-- Name: reservation_guests trg_link_reservation_guest_by_phone; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_link_reservation_guest_by_phone BEFORE INSERT ON public.reservation_guests FOR EACH ROW EXECUTE FUNCTION public.link_reservation_guest_by_phone();

--
-- Name: partner_contracts trg_log_contract_changes; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_log_contract_changes AFTER UPDATE ON public.partner_contracts FOR EACH ROW EXECUTE FUNCTION public.log_contract_changes();

--
-- Name: restaurant_media trg_media_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_media_updated_at BEFORE UPDATE ON public.restaurant_media FOR EACH ROW EXECUTE FUNCTION public.update_updated_at();

--
-- Name: support_tickets trg_notify_admin_support_ticket; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_notify_admin_support_ticket AFTER INSERT ON public.support_tickets FOR EACH ROW EXECUTE FUNCTION public.trg_notify_admin_support_ticket();

--
-- Name: elite_applications trg_notify_new_elite_application; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_notify_new_elite_application AFTER INSERT ON public.elite_applications FOR EACH ROW EXECUTE FUNCTION public.notify_admin_new_elite_application();

--
-- Name: gain_rule_requests trg_notify_new_rule_request; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_notify_new_rule_request AFTER INSERT ON public.gain_rule_requests FOR EACH ROW EXECUTE FUNCTION public.notify_admin_new_rule_request();

--
-- Name: redemption_events trg_notify_on_redemption_reject; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_notify_on_redemption_reject AFTER INSERT ON public.redemption_events FOR EACH ROW EXECUTE FUNCTION public.notify_admin_on_redemption_reject();

--
-- Name: onboarding_requests trg_onboarding_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_onboarding_updated_at BEFORE UPDATE ON public.onboarding_requests FOR EACH ROW EXECUTE FUNCTION public.set_onboarding_updated_at();

--
-- Name: loyalty_punch_cards trg_pc_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_pc_updated_at BEFORE UPDATE ON public.loyalty_punch_cards FOR EACH ROW EXECUTE FUNCTION public.trg_set_updated_at();

--
-- Name: pcc_feedbacks trg_pcc_feedbacks_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_pcc_feedbacks_updated_at BEFORE UPDATE ON public.pcc_feedbacks FOR EACH ROW EXECUTE FUNCTION public.set_pcc_feedbacks_updated_at();

--
-- Name: loyalty_plafonds trg_plafonds_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_plafonds_updated_at BEFORE UPDATE ON public.loyalty_plafonds FOR EACH ROW EXECUTE FUNCTION public.update_updated_at();

--
-- Name: resource_bookings trg_prevent_organizer_overlap; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_prevent_organizer_overlap BEFORE INSERT OR UPDATE OF start_at, end_at, status, organizer_id ON public.resource_bookings FOR EACH ROW EXECUTE FUNCTION public.prevent_organizer_overlap();

--
-- Name: TRIGGER trg_prevent_organizer_overlap ON resource_bookings; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TRIGGER trg_prevent_organizer_overlap ON public.resource_bookings IS 'Anti-overlap cross-modules pour les membres PCC. Sprint PCC bugfix 24/04.';

--
-- Name: profiles trg_profiles_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_profiles_updated_at BEFORE UPDATE ON public.profiles FOR EACH ROW EXECUTE FUNCTION public.update_updated_at();

--
-- Name: reservations trg_propagate_cancellation; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_propagate_cancellation AFTER UPDATE OF status ON public.reservations FOR EACH ROW EXECUTE FUNCTION public.propagate_cancellation_to_guests();

--
-- Name: reservations trg_reservation_push_insert; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_reservation_push_insert AFTER INSERT ON public.reservations FOR EACH ROW EXECUTE FUNCTION public.notify_reservation_change();

--
-- Name: reservations trg_reservation_push_update; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_reservation_push_update AFTER UPDATE ON public.reservations FOR EACH ROW WHEN ((old.status IS DISTINCT FROM new.status)) EXECUTE FUNCTION public.notify_reservation_change();

--
-- Name: reservations trg_reservations_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_reservations_updated_at BEFORE UPDATE ON public.reservations FOR EACH ROW EXECUTE FUNCTION public.update_updated_at();

--
-- Name: reservations trg_reset_reservation_reminders; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_reset_reservation_reminders BEFORE UPDATE ON public.reservations FOR EACH ROW EXECUTE FUNCTION public.reset_reservation_reminders_on_reschedule();

--
-- Name: resource_bookings trg_reset_resource_booking_reminders; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_reset_resource_booking_reminders BEFORE UPDATE OF start_at, end_at ON public.resource_bookings FOR EACH ROW EXECUTE FUNCTION public.reset_resource_booking_reminders_on_reschedule();

--
-- Name: resource_bookings trg_resource_booking_punch_card; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_resource_booking_punch_card AFTER UPDATE ON public.resource_bookings FOR EACH ROW EXECUTE FUNCTION public.trg_punch_card_on_booking_honoree();

--
-- Name: resource_bookings trg_resource_booking_rating_impact; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_resource_booking_rating_impact AFTER UPDATE ON public.resource_bookings FOR EACH ROW EXECUTE FUNCTION public.trg_no_show_impact_rating();

--
-- Name: restaurants trg_restaurants_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_restaurants_updated_at BEFORE UPDATE ON public.restaurants FOR EACH ROW EXECUTE FUNCTION public.update_updated_at();

--
-- Name: booking_rules trg_rules_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_rules_updated_at BEFORE UPDATE ON public.booking_rules FOR EACH ROW EXECUTE FUNCTION public.update_updated_at();

--
-- Name: restaurant_services trg_services_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_services_updated_at BEFORE UPDATE ON public.restaurant_services FOR EACH ROW EXECUTE FUNCTION public.update_updated_at();

--
-- Name: admin_wallet_transactions trg_set_admin_wallet_expiration; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_set_admin_wallet_expiration BEFORE INSERT ON public.admin_wallet_transactions FOR EACH ROW EXECUTE FUNCTION public.set_admin_wallet_expiration();

--
-- Name: loyalty_points trg_set_loyalty_expiration; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_set_loyalty_expiration BEFORE INSERT ON public.loyalty_points FOR EACH ROW EXECUTE FUNCTION public.set_loyalty_expiration();

--
-- Name: reservations trg_set_no_show_marked_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_set_no_show_marked_at BEFORE UPDATE ON public.reservations FOR EACH ROW WHEN ((old.status IS DISTINCT FROM new.status)) EXECUTE FUNCTION public.set_no_show_marked_at();

--
-- Name: profiles trg_set_referral_code; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_set_referral_code BEFORE INSERT ON public.profiles FOR EACH ROW EXECUTE FUNCTION public.set_referral_code();

--
-- Name: restaurants trg_set_restaurant_referral_code; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_set_restaurant_referral_code BEFORE INSERT ON public.restaurants FOR EACH ROW EXECUTE FUNCTION public.trg_set_restaurant_referral_code();

--
-- Name: staff_notification_preferences trg_staff_notification_preferences_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_staff_notification_preferences_updated_at BEFORE UPDATE ON public.staff_notification_preferences FOR EACH ROW EXECUTE FUNCTION public.update_updated_at();

--
-- Name: gain_rules trg_sync_gain_rule_to_tier; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_sync_gain_rule_to_tier AFTER INSERT OR UPDATE ON public.gain_rules FOR EACH ROW EXECUTE FUNCTION public.sync_gain_rule_to_tier();

--
-- Name: restaurant_tables trg_tables_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_tables_updated_at BEFORE UPDATE ON public.restaurant_tables FOR EACH ROW EXECUTE FUNCTION public.update_updated_at();

--
-- Name: tenant_branding trg_tenant_branding_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_tenant_branding_updated_at BEFORE UPDATE ON public.tenant_branding FOR EACH ROW EXECUTE FUNCTION public.tenant_branding_touch_updated_at();

--
-- Name: tenants trg_tenants_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_tenants_updated_at BEFORE UPDATE ON public.tenants FOR EACH ROW EXECUTE FUNCTION public.tenant_branding_touch_updated_at();

--
-- Name: reservations trg_update_client_rating; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_update_client_rating AFTER UPDATE ON public.reservations FOR EACH ROW EXECUTE FUNCTION public.update_client_rating_on_reservation();

--
-- Name: reservations trg_update_client_score; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_update_client_score AFTER UPDATE OF status ON public.reservations FOR EACH ROW EXECUTE FUNCTION public.trg_update_client_score();

--
-- Name: gain_rules trg_validate_gain_rule; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_validate_gain_rule BEFORE INSERT OR UPDATE ON public.gain_rules FOR EACH ROW EXECUTE FUNCTION public.validate_gain_rule();

--
-- Name: restaurant_gain_rules trg_validate_restaurant_gain_rule; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_validate_restaurant_gain_rule BEFORE INSERT OR UPDATE ON public.restaurant_gain_rules FOR EACH ROW EXECUTE FUNCTION public.validate_gain_rule();

--
-- Name: restaurant_zones trg_zones_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_zones_updated_at BEFORE UPDATE ON public.restaurant_zones FOR EACH ROW EXECUTE FUNCTION public.update_updated_at();

--
-- Name: company_settings update_company_settings_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_company_settings_updated_at BEFORE UPDATE ON public.company_settings FOR EACH ROW EXECUTE FUNCTION public.update_updated_at();

--
-- Name: contract_templates update_contract_templates_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_contract_templates_updated_at BEFORE UPDATE ON public.contract_templates FOR EACH ROW EXECUTE FUNCTION public.update_updated_at();

--
-- Name: custom_roles update_custom_roles_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_custom_roles_updated_at BEFORE UPDATE ON public.custom_roles FOR EACH ROW EXECUTE FUNCTION public.update_updated_at();

--
-- Name: explore_featured update_explore_featured_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_explore_featured_updated_at BEFORE UPDATE ON public.explore_featured FOR EACH ROW EXECUTE FUNCTION public.update_updated_at();

--
-- Name: gain_rule_requests update_gain_rule_requests_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_gain_rule_requests_updated_at BEFORE UPDATE ON public.gain_rule_requests FOR EACH ROW EXECUTE FUNCTION public.update_updated_at();

--
-- Name: oneclick_hi_invoices update_m3ak_hi_invoices_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_m3ak_hi_invoices_updated_at BEFORE UPDATE ON public.oneclick_hi_invoices FOR EACH ROW EXECUTE FUNCTION public.update_updated_at();

--
-- Name: offers update_offers_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_offers_updated_at BEFORE UPDATE ON public.offers FOR EACH ROW EXECUTE FUNCTION public.update_updated_at();

--
-- Name: promo_notification_requests update_promo_notification_requests_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_promo_notification_requests_updated_at BEFORE UPDATE ON public.promo_notification_requests FOR EACH ROW EXECUTE FUNCTION public.update_updated_at();

--
-- Name: restaurant_gain_rules update_restaurant_gain_rules_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_restaurant_gain_rules_updated_at BEFORE UPDATE ON public.restaurant_gain_rules FOR EACH ROW EXECUTE FUNCTION public.update_updated_at();

--
-- Name: restaurant_groups update_restaurant_groups_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_restaurant_groups_updated_at BEFORE UPDATE ON public.restaurant_groups FOR EACH ROW EXECUTE FUNCTION public.update_updated_at();

--
-- Name: restaurant_tier_config update_restaurant_tier_config_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_restaurant_tier_config_updated_at BEFORE UPDATE ON public.restaurant_tier_config FOR EACH ROW EXECUTE FUNCTION public.update_updated_at();

--
-- Name: restaurant_tier_status update_restaurant_tier_status_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_restaurant_tier_status_updated_at BEFORE UPDATE ON public.restaurant_tier_status FOR EACH ROW EXECUTE FUNCTION public.update_updated_at();

--
-- Name: support_tickets update_support_tickets_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_support_tickets_updated_at BEFORE UPDATE ON public.support_tickets FOR EACH ROW EXECUTE FUNCTION public.update_updated_at();

--
-- Name: action_logs action_logs_restaurant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.action_logs
    ADD CONSTRAINT action_logs_restaurant_id_fkey FOREIGN KEY (restaurant_id) REFERENCES public.restaurants(id) ON DELETE CASCADE;

--
-- Name: admin_audit_log admin_audit_log_actor_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_audit_log
    ADD CONSTRAINT admin_audit_log_actor_id_fkey FOREIGN KEY (actor_id) REFERENCES auth.users(id) ON DELETE SET NULL;

--
-- Name: admin_audit_log admin_audit_log_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_audit_log
    ADD CONSTRAINT admin_audit_log_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE SET NULL;

--
-- Name: admin_notifications admin_notifications_admin_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_notifications
    ADD CONSTRAINT admin_notifications_admin_id_fkey FOREIGN KEY (admin_id) REFERENCES auth.users(id) ON DELETE CASCADE;

--
-- Name: admin_wallet_transactions admin_wallet_transactions_restaurant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_wallet_transactions
    ADD CONSTRAINT admin_wallet_transactions_restaurant_id_fkey FOREIGN KEY (restaurant_id) REFERENCES public.restaurants(id) ON DELETE SET NULL;

--
-- Name: ai_usage_bypass ai_usage_bypass_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_usage_bypass
    ADD CONSTRAINT ai_usage_bypass_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;

--
-- Name: ai_usage ai_usage_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_usage
    ADD CONSTRAINT ai_usage_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;

--
-- Name: announcement_reads announcement_reads_announcement_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.announcement_reads
    ADD CONSTRAINT announcement_reads_announcement_id_fkey FOREIGN KEY (announcement_id) REFERENCES public.tenant_announcements(id) ON DELETE CASCADE;

--
-- Name: announcement_reads announcement_reads_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.announcement_reads
    ADD CONSTRAINT announcement_reads_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;

--
-- Name: app_documents app_documents_updated_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_documents
    ADD CONSTRAINT app_documents_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES auth.users(id);

--
-- Name: bookable_resources bookable_resources_restaurant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bookable_resources
    ADD CONSTRAINT bookable_resources_restaurant_id_fkey FOREIGN KEY (restaurant_id) REFERENCES public.restaurants(id) ON DELETE CASCADE;

--
-- Name: bookable_resources bookable_resources_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bookable_resources
    ADD CONSTRAINT bookable_resources_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE CASCADE;

--
-- Name: booking_rules booking_rules_restaurant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.booking_rules
    ADD CONSTRAINT booking_rules_restaurant_id_fkey FOREIGN KEY (restaurant_id) REFERENCES public.restaurants(id) ON DELETE CASCADE;

--
-- Name: chat_messages chat_messages_ticket_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_messages
    ADD CONSTRAINT chat_messages_ticket_id_fkey FOREIGN KEY (ticket_id) REFERENCES public.support_tickets(id) ON DELETE CASCADE;

--
-- Name: client_ratings client_ratings_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.client_ratings
    ADD CONSTRAINT client_ratings_client_id_fkey FOREIGN KEY (client_id) REFERENCES auth.users(id) ON DELETE CASCADE;

--
-- Name: contact_import_events contact_import_events_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contact_import_events
    ADD CONSTRAINT contact_import_events_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;

--
-- Name: contract_disabled_articles contract_disabled_articles_article_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contract_disabled_articles
    ADD CONSTRAINT contract_disabled_articles_article_id_fkey FOREIGN KEY (article_id) REFERENCES public.contract_template_articles(id) ON DELETE CASCADE;

--
-- Name: contract_disabled_articles contract_disabled_articles_contract_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contract_disabled_articles
    ADD CONSTRAINT contract_disabled_articles_contract_id_fkey FOREIGN KEY (contract_id) REFERENCES public.partner_contracts(id) ON DELETE CASCADE;

--
-- Name: contract_history contract_history_contract_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contract_history
    ADD CONSTRAINT contract_history_contract_id_fkey FOREIGN KEY (contract_id) REFERENCES public.partner_contracts(id) ON DELETE CASCADE;

--
-- Name: contract_template_articles contract_template_articles_template_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contract_template_articles
    ADD CONSTRAINT contract_template_articles_template_id_fkey FOREIGN KEY (template_id) REFERENCES public.contract_templates(id) ON DELETE CASCADE;

--
-- Name: contract_templates contract_templates_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contract_templates
    ADD CONSTRAINT contract_templates_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE SET NULL;

--
-- Name: device_tokens device_tokens_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.device_tokens
    ADD CONSTRAINT device_tokens_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;

--
-- Name: document_versions document_versions_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.document_versions
    ADD CONSTRAINT document_versions_created_by_fkey FOREIGN KEY (created_by) REFERENCES auth.users(id);

--
-- Name: elite_events elite_events_restaurant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.elite_events
    ADD CONSTRAINT elite_events_restaurant_id_fkey FOREIGN KEY (restaurant_id) REFERENCES public.restaurants(id) ON DELETE CASCADE;

--
-- Name: elite_rsvps elite_rsvps_event_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.elite_rsvps
    ADD CONSTRAINT elite_rsvps_event_id_fkey FOREIGN KEY (event_id) REFERENCES public.elite_events(id) ON DELETE CASCADE;

--
-- Name: elite_rsvps elite_rsvps_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.elite_rsvps
    ADD CONSTRAINT elite_rsvps_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;

--
-- Name: event_rsvps event_rsvps_event_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_rsvps
    ADD CONSTRAINT event_rsvps_event_id_fkey FOREIGN KEY (event_id) REFERENCES public.tenant_events(id) ON DELETE CASCADE;

--
-- Name: event_rsvps event_rsvps_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_rsvps
    ADD CONSTRAINT event_rsvps_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;

--
-- Name: expired_points expired_points_restaurant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.expired_points
    ADD CONSTRAINT expired_points_restaurant_id_fkey FOREIGN KEY (restaurant_id) REFERENCES public.restaurants(id);

--
-- Name: explore_featured explore_featured_restaurant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.explore_featured
    ADD CONSTRAINT explore_featured_restaurant_id_fkey FOREIGN KEY (restaurant_id) REFERENCES public.restaurants(id) ON DELETE CASCADE;

--
-- Name: action_logs fk_action_logs_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.action_logs
    ADD CONSTRAINT fk_action_logs_user FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE SET NULL;

--
-- Name: chat_messages fk_chat_messages_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_messages
    ADD CONSTRAINT fk_chat_messages_user FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE SET NULL;

--
-- Name: device_tokens fk_device_tokens_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.device_tokens
    ADD CONSTRAINT fk_device_tokens_user FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;

--
-- Name: elite_applications fk_elite_applications_reviewer; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.elite_applications
    ADD CONSTRAINT fk_elite_applications_reviewer FOREIGN KEY (reviewed_by) REFERENCES auth.users(id) ON DELETE SET NULL;

--
-- Name: elite_applications fk_elite_applications_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.elite_applications
    ADD CONSTRAINT fk_elite_applications_user FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;

--
-- Name: expired_points fk_expired_points_client; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.expired_points
    ADD CONSTRAINT fk_expired_points_client FOREIGN KEY (client_id) REFERENCES auth.users(id) ON DELETE CASCADE;

--
-- Name: expired_points fk_expired_points_original; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.expired_points
    ADD CONSTRAINT fk_expired_points_original FOREIGN KEY (original_point_id) REFERENCES public.loyalty_points(id) ON DELETE CASCADE;

--
-- Name: notifications fk_notifications_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;

--
-- Name: referrals fk_referrals_referrer; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.referrals
    ADD CONSTRAINT fk_referrals_referrer FOREIGN KEY (referrer_id) REFERENCES auth.users(id) ON DELETE CASCADE;

--
-- Name: scanned_tickets fk_scanned_tickets_client; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.scanned_tickets
    ADD CONSTRAINT fk_scanned_tickets_client FOREIGN KEY (client_id) REFERENCES auth.users(id) ON DELETE CASCADE;

--
-- Name: scanned_tickets fk_scanned_tickets_scanned_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.scanned_tickets
    ADD CONSTRAINT fk_scanned_tickets_scanned_by FOREIGN KEY (scanned_by) REFERENCES auth.users(id) ON DELETE SET NULL;

--
-- Name: fraud_alerts fraud_alerts_restaurant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fraud_alerts
    ADD CONSTRAINT fraud_alerts_restaurant_id_fkey FOREIGN KEY (restaurant_id) REFERENCES public.restaurants(id) ON DELETE SET NULL;

--
-- Name: friend_group_members friend_group_members_friend_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.friend_group_members
    ADD CONSTRAINT friend_group_members_friend_id_fkey FOREIGN KEY (friend_id) REFERENCES public.profiles(id) ON DELETE CASCADE;

--
-- Name: friend_group_members friend_group_members_group_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.friend_group_members
    ADD CONSTRAINT friend_group_members_group_id_fkey FOREIGN KEY (group_id) REFERENCES public.friend_groups(id) ON DELETE CASCADE;

--
-- Name: friend_groups friend_groups_owner_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.friend_groups
    ADD CONSTRAINT friend_groups_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES public.profiles(id) ON DELETE CASCADE;

--
-- Name: friendships friendships_addressee_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.friendships
    ADD CONSTRAINT friendships_addressee_id_fkey FOREIGN KEY (addressee_id) REFERENCES public.profiles(id) ON DELETE CASCADE;

--
-- Name: friendships friendships_requester_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.friendships
    ADD CONSTRAINT friendships_requester_id_fkey FOREIGN KEY (requester_id) REFERENCES public.profiles(id) ON DELETE CASCADE;

--
-- Name: gain_rule_requests gain_rule_requests_restaurant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.gain_rule_requests
    ADD CONSTRAINT gain_rule_requests_restaurant_id_fkey FOREIGN KEY (restaurant_id) REFERENCES public.restaurants(id);

--
-- Name: gain_rules gain_rules_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.gain_rules
    ADD CONSTRAINT gain_rules_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE SET NULL;

--
-- Name: invoice_lines invoice_lines_invoice_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_lines
    ADD CONSTRAINT invoice_lines_invoice_id_fkey FOREIGN KEY (invoice_id) REFERENCES public.oneclick_hi_invoices(id) ON DELETE CASCADE;

--
-- Name: lifecycle_events lifecycle_events_restaurant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lifecycle_events
    ADD CONSTRAINT lifecycle_events_restaurant_id_fkey FOREIGN KEY (restaurant_id) REFERENCES public.restaurants(id) ON DELETE CASCADE;

--
-- Name: loyalty_points loyalty_points_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.loyalty_points
    ADD CONSTRAINT loyalty_points_client_id_fkey FOREIGN KEY (client_id) REFERENCES auth.users(id) ON DELETE CASCADE;

--
-- Name: loyalty_points loyalty_points_restaurant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.loyalty_points
    ADD CONSTRAINT loyalty_points_restaurant_id_fkey FOREIGN KEY (restaurant_id) REFERENCES public.restaurants(id) ON DELETE CASCADE;

--
-- Name: loyalty_punch_cards loyalty_punch_cards_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.loyalty_punch_cards
    ADD CONSTRAINT loyalty_punch_cards_client_id_fkey FOREIGN KEY (client_id) REFERENCES public.profiles(id) ON DELETE CASCADE;

--
-- Name: loyalty_punch_cards loyalty_punch_cards_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.loyalty_punch_cards
    ADD CONSTRAINT loyalty_punch_cards_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE CASCADE;

--
-- Name: oneclick_hi_invoices m3ak_hi_invoices_restaurant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.oneclick_hi_invoices
    ADD CONSTRAINT m3ak_hi_invoices_restaurant_id_fkey FOREIGN KEY (restaurant_id) REFERENCES public.restaurants(id);

--
-- Name: monitor_logs monitor_logs_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.monitor_logs
    ADD CONSTRAINT monitor_logs_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE SET NULL;

--
-- Name: no_show_disputes no_show_disputes_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.no_show_disputes
    ADD CONSTRAINT no_show_disputes_client_id_fkey FOREIGN KEY (client_id) REFERENCES auth.users(id) ON DELETE CASCADE;

--
-- Name: no_show_disputes no_show_disputes_reservation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.no_show_disputes
    ADD CONSTRAINT no_show_disputes_reservation_id_fkey FOREIGN KEY (reservation_id) REFERENCES public.reservations(id) ON DELETE CASCADE;

--
-- Name: no_show_disputes no_show_disputes_resolved_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.no_show_disputes
    ADD CONSTRAINT no_show_disputes_resolved_by_fkey FOREIGN KEY (resolved_by) REFERENCES auth.users(id);

--
-- Name: no_show_disputes no_show_disputes_support_ticket_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.no_show_disputes
    ADD CONSTRAINT no_show_disputes_support_ticket_id_fkey FOREIGN KEY (support_ticket_id) REFERENCES public.support_tickets(id);

--
-- Name: notifications notifications_restaurant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_restaurant_id_fkey FOREIGN KEY (restaurant_id) REFERENCES public.restaurants(id) ON DELETE SET NULL;

--
-- Name: offer_impressions offer_impressions_offer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer_impressions
    ADD CONSTRAINT offer_impressions_offer_id_fkey FOREIGN KEY (offer_id) REFERENCES public.offers(id) ON DELETE CASCADE;

--
-- Name: offers offers_restaurant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offers
    ADD CONSTRAINT offers_restaurant_id_fkey FOREIGN KEY (restaurant_id) REFERENCES public.restaurants(id) ON DELETE CASCADE;

--
-- Name: offers offers_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offers
    ADD CONSTRAINT offers_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE SET NULL;

--
-- Name: onboarding_requests onboarding_requests_reviewed_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.onboarding_requests
    ADD CONSTRAINT onboarding_requests_reviewed_by_fkey FOREIGN KEY (reviewed_by) REFERENCES auth.users(id) ON DELETE SET NULL;

--
-- Name: onboarding_requests onboarding_requests_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.onboarding_requests
    ADD CONSTRAINT onboarding_requests_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE SET NULL;

--
-- Name: partner_contracts partner_contracts_parent_contract_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.partner_contracts
    ADD CONSTRAINT partner_contracts_parent_contract_id_fkey FOREIGN KEY (parent_contract_id) REFERENCES public.partner_contracts(id);

--
-- Name: partner_contracts partner_contracts_restaurant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.partner_contracts
    ADD CONSTRAINT partner_contracts_restaurant_id_fkey FOREIGN KEY (restaurant_id) REFERENCES public.restaurants(id) ON DELETE CASCADE;

--
-- Name: partner_contracts partner_contracts_template_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.partner_contracts
    ADD CONSTRAINT partner_contracts_template_id_fkey FOREIGN KEY (template_id) REFERENCES public.contract_templates(id);

--
-- Name: pcc_family_members pcc_family_members_member_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pcc_family_members
    ADD CONSTRAINT pcc_family_members_member_id_fkey FOREIGN KEY (member_id) REFERENCES auth.users(id) ON DELETE CASCADE;

--
-- Name: pcc_family_members pcc_family_members_related_member_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pcc_family_members
    ADD CONSTRAINT pcc_family_members_related_member_id_fkey FOREIGN KEY (related_member_id) REFERENCES auth.users(id) ON DELETE CASCADE;

--
-- Name: pcc_feedbacks pcc_feedbacks_member_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pcc_feedbacks
    ADD CONSTRAINT pcc_feedbacks_member_id_fkey FOREIGN KEY (member_id) REFERENCES auth.users(id) ON DELETE CASCADE;

--
-- Name: pcc_feedbacks pcc_feedbacks_reply_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pcc_feedbacks
    ADD CONSTRAINT pcc_feedbacks_reply_by_fkey FOREIGN KEY (reply_by) REFERENCES auth.users(id) ON DELETE SET NULL;

--
-- Name: pcc_feedbacks pcc_feedbacks_target_restaurant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pcc_feedbacks
    ADD CONSTRAINT pcc_feedbacks_target_restaurant_id_fkey FOREIGN KEY (target_restaurant_id) REFERENCES public.restaurants(id) ON DELETE SET NULL;

--
-- Name: point_distributions point_distributions_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.point_distributions
    ADD CONSTRAINT point_distributions_client_id_fkey FOREIGN KEY (client_id) REFERENCES public.profiles(id) ON DELETE CASCADE;

--
-- Name: point_distributions point_distributions_offer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.point_distributions
    ADD CONSTRAINT point_distributions_offer_id_fkey FOREIGN KEY (offer_id) REFERENCES public.offers(id) ON DELETE SET NULL;

--
-- Name: point_distributions point_distributions_restaurant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.point_distributions
    ADD CONSTRAINT point_distributions_restaurant_id_fkey FOREIGN KEY (restaurant_id) REFERENCES public.restaurants(id) ON DELETE SET NULL;

--
-- Name: point_gifts point_gifts_receiver_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.point_gifts
    ADD CONSTRAINT point_gifts_receiver_id_fkey FOREIGN KEY (receiver_id) REFERENCES public.profiles(id) ON DELETE CASCADE;

--
-- Name: point_gifts point_gifts_restaurant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.point_gifts
    ADD CONSTRAINT point_gifts_restaurant_id_fkey FOREIGN KEY (restaurant_id) REFERENCES public.restaurants(id) ON DELETE CASCADE;

--
-- Name: point_gifts point_gifts_sender_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.point_gifts
    ADD CONSTRAINT point_gifts_sender_id_fkey FOREIGN KEY (sender_id) REFERENCES public.profiles(id) ON DELETE CASCADE;

--
-- Name: profiles profiles_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.profiles
    ADD CONSTRAINT profiles_id_fkey FOREIGN KEY (id) REFERENCES auth.users(id) ON DELETE CASCADE;

--
-- Name: profiles profiles_tenant_group_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.profiles
    ADD CONSTRAINT profiles_tenant_group_id_fkey FOREIGN KEY (tenant_group_id) REFERENCES public.restaurant_groups(id) ON DELETE SET NULL;

--
-- Name: profiles profiles_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.profiles
    ADD CONSTRAINT profiles_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE SET NULL;

--
-- Name: promo_notification_requests promo_notification_requests_offer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.promo_notification_requests
    ADD CONSTRAINT promo_notification_requests_offer_id_fkey FOREIGN KEY (offer_id) REFERENCES public.offers(id) ON DELETE CASCADE;

--
-- Name: promo_notification_requests promo_notification_requests_restaurant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.promo_notification_requests
    ADD CONSTRAINT promo_notification_requests_restaurant_id_fkey FOREIGN KEY (restaurant_id) REFERENCES public.restaurants(id) ON DELETE CASCADE;

--
-- Name: quota_change_logs quota_change_logs_restaurant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quota_change_logs
    ADD CONSTRAINT quota_change_logs_restaurant_id_fkey FOREIGN KEY (restaurant_id) REFERENCES public.restaurants(id) ON DELETE CASCADE;

--
-- Name: redemption_events redemption_events_restaurant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.redemption_events
    ADD CONSTRAINT redemption_events_restaurant_id_fkey FOREIGN KEY (restaurant_id) REFERENCES public.restaurants(id) ON DELETE CASCADE;

--
-- Name: redemption_otp_requests redemption_otp_requests_restaurant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.redemption_otp_requests
    ADD CONSTRAINT redemption_otp_requests_restaurant_id_fkey FOREIGN KEY (restaurant_id) REFERENCES public.restaurants(id) ON DELETE CASCADE;

--
-- Name: referrals referrals_restaurant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.referrals
    ADD CONSTRAINT referrals_restaurant_id_fkey FOREIGN KEY (restaurant_id) REFERENCES public.restaurants(id);

--
-- Name: reservation_guests reservation_guests_reservation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservation_guests
    ADD CONSTRAINT reservation_guests_reservation_id_fkey FOREIGN KEY (reservation_id) REFERENCES public.reservations(id) ON DELETE CASCADE;

--
-- Name: reservations reservations_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservations
    ADD CONSTRAINT reservations_client_id_fkey FOREIGN KEY (client_id) REFERENCES public.profiles(id) ON DELETE CASCADE;

--
-- Name: reservations reservations_restaurant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservations
    ADD CONSTRAINT reservations_restaurant_id_fkey FOREIGN KEY (restaurant_id) REFERENCES public.restaurants(id) ON DELETE CASCADE;

--
-- Name: resource_bookings resource_bookings_organizer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.resource_bookings
    ADD CONSTRAINT resource_bookings_organizer_id_fkey FOREIGN KEY (organizer_id) REFERENCES public.profiles(id) ON DELETE CASCADE;

--
-- Name: resource_bookings resource_bookings_resource_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.resource_bookings
    ADD CONSTRAINT resource_bookings_resource_id_fkey FOREIGN KEY (resource_id) REFERENCES public.bookable_resources(id) ON DELETE CASCADE;

--
-- Name: restaurant_expired_pool restaurant_expired_pool_expired_point_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.restaurant_expired_pool
    ADD CONSTRAINT restaurant_expired_pool_expired_point_id_fkey FOREIGN KEY (expired_point_id) REFERENCES public.expired_points(id) ON DELETE CASCADE;

--
-- Name: restaurant_expired_pool restaurant_expired_pool_restaurant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.restaurant_expired_pool
    ADD CONSTRAINT restaurant_expired_pool_restaurant_id_fkey FOREIGN KEY (restaurant_id) REFERENCES public.restaurants(id) ON DELETE CASCADE;

--
-- Name: restaurant_expired_pool restaurant_expired_pool_restitution_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.restaurant_expired_pool
    ADD CONSTRAINT restaurant_expired_pool_restitution_id_fkey FOREIGN KEY (restitution_id) REFERENCES public.restaurant_restitutions(id) ON DELETE SET NULL;

--
-- Name: restaurant_gain_rules restaurant_gain_rules_restaurant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.restaurant_gain_rules
    ADD CONSTRAINT restaurant_gain_rules_restaurant_id_fkey FOREIGN KEY (restaurant_id) REFERENCES public.restaurants(id) ON DELETE CASCADE;

--
-- Name: restaurant_gain_rules restaurant_gain_rules_source_rule_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.restaurant_gain_rules
    ADD CONSTRAINT restaurant_gain_rules_source_rule_id_fkey FOREIGN KEY (source_rule_id) REFERENCES public.gain_rules(id) ON DELETE SET NULL;

--
-- Name: restaurant_groups restaurant_groups_owner_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.restaurant_groups
    ADD CONSTRAINT restaurant_groups_owner_user_id_fkey FOREIGN KEY (owner_user_id) REFERENCES public.profiles(id) ON DELETE SET NULL;

--
-- Name: restaurant_groups restaurant_groups_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.restaurant_groups
    ADD CONSTRAINT restaurant_groups_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE SET NULL;

--
-- Name: restaurant_media restaurant_media_restaurant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.restaurant_media
    ADD CONSTRAINT restaurant_media_restaurant_id_fkey FOREIGN KEY (restaurant_id) REFERENCES public.restaurants(id) ON DELETE CASCADE;

--
-- Name: restaurant_restitutions restaurant_restitutions_restaurant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.restaurant_restitutions
    ADD CONSTRAINT restaurant_restitutions_restaurant_id_fkey FOREIGN KEY (restaurant_id) REFERENCES public.restaurants(id) ON DELETE CASCADE;

--
-- Name: restaurant_services restaurant_services_restaurant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.restaurant_services
    ADD CONSTRAINT restaurant_services_restaurant_id_fkey FOREIGN KEY (restaurant_id) REFERENCES public.restaurants(id) ON DELETE CASCADE;

--
-- Name: restaurant_staff restaurant_staff_restaurant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.restaurant_staff
    ADD CONSTRAINT restaurant_staff_restaurant_id_fkey FOREIGN KEY (restaurant_id) REFERENCES public.restaurants(id) ON DELETE CASCADE;

--
-- Name: restaurant_staff restaurant_staff_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.restaurant_staff
    ADD CONSTRAINT restaurant_staff_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.profiles(id) ON DELETE CASCADE;

--
-- Name: restaurant_tables restaurant_tables_restaurant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.restaurant_tables
    ADD CONSTRAINT restaurant_tables_restaurant_id_fkey FOREIGN KEY (restaurant_id) REFERENCES public.restaurants(id) ON DELETE CASCADE;

--
-- Name: restaurant_tables restaurant_tables_zone_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.restaurant_tables
    ADD CONSTRAINT restaurant_tables_zone_id_fkey FOREIGN KEY (zone_id) REFERENCES public.restaurant_zones(id) ON DELETE CASCADE;

--
-- Name: restaurant_tier_status restaurant_tier_status_current_tier_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.restaurant_tier_status
    ADD CONSTRAINT restaurant_tier_status_current_tier_id_fkey FOREIGN KEY (current_tier_id) REFERENCES public.restaurant_tier_config(id);

--
-- Name: restaurant_tier_status restaurant_tier_status_restaurant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.restaurant_tier_status
    ADD CONSTRAINT restaurant_tier_status_restaurant_id_fkey FOREIGN KEY (restaurant_id) REFERENCES public.restaurants(id) ON DELETE CASCADE;

--
-- Name: restaurant_zones restaurant_zones_restaurant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.restaurant_zones
    ADD CONSTRAINT restaurant_zones_restaurant_id_fkey FOREIGN KEY (restaurant_id) REFERENCES public.restaurants(id) ON DELETE CASCADE;

--
-- Name: restaurants restaurants_group_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.restaurants
    ADD CONSTRAINT restaurants_group_id_fkey FOREIGN KEY (group_id) REFERENCES public.restaurant_groups(id) ON DELETE SET NULL;

--
-- Name: restaurants restaurants_referred_by_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.restaurants
    ADD CONSTRAINT restaurants_referred_by_id_fkey FOREIGN KEY (referred_by_id) REFERENCES public.restaurants(id) ON DELETE SET NULL;

--
-- Name: restaurants restaurants_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.restaurants
    ADD CONSTRAINT restaurants_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE SET NULL;

--
-- Name: scanned_tickets scanned_tickets_reservation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.scanned_tickets
    ADD CONSTRAINT scanned_tickets_reservation_id_fkey FOREIGN KEY (reservation_id) REFERENCES public.reservations(id) ON DELETE SET NULL;

--
-- Name: scanned_tickets scanned_tickets_restaurant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.scanned_tickets
    ADD CONSTRAINT scanned_tickets_restaurant_id_fkey FOREIGN KEY (restaurant_id) REFERENCES public.restaurants(id) ON DELETE CASCADE;

--
-- Name: seminar_requests seminar_requests_organizer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seminar_requests
    ADD CONSTRAINT seminar_requests_organizer_id_fkey FOREIGN KEY (organizer_id) REFERENCES public.profiles(id) ON DELETE SET NULL;

--
-- Name: seminar_requests seminar_requests_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seminar_requests
    ADD CONSTRAINT seminar_requests_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE CASCADE;

--
-- Name: staff_notification_preferences staff_notification_preferences_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.staff_notification_preferences
    ADD CONSTRAINT staff_notification_preferences_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;

--
-- Name: support_tickets support_tickets_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.support_tickets
    ADD CONSTRAINT support_tickets_client_id_fkey FOREIGN KEY (client_id) REFERENCES public.profiles(id);

--
-- Name: support_tickets support_tickets_restaurant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.support_tickets
    ADD CONSTRAINT support_tickets_restaurant_id_fkey FOREIGN KEY (restaurant_id) REFERENCES public.restaurants(id);

--
-- Name: system_alerts system_alerts_rule_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_alerts
    ADD CONSTRAINT system_alerts_rule_id_fkey FOREIGN KEY (rule_id) REFERENCES public.system_alert_rules(id) ON DELETE CASCADE;

--
-- Name: team_invitations team_invitations_restaurant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.team_invitations
    ADD CONSTRAINT team_invitations_restaurant_id_fkey FOREIGN KEY (restaurant_id) REFERENCES public.restaurants(id) ON DELETE CASCADE;

--
-- Name: tenant_admins tenant_admins_invited_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_admins
    ADD CONSTRAINT tenant_admins_invited_by_fkey FOREIGN KEY (invited_by) REFERENCES auth.users(id) ON DELETE SET NULL;

--
-- Name: tenant_admins tenant_admins_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_admins
    ADD CONSTRAINT tenant_admins_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE CASCADE;

--
-- Name: tenant_admins tenant_admins_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_admins
    ADD CONSTRAINT tenant_admins_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;

--
-- Name: tenant_announcements tenant_announcements_author_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_announcements
    ADD CONSTRAINT tenant_announcements_author_id_fkey FOREIGN KEY (author_id) REFERENCES auth.users(id) ON DELETE SET NULL;

--
-- Name: tenant_announcements tenant_announcements_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_announcements
    ADD CONSTRAINT tenant_announcements_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE CASCADE;

--
-- Name: tenant_branding tenant_branding_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_branding
    ADD CONSTRAINT tenant_branding_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE CASCADE;

--
-- Name: tenant_events tenant_events_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_events
    ADD CONSTRAINT tenant_events_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE CASCADE;

--
-- Name: tenant_features tenant_features_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_features
    ADD CONSTRAINT tenant_features_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE CASCADE;

--
-- Name: tenants tenants_company_settings_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenants
    ADD CONSTRAINT tenants_company_settings_id_fkey FOREIGN KEY (company_settings_id) REFERENCES public.company_settings(id) ON DELETE SET NULL;

--
-- Name: tenants tenants_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenants
    ADD CONSTRAINT tenants_created_by_fkey FOREIGN KEY (created_by) REFERENCES auth.users(id) ON DELETE SET NULL;

--
-- Name: tier_restaurant_offers tier_restaurant_offers_restaurant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tier_restaurant_offers
    ADD CONSTRAINT tier_restaurant_offers_restaurant_id_fkey FOREIGN KEY (restaurant_id) REFERENCES public.restaurants(id) ON DELETE CASCADE;

--
-- Name: user_favorites user_favorites_restaurant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_favorites
    ADD CONSTRAINT user_favorites_restaurant_id_fkey FOREIGN KEY (restaurant_id) REFERENCES public.restaurants(id) ON DELETE CASCADE;

--
-- Name: user_favorites user_favorites_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_favorites
    ADD CONSTRAINT user_favorites_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;

--
-- Name: user_roles user_roles_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT user_roles_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;

--
-- Name: system_alert_rules Admins manage alert rules; Type: POLICY; Schema: public; Owner: -
--

--
-- Name: system_alerts Admins manage alerts; Type: POLICY; Schema: public; Owner: -
--

--
-- Name: fraud_alerts Admins manage fraud alerts; Type: POLICY; Schema: public; Owner: -
--

--
-- Name: system_health_checks Admins manage health checks; Type: POLICY; Schema: public; Owner: -
--

--
-- Name: restaurants Anyone can read restaurants; Type: POLICY; Schema: public; Owner: -
--

--
-- Name: gain_rule_requests Senior staff create rule requests; Type: POLICY; Schema: public; Owner: -
--

--
-- Name: restaurant_staff Senior staff delete restaurant staff; Type: POLICY; Schema: public; Owner: -
--

--
-- Name: restaurant_gain_rules Senior staff manage own gain rules DELETE; Type: POLICY; Schema: public; Owner: -
--

--
-- Name: restaurant_gain_rules Senior staff manage own gain rules INSERT; Type: POLICY; Schema: public; Owner: -
--

--
-- Name: restaurant_gain_rules Senior staff manage own gain rules UPDATE; Type: POLICY; Schema: public; Owner: -
--

--
-- Name: restaurant_staff Senior staff update restaurant staff; Type: POLICY; Schema: public; Owner: -
--

--
-- Name: partner_contracts Staff read own contracts; Type: POLICY; Schema: public; Owner: -
--

--
-- Name: team_invitations Staff read own invitations; Type: POLICY; Schema: public; Owner: -
--

--
-- Name: restaurant_gain_rules Staff read own restaurant gain rules; Type: POLICY; Schema: public; Owner: -
--

--
-- Name: team_invitations Staff update invitations; Type: POLICY; Schema: public; Owner: -
--

--
-- Name: ai_usage Users can insert their own usage; Type: POLICY; Schema: public; Owner: -
--

--
-- Name: ai_usage Users can read their own usage; Type: POLICY; Schema: public; Owner: -
--

--
-- Name: ai_usage Users can update their own usage; Type: POLICY; Schema: public; Owner: -
--

--
-- Name: action_logs; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: admin_audit_log; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: admin_notifications; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: admin_wallet_transactions; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: onboarding_requests admins_select_onboarding; Type: POLICY; Schema: public; Owner: -
--

--
-- Name: onboarding_requests admins_update_onboarding; Type: POLICY; Schema: public; Owner: -
--

--
-- Name: ai_usage; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: ai_usage_bypass; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: announcement_reads; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: app_documents; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: bookable_resources; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: booking_rules; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: chat_messages; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: client_ratings; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: client_score_config; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: company_settings; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: contact_import_events; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: contract_disabled_articles; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: contract_history; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: contract_template_articles; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: contract_templates; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: custom_roles; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: device_tokens; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: document_versions; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: elite_applications; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: elite_events; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: elite_rsvps; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: email_bounces; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: event_rsvps; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: expired_points; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: explore_featured; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: fraud_alerts; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: friend_group_members; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: friend_groups; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: friendships; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: gain_rule_requests; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: gain_rules; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: invoice_lines; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: lifecycle_events; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: loyalty_plafonds; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: loyalty_points; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: loyalty_punch_cards; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: monitor_logs; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: no_show_disputes; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: notifications; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: offer_impressions; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: offers; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: onboarding_requests; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: oneclick_hi_invoices; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: partner_contracts; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: pcc_family_members; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: pcc_feedbacks; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: point_distributions; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: point_gifts; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: profiles; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: promo_notification_requests; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: quota_change_logs; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: redemption_events; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: redemption_otp_requests; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: referrals; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: reservation_guests; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: reservations; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: resource_bookings; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: restaurant_expired_pool; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: restaurant_gain_rules; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: restaurant_groups; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: restaurant_media; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: restaurant_restitutions; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: restaurant_services; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: restaurant_staff; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: restaurant_tables; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: restaurant_tier_config; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: restaurant_tier_status; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: restaurant_zones; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: restaurants; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: rule_templates; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: scanned_tickets; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: seminar_requests; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: staff_notification_preferences; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: staff_role_permissions; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: support_tickets; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: system_alert_rules; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: system_alerts; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: system_health_checks; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: team_invitations; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: tenant_admins; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: tenant_announcements; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: tenant_branding; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: tenant_events; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: tenant_features; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: tenants; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: tier_restaurant_offers; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: tier_thresholds; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: user_favorites; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- Name: user_roles; Type: ROW SECURITY; Schema: public; Owner: -
--

--
-- PostgreSQL database dump complete
--

\unrestrict 0YxiKgvX5wMLzN9pNhW3BUFmYlY6DwjFER1IYoOdrZUndawMEbHzbmd4aRTQO99


