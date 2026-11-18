# Kaya CLI

`kaya` signs a user in to Kaya, seeds an organization's agent config from today's repositories, and delivers that config to Claude Code sessions as signed releases (`install`, `launch`, `sync`, `refresh`, `status`, `watch`, `hook`), proposes changes of that config for review (`propose`), shares lessons between sessions and repositories (`lessons`), and records Claude Code sessions for Kaya (`hook record`, `sender`, `statusline`). The design is in `docs/private/plans/kaya/01_KAYA.md`, section 4.5.

## Build and test

```sh
make build   # bin/kaya
go vet ./...
make lint    # golangci-lint v2.13.2, config in .golangci.yml
make test    # go test -race ./...; the seed tests need git
```

## Sign-in

```sh
kaya login          # production: api.korur.com, realm sensey at auth.korur.com
kaya login --dev    # the local Oba stack: API :8080, Keycloak :8086
kaya logout
```

`kaya login` runs the device authorization grant against the public Keycloak client `kaya-cli`, then calls the platform `POST /users/me`, which creates or links your Oba user and lists the organizations you belong to. The tokens, the API URL and the chosen organization are stored in `<user config dir>/kaya/credentials.json` (mode 0600) and refreshed when they expire. With several memberships, pass `--org <id>` to `login` or to each command.

Every setting can be given as a flag or an environment variable: `--api-url` / `KAYA_API_URL`, `--issuer` / `KAYA_ISSUER`, `--client-id` / `KAYA_CLIENT_ID`, `--org` / `KAYA_ORG_ID`. A flag wins over the environment, the environment over the stored sign-in, and the stored sign-in over the production defaults.

User commands use this sign-in. Recording and config delivery use the machine token `KAYA_RECORDER_TOKEN` instead.

## Seed

```sh
kaya seed --agents ~/src/agents --repo ~/src/village --repo ~/src/acme
kaya seed --repo . --dry-run      # print what would be sent; no sign-in, no network
kaya seed --repo . --json         # the seed run as JSON
kaya seed --repo . --project kagi=projects/kagi   # also seed the folder projects/kagi as the project kagi
```

Only an OWNER seeds. The seed:

1. reads every pack under `packs/` of the `korurlabs/agents` clone at the consumer paths the sync writes (`PACK_LAYOUT` of `tools/sync.py`: flat directories take only their direct files, `hooks/` leaves `README.md` and subdirectories such as `tests/` in the source, `.gitkeep`, `.DS_Store` and `__pycache__/` are never delivered), with file modes and the `.claude/skills/<name>` links;
2. reads per repository its packs from `.agents/.managed.json`, and its own files at the paths Kaya delivers (`AGENTS.md`, `.agents/AGENTS.md`, `.agents/{rules,skills,roles,workflows,templates,hooks}/`, `.claude/{agents,skills}/`, `.claude/settings.json`, and the convention docs `docs/private/conventions/**/*.md`) without following symlinks; managed files come from the packs, not from the repository. Each repository is seeded as its root project, a PROJECT set without `rootPath` named after the repository (at most 40 characters), which carries the repository's own files and the packs it composes. The convention docs belong to the root project and are read only from the repository, never from the agents clone; no other part of `docs/` is read;
3. reports every other path under `.agents/`, `.claude/`, `docs/private/conventions/` (a file there that is not Markdown: "Only Markdown convention docs are delivered"), the root agent files and the agents clone as not imported, with the reason, and every managed file whose disk hash differs from `.managed.json` as drift; report paths are prefixed with their source (`village:.agents/memory/lessons.md`, `agents:tools/`);
4. posts the seed run; when the server answers `KAY_CFG_009`, uploads exactly the missing blobs with `PUT /blobs/{sha256}` and posts again, so a repeated seed uploads nothing;
5. prints the report (the entities stored per kind, each set, the releases, refused files with their error code and reason) and compares each repository's current release with the repository on disk: same paths, sha256, modes and link targets.

`--project <name>=<root>` (repeatable) seeds a folder of a repository as a project: a folder project, a PROJECT set (`kind`, `name`, the repository's `remoteUrl`, `rootPath`), which Kaya creates when it is absent. The name is 1 to 40 of `a-z`, `0-9` and `-` and differs from the root project's; the root is 1 to 4 folders, none starting with a dot, not under `docs/`, and no root may lie inside another. The repository is the one `--repo` whose checkout holds the root. The folder project takes from the root project the files of the project namespace, and reads `<root>/AGENTS.md`:

| Kind | Paths of the project `<p>` |
|---|---|
| Rules | `.agents/rules/<p>/**.md` |
| Skills | `.agents/skills/<p>-<name>/**`, the link `.claude/skills/<p>-<name>` |
| Subagents | `.claude/agents/<p>-*.md` |
| Roles, workflows, templates | `.agents/{roles,workflows,templates}/<p>/**` |
| Convention docs | `docs/private/conventions/projects/<p>/**.md` |
| Agents file | `<root>/AGENTS.md` |

Every other `AGENTS.md` or `CLAUDE.md` below the root, and every file of a `.claude/` or `.agents/` directory there (`projects/kagi/operator/AGENTS.md`, `projects/kagi/.claude/settings.local.json`), is reported as not imported with "Nested agent files stay in git"; `node_modules/` and other hidden directories below the root are not read. The seed sets no override markers, so the release still equals the repository. A dry run prints each project set with its root. A later seed of the repository names every project Kaya holds for it: Kaya refuses a seed that leaves one out, and the error names the `--project <name>=<root>` to add. When Kaya refuses a seed, `kaya seed` prints the reason of each refused field below the error code. The design is in `docs/private/plans/kaya/03_KAYA_PROJECTS.md`.

Without `--agents`, the packs the server holds are kept and only the repositories' own files are seeded. A repository without `.agents/.managed.json` has been cut over to Kaya; the seed refuses it unless `--after-cutover` is passed, which needs `--agents`.

Exit status: 0 when the run is PASSED and every release equals its repository; 2 when the run is FAILED or a release differs; 1 on any other error.

## Layout

| Path | Holds |
|---|---|
| `main.go` | entry point; the version comes in through `-ldflags "-X main.version=..."` |
| `cmd/` | one file per command, each registering itself on `rootCmd` |
| `internal/config/` | API URL, issuer, client id and organization resolution |
| `internal/auth/` | device authorization, the credentials file, token refresh |
| `internal/client/` | the Kaya API client: the `CustomResponse` envelope, `APIError`, one file per API area (`users.go`, `seed.go`) |
| `internal/seed/` | reading packs and repositories, the seed request, the upload and release comparison, the report |
| `internal/client/delivery.go` | the `/kaya/delivery/**` calls with the machine token, and `GET /kaya/signing-keys` |
| `internal/delivery/` | trusted keys, manifest verification, the path and link rules, the blob cache, versions and the switch, sessions, the tamper check, the session's release report |
| `internal/install/` | the machine setup of `kaya install` |
| `internal/jsonedit/` | key-level edits of JSON settings files that keep every other key as written |
| `internal/client/proposal.go` | `POST /kaya/delivery/proposals` with the machine token |
| `internal/client/lessons.go` | the `/kaya/delivery/lessons` calls with the machine token |
| `internal/lessons/` | the local checks of a lesson, the session a lesson belongs to, splitting and importing a lessons file |
| `internal/propose/` | collecting a proposal against the active release, the local refusals, the request, the texts for Kaya's refusals |
| `internal/redact/` | what looks like a secret: `Text` redacts it, `Find` names the lines that hold one |
| `internal/capture/` | one hook event into the local queue: scope, event, summary, session facts, transcript facts, plan and handoff snapshots, the queue files and locks, the status line's usage file |
| `internal/sender/` | the queue to Kaya: batches, answers, back-off, the detached start |
| `internal/client/ingest.go` | `POST /kaya/ingest/batches` with the machine token, no redirects |

## Config delivery

A checkout is delivered when it holds a committed `kaya.yaml` and no `.agents/.managed.json` (until the cutover, korurlabs/agents still syncs it and Kaya leaves it alone). `kaya.yaml` may set `remoteUrl`, when the origin remote does not name the repository, and `organization`, the organization id every release must be signed for; without it the first verified release pins it.

```sh
kaya install            # once per machine; --dry-run shows the changes
kaya sync               # fetch, verify and switch; exits 1 without a verified release
kaya refresh            # switch even while other sessions of the checkout run
kaya status [--json]    # local state only
```

- `kaya install` sets `claudeCode.claudeProcessWrapper` in the VS Code user settings to `kaya-launch` (the same binary under that name; Homebrew and `install.sh` install the link, otherwise `~/.local/share/kaya/bin/kaya-launch`), adds `claude() { kaya launch claude "$@"; }` to `~/.bashrc` and `~/.zshrc`, and registers `kaya hook <event>` for `SessionStart`, `SessionEnd`, `PreToolUse`, `UserPromptSubmit` and `PostToolUse` in the Claude Code user settings, and removes the `kaya hook config-change` registration of `ConfigChange` an older kaya made. Each changed file is first copied to `<file>.kaya-backup-<time>`. A VS Code settings file with comments is left alone; the command prints the line to add.
- `kaya launch <command>` syncs (3 s online at most, then the cache), registers the process under `.kaya/sessions/`, starts `kaya watch`, sets `KAYA_RELEASE`, `KAYA_RELEASE_STATE`, `KAYA_LAUNCHED=1` and `KAYA_CHECKOUT`, and execs the command. As `kaya-launch` it takes an absolute executable first argument as the command (the extension's bundled Claude), otherwise it runs `claude` with the arguments.
- Delivered paths: `AGENTS.md`, `.agents/AGENTS.md`, `.claude/settings.json`, everything under `.agents/{rules,skills,roles,workflows,templates,hooks}/` and `.claude/{agents,skills}/`, the Markdown files under `docs/private/conventions/`, and the `AGENTS.md` of a project folder, one to four folders deep with no folder starting with a dot (`projects/kagi/AGENTS.md`). A project's other files live at the namespace paths of the table under Seed, at the repository root.
- Start folder: Claude Code reads `.claude/settings.json` only from the folder a session starts in and does not load the delivered rules, whose links lead out of a sub-folder. A session started below the checkout root (`projects/kagi`) therefore runs without the delivered hooks, guardrail deny rules and rules; `kaya hook session-start` puts a warning into its context that names the folder and asks for a restart at the checkout root.
- Trust: a release is used only when its Ed25519 signature verifies with a key compiled into `releaseKeys` (`internal/delivery/keys.go`), its manifest hashes to `manifestSha256`, it names the checkout's organization and normalised remote, its number is not below the highest verified here, every path passes the server's allow-list and link rules, and every blob hashes to its name. Only then is anything written. For the local stack, `kaya signing-keys` prints a `KAYA_DEV_TRUSTED_KEYS` line, honored only while the API is a loopback address.
- Layout: `.kaya/versions/<n>/` holds each built release, `.kaya/current` points at the active one (switched by one rename), and every delivered path is a relative link into `.kaya/current`, except `.claude/settings.json`, a regular file with the guardrail deny rules merged into `permissions.deny`. `.agents/.kaya-release.json` names the active release. The blob cache is `$XDG_CACHE_HOME/kaya/blobs/<sha256>`, shared by the machine's checkouts. The newest three versions stay.
- Pinning: while another session of the checkout runs, a newer release is staged (state STALE) and activated by `kaya hook session-end` of the last session, or by `kaya refresh`.
- States: FRESH (the newest release the server named), CACHED (Kaya not reached, or its newest release not taken), STALE (a newer release waits), NONE (no verified release; the guard refuses `Bash`, `Edit`, `Write`, `MultiEdit`, `NotebookEdit`, `Skill` and `Agent`), UNVERIFIED (the active release fails its check and cannot be restored). `kaya hook session-start` writes the state, the release and `deliveredBy` (LAUNCHER or HOOK) to `$XDG_STATE_HOME/kaya/queue/sessions/<session id>/config-release.json`, which the sender adds to the session's batch.
- Hooks read only local state, except `session-start`: in a session the launcher did not start it syncs, switches when no always-on file changed (`reloadSkills`), and otherwise stages the release and asks for a restart; in every session it asks Kaya for plan feedback and lessons (below). `pre-tool-use` also refuses edits of delivered paths and runs `.agents/hooks/dispatch.sh` of the checkout when neither the project settings nor the korurlabs/agents user-level launcher run it.
- Watcher: `kaya watch` asks `/kaya/delivery/current` every minute and builds a newer release for the next session, without switching.
- Plan feedback: every 15 seconds `kaya watch` fetches the reviewer feedback on plans whose latest version came from a live session of the checkout (`GET /kaya/delivery/plan-feedback?sessionId=`) into `.kaya/inbox/<session id>.json`, leaving out what a hook already delivered, and posts `.kaya/inbox/<session id>.acks.json` to `POST /kaya/delivery/plan-feedback/acks`, deleting each id after a 2xx (the last round runs when the last session ended). `kaya hook user-prompt-submit` and `post-tool-use` read only the inbox: they add the items as `additionalContext` (10 000 characters at most; the rest is named as "and N more on the Kaya plan page <url>" and waits for the next hook) and move the added ids to the acks file. No inbox gives no output. `session-start` asks `GET /kaya/delivery/plan-feedback?remoteUrl=&branch=` for the feedback a session that ended on the checkout's branch left, for 2 seconds at most, adds it after the release lines, and acknowledges it with the new session id (queued in the acks file when Kaya is not reached); offline it adds nothing and the feedback stays pending. `session-end` removes the session's inbox.
- Lessons: `session-start` asks `GET /kaya/delivery/lessons?remoteUrl=&since=` with `If-None-Match`, for 2 seconds at most (see Lessons).
- Machine token: `KAYA_RECORDER_TOKEN`, else `kagi run --personal --path /kaya/recorder`.

## Capture

`kaya` records Claude Code sessions for Kaya: their events, summary, plans, handoff snapshots, user, account and usage limits. Nothing else on the machine records them.

```sh
kaya hook record    # one hook event on stdin into the local queue
kaya statusline     # the status line: usage limits into the queue, then your own status line
kaya sender --once  # hidden: send the queue once; hooks start the sender by themselves
```

- Registration: the repository's `.claude/settings.json`, not `kaya install`. It runs `kaya hook record` for `PreToolUse` (`Agent|Skill|ExitPlanMode`), `PostToolUse` and `PostToolUseFailure` (`Agent|Skill|Bash|Edit|Write|MultiEdit`), `UserPromptSubmit`, `UserPromptExpansion`, `SessionStart`, `SubagentStart`, `SubagentStop`, `Stop` and `SessionEnd`, and `kaya statusline` as its `statusLine`. Without `kaya` on the `PATH`, each hook prints `kaya recorder: kaya not found, nothing recorded` on stderr and exits 0, and the status line shows `kaya status line: kaya not found`; the session itself is not affected.
- Scope: an event is recorded only in a checkout with a committed `kaya.yaml`. In any other checkout the hook writes nothing.
- `kaya hook record` reads no network, prints nothing on stdout, runs git only on `SessionStart`, `Stop` and `SessionEnd`, and exits 0 on every error with one `kaya hook: nothing recorded: ...` line on stderr. Prompts, command arguments, commands and messages are redacted before they are written; plan and handoff snapshots are redacted when the sender builds the batch. After it queued an event it starts `kaya sender` unless one runs or `KAYA_SENDER=off` is set.
- `kaya sender` is one process per machine (`sender.lock`). It reads the machine token once (as config delivery does), sends only over https or to a loopback address, follows no redirect, and every 2 seconds sends each session one batch to `POST /kaya/ingest/batches`. A 401 stops it until the token changes; 400, 409, 413 and 422 park the session until the token changes; 403 `KAY_SEC_007` (no Kaya user holds the session's git email), 429, 5xx and network errors back off from 2 seconds to 5 minutes and keep the queue. It exits after 10 idle minutes and removes an ended, acknowledged session after 7 days.
- `kaya statusline` reads the session JSON Claude Code passes. When `rate_limits` holds a `five_hour` or `seven_day` window (a percentage from 0 to 100, a reset within 8 days) that differs from the last one, it writes the usage file, which the sender adds to the session's next batch. It then runs your own `statusLine` command from the Claude Code user settings (`$CLAUDE_CONFIG_DIR/settings.json`, else `~/.claude/settings.json`) with the same input, `KAYA_STATUSLINE=1` and a 2 second limit, and prints its output unchanged. Without one, the line is `<model> | 5h 23% | 7d 41%`; when your command fails or times out, that line ends with a pointer to `recorder.log`. With `KAYA_STATUSLINE=1` it neither captures nor wraps, so a command that calls `kaya statusline` cannot recurse.

The local state lives in `$XDG_STATE_HOME/kaya` (`~/.local/state/kaya` without it, on macOS as well), mode 0700. Its format belongs to this CLI alone.

| Path | Holds |
|---|---|
| `queue/sessions/<id>/events.jsonl` | the session's events, one JSON line each, with a per-session `seq` from 1 |
| `queue/sessions/<id>/summary.json`, `session.json` | the run summary, and the session's machine, Claude Code version, model, remote, git author email and Claude account |
| `queue/sessions/<id>/config-release.json` | the release report of `kaya hook session-start` |
| `queue/sessions/<id>/plans/`, `handoffs/` | plan and handoff snapshots waiting to be sent |
| `queue/sessions/<id>/cursor.json`, `.lock` | what Kaya acknowledged, and the lock every write of the queue holds |
| `queue/usage/<id>.json` | the latest usage limits the status line saw |
| `state.json` | what the sender last learned about its token: a fingerprint, never the token |
| `sender.lock`, `sender.started` | the sender's lock, and its last start by a hook (the back-off of restarts) |
| `statusline-logged.json` | which status line problems were already logged for which session |
| `recorder.log`, `recorder.log.1` | the sender's and the status line's log, moved to `.1` at 1 MB |

### Moving from the Python recorder

The Python recorder is gone, and events it left queued are not sent. Once per machine, after you updated the CLI (`brew upgrade korurlabs/tap/kaya-cli`, or `make build` here):

1. Stop a Python sender that still runs: `pkill -f 'recorder/sender'`.
2. Remove its queue and state:

   ```sh
   rm -rf "${XDG_STATE_HOME:-$HOME/.local/state}/kaya/queue" "${XDG_STATE_HOME:-$HOME/.local/state}/kaya/state.json"
   ```

3. Start new Claude Code sessions. A session that ran across the change starts its `seq` again from 1, and Kaya drops the events whose `seq` it already holds.

A checkout on an older branch still has the Python rows in its `.claude/settings.json` and records through Python while that branch still has the Python recorder.

## Propose

```sh
kaya propose --reason "Allow the kaizen CLI" .claude/settings.json
kaya propose --dry-run                     # every changed delivered path and new config file, checked, not sent
kaya propose --reason "..." --from /tmp/edit .agents/rules/core.md
kaya propose --reason "..." --remove .agents/rules/old.md
```

- A proposal is made on the release the checkout runs (`kaya status`); the checkout needs a verified active release. Each named path is compared with that release: a delivered file edited through its link or replaced by a regular file, a changed link target or mode, a removed path, or a new file on the allow-list, including a new Markdown file under `docs/private/conventions/`. A directory stands for every config file below it; a folder of the checkout such as `projects/kagi` stands for its own `AGENTS.md` and the delivered paths below it, never for the nested agent files that stay in git. Without paths, every changed delivered path and every new config file is proposed; a new `<root>/AGENTS.md` of the checkout is proposed by naming it or its folder.
- `--from <dir>` reads the named paths (relative to `<dir>`, laid out like the repository) instead of the checkout. It is the route of a session agent, which `kaya hook pre-tool-use` keeps from editing delivered paths in place; `--remove` names a delivered path to remove.
- Refused locally, before anything is sent: paths off the allow-list, a link at a `SKILL.md`, under `.agents/hooks/` or at `.claude/settings.json`, links that leave the repository or end at nothing (also after a removal), paths below a delivered link, paths that differ only in letter case, settings that are not a JSON object, more than 200 files or a body over 8 MiB, a reason outside 10 to 1000 characters, and a file over a size limit (5 MiB, a `SKILL.md` under 1500 lines, description plus `when_to_use` at most 1536 characters) unless the release already held it over the limit and it does not grow. Frontmatter keys and the dispatcher check are Kaya's alone.
- The guardrail deny rules merged into `.claude/settings.json` are taken out of a proposed settings file, so that no proposal copies them into the pack.
- Without `--reason`, the command asks for one in a terminal and refuses otherwise.
- On success it prints the proposal's number, the name of its pack or project and the review URL. Kaya's refusals are explained: KAY_CFG_002 (a proposed path changed in Kaya since the release; keep your edits, sync and propose again), KAY_CFG_019 (paths of several packs or projects; one command per owner is printed), KAY_CFG_001 and KAY_CFG_003 (each violation), KAY_CFG_015 (another change of the pack or project in review), KAY_CFG_016 (nothing changes), KAY_NF_001, the token and membership refusals.
- Until a release carries the change, `kaya sync` puts the delivered files back, which drops edits made in place.

## Lessons

The Kaya app holds the lessons of an organization, each for one repository or for the whole organization. `kaya lessons` reads and records them with the machine token, in any git checkout.

```sh
kaya lessons add --title "<the rule as a sentence>" --body-file /tmp/lesson.md [--repo-wide] [--session <id>]
kaya lessons list [--search <topic>] [--json]
kaya lessons show <id>
kaya lessons remove <id>
kaya lessons import <file> [--repo-wide]
```

- A title is the rule, at most 300 characters on one line; the body, at most 4000 characters, says what went wrong, why, and the correct approach. `add` prints the lesson's id and web URL. Without `--repo-wide` the lesson belongs to the checkout's repository.
- The session of a lesson is `--session`, else the session record of the Claude Code process that runs the command, else the only live session; with none known the lesson has no session link.
- `list` prints `- <id> <title>` lines for the checkout's repository and the organization-wide lessons, newest first, at most 100. `--search` matches title and text and prints the text as well. `show` prints a whole lesson. `remove` deletes one for good; only its author, an ADMIN or an OWNER may.
- `import` splits a file at lines starting `### ` outside code fences, heading as title and the rest as body, and adds each block unless the same title exists in the same scope. It checks every block first and refuses the whole file when one breaks a limit. Run twice, it creates nothing the second time.
- Every failed call exits non-zero.

`kaya hook session-start` keeps a delivered checkout up to date, and never fails on this step:

- It sends the stored `lessonsEtag` of `.kaya/state.json` as `If-None-Match` and the stored `lessonsSeenAt` as `since`, for 2 seconds at most.
- On a 200 it rewrites `.kaya/lessons.md`, the titles of the list, and adds to the session's context `Kaya lessons new or edited since your last session here:` with the changed titles, then `and N more, see kaya lessons list` for every lesson of the list it did not name. Lessons come last in the 10 000 characters: titles that do not fit count as "more". Unchanged lessons add nothing.
- `lessonsSeenAt` is the newest `updatedAt` Kaya answered, not the machine's clock, so a skewed clock neither misses nor repeats a lesson. It belongs to the checkout, so people who share a checkout share it. A checkout's first answer marks nothing as changed; it fills the file. When the answer is a list of no lessons, the first lessons added later are all named.
- `lessonsEtag`, `lessonsSeenAt` and the file are stored together once the context is written. On a 304, offline, or on any refusal (an archived repository answers `KAY_VAL_002`) the cached file and the stored values stay, the error goes to stderr and the session starts. Changed titles that find no room at all are stored by a later session, which names them.

## Release

Tag `kaya-cli/vX.Y.Z`; `.github/workflows/kaya-cli-release.yml` publishes to the public `korurlabs/kaya-cli` and `korurlabs/tap/kaya-cli` from `.goreleaser.yaml`. See `.agents/rules/14-go-modules-and-releases.md`.
