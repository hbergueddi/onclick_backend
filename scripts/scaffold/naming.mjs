// naming.mjs — Conversion table_name → ClassName, column_name → fieldName,
// et inférence du groupe métier (sous-package).

/**
 * snake_case → PascalCase.
 *   "user_roles"           → "UserRole"     (singularisation simple, voir note)
 *   "v_restaurants_core"   → "VRestaurantsCore" (puis post-traité par viewClassName)
 *   "tenant_admins"        → "TenantAdmin"
 *   "monitor_logs"         → "MonitorLog"
 *
 * Note de design : la singularisation est volontairement minimale (juste -s
 * final stripé). Les cas anglais irréguliers sont marginaux dans le schéma
 * (juste `friendships`, `friend_groups` qu'on accepte au pluriel).
 */
export function tableNameToClassName(tableName) {
  // Cas vue : "v_xxx_yyy" → "XxxYyyView"
  if (tableName.startsWith("v_")) {
    return viewClassName(tableName);
  }
  if (tableName.startsWith("mv_")) {
    return viewClassName(tableName.slice(1)); // strip "m" → "v_xxx" path
  }
  return pascalCase(stripTrailingS(tableName));
}

function viewClassName(name) {
  // "v_restaurants_core" → "RestaurantsCoreView"
  const stripped = name.replace(/^v_/, "");
  return pascalCase(stripped) + "View";
}

function pascalCase(snake) {
  return snake
    .split("_")
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1).toLowerCase())
    .join("");
}

function stripTrailingS(name) {
  // Strip simple plural-s on the LAST segment only
  const parts = name.split("_");
  const last = parts[parts.length - 1];
  if (last.endsWith("ies") && last.length > 3) {
    parts[parts.length - 1] = last.slice(0, -3) + "y"; // categories → category
  } else if (last.endsWith("ses") && last.length > 4) {
    parts[parts.length - 1] = last.slice(0, -2); // addresses → addresse (proche)
  } else if (last.endsWith("s") && last.length > 1 && !last.endsWith("ss")) {
    parts[parts.length - 1] = last.slice(0, -1);
  }
  return parts.join("_");
}

/**
 * snake_case → camelCase.
 *   "first_name"  → "firstName"
 *   "created_at"  → "createdAt"
 *   "tenant_id"   → "tenantId"
 */
export function columnNameToFieldName(columnName) {
  const parts = columnName.split("_");
  return (
    parts[0].toLowerCase() +
    parts
      .slice(1)
      .map((p) => p.charAt(0).toUpperCase() + p.slice(1).toLowerCase())
      .join("")
  );
}

/**
 * Inférence du sous-package métier depuis le nom de table.
 *
 * Stratégie :
 *   1. exact match dans EXACT_GROUPS
 *   2. prefix match dans PREFIX_GROUPS (premier qui matche, ordre déclaré)
 *   3. fallback "misc"
 */
const EXACT_GROUPS = {
  // === auth ===
  user_roles: "auth",
  profiles: "auth",
  custom_roles: "auth",
  staff_role_permissions: "auth",
  // === tenant ===
  tenants: "tenant",
  // === reservation ===
  reservations: "reservation",
  reservation_guests: "reservation",
  friendships: "reservation",
  friend_groups: "reservation",
  friend_group_members: "reservation",
  booking_rules: "reservation",
  // === marketing ===
  offers: "marketing",
  referrals: "marketing",
  promo_notification_requests: "marketing",
  offer_impressions: "marketing",
  explore_featured: "marketing",
  // === pcc ===
  bookable_resources: "pcc",
  resource_bookings: "pcc",
  seminar_requests: "pcc",
  event_rsvps: "pcc",
  // === admin ===
  admin_audit_log: "admin",
  admin_notifications: "admin",
  admin_wallet_transactions: "admin",
  monitor_logs: "admin",
  monitor_metrics: "admin",
  email_bounces: "admin",
  device_tokens: "admin",
  notifications: "admin",
  action_logs: "admin",
  lifecycle_events: "admin",
  system_health_checks: "admin",
  system_alerts: "admin",
  system_alert_rules: "admin",
  fraud_alerts: "admin",
  team_invitations: "admin",
  staff_notification_preferences: "admin",
  contact_import_events: "admin",
  app_documents: "admin",
  document_versions: "admin",
  quota_change_logs: "admin",
  // === support ===
  support_tickets: "support",
  client_ratings: "support",
  client_visible_ratings: "support",
  no_show_disputes: "support",
  rule_templates: "support",
  chat_messages: "support",
  client_score_config: "support",
  // === contract ===
  partner_contracts: "contract",
  contract_templates: "contract",
  contract_template_articles: "contract",
  contract_disabled_articles: "contract",
  contract_history: "contract",
  oneclick_hi_invoices: "contract",
  invoice_lines: "contract",
  company_settings: "contract",
  onboarding_requests: "contract",
  // === loyalty ===
  loyalty_points: "loyalty",
  loyalty_punch_cards: "loyalty",
  loyalty_plafond: "loyalty",
  point_distributions: "loyalty",
  point_gifts: "loyalty",
  gain_rules: "loyalty",
  gain_rule_requests: "loyalty",
  scanned_tickets: "loyalty",
  elite_applications: "loyalty",
  elite_events: "loyalty",
  elite_rsvps: "loyalty",
  user_favorites: "loyalty",
  ai_usage: "loyalty",
  ai_usage_bypass: "loyalty",
  expired_points: "loyalty",
  redemption_events: "loyalty",
  redemption_otp_requests: "loyalty",
  tier_thresholds: "loyalty",
  tier_restaurant_offers: "loyalty",
};

const PREFIX_GROUPS = [
  ["restaurant_", "restaurant"],
  ["restaurants", "restaurant"], // exact match for "restaurants" (no underscore)
  ["v_restaurants", "restaurant"],
  ["v_restaurant", "restaurant"],
  ["tenant_", "tenant"],
  ["announcement_", "tenant"],
  ["pcc_", "pcc"],
  ["v_admin", "admin"],
  ["v_client", "loyalty"],
  ["mv_reservations", "reservation"],
  ["v_", "misc_view"],
  ["mv_", "misc_view"],
];

export function inferGroupFromTable(tableName) {
  if (EXACT_GROUPS[tableName]) {
    return EXACT_GROUPS[tableName];
  }
  for (const [prefix, group] of PREFIX_GROUPS) {
    if (tableName === prefix || tableName.startsWith(prefix)) {
      return group;
    }
  }
  return "misc";
}

/**
 * Tables déjà créées manuellement en Phase 3 ou tables système — le scaffolder
 * les saute pour ne pas écraser le pilote ni mapper du système.
 */
export const SKIP_TABLES = new Set([
  // Pilotes Phase 3
  "user_roles",
  "profiles",
  "restaurants",
  "reservations",
  "tenants",
  "tenant_admins",
  "v_restaurants_core",
  // Tables système — ne JAMAIS mapper
  "flyway_schema_history",
]);

/**
 * Pour les enums DB, le ClassName Java suit la même règle de pascalCase.
 *   "app_role"             → "AppRole"
 *   "reservation_status"   → "ReservationStatus"
 *   "punch_card_activity"  → "PunchCardActivity"
 */
export function enumNameToClassName(enumName) {
  return pascalCase(enumName);
}
