#!/usr/bin/env python3
"""Build the CA agents for a target runtime.

    python tools/build.py list
    python tools/build.py ollama [--base qwen3:14b] [--create]
    python tools/build.py claude-skills [--dest ~/.claude/skills]
    python tools/build.py prompt gst-advisor            # paste into a Claude Project

Every target starts from the same agents/<name>/SKILL.md, so one edit updates all runtimes.
"""
from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from agentkit import BUILD_DIR, get_agent, load_agents  # noqa: E402

DEFAULT_BASE_MODEL = "qwen3:14b"
OLLAMA_PREFIX = "ca-"


def cmd_list(_: argparse.Namespace) -> None:
    for a in load_agents():
        print(f"{a.key:24s} {a.description}")


def modelfile_text(agent, base: str, num_ctx: int, temperature: float) -> str:
    system = agent.system_prompt().replace('"""', "'''")  # keep the SYSTEM block parseable
    return (
        f"# Generated from agents/{agent.key}/SKILL.md - edit that file, not this one.\n"
        f"FROM {base}\n"
        f"PARAMETER temperature {temperature}\n"
        f"PARAMETER num_ctx {num_ctx}\n"
        f"PARAMETER top_p 0.9\n"
        f'SYSTEM """\n{system}"""\n'
    )


def cmd_ollama(args: argparse.Namespace) -> None:
    out_dir = BUILD_DIR / "ollama"
    out_dir.mkdir(parents=True, exist_ok=True)
    agents = [get_agent(args.agent)] if args.agent else load_agents()
    for a in agents:
        mf = out_dir / f"{a.key}.Modelfile"
        mf.write_text(modelfile_text(a, args.base, args.num_ctx, args.temperature), encoding="utf-8")
        tag = f"{OLLAMA_PREFIX}{a.key}"
        print(f"wrote {mf.relative_to(BUILD_DIR.parent)}  ->  ollama create {tag} -f {mf}")
        if args.create:
            if shutil.which("ollama") is None:
                raise SystemExit("ollama is not on PATH. Install from https://ollama.com/download and retry.")
            subprocess.run(["ollama", "create", tag, "-f", str(mf)], check=True)
    if not args.create:
        print("\nRun again with --create to register the models, then: ollama run ca-gst-advisor")


def cmd_claude_skills(args: argparse.Namespace) -> None:
    dest = Path(args.dest).expanduser()
    dest.mkdir(parents=True, exist_ok=True)
    for a in load_agents():
        target = dest / a.key
        target.mkdir(parents=True, exist_ok=True)
        # Inline the shared files so the skill is self-contained wherever it is copied.
        text = (
            "---\n"
            f"name: {a.name}\n"
            f"description: {a.description}\n"
            "---\n\n"
            + a.system_prompt()
        )
        (target / "SKILL.md").write_text(text, encoding="utf-8")
        print(f"installed {target / 'SKILL.md'}")
    print(f"\nRestart Claude Code / Claude Desktop, then type /{load_agents()[0].key} to try one.")


def cmd_prompt(args: argparse.Namespace) -> None:
    a = get_agent(args.agent)
    sys.stdout.write(a.system_prompt())


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="cmd", required=True)

    sub.add_parser("list", help="list agents").set_defaults(fn=cmd_list)

    o = sub.add_parser("ollama", help="write Modelfiles (and optionally run `ollama create`)")
    o.add_argument("--base", default=DEFAULT_BASE_MODEL, help=f"base model tag (default {DEFAULT_BASE_MODEL})")
    o.add_argument("--num-ctx", type=int, default=16384, help="context window tokens (default 16384)")
    o.add_argument("--temperature", type=float, default=0.2)
    o.add_argument("--agent", help="build only this agent")
    o.add_argument("--create", action="store_true", help="also run `ollama create` for each agent")
    o.set_defaults(fn=cmd_ollama)

    c = sub.add_parser("claude-skills", help="install as Claude Code / Claude Desktop skills")
    c.add_argument("--dest", default="~/.claude/skills", help="skills folder (default ~/.claude/skills)")
    c.set_defaults(fn=cmd_claude_skills)

    pr = sub.add_parser("prompt", help="print the full system prompt for one agent (for Claude Projects)")
    pr.add_argument("agent")
    pr.set_defaults(fn=cmd_prompt)

    args = p.parse_args()
    args.fn(args)


if __name__ == "__main__":
    main()
