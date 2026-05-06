// types.mjs — Mapping pg type → Java type pour le scaffolder.
//
// Sources :
//   - data_type            (information_schema standard, ex: "uuid", "text",
//                          "ARRAY", "USER-DEFINED", "jsonb", ...)
//   - udt_name             (PG natif, ex: "uuid", "text", "_text" pour ARRAY,
//                          "app_role" pour enum, ...)
//   - numericPrecision/Scale pour numeric(p,s)

import { enumNameToClassName } from "./naming.mjs";

/**
 * Map udt_name → java type primitif (boxed) sans logique métier.
 * Les cas complexes (ARRAY, enum, JSONB) sont gérés à part dans `mapColumn`.
 */
const SIMPLE_TYPES = {
  uuid: "UUID",
  text: "String",
  varchar: "String",
  bpchar: "String",
  bool: "Boolean",
  boolean: "Boolean",
  int2: "Short",
  int4: "Integer",
  int8: "Long",
  float4: "Float",
  float8: "Double",
  numeric: "BigDecimal", // overridden if precision/scale → decided in mapColumn
  date: "LocalDate",
  time: "LocalTime",
  timetz: "OffsetTime",
  timestamp: "LocalDateTime",
  timestamptz: "Instant",
  bytea: "byte[]",
  tsvector: null, // intentionally NOT mapped — see Pattern 3 in PILOT-PATTERNS.md
  interval: "Duration",
};

/**
 * Tente de mapper une colonne pg vers du Java. Retourne :
 *   {
 *     javaType: "UUID" | "String" | "List<String>" | "ReservationStatus" | "Map<String,Object>" | null
 *     imports: ["java.util.UUID", ...]
 *     extra: {
 *       isEnum: boolean,
 *       enumName: string | null,         // udt_name si enum
 *       isArray: boolean,
 *       isJsonb: boolean,
 *       skip: boolean,                   // true si on doit ignorer la colonne (ex: tsvector)
 *     }
 *   }
 *
 * Les `enumNames` est l'ensemble des enums DB connus (Set<string>).
 */
export function mapColumn(col, enumNames) {
  const { dataType, udtName, numericPrecision, numericScale } = col;

  // tsvector — non mappé
  if (udtName === "tsvector") {
    return { javaType: null, imports: [], extra: { skip: true } };
  }

  // ARRAY type — udt_name commence par "_"
  if (dataType === "ARRAY" || udtName.startsWith("_")) {
    const baseUdt = udtName.startsWith("_") ? udtName.slice(1) : udtName;
    const baseJava = SIMPLE_TYPES[baseUdt] || "String";
    const javaType = `List<${baseJava}>`;
    const imports = ["java.util.List", "java.util.ArrayList"];
    if (baseJava === "UUID") imports.push("java.util.UUID");
    if (baseJava === "Instant") imports.push("java.time.Instant");
    return {
      javaType,
      imports,
      extra: { isArray: true, baseJavaType: baseJava },
    };
  }

  // JSONB
  if (dataType === "jsonb" || udtName === "jsonb") {
    return {
      javaType: "Map<String, Object>",
      imports: ["java.util.Map", "java.util.HashMap"],
      extra: { isJsonb: true },
    };
  }

  // Enum DB — dataType="USER-DEFINED" + udtName ∈ enumNames
  if (dataType === "USER-DEFINED" && enumNames.has(udtName)) {
    const enumClass = enumNameToClassName(udtName);
    return {
      javaType: enumClass,
      // L'enum vit dans com.onesley.oneclick.entity.shared
      imports: [`com.onesley.oneclick.entity.shared.${enumClass}`],
      extra: { isEnum: true, enumName: udtName },
    };
  }

  // numeric sans précision → BigDecimal
  if (udtName === "numeric") {
    return {
      javaType: "BigDecimal",
      imports: ["java.math.BigDecimal"],
      extra: { numericPrecision, numericScale },
    };
  }

  // Mapping simple
  const java = SIMPLE_TYPES[udtName];
  if (java) {
    const imports = [];
    if (java === "UUID") imports.push("java.util.UUID");
    else if (java === "Instant") imports.push("java.time.Instant");
    else if (java === "LocalDate") imports.push("java.time.LocalDate");
    else if (java === "LocalTime") imports.push("java.time.LocalTime");
    else if (java === "OffsetTime") imports.push("java.time.OffsetTime");
    else if (java === "LocalDateTime") imports.push("java.time.LocalDateTime");
    else if (java === "BigDecimal") imports.push("java.math.BigDecimal");
    else if (java === "Duration") imports.push("java.time.Duration");
    return { javaType: java, imports, extra: {} };
  }

  // Fallback : on log + on skip
  console.warn(`  WARN: unknown type for column ${col.name}: dataType=${dataType} udtName=${udtName}`);
  return { javaType: null, imports: [], extra: { skip: true } };
}

/**
 * Détecte si une table porte les patterns d'audit pour choisir la superclass.
 * Retourne :
 *   "TimestampedEntity" | "AuditedEntity" | null
 */
export function detectAuditSuperclass(columns) {
  const colNames = new Set(columns.map((c) => c.name));
  const hasCreatedAt = colNames.has("created_at");
  const hasUpdatedAt = colNames.has("updated_at");
  const hasCreatedBy = colNames.has("created_by");
  const hasModifiedBy = colNames.has("modified_by");

  if (hasCreatedAt && hasUpdatedAt && hasCreatedBy && hasModifiedBy) {
    return "AuditedEntity";
  }
  if (hasCreatedAt && hasUpdatedAt) {
    return "TimestampedEntity";
  }
  return null;
}

/**
 * Colonnes héritées de la superclass — exclues du mapping explicite de l'entité.
 */
export const TIMESTAMPED_INHERITED = new Set(["created_at", "updated_at"]);
export const AUDITED_INHERITED = new Set([
  "created_at",
  "updated_at",
  "created_by",
  "modified_by",
]);
