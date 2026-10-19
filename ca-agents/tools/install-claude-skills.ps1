# Windows: double-click or run in PowerShell. Installs the agents as Claude Code / Claude Desktop skills.
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
python "$here\build.py" claude-skills
