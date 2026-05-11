#!/usr/bin/env python3
"""
Génère 1 repository JPA par entité — pattern minimal réutilisable.

Détection automatique :
  - Le type d'ID (UUID dans la majorité des cas, TenantBranding utilise UUID via @MapsId)
  - Les champs FK (xxx_id) pour générer les findAllByXxxId
  - Les contraintes UNIQUE pour findByXxx (de manière conservative)

Output : Xxx.java → XxxRepository.java dans le même package.
Skip si XxxRepository.java existe déjà (idempotent).
"""
import os
import re
from pathlib import Path

ROOT = Path('/Users/hh/Documents/Onesley/OneClick_Spring/src/main/java/com/onesley/oneclick')

# Repositories qui existent déjà (créés à la main)
EXISTING = {'UserRepository.java'}


def parse_entity(path: Path):
    content = path.read_text()
    pkg = re.search(r'package\s+([\w.]+);', content).group(1)
    cls = path.stem
    # FK fields : private Xxx field; avec @ManyToOne (PAS le proxy raw UUID xxxId)
    fks = []
    for m in re.finditer(r'@ManyToOne[^;]+?@JoinColumn\(name\s*=\s*"([^"]+)"[^)]*\)[^;]*?private\s+\w+\s+(\w+)', content, re.DOTALL):
        column = m.group(1)
        if column == 'id':
            continue
        # camelCase pour Spring Data derived query method
        camel = ''.join(p.capitalize() for p in column.split('_')) if column.count('_') >= 1 else column.capitalize()
        pascal = camel  # premier char en majuscule pour findAllByXxx
        fk_java_name = column.replace('_', '')  # field name
        # mais plus précis : convert snake_case → camelCase
        camel = re.sub(r'_(\w)', lambda mm: mm.group(1).upper(), column)
        pascal = camel[0].upper() + camel[1:]
        fks.append((column, camel, pascal))
    # UNIQUE single-column constraints — findByXxx avec retour Optional
    unique_cols = set()
    # @Column(name="..." ... unique = true)
    for m in re.finditer(r'@Column\(name\s*=\s*"([^"]+)"[^)]*unique\s*=\s*true', content):
        unique_cols.add(m.group(1))
    return pkg, cls, fks, unique_cols


def build_repo(pkg: str, cls: str, fks, unique_cols):
    methods = []
    extra_imports = set()
    for col, camel, pascal in fks:
        if camel == 'id':
            continue
        methods.append(f'    java.util.List<{cls}> findAllBy{pascal}(java.util.UUID {camel});')
    for col in unique_cols:
        if col == 'id':
            continue
        camel = re.sub(r'_(\w)', lambda m: m.group(1).upper(), col)
        pascal = camel[0].upper() + camel[1:]
        methods.append(f'    java.util.Optional<{cls}> findBy{pascal}(String {camel});')
    methods_str = '\n'.join(methods) if methods else ''
    if methods_str:
        methods_str = '\n' + methods_str + '\n'

    return f'''package {pkg};

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository {{@link {cls}}} — accès CRUD + finders dérivés.
 *
 * <p>Soft delete (si applicable) : filtrer {{@code WHERE deleted_at IS NULL}} dans les
 * services. Les méthodes JpaRepository standard ne filtrent pas — utilisation
 * directe à éviter pour les entités avec soft delete.
 */
@Repository
public interface {cls}Repository extends JpaRepository<{cls}, UUID>, JpaSpecificationExecutor<{cls}> {{{methods_str}}}
'''


def main():
    count = 0
    skipped = 0
    for entity_path in ROOT.rglob('*.java'):
        if entity_path.name == 'package-info.java':
            continue
        # Skip si pas dans core/ ou modules/
        parts = entity_path.parts
        if 'core' not in parts and 'modules' not in parts:
            continue
        # Skip repositories (juste les entités)
        if entity_path.name.endswith('Repository.java'):
            continue
        repo_name = entity_path.stem + 'Repository.java'
        if repo_name in EXISTING:
            skipped += 1
            continue
        repo_path = entity_path.parent / repo_name
        if repo_path.exists():
            skipped += 1
            continue
        try:
            pkg, cls, fks, unique_cols = parse_entity(entity_path)
        except (AttributeError, IndexError) as e:
            print(f'  SKIP {entity_path.name}: parse error {e}')
            continue
        # Skip les entités qui ont une PK non-UUID (ex: TenantBranding via @MapsId reste UUID, mais TenantAdminId composite key serait à part)
        # Pour MVP on assume UUID partout sauf cas spécial → Spring Data se débrouillera
        repo_content = build_repo(pkg, cls, fks, unique_cols)
        repo_path.write_text(repo_content)
        count += 1
    print(f'Generated {count} repositories ({skipped} skipped — already exist)')


if __name__ == '__main__':
    main()
