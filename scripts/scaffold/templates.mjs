// templates.mjs — Émet les 6 fichiers Java pour une table donnée.
//
// Chaque fonction retourne `{ path, content }` ou `null` si non applicable.

import {
  tableNameToClassName,
  columnNameToFieldName,
  inferGroupFromTable,
  enumNameToClassName,
  inferAuthorizationFromGroup,
} from "./naming.mjs";
import {
  mapColumn,
  detectAuditSuperclass,
  TIMESTAMPED_INHERITED,
  AUDITED_INHERITED,
} from "./types.mjs";

const BASE_PACKAGE = "com.onesley.oneclick";

// ════════════════════════════════════════════════════════════════════════
// Helpers communs
// ════════════════════════════════════════════════════════════════════════

function dedupeImports(imports) {
  return [...new Set(imports)].sort();
}

function importLines(imports) {
  return dedupeImports(imports)
    .map((i) => `import ${i};`)
    .join("\n");
}

function pluralize(name) {
  // For controller paths : "user_role" → "user-roles"
  return name.replace(/_/g, "-") + (name.endsWith("s") ? "" : "s");
}

function jpaPath(group, layer, className) {
  return `src/main/java/com/onesley/oneclick/${layer}/${group}/${className}.java`;
}

// ════════════════════════════════════════════════════════════════════════
// 1. Entity
// ════════════════════════════════════════════════════════════════════════

export function entityTemplate(rel, enumNames) {
  const className = tableNameToClassName(rel.name);
  const group = inferGroupFromTable(rel.name);
  const isView = rel.kind === "VIEW";

  const audit = isView ? null : detectAuditSuperclass(rel.columns);
  const inheritedSet =
    audit === "AuditedEntity"
      ? AUDITED_INHERITED
      : audit === "TimestampedEntity"
      ? TIMESTAMPED_INHERITED
      : new Set();

  const isCompositePk = rel.primaryKey.length > 1;
  const idClassName = isCompositePk ? `${className}Id` : null;

  // Vues : pas de PK déclarée en DB → prendre la colonne "id" si elle existe,
  // sinon la première colonne. Une @Entity SANS @Id est rejetée par Hibernate.
  const virtualPk = isView
    ? rel.columns.find((c) => c.name === "id")?.name ||
      rel.columns[0]?.name ||
      null
    : null;

  const imports = new Set([
    "jakarta.persistence.Column",
    "jakarta.persistence.Entity",
    "jakarta.persistence.Id",
    "jakarta.persistence.Table",
  ]);

  if (isView) {
    imports.add("org.hibernate.annotations.Immutable");
  }
  if (isCompositePk) {
    imports.add("jakarta.persistence.IdClass");
  }
  if (audit === "TimestampedEntity") {
    imports.add(`${BASE_PACKAGE}.audit.TimestampedEntity`);
  } else if (audit === "AuditedEntity") {
    imports.add(`${BASE_PACKAGE}.audit.AuditedEntity`);
  }

  // Détection createdBy seul (sans modifiedBy) → audit partiel inline
  const hasCreatedByOnly =
    !audit &&
    rel.columns.some((c) => c.name === "created_by") &&
    !rel.columns.some((c) => c.name === "modified_by");

  // Si table a created_at sans updated_at → annotation @CreatedDate inline
  const hasOnlyCreatedAt =
    !isView &&
    !audit &&
    rel.columns.some((c) => c.name === "created_at") &&
    !rel.columns.some((c) => c.name === "updated_at");

  if (hasCreatedByOnly || hasOnlyCreatedAt) {
    imports.add("jakarta.persistence.EntityListeners");
    imports.add("org.springframework.data.jpa.domain.support.AuditingEntityListener");
  }
  if (hasCreatedByOnly) {
    imports.add("org.springframework.data.annotation.CreatedBy");
  }
  if (hasOnlyCreatedAt) {
    imports.add("org.springframework.data.annotation.CreatedDate");
  }

  // ── Génération des champs ──
  const fields = [];
  for (const col of rel.columns) {
    if (inheritedSet.has(col.name)) continue;

    const mapping = mapColumn(col, enumNames);
    if (mapping.extra?.skip) continue;

    mapping.imports.forEach((i) => imports.add(i));

    const fieldName = columnNameToFieldName(col.name);
    const isId =
      rel.primaryKey.includes(col.name) || (isView && col.name === virtualPk);
    const ann = [];

    if (isId) ann.push("@Id");

    // Bean Validation derived from DB constraints (entity-level, defense in depth).
    // Skip on views (no writes ever happen) and on @Id (validation makes no sense
    // for the PK, which is either DB-generated or required by definition).
    if (!isView && !isId) {
      // NOT NULL → @NotNull (sauf si default=now()/gen_random_uuid() côté DB)
      if (
        !col.nullable &&
        !col.defaultValue?.includes("now()") &&
        !col.defaultValue?.includes("gen_random_uuid")
      ) {
        // @NotNull pour les Object types ; pour les String on préfère @NotBlank
        if (mapping.javaType === "String") {
          ann.unshift("@NotBlank");
          imports.add("jakarta.validation.constraints.NotBlank");
        } else {
          ann.unshift("@NotNull");
          imports.add("jakarta.validation.constraints.NotNull");
        }
      }
      // varchar(N) ou character_maximum_length → @Size(max=N)
      if (col.charLength && mapping.javaType === "String") {
        ann.unshift(`@Size(max = ${col.charLength})`);
        imports.add("jakarta.validation.constraints.Size");
      }
      // numeric(p,s) → @Digits(integer=p-s, fraction=s)
      if (
        mapping.javaType === "BigDecimal" &&
        col.numericPrecision &&
        col.numericScale != null
      ) {
        const integerPart = col.numericPrecision - col.numericScale;
        ann.unshift(
          `@Digits(integer = ${integerPart}, fraction = ${col.numericScale})`
        );
        imports.add("jakarta.validation.constraints.Digits");
      }
      // Heuristique format email
      if (col.name === "email" || col.name.endsWith("_email")) {
        ann.unshift("@Email");
        imports.add("jakarta.validation.constraints.Email");
      }
    }

    if (isView) {
      ann.push(
        `@Column(name = "${col.name}", insertable = false, updatable = false)`
      );
    } else {
      const colArgs = [`name = "${col.name}"`];
      if (!col.nullable) colArgs.push("nullable = false");
      if (isId && col.defaultValue?.includes("gen_random_uuid")) {
        colArgs.push("updatable = false");
      }
      if (col.name === "created_at" && hasOnlyCreatedAt) {
        colArgs.push("updatable = false");
      }
      if (col.name === "created_by" && hasCreatedByOnly) {
        colArgs.push("updatable = false");
      }
      if (mapping.extra?.numericPrecision) {
        colArgs.push(`precision = ${col.numericPrecision}`);
        if (col.numericScale) colArgs.push(`scale = ${col.numericScale}`);
      }
      if (mapping.extra?.isArray) {
        colArgs.push(`columnDefinition = "${col.udtName.slice(1)}[]"`);
      }
      if (mapping.extra?.isJsonb) {
        colArgs.push(`columnDefinition = "jsonb"`);
      }
      if (mapping.extra?.isEnum) {
        colArgs.push(`columnDefinition = "${col.udtName}"`);
      }
      ann.push(`@Column(${colArgs.join(", ")})`);
    }

    if (mapping.extra?.isEnum) {
      imports.add("jakarta.persistence.Enumerated");
      imports.add("jakarta.persistence.EnumType");
      imports.add("org.hibernate.annotations.JdbcTypeCode");
      imports.add("org.hibernate.type.SqlTypes");
      ann.unshift("@Enumerated(EnumType.STRING)");
      ann.splice(1, 0, "@JdbcTypeCode(SqlTypes.NAMED_ENUM)");
    } else if (mapping.extra?.isArray) {
      imports.add("org.hibernate.annotations.JdbcTypeCode");
      imports.add("org.hibernate.type.SqlTypes");
      ann.unshift("@JdbcTypeCode(SqlTypes.ARRAY)");
    } else if (mapping.extra?.isJsonb) {
      imports.add("org.hibernate.annotations.JdbcTypeCode");
      imports.add("org.hibernate.type.SqlTypes");
      ann.unshift("@JdbcTypeCode(SqlTypes.JSON)");
    }

    if (col.name === "created_by" && hasCreatedByOnly) {
      ann.unshift("@CreatedBy");
    } else if (col.name === "created_at" && hasOnlyCreatedAt) {
      ann.unshift("@CreatedDate");
    }

    let initializer = "";
    if (mapping.extra?.isArray) initializer = ` = new ArrayList<>()`;
    else if (mapping.extra?.isJsonb) initializer = ` = new HashMap<>()`;

    fields.push({
      annotations: ann,
      type: mapping.javaType,
      name: fieldName,
      initializer,
      isId,
    });
  }

  // ── Render ──
  const classAnnotations = ["@Entity"];
  if (isView) classAnnotations.push("@Immutable");
  classAnnotations.push(`@Table(name = "${rel.name}")`);
  if (isCompositePk) classAnnotations.push(`@IdClass(${idClassName}.class)`);
  if (hasCreatedByOnly || hasOnlyCreatedAt) {
    classAnnotations.push("@EntityListeners(AuditingEntityListener.class)");
  }

  const extendsClause = audit ? ` extends ${audit}` : "";

  const fieldsBlock = fields
    .map((f) => {
      const lines = f.annotations.map((a) => `    ${a}`);
      lines.push(`    private ${f.type} ${f.name}${f.initializer};`);
      return lines.join("\n");
    })
    .join("\n\n");

  const gettersBlock = fields
    .map((f) => {
      const cap = f.name.charAt(0).toUpperCase() + f.name.slice(1);
      return `    public ${f.type} get${cap}() { return ${f.name}; }`;
    })
    .join("\n");

  const content = `package ${BASE_PACKAGE}.entity.${group};

${importLines([...imports])}

/**
 * Entité {@code public.${rel.name}} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : ${describePattern(audit, isView, isCompositePk, hasCreatedByOnly, hasOnlyCreatedAt)}.
 */
${classAnnotations.join("\n")}
public class ${className}${extendsClause} {

${fieldsBlock}

    protected ${className}() {
        // JPA
    }

${gettersBlock}
}
`;

  return { path: jpaPath(group, "entity", className), content };
}

function describePattern(audit, isView, isCompositePk, hasCreatedByOnly, hasOnlyCreatedAt) {
  const parts = [];
  if (isView) parts.push("vue read-only @Immutable");
  if (audit === "AuditedEntity") parts.push("audit niveau 1 (4 colonnes)");
  else if (audit === "TimestampedEntity") parts.push("created_at + updated_at hérités");
  if (hasCreatedByOnly) parts.push("audit partiel created_by inline");
  if (hasOnlyCreatedAt) parts.push("created_at sans updated_at, inline");
  if (isCompositePk) parts.push("PK composite via @IdClass");
  if (parts.length === 0) parts.push("entité simple");
  return parts.join(" + ");
}

// ════════════════════════════════════════════════════════════════════════
// 2. IdClass (composite PK)
// ════════════════════════════════════════════════════════════════════════

export function idClassTemplate(rel, enumNames) {
  if (rel.primaryKey.length <= 1) return null;
  const className = tableNameToClassName(rel.name);
  const idClassName = `${className}Id`;
  const group = inferGroupFromTable(rel.name);

  const imports = new Set([
    "java.io.Serializable",
    "java.util.Objects",
  ]);

  const fields = rel.primaryKey.map((pkCol) => {
    const col = rel.columns.find((c) => c.name === pkCol);
    const mapping = mapColumn(col, enumNames);
    mapping.imports.forEach((i) => imports.add(i));
    return { name: columnNameToFieldName(pkCol), type: mapping.javaType };
  });

  const fieldsBlock = fields
    .map((f) => `    private ${f.type} ${f.name};`)
    .join("\n");

  const gettersBlock = fields
    .map((f) => {
      const cap = f.name.charAt(0).toUpperCase() + f.name.slice(1);
      return `    public ${f.type} get${cap}() { return ${f.name}; }`;
    })
    .join("\n");

  const ctorParams = fields.map((f) => `${f.type} ${f.name}`).join(", ");
  const ctorBody = fields.map((f) => `        this.${f.name} = ${f.name};`).join("\n");

  const equalsBody = fields
    .map((f) => `Objects.equals(${f.name}, other.${f.name})`)
    .join("\n            && ");

  const hashFields = fields.map((f) => f.name).join(", ");

  const content = `package ${BASE_PACKAGE}.entity.${group};

${importLines([...imports])}

/**
 * Clé composite pour {@link ${className}} (PK = ${rel.primaryKey.join(" + ")}).
 * Généré par scripts/scaffold-jpa.mjs.
 */
public class ${idClassName} implements Serializable {

${fieldsBlock}

    public ${idClassName}() {
        // JPA
    }

    public ${idClassName}(${ctorParams}) {
${ctorBody}
    }

${gettersBlock}

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ${idClassName} other)) return false;
        return ${equalsBody};
    }

    @Override
    public int hashCode() {
        return Objects.hash(${hashFields});
    }
}
`;

  return { path: jpaPath(group, "entity", idClassName), content };
}

// ════════════════════════════════════════════════════════════════════════
// 3. Enums DB
// ════════════════════════════════════════════════════════════════════════

export function enumTemplate(enumDef) {
  const className = enumNameToClassName(enumDef.name);
  const values = enumDef.values.join(",\n    ");
  const hasNonAscii = enumDef.values.some((v) => /[^\x00-\x7F]/.test(v));
  const suppressLine = hasNonAscii
    ? '@SuppressWarnings("NonAsciiCharacters")\n'
    : "";

  const content = `package ${BASE_PACKAGE}.entity.shared;

/**
 * Enum {@code public.${enumDef.name}} (généré par scripts/scaffold-jpa.mjs).
 */
${suppressLine}public enum ${className} {
    ${values}
}
`;

  return {
    path: `src/main/java/com/onesley/oneclick/entity/shared/${className}.java`,
    content,
  };
}

// ════════════════════════════════════════════════════════════════════════
// 4. Repository
// ════════════════════════════════════════════════════════════════════════

export function repositoryTemplate(rel, enumNames) {
  const className = tableNameToClassName(rel.name);
  const group = inferGroupFromTable(rel.name);
  const isView = rel.kind === "VIEW";
  const isCompositePk = rel.primaryKey.length > 1;
  const idClassName = isCompositePk ? `${className}Id` : "UUID";
  const repoName = `${className}Repository`;

  const imports = new Set([
    `${BASE_PACKAGE}.entity.${group}.${className}`,
  ]);

  if (isView) {
    imports.add("org.springframework.data.repository.Repository");
  } else {
    imports.add("org.springframework.data.jpa.repository.JpaRepository");
    imports.add("org.springframework.stereotype.Repository");
  }

  if (isCompositePk) {
    imports.add(`${BASE_PACKAGE}.entity.${group}.${idClassName}`);
  } else {
    imports.add("java.util.UUID");
  }

  const baseRepo = isView
    ? `Repository<${className}, ${idClassName}>`
    : `JpaRepository<${className}, ${idClassName}>`;

  const annot = isView ? "" : "@Repository\n";

  // For views, no methods inherited — declare the read-only set manually
  let viewMethods = "";
  if (isView) {
    imports.add("java.util.List");
    imports.add("java.util.Optional");
    viewMethods = `
    Optional<${className}> findById(${idClassName} id);

    List<${className}> findAll();

    long count();
`;
  }

  const content = `package ${BASE_PACKAGE}.repository.${group};

${importLines([...imports])}

/**
 * Repository ${isView ? "read-only " : ""}pour {@link ${className}} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
${annot}public interface ${repoName} extends ${baseRepo} {
${viewMethods}}
`;

  return { path: jpaPath(group, "repository", repoName), content };
}

// ════════════════════════════════════════════════════════════════════════
// 5. DTO record
// ════════════════════════════════════════════════════════════════════════

export function dtoTemplate(rel, enumNames) {
  const className = tableNameToClassName(rel.name);
  const group = inferGroupFromTable(rel.name);
  const dtoName = `${className}Dto`;
  const isView = rel.kind === "VIEW";

  const imports = new Set();
  const fields = [];

  for (const col of rel.columns) {
    const mapping = mapColumn(col, enumNames);
    if (mapping.extra?.skip) continue;
    mapping.imports.forEach((i) => imports.add(i));
    if (mapping.extra?.isEnum) {
      imports.add(`${BASE_PACKAGE}.entity.shared.${mapping.javaType}`);
    }
    fields.push({
      type: mapping.javaType,
      name: columnNameToFieldName(col.name),
    });
  }

  const fieldsList = fields
    .map((f, i) => `    ${f.type} ${f.name}${i === fields.length - 1 ? "" : ","}`)
    .join("\n");

  const content = `package ${BASE_PACKAGE}.dto.${group};

${importLines([...imports])}

/**
 * DTO ${isView ? "read-only " : ""}pour {@code ${rel.name}} (généré par scripts/scaffold-jpa.mjs).
 */
public record ${dtoName}(
${fieldsList}
) {
}
`;

  return { path: jpaPath(group, "dto", dtoName), content };
}

// ════════════════════════════════════════════════════════════════════════
// 6. Mapper MapStruct
// ════════════════════════════════════════════════════════════════════════

export function mapperTemplate(rel) {
  const className = tableNameToClassName(rel.name);
  const group = inferGroupFromTable(rel.name);
  const dtoName = `${className}Dto`;
  const mapperName = `${className}Mapper`;

  const content = `package ${BASE_PACKAGE}.mapper.${group};

import ${BASE_PACKAGE}.dto.${group}.${dtoName};
import ${BASE_PACKAGE}.entity.${group}.${className};
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code ${className} ↔ ${dtoName}} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface ${mapperName} {

    ${dtoName} toDto(${className} entity);

    List<${dtoName}> toDtoList(List<${className}> entities);
}
`;

  return { path: jpaPath(group, "mapper", mapperName), content };
}

// ════════════════════════════════════════════════════════════════════════
// 7. Service
// ════════════════════════════════════════════════════════════════════════

export function serviceTemplate(rel) {
  const className = tableNameToClassName(rel.name);
  const group = inferGroupFromTable(rel.name);
  const dtoName = `${className}Dto`;
  const isView = rel.kind === "VIEW";
  const isCompositePk = rel.primaryKey.length > 1;
  const serviceName = `${className}Service`;
  const repoName = `${className}Repository`;
  const mapperName = `${className}Mapper`;

  const imports = new Set([
    `${BASE_PACKAGE}.dto.${group}.${dtoName}`,
    `${BASE_PACKAGE}.mapper.${group}.${mapperName}`,
    `${BASE_PACKAGE}.repository.${group}.${repoName}`,
    "org.springframework.stereotype.Service",
    "org.springframework.transaction.annotation.Transactional",
    "java.util.List",
  ]);

  let findByIdMethod = "";
  if (rel.primaryKey.length === 1) {
    imports.add("java.util.Optional");
    imports.add("java.util.UUID");
    findByIdMethod = `
    public Optional<${dtoName}> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }
`;
  }

  const findAllMethod = `
    public List<${dtoName}> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
`;

  const content = `package ${BASE_PACKAGE}.service.${group};

${importLines([...imports])}

/**
 * Service {@link ${className}} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class ${serviceName} {

    private final ${repoName} repository;
    private final ${mapperName} mapper;

    public ${serviceName}(${repoName} repository, ${mapperName} mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }
${findByIdMethod}${findAllMethod}}
`;

  return { path: jpaPath(group, "service", serviceName), content };
}

// ════════════════════════════════════════════════════════════════════════
// 8. Controller
// ════════════════════════════════════════════════════════════════════════

export function controllerTemplate(rel) {
  const className = tableNameToClassName(rel.name);
  const group = inferGroupFromTable(rel.name);
  const dtoName = `${className}Dto`;
  const serviceName = `${className}Service`;
  const controllerName = `${className}Controller`;
  const isView = rel.kind === "VIEW";
  // Vues : préfixe /api/views/ pour éviter les collisions d'URL avec la
  // table sous-jacente (ex: admin_audit_log + v_admin_audit_log).
  const urlPath = isView
    ? "views/" + pluralize(rel.name.replace(/^v_/, "")).replace(/^v_/, "")
    : pluralize(rel.name);

  const authExpression = inferAuthorizationFromGroup(group);

  const imports = new Set([
    `${BASE_PACKAGE}.dto.${group}.${dtoName}`,
    `${BASE_PACKAGE}.service.${group}.${serviceName}`,
    "io.swagger.v3.oas.annotations.tags.Tag",
    "io.swagger.v3.oas.annotations.Operation",
    "org.springframework.web.bind.annotation.GetMapping",
    "org.springframework.web.bind.annotation.RequestMapping",
    "org.springframework.web.bind.annotation.RestController",
    "java.util.List",
  ]);

  if (authExpression) {
    imports.add("org.springframework.security.access.prepost.PreAuthorize");
  }

  let findByIdEndpoint = "";
  if (rel.primaryKey.length === 1) {
    imports.add("org.springframework.http.ResponseEntity");
    imports.add("org.springframework.web.bind.annotation.PathVariable");
    imports.add("java.util.UUID");
    findByIdEndpoint = `
    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une ${className} par UUID")
    public ResponseEntity<${dtoName}> findById(@PathVariable UUID id) {
        return service.findById(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
`;
  }

  const content = `package ${BASE_PACKAGE}.controller.${group};

${importLines([...imports])}

/**
 * REST controller pour {@link ${dtoName}} (généré par scripts/scaffold-jpa.mjs).
 * Endpoints minimaux — étendre selon les besoins métier (filtres, pagination, mutations).
 *
 * <p>Sécurité par défaut${authExpression ? ` : ${authExpression}` : ` : permitAll (groupe non classifié)`}.
 * À raffiner endpoint par endpoint quand la business logic est portée (Phase 11+).
 */
@RestController
@RequestMapping("/api/${urlPath}")
@Tag(name = "${className}", description = "Auto-generated controller for ${rel.name}")${authExpression ? `\n@PreAuthorize("${authExpression}")` : ""}
public class ${controllerName} {

    private final ${serviceName} service;

    public ${controllerName}(${serviceName} service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Liste tous les éléments")
    public List<${dtoName}> findAll() {
        return service.findAll();
    }
${findByIdEndpoint}}
`;

  return { path: jpaPath(group, "controller", controllerName), content };
}
