# Kalkan CLI

`kalkan` reads and writes the pages of Kalkan, the Oba docs app, from the terminal. Developers and Claude Code agents use it to keep repository docs in Kalkan.

## Build and test

```sh
make build   # bin/kalkan
go vet ./...
make lint    # golangci-lint v2.13.2, config in .golangci.yml
make test    # go test -race ./...
```

## Sign-in

```sh
kalkan login          # production: api.korur.com, realm sensey at auth.korur.com
kalkan login --dev    # the local Oba stack: API :8080, Keycloak :8086
kalkan logout
```

`kalkan login` runs the device authorization grant against the public Keycloak client `kalkan-cli`, then calls the platform `POST /users/me`, which lists the organizations you belong to. The tokens, the API URL and the chosen organization are stored in `<user config dir>/kalkan/credentials.json` (mode 0600) and refreshed when they expire. With several memberships, pass `--org <id>` to `login` or to each command.

Every setting can be given as a flag or an environment variable: `--api-url` / `KALKAN_API_URL`, `--issuer` / `KALKAN_ISSUER`, `--client-id` / `KALKAN_CLIENT_ID`, `--org` / `KALKAN_ORG_ID`. A flag wins over the environment, the environment over the `organization` of `kalkan.yaml` (organization only), that over the stored sign-in, and the stored sign-in over the production defaults.

Only `login` is interactive. Every other command runs unattended: without a usable sign-in it exits with code 2 and asks for `kalkan login`.

## Commands

```sh
kalkan pages                       # every page you can see, as a tree
kalkan pages isms/kontroller       # that page and the pages below it
kalkan get isms/politikalar        # every translation under a header
kalkan get isms/politikalar --lang tr > politika.md   # one body, raw Markdown
kalkan put engineering/plans/oba/07 --lang en --title "Kalkan docs" --file 07_KALKAN_DOCS.md
cat notes.md | kalkan put engineering/notes --title Notes --file - --compliance
```

A path is one to ten slugs joined by `/`; a slug starts with a letter or digit and holds up to 99 letters, digits, `.`, `_` or `-`. An invalid path fails before any call.

`put` writes one language of one page: it creates the page when the path names none (every parent must exist; a path of one slug creates a root page, which needs the ADMIN role) and otherwise updates that language, keeping the others. `--compliance` sets the mark, `--compliance=false` clears it, and leaving it out keeps it. A page edited in Kalkan since the last push is not written unless `--force` is given.

`--json` prints the result as JSON on stdout: the flat page array for `pages`, the page with its translations for `get`, `{"id","path","outcome","version"}` for `put` (outcome `created`, `updated` or `unchanged`). An error goes to stderr once, as `{"error":{"code","message"}}` with `--json` and as `Error: <message> (<code>)` without it. The code is the API's error code, `HTTP_<status>` when the API sent none, `NOT_SIGNED_IN`, or `CLI_ERROR` for a failure on this machine.

## Push a directory

```sh
kalkan push docs/claude/plans engineering/plans            # one directory under one page path
kalkan push docs/claude/plans engineering/plans --dry-run  # the outcome per page, nothing written
kalkan push                                                # every entry of kalkan.yaml
```

`kalkan.yaml`, in the current directory or at the git root:

```yaml
organization: <organization id>
push:
  - dir: docs/claude/plans   # relative to kalkan.yaml
    to: engineering/plans
    lang: en                 # optional; files without a .tr or .en suffix
```

Layout: `a/b.md` is page `a/b` in `--lang` (default `en`); `a/b.tr.md` and `a/b.en.md` set the language; `a/index[.lang].md` or `a/README[.lang].md` is page `a`, and the directory itself is the target page. A directory that holds Markdown but no index file becomes a page titled by its name with an empty body; once that page exists, a push leaves it as it is. Hidden entries, symbolic links and directories named `handoffs` are not read. The title is the front matter `title`, else the first `# ` heading (taken out of the body), else the slug; front matter `position` and `compliance` are optional and must agree across languages. Links to Markdown files of the directory become page paths (`/engineering/plans/oba/07_KALKAN_DOCS`). Other files linked by a relative path inside the directory are uploaded to every page that links them, once per page, as `<sha256 first 12 hex>-<name>`; each page holds its own copy, and the link is set to the attachment URL without its signature, so an unchanged directory gives unchanged pages.

Every page gets one outcome: `created`, `updated`, `unchanged`, `conflict` (edited in Kalkan since the last push; `--force` overwrites), `denied`, `failed`, or `skipped` (below a page that was denied, failed or skipped). The run goes on past a conflict, a denial or a failure, and stops at a lost sign-in. `--json` prints `{"counts":{...},"pages":[{"path","outcome","version","error"}]}` on stdout on every run, with `error` as `{"code","message"}` on every outcome but the first three. In both modes each error prints once on stderr: a run-level error, and the error of every page that conflicted, was denied or failed; a skipped page adds nothing there, because its report entry names the cause: its line on stdout without `--json`, its `error` with `--json`, either way the path of the page above it that was not written (`PARENT_NOT_WRITTEN`) or the run-level reason. The exit code of a run with several outcomes is the first of 1, 2, 4, 3 that occurred. An invalid `kalkan.yaml`, a missing directory or a file name that is not a slug ends the run with 1 before any call.

## Exit codes

| Code | Meaning |
|---|---|
| 0 | success |
| 1 | any other error: invalid input, a validation error of the API, a network error, a token endpoint that is down |
| 2 | not signed in: no stored sign-in, a sign-in at another issuer, a refused refresh, or a 401 |
| 3 | conflict: the page was changed in Kalkan since the last push (`KLK_STA_004`), or a concurrent write (`GEN_STA_001`) |
| 4 | denied: 403, 404 (a page that is missing or hidden from you), or no access to the Kalkan app (`GRP_SEC_001`) |
