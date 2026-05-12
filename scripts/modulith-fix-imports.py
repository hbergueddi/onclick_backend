#!/usr/bin/env python3
"""
modulith-fix-imports.py — Ajoute les imports manquants après modulith-split.

Pour un module M (ex: modules/payment) :
  - Liste les types publics exposés dans M/api/*.java (records + outer DTOs class)
  - Liste les types internes dans M/internal/*.java (entities, repos, services)
  - Dans M/<X>Controller.java : ajoute les imports nécessaires (api.<Dto>, internal.<Service>)
  - Dans M/api/*.java : ajoute les imports internal.<Entity> nécessaires (méthodes from())
  - Dans M/internal/*.java : ajoute les imports api.<Dto> nécessaires

Usage :
    python3 scripts/modulith-fix-imports.py modules/payment
"""

import re
import sys
from pathlib import Path


def list_top_level_classes(java_path: Path) -> set[str]:
    """
    Renvoie l'ensemble des types top-level + records nested déclarés dans un .java.
    Inclut les nested public records (ex: PaymentDtos.PaymentDto).
    """
    text = java_path.read_text(encoding="utf-8")
    names = set()
    # Top-level class/interface/enum/record
    for m in re.finditer(r"^public\s+(?:final\s+)?(?:abstract\s+)?(?:class|interface|enum|record)\s+(\w+)", text, re.MULTILINE):
        names.add(m.group(1))
    # Nested public records (à 1 niveau de profondeur — typique pour les containers DTO)
    for m in re.finditer(r"^\s{4}public\s+record\s+(\w+)", text, re.MULTILINE):
        names.add(m.group(1))
    return names


def index_module(module_path: Path, base_pkg: str) -> dict[str, str]:
    """
    Indexe les classes du module : type-name → FQN.
    Cherche dans api/, internal/, et racine.
    """
    fqn_map: dict[str, str] = {}
    for sub in ("api", "internal", ""):
        dir_path = module_path / sub if sub else module_path
        if not dir_path.is_dir():
            continue
        sub_pkg = f"{base_pkg}.{sub}" if sub else base_pkg
        for f in dir_path.iterdir():
            if f.is_file() and f.suffix == ".java" and f.name != "package-info.java":
                for cls in list_top_level_classes(f):
                    # outer class name (matche le filename)
                    outer = f.stem
                    if cls == outer:
                        fqn_map[cls] = f"{sub_pkg}.{cls}"
                    else:
                        # nested record : FQN = outer.NestedName
                        fqn_map[cls] = f"{sub_pkg}.{outer}.{cls}"
    return fqn_map


def add_imports(file_path: Path, needed_fqns: set[str], existing_pkg: str) -> int:
    """
    Ajoute les imports manquants au fichier. Retourne le nombre d'imports ajoutés.
    """
    text = file_path.read_text(encoding="utf-8")
    # Imports déjà présents
    existing = set(re.findall(r"^import\s+([\w\.]+);", text, re.MULTILINE))
    # Filtre : ne pas importer ce qui est exactement dans le même package que le fichier
    # (les sous-packages api/, internal/ sont des packages distincts → imports requis)
    def parent_pkg(fqn: str) -> str:
        return fqn.rsplit(".", 1)[0]
    to_add = sorted(fqn for fqn in needed_fqns if fqn not in existing and parent_pkg(fqn) != existing_pkg)
    if not to_add:
        return 0

    # Trouver la ligne `package ...;` puis insérer après le bloc d'imports existants
    lines = text.split("\n")
    pkg_idx = next((i for i, l in enumerate(lines) if l.startswith("package ")), 0)

    # Index du dernier import
    last_import_idx = pkg_idx
    for i, l in enumerate(lines[pkg_idx + 1 :], pkg_idx + 1):
        if l.startswith("import "):
            last_import_idx = i
        elif l.strip() and not l.startswith("//") and not l.startswith("/*"):
            break

    new_imports = [f"import {fqn};" for fqn in to_add]
    insert_at = last_import_idx + 1
    lines = lines[:insert_at] + new_imports + lines[insert_at:]
    file_path.write_text("\n".join(lines), encoding="utf-8")
    return len(to_add)


def find_referenced_types(file_path: Path, all_types: set[str]) -> set[str]:
    """
    Renvoie l'ensemble des types du module référencés dans ce fichier
    (par scan grossier — détecte les usages "Type" comme classe).
    """
    text = file_path.read_text(encoding="utf-8")
    # Strip comments and strings (basique)
    text = re.sub(r"//[^\n]*", "", text)
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.DOTALL)
    text = re.sub(r'"(?:[^"\\]|\\.)*"', '""', text)

    referenced = set()
    for t in all_types:
        # match `Type` comme identifier — pas substring
        if re.search(rf"\b{re.escape(t)}\b", text):
            referenced.add(t)
    return referenced


def main():
    if len(sys.argv) != 2:
        print("Usage: modulith-fix-imports.py <module-relative-path>")
        sys.exit(1)

    repo_root = Path(__file__).resolve().parent.parent
    src_root = repo_root / "src/main/java/com/onesley/oneclick"
    module_rel = sys.argv[1]
    module_path = src_root / module_rel
    base_pkg = "com.onesley.oneclick." + module_rel.replace("/", ".")

    fqn_map = index_module(module_path, base_pkg)
    all_types = set(fqn_map.keys())

    # Process every .java file under the module
    total_added = 0
    for sub in ("", "api", "internal"):
        dir_path = module_path / sub if sub else module_path
        if not dir_path.is_dir():
            continue
        sub_pkg = f"{base_pkg}.{sub}" if sub else base_pkg
        for f in dir_path.iterdir():
            if f.is_file() and f.suffix == ".java" and f.name != "package-info.java":
                # Filter to only sub-package files for the recursion (not search/ etc.)
                if sub == "" and f.is_dir():
                    continue
                referenced = find_referenced_types(f, all_types)
                # Types declared in THIS file (don't import yourself)
                self_types = list_top_level_classes(f)
                needed = referenced - self_types
                needed_fqns = {fqn_map[t] for t in needed if t in fqn_map}
                # Filtrer aussi les outer dont on a importé un nested (Java pas besoin)
                final_fqns = set()
                for fqn in needed_fqns:
                    parts = fqn.split(".")
                    # Si le type est nested (outer.Nested), importer outer
                    # mais Java permet aussi de l'importer directement
                    final_fqns.add(fqn)
                added = add_imports(f, final_fqns, sub_pkg)
                if added > 0:
                    print(f"  +{added} import(s) → {f.relative_to(module_path)}")
                total_added += added

    print(f"\n✓ Module {module_rel} : {total_added} imports ajoutés")


if __name__ == "__main__":
    main()
