"""Shared helpers: locate agents, parse SKILL.md, compose the full system prompt."""
from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
AGENTS_DIR = ROOT / "agents"
SHARED_DIR = ROOT / "shared"
BUILD_DIR = ROOT / "build"

FRONTMATTER_RE = re.compile(r"^---\s*\n(.*?)\n---\s*\n(.*)$", re.DOTALL)


@dataclass
class Agent:
    key: str            # folder name, e.g. gst-advisor
    name: str           # frontmatter name
    description: str    # frontmatter description
    body: str           # markdown body (the agent's own instructions)
    path: Path

    def system_prompt(self, include_shared: bool = True) -> str:
        """Ground rules + firm profile + agent instructions, in that order."""
        parts: list[str] = []
        if include_shared:
            for fname in ("ground-rules.md", "firm-profile.md"):
                f = SHARED_DIR / fname
                if f.exists():
                    parts.append(f.read_text(encoding="utf-8").strip())
        parts.append(self.body.strip())
        return "\n\n---\n\n".join(parts) + "\n"


def parse_skill(path: Path) -> Agent:
    text = path.read_text(encoding="utf-8")
    m = FRONTMATTER_RE.match(text)
    if not m:
        raise ValueError(f"{path}: missing YAML frontmatter (--- name/description ---)")
    meta_raw, body = m.group(1), m.group(2)
    meta: dict[str, str] = {}
    for line in meta_raw.splitlines():
        if ":" in line:
            k, v = line.split(":", 1)
            meta[k.strip()] = v.strip()
    if "name" not in meta or "description" not in meta:
        raise ValueError(f"{path}: frontmatter needs both 'name' and 'description'")
    return Agent(
        key=path.parent.name,
        name=meta["name"],
        description=meta["description"],
        body=body,
        path=path,
    )


def load_agents(include_template: bool = False) -> list[Agent]:
    agents: list[Agent] = []
    for skill in sorted(AGENTS_DIR.glob("*/SKILL.md")):
        if skill.parent.name.startswith("_") and not include_template:
            continue
        agents.append(parse_skill(skill))
    return agents


def get_agent(key: str) -> Agent:
    path = AGENTS_DIR / key / "SKILL.md"
    if not path.exists():
        names = ", ".join(a.key for a in load_agents())
        raise SystemExit(f"No agent named '{key}'. Available: {names}")
    return parse_skill(path)
