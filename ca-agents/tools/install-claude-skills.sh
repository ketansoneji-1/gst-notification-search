#!/usr/bin/env bash
# macOS / Linux: installs the agents as Claude Code / Claude Desktop skills.
set -euo pipefail
cd "$(dirname "$0")"
python3 build.py claude-skills
