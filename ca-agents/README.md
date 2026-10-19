# CA Practice AI Agents - write once, run on Claude or offline on Ollama

A starter kit of specialist AI agents for an Indian Chartered Accountancy practice. Each agent is one plain-text file. The same file runs on Claude (claude.ai, Claude Desktop, Claude Code, Claude API) and on any PC offline with Ollama. Edit the file once and every runtime picks up the change.

## What is in the box

| Agent | Use it for |
|---|---|
| `gst-advisor` | GST advisory: registration, ITC, RCM, place of supply, returns, e-invoicing, refunds, Section 73/74/74A notices |
| `income-tax-advisor` | Direct tax under the 1961 Act and the 2025 Act: computation, TDS/TCS, 44AD/44ADA/44AB, capital gains, assessments |
| `tax-audit-reviewer` | Clause-wise Form 3CD review, reconciliations with GST and TDS data, draft queries and qualifications |
| `notice-reply-drafter` | Structured replies to ASMT-10, DRC-01A/01, 143(2), 148A and other notices, with a partner's strategy note |
| `roc-compliance` | Companies Act and LLP Act calendars, forms, resolutions and minutes in SS-1/SS-2 format |
| `client-communication` | Emails, WhatsApp messages, engagement letters and plain-language explanations in the firm's house style |

Folder layout:

```
ca-agents/
  agents/<name>/SKILL.md     one agent = one file (frontmatter + instructions)
  agents/_TEMPLATE/SKILL.md  copy this to make a new agent
  shared/ground-rules.md     citation discipline, output format, ICAI conduct - applied to every agent
  shared/firm-profile.md     your firm name, FRN, signature block, house style - fill this in first
  tools/build.py             converts agents into Ollama Modelfiles, Claude skills, or a Claude Project prompt
  tools/ca_agent.py          terminal chat with any agent on Claude or Ollama
  build/ollama/*.Modelfile   generated; commit them so colleagues can `ollama create` without Python
```

## How an agent is built

Every agent's system prompt is assembled in this order: ground rules, then firm profile, then the agent's own instructions. The `SKILL.md` format is the Agent Skills format Claude uses (a `name` and `description` in YAML frontmatter, then Markdown). Ollama does not read skills, so `build.py` wraps the same text into a Modelfile `SYSTEM` block. Nothing is duplicated by hand.

## Step 0 - ten minutes of setup (everyone)

1. Install Python 3.10 or later from python.org (Windows: tick "Add Python to PATH").
2. Download or `git clone` this repository.
3. Open `ca-agents/shared/firm-profile.md` and replace every `[placeholder]` with the firm's details. This file becomes part of every agent.
4. Read `shared/ground-rules.md` once and adjust anything that does not match the firm's style.

## Path A - Claude on claude.ai (no installation, best quality)

1. In a terminal: `python ca-agents/tools/build.py prompt gst-advisor > gst-prompt.txt`
2. On claude.ai, create a Project named "GST Advisor". Paste the contents of `gst-prompt.txt` into the Project's custom instructions.
3. Upload reference material to the Project knowledge: your firm's standard formats, past opinions you are happy to reuse, the ICAI Guidance Note, rate charts.
4. Repeat for each agent. One Project per agent keeps the behaviour focused.

Use this path for complex advisory work. Do not paste client PAN, Aadhaar or bank details unless your Claude plan's data-handling terms satisfy you; anonymise first.

## Path B - Claude Desktop or Claude Code skills (invoke with a slash command)

```
python ca-agents/tools/build.py claude-skills
```

This installs a self-contained copy of each agent into `~/.claude/skills/<name>/SKILL.md` (Windows: `C:\Users\<you>\.claude\skills`). Restart Claude Code or Claude Desktop and type `/gst-advisor` followed by your question. Claude also calls the skill on its own when a question matches the description line. Windows users can double-click `tools/install-claude-skills.ps1`.

## Path C - fully offline with Ollama (any PC, no data leaves the office)

1. Install Ollama from https://ollama.com/download (Windows, macOS, Linux).
2. Pull a base model sized for the machine (see the table below), for example `ollama pull qwen3:14b`.
3. Build and register the agents:
   ```
   python ca-agents/tools/build.py ollama --base qwen3:14b --create
   ```
4. Chat: `ollama run ca-gst-advisor` (every agent is prefixed `ca-`). Type `/bye` to exit.
5. For a chat window instead of a terminal, install Open WebUI (https://docs.openwebui.com) and select the `ca-*` models from its model list. Open WebUI also lets you attach PDFs and build a document "knowledge" collection per agent.

Without Python, a colleague can still run `ollama create ca-gst-advisor -f ca-agents/build/ollama/gst-advisor.Modelfile` using the committed Modelfiles.

Choosing a base model (check https://ollama.com/library for current tags):

| Machine | Suggested base model | Notes |
|---|---|---|
| 8 GB RAM laptop, no GPU | `qwen3:8b` or `llama3.1:8b` | Fine for drafting and summaries; weaker on multi-step tax computation |
| 16 GB RAM | `qwen3:14b` or `gemma3:12b` | Good default for the whole kit |
| 32 GB RAM or a 12 GB+ GPU | `qwen3:32b` | Noticeably better reasoning on ITC and 3CD clause analysis |
| 64 GB RAM or a 24 GB+ GPU | `llama3.3:70b` or `qwen3:30b-a3b` | Closest offline quality to a cloud model |

Pass a different base with `--base`, and a larger context for long notices with `--num-ctx 32768`.

## Path D - one terminal command for either provider

```
python ca-agents/tools/ca_agent.py gst-advisor                      # Ollama, offline
python ca-agents/tools/ca_agent.py gst-advisor --provider claude    # Claude API
python ca-agents/tools/ca_agent.py notice-reply-drafter --file notice.txt
```

For the Claude provider run `pip install anthropic` and set the `ANTHROPIC_API_KEY` environment variable (or sign in once with `ant auth login`). The default Claude model is `claude-opus-5-5`; override with `--model` or the `CA_AGENT_CLAUDE_MODEL` variable. The Claude path enables Anthropic's server-side refusal fallback by default so a borderline request still gets an answer; disable with `--no-fallback`. Type `/save` in the chat to write the transcript to `build/transcripts/`.

## Which path for which work

| Situation | Recommended path |
|---|---|
| Complex opinion, large notice, need the best reasoning | Claude (Path A or B) |
| Client data that must not leave the premises | Ollama (Path C) |
| Routine drafting: reminders, document lists, resolutions | Ollama is sufficient |
| Team members on different PCs with no technical setup | Claude Projects (Path A) or committed Modelfiles (Path C) |
| You want to automate later (Tally export, GST portal data, this repository's `data.json`) | Path D, then add tools to `ca_agent.py` or build an MCP server |

## Governance before the team uses this

1. Every output is a draft for review by a Chartered Accountant. The ground rules make each agent say so; keep that line.
2. Decide which data classes may go to a cloud model and which stay on Ollama. Write it down as an office SOP.
3. Agents can misstate thresholds and dates. They are instructed to flag these as "verify"; make verification against the notification text (the GST search tool in this repository, the Finance Act, the MCA portal) a step in the SOP.
4. Keep transcripts for engagements where the agent's draft was used, the way you would keep working papers.
5. Do not let an agent cite a case the user did not supply unless the citation has been checked in a reporter.

## A four-week start plan

- Week 1: Set up Path C on one office PC and Path A on your own Claude account. Run ten past queries through `gst-advisor` and `income-tax-advisor`. Note where the answer was wrong or vague.
- Week 2: Fix the firm profile and ground rules based on week 1. Add firm-specific instructions to the agents (your standard reply formats, your preferred order of 3CD review).
- Week 3: Give `client-communication` and `roc-compliance` to the compliance team on Ollama. Collect feedback.
- Week 4: Add one new agent using `_TEMPLATE` (for example `tds-reconciliation` or `bank-statement-analyser`). Decide whether to connect data sources (Tally exports, the `data.json` in this repository) through tools.

## Making a new agent

1. Copy `agents/_TEMPLATE` to `agents/<new-name>` and edit `SKILL.md`. Keep the `description` line specific; Claude uses it to decide when to call the skill.
2. Rebuild: `python tools/build.py ollama --create` and `python tools/build.py claude-skills`.
3. Test the same five questions on both runtimes and compare. Tighten the "Output format" section until both produce the same structure.

## Troubleshooting

- `Could not reach Ollama`: open the Ollama app or run `ollama serve`, then check `ollama list` shows the `ca-*` models.
- Ollama answers are slow or cut off: use a smaller base model or reduce `--num-ctx`.
- Claude says credentials were rejected: set `ANTHROPIC_API_KEY` in the same terminal session, or run `ant auth login`.
- A skill does not appear in Claude Code: confirm the file is at `~/.claude/skills/<name>/SKILL.md` and restart.
