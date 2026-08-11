"""Repository-level truth checks that should fail when public docs drift."""

import re
from pathlib import Path
from urllib.parse import unquote


REPO_ROOT = Path(__file__).resolve().parents[2]
LINK_PATTERN = re.compile(r"!?\[[^\]]*\]\(([^)]+)\)")


def test_release_and_contribution_foundations_exist():
    required = [
        "LICENSE",
        "SECURITY.md",
        "CONTRIBUTING.md",
        "CHANGELOG.md",
        "VERSION",
        "docs/RELEASE_CHECKLIST.md",
        "docs/MODEL_PROVENANCE.md",
        "models/model-catalog.v1.json",
        ".github/pull_request_template.md",
        ".github/workflows/desktop-ci.yml",
        ".github/workflows/android-ci.yml",
    ]
    missing = [path for path in required if not (REPO_ROOT / path).is_file()]
    assert not missing, f"Missing repository foundations: {missing}"


def test_active_markdown_has_no_broken_relative_links():
    markdown_files = [REPO_ROOT / "README.md", REPO_ROOT / "CONTRIBUTING.md", REPO_ROOT / "SECURITY.md"]
    markdown_files.extend(
        path
        for path in (REPO_ROOT / "docs").rglob("*.md")
        if "_archive" not in path.parts and "_reference" not in path.parts
    )

    broken = []
    for document in markdown_files:
        for raw_target in LINK_PATTERN.findall(document.read_text(encoding="utf-8")):
            target = raw_target.strip().strip("<>").split("#", 1)[0]
            if not target or target.startswith(("http://", "https://", "mailto:")):
                continue
            resolved = (document.parent / unquote(target)).resolve()
            if not resolved.exists():
                broken.append(f"{document.relative_to(REPO_ROOT)} -> {raw_target}")

    assert not broken, "Broken active documentation links:\n" + "\n".join(broken)
