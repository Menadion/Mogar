"""PreToolUse guard for the Bash tool: stop the two shell traps that keep recurring, and
remind the agent to announce a delegate.sh call (see DELEGATE_CALL below).

  1. A `cd` outside a subshell. It moves the session's primary working directory and the move
     persists, so every later call is rooted in the wrong repo (CLAUDE.md, This machine;
     agent-recurring-mistakes pattern 4: fired three times in one sitting, then again after the
     rule was loaded). Use `git -C <path>`, an absolute path, or `( cd <path> && ... )`.
  2. A command that reads stdin with nothing attached: `cat > file` with no heredoc, `claude -p`
     and `codex exec` with no `<`. It waits until the timeout (120 s) and costs a round trip.

It denies rather than nudges, because a nudge on `cd` arrives after the directory has moved.
The reason text goes back to the agent, which rewrites the command once.

It is a text scan, not a shell parser. It strips heredoc bodies, drops quoted text, and treats
`( ... )`, `$( ... )` and backticks as subshells, where `cd` is allowed but the stdin check still
runs. When it cannot parse a command it lets it through: a false positive costs one retry, a
failure of the guard itself must never block work.

Limits, each real:
  - Bash tool only. The PowerShell tool is not covered.
  - Wired in this repo's .claude/settings.json, so it covers sessions rooted in this repo. A
    session rooted in another repo needs the same entry in that repo's settings.
  - A `cd` hidden inside `bash -c "..."` or a script it runs is invisible to a text scan.
  - Cloud containers have no `py`, so there the hook errors without blocking.

This is a per-repo copy of Engine-Room's .claude/scripts/bash_guard.py (carried here so a
session rooted in this repo, away from the laptop, is still guarded). Keep the two in sync.
"""

import json
import os
import re
import sys

SKIP_WORDS = {"if", "then", "do", "else", "elif", "while", "until", "!", "{", "time"}
CLIS = {"claude", "claude.exe", "codex", "codex.cmd", "codex.exe"}

WHY_CD = (
    "A `cd` outside a subshell moves the session's working directory and it stays moved "
    "(CLAUDE.md, This machine). Use `git -C <path>`, an absolute path, or "
    "`( cd <path> && ... )`, then run the command again."
)
WHY_CAT = (
    "`cat >` with no file operand reads stdin and hangs until the timeout. Write the file with "
    "the Write tool, or give cat a heredoc (`cat > f <<'EOF'`)."
)
WHY_CLI = (
    "`claude -p` and `codex exec` read stdin, and with none attached they wait until the "
    "timeout. Add `< /dev/null` (or a heredoc, or a pipe) and run it again."
)

# A third job, and the only one that nudges instead of denying. M reads the app's transcript,
# where a delegate.sh call is folded into "Ran N commands", so he could not see a delegation
# happen and had to ask at the end of a session. Plain text between tool calls is what he does
# see, so the call is announced in one. This cannot be a deny: the transcript a hook can read
# sometimes lags the call (measured 2026-11-10, the announce missing in 1 run of 4), so a deny
# would block calls that were announced. `delegate.sh log` is not a delegation and is not matched.
DELEGATE_CALL = re.compile(r"""delegate\.sh["']?\s+["']?(?:gemini|codex-write|codex)\b""")
ANNOUNCE = (
    "DELEGATE ANNOUNCE: this is a delegate.sh call. If the text you wrote just before it did "
    "not include a line of the form `DELEGATE <target>: <what is being read or written, and "
    "for what>`, write that line as plain text in your next message, before anything else. "
    "M cannot see the call in the app; that line is how he sees it (CLAUDE.md, Delegating "
    "off the Claude limit)."
)


def strip_heredocs(cmd):
    out, end = [], None
    for ln in cmd.split("\n"):
        if end is not None:
            if ln.strip() == end:
                end = None
            continue
        out.append(ln)
        m = re.search(r"(?<!<)<<(?!<)-?\s*(['\"]?)([A-Za-z_][A-Za-z0-9_]*)\1", ln)
        if m:
            end = m.group(2)
    return "\n".join(out)


def split(text):
    """Return (elements, groups): depth-0 command elements as (text, piped_in) with quoted text
    replaced by Q and subshells removed, plus the text of each subshell found."""
    elems, groups = [], []
    cur, piped, i, n = [], False, 0, len(text)

    def flush(next_piped):
        nonlocal cur, piped
        elems.append(("".join(cur), piped))
        cur, piped = [], next_piped

    def matching(start):
        depth, j = 0, start
        while j < n:
            c = text[j]
            if c == "\\":
                j += 2
                continue
            if c in "'\"":
                j = text.find(c, j + 1)
                if j < 0:
                    return n
            elif c == "(":
                depth += 1
            elif c == ")":
                depth -= 1
                if depth == 0:
                    return j
            j += 1
        return n

    while i < n:
        c = text[i]
        if c == "\\":
            i += 2
        elif c in "'\"":
            j = text.find(c, i + 1)
            while c == '"' and j > 0 and text[j - 1] == "\\":
                j = text.find(c, j + 1)
            i = (j if j >= 0 else n - 1) + 1
            cur.append("Q")
        elif c == "`":
            j = text.find("`", i + 1)
            j = n - 1 if j < 0 else j
            groups.append(text[i + 1 : j])
            i = j + 1
            cur.append("Q")
        elif c == "(" or (c == "$" and text[i + 1 : i + 2] == "("):
            start = i if c == "(" else i + 1
            j = matching(start)
            groups.append(text[start + 1 : j])
            i = j + 1
            cur.append("Q")
        elif c == "#" and (not cur or cur[-1] in " \t"):
            j = text.find("\n", i)
            i = n if j < 0 else j
        elif c in ";\n":
            flush(False)
            i += 1
        elif c == "&" and text[i + 1 : i + 2] == "&":
            flush(False)
            i += 2
        elif c == "&" and (text[i - 1 : i] == ">" or text[i + 1 : i + 2] == ">"):
            cur.append(c)
            i += 1
        elif c == "&":
            flush(False)
            i += 1
        elif c == "|" and text[i + 1 : i + 2] == "|":
            flush(False)
            i += 2
        elif c == "|":
            flush(True)
            i += 1
        else:
            cur.append(c)
            i += 1
    flush(False)
    return [(t.strip(), p) for t, p in elems if t.strip()], groups


def words_of(element):
    words = element.split()
    while words and (re.match(r"^[A-Za-z_][A-Za-z0-9_]*=", words[0]) or words[0] in SKIP_WORDS):
        words = words[1:]
    return words


def is_redirect(tok):
    return re.match(r"^(\d*>>?|&>>?|\d*>&\d+)\S*$", tok) is not None


def check(text, allow_cd):
    elems, groups = split(strip_heredocs(text))
    for element, piped in elems:
        words = words_of(element)
        if not words:
            continue
        head = os.path.basename(words[0].replace("\\", "/"))
        if not allow_cd and head in ("cd", "pushd"):
            return WHY_CD
        if piped or "<" in element:
            continue
        if head == "cat":
            rest = words[1:]
            targets = iter(rest)
            only_out = True
            for tok in targets:
                if tok in (">", ">>") or re.match(r"^\d+>>?$", tok):
                    next(targets, None)
                elif not is_redirect(tok):
                    only_out = False
            if only_out and any(is_redirect(t) or t in (">", ">>") for t in rest):
                return WHY_CAT
        elif head in CLIS and (
            (head.startswith("claude") and ("-p" in words or "--print" in words))
            or (head.startswith("codex") and "exec" in words[1:3])
        ):
            return WHY_CLI
    for g in groups:
        why = check(g, True)
        if why:
            return why
    return None


def main():
    try:
        data = json.load(sys.stdin)
        command = (data.get("tool_input") or {}).get("command") or ""
        why = check(command, False)
    except Exception:
        return
    if why:
        print(json.dumps({"hookSpecificOutput": {
            "hookEventName": "PreToolUse",
            "permissionDecision": "deny",
            "permissionDecisionReason": "bash_guard: " + why,
        }}))
    elif DELEGATE_CALL.search(command):
        print(json.dumps({"hookSpecificOutput": {
            "hookEventName": "PreToolUse",
            "additionalContext": ANNOUNCE,
        }}))


if __name__ == "__main__":
    main()
