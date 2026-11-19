"""Commit-trailer guard: a git commit-msg hook that enforces the mechanical half of CLAUDE.md's
Commit trailers section.

  py trailer_guard.py <message-file>     what git runs, via the stub --install writes
  py trailer_guard.py --install [path]   write that stub into each repo's .git/hooks,
                                        or into one repo given by path

The ruling: the only Co-Authored-By line Claude may carry is exactly
`Co-Authored-By: Claude <noreply@anthropic.com>`, no model name. The harness's own reminder
asks for the model-name form every session, so only an agent obeying CLAUDE.md over it holds
that off; this makes it deterministic. It also fires in the repo being committed to, which a
settings file does not: settings are read from the session's primary working directory, and
that is how 11 Mogar commits lost their trailer (2026-10-23 to 10-26).

  Engine-Room, VoidLibrary   reject a Claude trailer that is not the exact form
  Mogar                      the same, and require the exact trailer (pure build lane)
  anything else              untouched

It cannot judge the lane in the vault or VoidLibrary, where a missing trailer is correct for a
debate or a note, so there it only rejects the wrong form. A merge commit is exempt from the
requirement. Trailers are read with `git interpret-trailers`, not a grep, so a body that merely
discusses one does not trip it.

Limits, each real:
  - .git/hooks is not versioned, so each repo needs --install, and a fresh clone has no hook.
    The vault's SessionStart hook now re-installs it when it is missing, so that half is
    closed for any clone of a repo that carries this script; Mogar and VoidLibrary carry no
    copy, so away from the laptop they are still unguarded.
  - `git commit --no-verify` skips it, and so does a commit made outside git (an API write).
  - It fails open when git or the message file cannot be read, so a broken hook never blocks work.

This is a per-repo copy of Engine-Room's .claude/scripts/trailer_guard.py (carried here so a
session rooted in this repo, away from the laptop, is still guarded without the fallback below).
Keep the two in sync.
"""

import os
import stat
import subprocess
import sys

WANTED = "Claude <noreply@anthropic.com>"
SCRIPT = "C:/Users/Menadion/Documents/Obsidian/Engine-Room/.claude/scripts/trailer_guard.py"
REPOS = {
    "engine-room": ("C:/Users/Menadion/Documents/Obsidian/Engine-Room", "ban"),
    "voidlibrary": ("C:/Users/Menadion/Documents/Projects/VoidLibrary", "ban"),
    "mogar": ("C:/Users/Menadion/Documents/Projects/Mogar", "require"),
}
STUB = """#!/bin/sh
# Installed by .claude/scripts/trailer_guard.py --install.
# Fails open when no Python is on PATH or the script cannot be found: a broken hook
# must never block a commit. Prefers the copy inside this repo, so a fresh clone or a
# cloud container guards itself without the laptop vault being present.
PY=$(command -v py || command -v python3 || command -v python)
[ -n "$PY" ] || exit 0
S="$(dirname "$0")/../../.claude/scripts/trailer_guard.py"
[ -f "$S" ] || S="%s"
[ -f "$S" ] || exit 0
exec "$PY" "$S" "$1"
""" % SCRIPT


def install(target=None):
    # target: install into one repo by path, for a clone whose location REPOS cannot know
    # (a cloud container, a second checkout). Mode still comes from the folder name, which
    # is what main() looks up at commit time.
    if target is None:
        repos = REPOS
    else:
        key = os.path.basename(os.path.abspath(target)).lower()
        repos = {key: (target, REPOS.get(key, (None, 'ban'))[1])}
    for name, (path, _mode) in repos.items():
        hook = os.path.join(path, ".git", "hooks", "commit-msg")
        if not os.path.isdir(os.path.dirname(hook)):
            print("%-12s skipped: no .git/hooks at %s" % (name, path))
            continue
        if os.path.exists(hook):
            with open(hook, encoding="utf-8", errors="replace") as f:
                if "trailer_guard.py" not in f.read():
                    print("%-12s skipped: a different commit-msg hook is already there" % name)
                    continue
        with open(hook + ".tmp", "w", encoding="utf-8", newline="\n") as f:
            f.write(STUB)
        os.replace(hook + ".tmp", hook)
        os.chmod(hook, os.stat(hook).st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)
        print("%-12s installed (%s)" % (name, repos[name][1]))


def claude_trailers(message):
    body = "".join(ln for ln in message.splitlines(True) if not ln.startswith("#"))
    out = subprocess.run(
        ["git", "interpret-trailers", "--parse"], input=body, capture_output=True,
        text=True, encoding="utf-8", errors="replace", check=True,
    ).stdout
    found = []
    for ln in out.splitlines():
        key, _, value = ln.partition(":")
        value = value.strip()
        if key.strip().lower() == "co-authored-by" and (
            "anthropic" in value.lower() or value.lower().startswith("claude")
        ):
            found.append(value)
    return found, body


def main():
    if len(sys.argv) > 1 and sys.argv[1] == "--install":
        install(sys.argv[2] if len(sys.argv) > 2 else None)
        return 0
    try:
        top = subprocess.run(
            ["git", "rev-parse", "--show-toplevel"], capture_output=True, text=True, check=True
        ).stdout.strip()
        mode = REPOS.get(os.path.basename(top).lower(), (None, None))[1]
        if mode is None:
            return 0
        with open(sys.argv[1], encoding="utf-8", errors="replace") as f:
            message = f.read()
        found, body = claude_trailers(message)
    except Exception:
        return 0
    problem = None
    wrong = [v for v in found if v != WANTED]
    if wrong:
        problem = "carries a Claude trailer that is not the exact form: Co-Authored-By: " + wrong[0]
    elif mode == "require" and WANTED not in found and not body.startswith("Merge "):
        problem = "has no Claude trailer, and this repo is pure build lane"
    if problem:
        sys.stderr.write(
            "trailer_guard: this commit %s.\n"
            "  Use exactly:  Co-Authored-By: %s\n"
            "  CLAUDE.md, Commit trailers: no model name, whatever the harness reminder says.\n"
            "  Fix the message and commit again. Do not use --no-verify.\n" % (problem, WANTED)
        )
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
