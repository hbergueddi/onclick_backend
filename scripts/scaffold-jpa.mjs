#!/usr/bin/env node
// scaffold-jpa.mjs — Générateur JPA pour OneClick Spring (Phase 4 — Option A).
//
// Usage :
//   node scripts/scaffold-jpa.mjs --dry-run            # affiche les fichiers prévus, n'écrit rien
//   node scripts/scaffold-jpa.mjs --only=user_roles    # génère 1 seule table (debug)
//   node scripts/scaffold-jpa.mjs --group=tenant       # génère 1 groupe métier
//   node scripts/scaffold-jpa.mjs                      # bulk : tout sauf les pilotes
//   node scripts/scaffold-jpa.mjs --force              # génère même les pilotes (écrase)
//
// Variables d'environnement (cf scripts/scaffold/db.mjs) :
//   PGHOST=localhost  PGPORT=5432  PGDATABASE=oneclick_local  PGUSER=hh
//
// Le script :
//   1. Connecte à la DB
//   2. Liste tables + vues + enums du schéma public
//   3. Pour chaque relation non-pilote : émet 6 fichiers (entity, repo, dto, mapper, service, controller)
//      + un IdClass pour les PK composites
//   4. Pour chaque enum DB : émet 1 fichier dans entity/shared/
//
// Le script NE compile PAS — l'utilisateur lance `./mvnw compile` après pour valider.

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { connect, listRelations, describeRelation, listEnums } from "./scaffold/db.mjs";
import {
  inferGroupFromTable,
  SKIP_TABLES,
  tableNameToClassName,
} from "./scaffold/naming.mjs";
import {
  entityTemplate,
  idClassTemplate,
  enumTemplate,
  repositoryTemplate,
  dtoTemplate,
  mapperTemplate,
  serviceTemplate,
  controllerTemplate,
} from "./scaffold/templates.mjs";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const PROJECT_ROOT = path.resolve(__dirname, "..");

const args = parseArgs(process.argv.slice(2));

function parseArgs(argv) {
  const out = { dryRun: false, only: null, group: null, force: false };
  for (const a of argv) {
    if (a === "--dry-run") out.dryRun = true;
    else if (a === "--force") out.force = true;
    else if (a.startsWith("--only=")) out.only = a.slice(7);
    else if (a.startsWith("--group=")) out.group = a.slice(8);
    else if (a === "--help" || a === "-h") {
      console.log(`Usage:
  node scripts/scaffold-jpa.mjs [options]

Options:
  --dry-run             Print planned files without writing them
  --only=<table>        Generate only one table
  --group=<name>        Generate only tables in one functional group (auth, tenant, …)
  --force               Generate even pilot tables (overwrite manual code) — DANGEROUS
  -h, --help            This help`);
      process.exit(0);
    }
  }
  return out;
}

function writeFileIfNeeded(filePath, content) {
  const fullPath = path.join(PROJECT_ROOT, filePath);
  fs.mkdirSync(path.dirname(fullPath), { recursive: true });
  fs.writeFileSync(fullPath, content);
}

async function main() {
  const startedAt = Date.now();
  console.log("→ connecting to Postgres…");
  const client = await connect();

  try {
    const relations = await listRelations(client);
    const enums = await listEnums(client);
    const enumNames = new Set(enums.map((e) => e.name));

    console.log(`→ found ${relations.length} relations, ${enums.length} enums`);

    // === Filtre selon les args ===
    let target = relations;
    if (args.only) {
      target = target.filter((r) => r.name === args.only);
      if (target.length === 0) {
        console.error(`✗ table not found: ${args.only}`);
        process.exit(1);
      }
    }
    if (args.group) {
      target = target.filter((r) => inferGroupFromTable(r.name) === args.group);
    }
    if (!args.force) {
      target = target.filter((r) => !SKIP_TABLES.has(r.name));
    }

    // === Génération enums (toujours, sauf si --only=table) ===
    let writtenCount = 0;
    let skippedExistingCount = 0;

    if (!args.only && (!args.group || args.group === "shared")) {
      console.log(`\n→ enums (${enums.length}):`);
      for (const e of enums) {
        const file = enumTemplate(e);
        if (args.dryRun) {
          console.log(`  [dry] ${file.path}`);
        } else {
          writeFileIfNeeded(file.path, file.content);
          console.log(`  ✓ ${file.path}`);
          writtenCount++;
        }
      }
    }

    // === Génération relations ===
    console.log(`\n→ relations (${target.length}):`);
    const generators = [
      entityTemplate,
      idClassTemplate,
      repositoryTemplate,
      dtoTemplate,
      mapperTemplate,
      serviceTemplate,
      controllerTemplate,
    ];

    for (const rel of target) {
      const detail = await describeRelation(client, rel.name, rel.kind);
      const group = inferGroupFromTable(rel.name);
      const className = tableNameToClassName(rel.name);
      console.log(`\n  · ${rel.kind === "VIEW" ? "[V] " : ""}${rel.name}  →  ${group}.${className}`);

      for (const gen of generators) {
        const result = gen(detail, enumNames);
        if (!result) continue;
        if (args.dryRun) {
          console.log(`      [dry] ${result.path}`);
        } else {
          writeFileIfNeeded(result.path, result.content);
          console.log(`      ✓ ${result.path}`);
          writtenCount++;
        }
      }
    }

    const elapsed = ((Date.now() - startedAt) / 1000).toFixed(1);
    console.log(
      `\n${args.dryRun ? "[dry] " : ""}done in ${elapsed}s — ${writtenCount} files ${args.dryRun ? "would be" : ""} written, ${skippedExistingCount} skipped (existing).`
    );
  } finally {
    await client.end();
  }
}

main().catch((err) => {
  console.error("✗ fatal:", err);
  process.exit(1);
});
