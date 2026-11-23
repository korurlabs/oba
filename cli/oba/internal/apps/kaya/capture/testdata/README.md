# Golden corpus of the Python recorder

Recorded from Kaya's former Python recorder (its hook, state and sender modules; spec 18 deleted it, and the commit below still holds it) at commit `22749e58080f79657556b1ab7f45edaf5a82c8b9` of branch `kaya-cli-capture`, with the recorder tree unmodified (its last change is `1b8fdf4a3`). Python 3.14.8, git 2.56.0. The Go port of spec 18 (`docs/private/plans/kaya/18_KAYA_CLI_CAPTURE.md`) must reproduce these outputs. The redaction corpus is in `../../redact/testdata/`.

## Command

The generator imports the recorder, so it is not committed. It was run from the repository root as

```
python3 <scratchpad>/corpus18/gen.py "$PWD" "$PWD/projects/kaya/cli/internal/capture/testdata" "$PWD/projects/kaya/cli/internal/redact/testdata"
```

It runs, per case and per step, `driver.py`, which calls the recorder's own `record.main()` as a fresh `python3 -I` process with the clock fixed to the step's `at` and the machine name fixed to `golden-host`. After the last step it runs the recorder's sender as `python3 -I` with `--once` against a local fake ingest server, once per scenario. `KAYA_SENDER=off`, `HOME`, `XDG_STATE_HOME`, `CLAUDE_CONFIG_DIR`, the git config and `PATH` all point into a scratch directory, so the developer's own `~/.local/state/kaya` is never read or written. Two runs give byte-identical files.

## Layout

```
transcripts/              transcript files the hook inputs point to ({{TRANSCRIPTS}})
cases/<name>/case.json    the input: setup and the steps
cases/<name>/expected/    what the Python code produced, decoded
```

`case.json` keys: `session_id`; `branch`, `remote`, `email` (the scratch repository's branch, `origin` URL and `user.email`; null means none); `ssh_aliases` (alias to host; see below); `account` (true: `.claude.json` holds `oauthAccount` with the six account fields plus other fields that must never be copied); `worktree` (a linked worktree `.claude/worktrees/<name>` was added); `repo_files` and each step's `files` (`path`, `content`, `mtime`: written into the checkout, with that modification time, before the first step or before that step); `queue_files` (files in the session's queue directory before the first step, for example `config-release.json`); `state_files` (files in the state directory, for example `state.json`); `usage` (the status line's usage file); `queue_edits` (changes to `events.jsonl` after the last step); `scenarios` (sender runs, below); `steps`.

A step is `{at, hook, files?, env?}` or `{at, raw_stdin}`. `at` is the fixed clock of that call. `hook` is the JSON the recorder reads on stdin. `env` adds environment variables to that one call.

Placeholders, in inputs and in expected outputs: `{{REPO}}` the scratch checkout, `{{TRANSCRIPTS}}` the `transcripts/` directory of this corpus, `{{OUTSIDE}}` a directory outside any git repository, `{{STATE}}` the state directory (only in messages).

Large values: a string longer than 4096 characters is stored as `{"$repeat": "<unit>", "$times": N}` or as `{"$concat": [<string or $repeat object>, ...]}`. A test expands these recursively in the input and in the expected files before it compares. Lengths are in characters (runes), never bytes.

JSON files are written with sorted keys. Compare decoded values, not text.

### Expected files per case

- `steps.json`: per step `exit` (always 0), `stdout` (always empty) and `stderr` (lines, placeholders applied).
- `events.json`: every line of the queue's `events.jsonl` after the last step and before `queue_edits`, with `seq`.
- `summary.json`, `session.json`: the queue's files after the last step. `session.json` holds `machineName: "golden-host"`; the spec compares it without the machine name.
- `snapshots.json`: every file under `handoffs/` and `plans/` of the queue directory (path, `content` as written locally, before redaction, and `mtime`).
- `files.json`: the queue directory listing after the last step (`.lock` and temporary files left out).
- `queue_after_edits.json`: only with `queue_edits`; `events.jsonl` after the edits.
- `batch.json`: the body the sender POSTed first in the `ok` scenario.
- `sender/<scenario>.json`: per run `token` (`A` or `B`), `exit`, `requests` (`authorized`: the bearer header equalled the run's token; `contentType`; `body`, or the text `same as ../batch.json`), `log` (`recorder.log` lines without timestamp and pid), `state` (`state.json`), `cursor` (`cursor.json`), `files` (queue directory after the run), `queueAfter` (with `queue_edits`: `events.jsonl` and `events.invalid.jsonl`), `usageFileLeft`. `updatedAt`, `lastSentAt`, `sentAt` and `parked.at` are removed; fingerprints are replaced by `<fingerprint>` because the Go fingerprint need not equal Python's.
- `hook-errors` and cases whose input never reaches the session directory have `sessions.json` (events by session directory) instead of the files above.

Tokens: `A` is `kyt_` plus 43 `A`, `B` is `kyt_` plus 43 `B`. They appear in no expected file.

### The ssh stand-in

`ssh -G -- <host>` was replaced by a script that prints `hostname <resolved>` from `ssh_aliases`, prints the host itself when it is not listed, and exits 255 for `broken-host`. A Go test must inject the same resolver.

### Scenarios

Each scenario starts from the state after the last step. `responses` is the list of answers the fake gives in order; once empty it answers 200 and acknowledges the highest `seq` of the batch. A run is one `sender --once` round. Scenarios: `ok`, `forbidden-sec-007` (403 `KAY_SEC_007` with `errorValues.gitAuthorEmail`, then accepted), `unauthorized` (401, then the same token is refused without a request, then token B is sent), `unprocessable-park` (422 parks, same token sends nothing, token B sends), `conflict-park` (409), `bad-request-park` (400), `too-large-park` (413), `too-many-requests` (429 then 200), `server-error` (503 then 200), `partial-acknowledgement` (200 with `acknowledgedSeq` 5, then the rest), `no-acknowledged-seq` (200 without data). `config-release-states` has scenarios for the report file variants and the usage file variants.

## Cases

`fixtures-session` the 15 recorder hook fixtures in order, with a handoff, a config release report and a usage file; `handoff-bash-heredoc` heredoc handoff, rescan, older and temporary files; `plan-message-stop`, `plan-exitplanmode`, `plan-spec-files` (linked worktree, Write, Edit, MultiEdit, Bash, failures, 200000 and 200001 characters, multibyte); `model-and-transcript` (model of the hook, of the Agent call, of the main and subagent transcript tail, version); `event-shapes` (every event kind and `ok` rule); `limits` and `limits-plan-message` (200000-character inputs, cuts at 500, 300 and 2000 characters counted in characters, handoff redacted before the 200000 cut); `event-redaction`; `remote-*` (ssh aliases, `ssh://` with port, `git+ssh://`, https with user and password, unresolved alias, failing ssh, local path, no remote); `git-identity`; `summary-fold`; `queue-damage`; `token-warning-*`; `hook-errors`; `config-release-states`.

## What the corpus does not pin

- The scope check is new in the Go port; the Python recorder records every git checkout. `oba kaya hook record` records only a checkout one of whose remotes is on the organization's repository list, and makes that check before it calls `Record`, so the corpus replays `Record` directly and pins no scope.
- The summary key `project` is in `summary.json` (the Python recorder wrote it) but is no longer sent by the Go port; `batch.json` still holds it. The spec compares the summary keys except `project`.
- The 403 log line is Python's (`Kaya answered 403 KAY_SEC_007; retrying in 2 s`); the spec asks the Go line to carry the `gitAuthorEmail` as well.
- The run export under `.kaya/runs`, the skills catalogue, the permission overview and `--backfill` are removed by the spec and are not covered.

## Python behaviours the corpus records, for the implementer

- `description` of an Agent call is redacted but not cut (the `limits` case keeps 5000 characters).
- A Bash event without a command has `command: ""`; a prompt that holds only harness blocks has no `prompt` key.
- A briefed handoff path `handoffs/../x.md` puts `..` into `briefed_features`; every rescan then writes the stderr line `briefed handoff directory '..' refused` (see `event-shapes`).
- Plans are written with the step's clock as modification time in this corpus (the Python code uses the wall clock); `capturedAt` of a session plan is therefore the `at` of the step that wrote it.
- Redaction follows Python's Unicode case folding (`ſecret=`, `KEY=`) and Unicode whitespace; see the redact corpus.
