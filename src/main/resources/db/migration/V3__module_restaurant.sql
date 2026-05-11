-- ════════════════════════════════════════════════════════════════════
-- V3 — modules/restaurant (§4)
-- ════════════════════════════════════════════════════════════════════
-- 6 tables : Restaurant, Staff, Zone, Table, Service, BusinessHour
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE restaurants (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    name            text NOT NULL,
    description     text,
    phone           text,
    address         text,
    city            text NOT NULL,
    latitude        numeric(10, 7),
    longitude       numeric(10, 7),
    status          text NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'paused', 'archived')),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    deleted_at      timestamptz,
    created_by      uuid REFERENCES users(id) ON DELETE SET NULL,
    updated_by      uuid REFERENCES users(id) ON DELETE SET NULL
);
CREATE INDEX idx_restaurants_tenant ON restaurants(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_restaurants_city ON restaurants(city) WHERE deleted_at IS NULL;
CREATE INDEX idx_restaurants_status ON restaurants(status) WHERE deleted_at IS NULL;
COMMENT ON TABLE restaurants IS 'Restaurants partenaires — base du catalogue (§4)';

CREATE TABLE restaurant_staffs (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id   uuid NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    user_id         uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_code       text NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    deleted_at      timestamptz,
    UNIQUE (restaurant_id, user_id)
);
CREATE INDEX idx_restaurant_staffs_restaurant ON restaurant_staffs(restaurant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_restaurant_staffs_user ON restaurant_staffs(user_id) WHERE deleted_at IS NULL;
COMMENT ON TABLE restaurant_staffs IS 'Junction user × restaurant avec rôle staff (owner, manager, server, etc.)';

CREATE TABLE restaurant_zones (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id   uuid NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    name            text NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_restaurant_zones_restaurant ON restaurant_zones(restaurant_id);
COMMENT ON TABLE restaurant_zones IS 'Zones physiques d''un restaurant (Terrasse, Salle, Bar)';

CREATE TABLE restaurant_tables (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    zone_id         uuid NOT NULL REFERENCES restaurant_zones(id) ON DELETE CASCADE,
    table_number    text NOT NULL,
    seats           integer NOT NULL CHECK (seats > 0),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (zone_id, table_number)
);
CREATE INDEX idx_restaurant_tables_zone ON restaurant_tables(zone_id);
COMMENT ON TABLE restaurant_tables IS 'Tables physiques par zone';

CREATE TABLE restaurant_services (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id   uuid NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    name            text NOT NULL,
    start_time      time NOT NULL,
    end_time        time NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CHECK (end_time > start_time)
);
CREATE INDEX idx_restaurant_services_restaurant ON restaurant_services(restaurant_id);
COMMENT ON TABLE restaurant_services IS 'Services proposés (brunch, déjeuner, dîner) avec créneaux horaires';

-- BusinessHour est polymorphique (entity_type + entity_id) — utilisable pour restaurant ET resource
CREATE TABLE business_hours (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type     text NOT NULL,
    entity_id       uuid NOT NULL,
    day_of_week     integer NOT NULL CHECK (day_of_week BETWEEN 0 AND 6),
    start_time      time NOT NULL,
    end_time        time NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CHECK (end_time > start_time)
);
CREATE INDEX idx_business_hours_entity ON business_hours(entity_type, entity_id, day_of_week);
COMMENT ON TABLE business_hours IS 'Horaires d''ouverture polymorphiques (restaurant, resource, etc.). Remplace opening_hours jsonb legacy';
