#!/usr/bin/env python3
"""Chat with any CA agent from the terminal, on Claude (cloud) or Ollama (offline).

    python tools/ca_agent.py gst-advisor                       # Ollama, local, default
    python tools/ca_agent.py gst-advisor --provider claude     # Claude API
    python tools/ca_agent.py notice-reply-drafter --file notice.txt

Inside the chat:  /save  writes the transcript to build/transcripts/   /quit  exits.

Claude:  pip install anthropic   and set ANTHROPIC_API_KEY (or run `ant auth login`).
Ollama:  install from https://ollama.com, then `ollama pull qwen3:14b` (or any base model).
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import sys
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from agentkit import BUILD_DIR, get_agent  # noqa: E402

OLLAMA_URL = os.environ.get("OLLAMA_HOST", "http://localhost:11434").rstrip("/")
OLLAMA_MODEL = os.environ.get("CA_AGENT_OLLAMA_MODEL", "qwen3:14b")
CLAUDE_MODEL = os.environ.get("CA_AGENT_CLAUDE_MODEL", "claude-opus-5-5")


# ---------------------------------------------------------------- Ollama (offline)
def ollama_chat(model: str, system: str, history: list[dict]) -> str:
    """Stream a reply from a local Ollama server using its native /api/chat endpoint."""
    payload = {
        "model": model,
        "messages": [{"role": "system", "content": system}] + history,
        "stream": True,
        "options": {"temperature": 0.2, "num_ctx": 16384},
    }
    req = urllib.request.Request(
        f"{OLLAMA_URL}/api/chat",
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
    )
    out: list[str] = []
    try:
        with urllib.request.urlopen(req) as resp:
            for line in resp:
                if not line.strip():
                    continue
                chunk = json.loads(line)
                piece = chunk.get("message", {}).get("content", "")
                if piece:
                    out.append(piece)
                    print(piece, end="", flush=True)
                if chunk.get("done"):
                    break
    except urllib.error.URLError as e:
        raise SystemExit(
            f"\nCould not reach Ollama at {OLLAMA_URL} ({e}). "
            "Start it with `ollama serve` or open the Ollama app, and check the model is pulled."
        )
    print()
    return "".join(out)


# ---------------------------------------------------------------- Claude (cloud)
def claude_chat(model: str, system: str, history: list[dict], use_fallback: bool) -> str:
    try:
        import anthropic
    except ImportError:
        raise SystemExit("Run `pip install anthropic` first.")

    client = anthropic.Anthropic()  # reads ANTHROPIC_API_KEY or an `ant auth login` profile
    out: list[str] = []
    kwargs = dict(model=model, max_tokens=16000, system=system, messages=history)
    try:
        if use_fallback:
            # On a safety-classifier refusal the API re-runs the request on a fallback model
            # inside the same call, so the practitioner still gets an answer.
            stream_cm = client.beta.messages.stream(
                betas=["server-side-fallback-2026-07-01"], fallbacks="default", **kwargs
            )
        else:
            stream_cm = client.messages.stream(**kwargs)
        with stream_cm as stream:
            for text in stream.text_stream:
                out.append(text)
                print(text, end="", flush=True)
            final = stream.get_final_message()
    except anthropic.AuthenticationError:
        raise SystemExit("\nClaude rejected the credentials. Set ANTHROPIC_API_KEY or run `ant auth login`.")
    except anthropic.RateLimitError:
        raise SystemExit("\nClaude rate limit reached. Wait a minute and retry.")
    except anthropic.APIStatusError as e:
        raise SystemExit(f"\nClaude API error {e.status_code}: {e.message}")
    except anthropic.APIConnectionError as e:
        raise SystemExit(f"\nCould not connect to the Claude API: {e}")
    print()
    if final.stop_reason == "refusal":
        print("[Claude declined this request. Rephrase it, or run the same agent on Ollama.]")
    elif final.stop_reason == "max_tokens":
        print("[Reply was cut off at the token limit. Ask the agent to continue.]")
    return "".join(out)


# ---------------------------------------------------------------- chat loop
def save_transcript(agent_key: str, provider: str, history: list[dict]) -> Path:
    folder = BUILD_DIR / "transcripts"
    folder.mkdir(parents=True, exist_ok=True)
    stamp = dt.datetime.now().strftime("%Y%m%d-%H%M%S")
    path = folder / f"{agent_key}-{provider}-{stamp}.md"
    lines = [f"# {agent_key} ({provider}) - {stamp}\n"]
    for m in history:
        who = "You" if m["role"] == "user" else "Agent"
        lines.append(f"## {who}\n\n{m['content']}\n")
    path.write_text("\n".join(lines), encoding="utf-8")
    return path


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("agent", help="agent folder name, e.g. gst-advisor (see `python tools/build.py list`)")
    p.add_argument("--provider", choices=["ollama", "claude"], default="ollama")
    p.add_argument("--model", help=f"override model (defaults: Ollama {OLLAMA_MODEL}, Claude {CLAUDE_MODEL})")
    p.add_argument("--file", help="text file (notice, trial balance extract) to attach to your first message")
    p.add_argument("--no-fallback", action="store_true", help="Claude only: disable server-side refusal fallback")
    args = p.parse_args()

    agent = get_agent(args.agent)
    system = agent.system_prompt()
    model = args.model or (OLLAMA_MODEL if args.provider == "ollama" else CLAUDE_MODEL)
    history: list[dict] = []

    print(f"Agent: {agent.name} | Provider: {args.provider} | Model: {model}")
    print("Type your question. /save to save transcript, /quit to exit.\n")

    attachment = Path(args.file).read_text(encoding="utf-8", errors="replace") if args.file else None

    while True:
        try:
            user = input("You> ").strip()
        except (EOFError, KeyboardInterrupt):
            print()
            break
        if not user:
            continue
        if user in ("/quit", "/exit"):
            break
        if user == "/save":
            print(f"saved {save_transcript(agent.key, args.provider, history)}")
            continue
        if attachment:
            user = f"{user}\n\n--- Attached document ---\n{attachment}"
            attachment = None
        history.append({"role": "user", "content": user})
        print("Agent> ", end="", flush=True)
        if args.provider == "ollama":
            reply = ollama_chat(model, system, history)
        else:
            reply = claude_chat(model, system, history, use_fallback=not args.no_fallback)
        history.append({"role": "assistant", "content": reply})

    if history:
        print(f"Transcript saved to {save_transcript(agent.key, args.provider, history)}")


if __name__ == "__main__":
    main()
