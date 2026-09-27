#!/usr/bin/env python3
"""Drive the test bot (tools/testbot.js) and the server console together, one scripted step per call.

Usage:  python tools/botdrive.py [--timeout 40] STEP [STEP ...]
  bot:<line>    append a line to run/bot-cmds.txt (see testbot.js for the command set)
  rcon:<cmd>    run a console command over RCON and print its reply
  sleep:<ms>    pause

A "!mark <id>" is appended at the end; the call waits until the bot logs it, then prints every bot log line
written during this step. Exit code 3 on timeout.
"""
import argparse
import os
import sys
import time
import uuid
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rcon  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
BOT_LOG = ROOT / "run" / "bot.log"
BOT_CMDS = ROOT / "run" / "bot-cmds.txt"


def append(line: str) -> None:
    with BOT_CMDS.open("a", encoding="utf-8") as f:
        f.write(line + "\n")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--timeout", type=float, default=40)
    ap.add_argument("steps", nargs="+")
    a = ap.parse_args()

    start = BOT_LOG.stat().st_size if BOT_LOG.exists() else 0
    for step in a.steps:
        kind, _, body = step.partition(":")
        if kind == "bot":
            append(body)
        elif kind == "rcon":
            rcon.run([body])
        elif kind == "sleep":
            append(f"!sleep {int(body)}")
        else:
            print(f"unknown step: {step}", file=sys.stderr)
            return 2
    mark = uuid.uuid4().hex[:8]
    append(f"!mark {mark}")

    deadline = time.time() + a.timeout
    while time.time() < deadline:
        data = BOT_LOG.read_text(encoding="utf-8", errors="replace") if BOT_LOG.exists() else ""
        new = data[start:]
        if f"MARK {mark}" in new:
            print("--- bot ---")
            print(new.replace(f"MARK {mark}", "").rstrip())
            return 0
        time.sleep(0.5)
    print("--- bot (TIMEOUT) ---")
    print((BOT_LOG.read_text(encoding="utf-8", errors="replace")[start:]).rstrip())
    return 3


if __name__ == "__main__":
    sys.exit(main())
