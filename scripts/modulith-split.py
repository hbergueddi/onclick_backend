#!/usr/bin/env python3
"""
modulith-split.py — Migre un module Spring Modulith vers la structure api/ + internal/.

Usage :
    python3 scripts/modulith-split.py modules/payment

Pour un module M :
  - Déplace les *Dtos.java vers M/api/
  - Déplace les Entity/Repository/Service/EventListener vers M/internal/
  - Met à jour la déclaration `package` dans chaque fichier déplacé
  - Garde le Controller + package-info.java à la racine du module

NB : ne touche PAS au package-info.java (le sequencer manuel finalise après).
Ne touche PAS aux fichiers du module qui sont déjà dans api/ ou internal/ (idempotent).

Heuristiques de classification :
  - Suffix "Dto" / "Dtos" / "CreateDto" / "UpdateDto" / "Event"  → api/
  - Suffix "Repository" / "Service" / "Mapper" / "EventListener" / "Document" → internal/
  - Annotation @Entity à la racine du fichier (1ère classe) → internal/
  - Sinon : internal/ par défaut (entity sans suffixe particulier)
  - *Controller.java : garde racine
  - package-info.java : garde racine
"""

import os
import re
import sys
from pathlib import Path


def classify(path: Path) -> str:
    """Retourne 'api', 'internal' ou 'root' selon le nom + contenu du fichier."""
    name = path.name
    if name == "package-info.java":
        return "root"
    if re.search(r"Controller\.java$", name):
        return "root"
    # DTOs container ou record exposés
    if re.search(r"Dtos?\.java$|CreateDto\.java$|UpdateDto\.java$|RequestDto\.java$|ResponseDto\.java$|Event\.java$", name):
        return "api"
    # Services internes, Repositories, EventListeners, Mappers, Documents ES
    if re.search(r"Repository\.java$|Service\.java$|ServiceImpl\.java$|Mapper\.java$|EventListener\.java$|Document\.java$|SyncService\.java$", name):
        return "internal"
    # Pour les Entities, on lit le contenu
    try:
        head = path.read_text(encoding="utf-8")[:2000]
    except OSError:
        return "internal"
    if "@Entity" in head:
        return "internal"
    # Default : internal (entité métier sans suffixe particulier)
    return "internal"


def update_package(path: Path, old_pkg: str, new_pkg: str) -> None:
    """Réécrit la 1ère ligne `package ...;` du fichier."""
    text = path.read_text(encoding="utf-8")
    new_text = re.sub(
        rf"^package\s+{re.escape(old_pkg)}\s*;",
        f"package {new_pkg};",
        text,
        count=1,
        flags=re.MULTILINE,
    )
    if new_text != text:
        path.write_text(new_text, encoding="utf-8")


def main():
    if len(sys.argv) != 2:
        print("Usage: modulith-split.py <module-relative-path>")
        sys.exit(1)

    repo_root = Path(__file__).resolve().parent.parent
    src_root = repo_root / "src/main/java/com/onesley/oneclick"
    module_rel = sys.argv[1]  # ex: "modules/payment"
    module_path = src_root / module_rel

    if not module_path.is_dir():
        print(f"❌ Module not found: {module_path}")
        sys.exit(2)

    base_pkg = "com.onesley.oneclick." + module_rel.replace("/", ".")
    api_dir = module_path / "api"
    internal_dir = module_path / "internal"
    api_dir.mkdir(exist_ok=True)
    internal_dir.mkdir(exist_ok=True)

    moved_count = 0
    files = [
        p for p in module_path.iterdir()
        if p.is_file() and p.suffix == ".java"
    ]
    for f in sorted(files):
        target = classify(f)
        if target == "root":
            continue
        target_dir = api_dir if target == "api" else internal_dir
        new_path = target_dir / f.name
        f.rename(new_path)
        update_package(new_path, base_pkg, f"{base_pkg}.{target}")
        moved_count += 1
        print(f"  {target}/  ← {f.name}")

    # Cleanup empty dirs
    if not any(api_dir.iterdir()):
        api_dir.rmdir()
    if not any(internal_dir.iterdir()):
        internal_dir.rmdir()

    print(f"\n✓ Module {module_rel} : {moved_count} fichiers déplacés")


if __name__ == "__main__":
    main()
