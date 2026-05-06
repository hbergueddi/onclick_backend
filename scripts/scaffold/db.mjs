// db.mjs — Introspection du schéma Postgres pour le scaffolder JPA.
//
// Toutes les requêtes ciblent le schéma `public`. Les méthodes ne ferment pas
// la connexion — l'appelant gère le cycle de vie.

import pg from "pg";

const { Client } = pg;

export async function connect() {
  const client = new Client({
    host: process.env.PGHOST || "localhost",
    port: parseInt(process.env.PGPORT || "5432", 10),
    database: process.env.PGDATABASE || "oneclick_local",
    user: process.env.PGUSER || "hh",
    password: process.env.PGPASSWORD || "",
  });
  await client.connect();
  return client;
}

/**
 * Liste toutes les tables + vues du schéma public.
 *
 * Retourne : [{ name, kind: "TABLE" | "VIEW" }, ...]
 */
export async function listRelations(client) {
  const tables = await client.query(`
    SELECT table_name AS name, 'TABLE' AS kind
    FROM information_schema.tables
    WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
    ORDER BY table_name
  `);
  const views = await client.query(`
    SELECT table_name AS name, 'VIEW' AS kind
    FROM information_schema.views
    WHERE table_schema = 'public'
    ORDER BY table_name
  `);
  return [...tables.rows, ...views.rows];
}

/**
 * Détails d'une relation (table ou vue).
 *
 * Retourne :
 *   {
 *     name: "user_roles",
 *     kind: "TABLE",
 *     columns: [{name, dataType, udtName, nullable, defaultValue, charLength, numericPrecision, numericScale, position}],
 *     primaryKey: ["id"]                      // [] pour une vue
 *     uniqueConstraints: [{name, columns}],   // hors PK
 *     foreignKeys: [{name, column, refTable, refColumn, refSchema}],
 *   }
 */
export async function describeRelation(client, name, kind) {
  const cols = await client.query(
    `
    SELECT column_name AS name,
           data_type AS "dataType",
           udt_name AS "udtName",
           is_nullable = 'YES' AS nullable,
           column_default AS "defaultValue",
           character_maximum_length AS "charLength",
           numeric_precision AS "numericPrecision",
           numeric_scale AS "numericScale",
           ordinal_position AS position
    FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = $1
    ORDER BY ordinal_position
  `,
    [name]
  );

  if (kind === "VIEW") {
    return {
      name,
      kind,
      columns: cols.rows,
      primaryKey: [],
      uniqueConstraints: [],
      foreignKeys: [],
    };
  }

  const pk = await client.query(
    `
    SELECT kcu.column_name, kcu.ordinal_position
    FROM information_schema.table_constraints tc
    JOIN information_schema.key_column_usage kcu
      ON tc.constraint_name = kcu.constraint_name
     AND tc.table_schema = kcu.table_schema
    WHERE tc.table_schema = 'public'
      AND tc.table_name = $1
      AND tc.constraint_type = 'PRIMARY KEY'
    ORDER BY kcu.ordinal_position
  `,
    [name]
  );

  const uniques = await client.query(
    `
    SELECT tc.constraint_name AS name,
           array_agg(kcu.column_name ORDER BY kcu.ordinal_position) AS columns
    FROM information_schema.table_constraints tc
    JOIN information_schema.key_column_usage kcu
      ON tc.constraint_name = kcu.constraint_name
     AND tc.table_schema = kcu.table_schema
    WHERE tc.table_schema = 'public'
      AND tc.table_name = $1
      AND tc.constraint_type = 'UNIQUE'
    GROUP BY tc.constraint_name
  `,
    [name]
  );

  const fks = await client.query(
    `
    SELECT tc.constraint_name AS name,
           kcu.column_name AS column,
           ccu.table_schema AS "refSchema",
           ccu.table_name AS "refTable",
           ccu.column_name AS "refColumn"
    FROM information_schema.table_constraints tc
    JOIN information_schema.key_column_usage kcu
      ON tc.constraint_name = kcu.constraint_name
     AND tc.table_schema = kcu.table_schema
    JOIN information_schema.constraint_column_usage ccu
      ON tc.constraint_name = ccu.constraint_name
     AND tc.table_schema = ccu.table_schema
    WHERE tc.table_schema = 'public'
      AND tc.table_name = $1
      AND tc.constraint_type = 'FOREIGN KEY'
    ORDER BY tc.constraint_name
  `,
    [name]
  );

  return {
    name,
    kind,
    columns: cols.rows,
    primaryKey: pk.rows.map((r) => r.column_name),
    uniqueConstraints: uniques.rows,
    foreignKeys: fks.rows,
  };
}

/**
 * Liste tous les enum types du schéma public.
 *
 * Retourne : [{ name, values: [...] }]
 *
 * Note : `array_agg(text)` est sérialisé par node-postgres en string brute
 * `{val1,val2}` (le client n'inspecte pas le retour de fonctions). On itère
 * donc 1 ligne par valeur et on groupe côté JS.
 */
export async function listEnums(client) {
  const result = await client.query(`
    SELECT t.typname AS name, e.enumlabel AS value, e.enumsortorder AS pos
    FROM pg_type t
    JOIN pg_enum e ON e.enumtypid = t.oid
    JOIN pg_namespace n ON n.oid = t.typnamespace
    WHERE n.nspname = 'public'
    ORDER BY t.typname, e.enumsortorder
  `);
  const grouped = new Map();
  for (const row of result.rows) {
    if (!grouped.has(row.name)) grouped.set(row.name, []);
    grouped.get(row.name).push(row.value);
  }
  return Array.from(grouped.entries()).map(([name, values]) => ({
    name,
    values,
  }));
}
