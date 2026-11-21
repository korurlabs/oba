# Oba CLI

`oba` works with the Oba apps from a terminal. The command path picks the app (`oba otak ...`). This version signs a user in, chooses the organization, lists Otak relations (leads, customers and suppliers) with their ids (`oba otak relation list`), creates, updates and deletes them and their contact persons (`oba otak contact`) from one JSON object on stdin, manages ICPs (also worked out with Claude Code in `oba otak icp brainstorm`) and events (`oba otak event`), finds new leads for an ICP, each with up to two business contacts, with Claude Code, pushes and pulls document templates as files, prepares outreaches and manages the attachments of an outreach's mail or a relation (`oba otak template`, `outreach`, `attachment`). `oba kaya` delivers the organization's agent config to Claude Code as signed releases and records its sessions (see Kaya).

## Install

Releases support macOS and Linux on amd64 and arm64. They are published to the public `korurlabs/oba-cli`, which holds release assets and no source. `oba --version` prints the version, followed by the commit and build time for a release build.

With Homebrew:

```sh
brew install korurlabs/tap/oba-cli   # installs the binary oba, and oba-kaya-launch linked to it
```

With the installer:

```sh
curl -sSfL https://github.com/korurlabs/oba-cli/releases/latest/download/install.sh -o oba-install.sh
sh oba-install.sh
```

The installer picks the archive for your system, checks its SHA-256 checksum against the release's checksums file, and installs `oba`, and the link `oba-kaya-launch` to it, in `/usr/local/bin` when that is writable, else in `~/.local/bin`. It never calls sudo. `OBA_INSTALL_DIR="$HOME/bin" sh oba-install.sh` chooses the directory. Add the directory to your `PATH` when the installer says it is missing.

By hand: download `oba-cli_<version>_<os>_<arch>.tar.gz` from [the releases](https://github.com/korurlabs/oba-cli/releases/latest), compare its SHA-256 digest with `oba-cli_<version>_checksums.txt`, and put the `oba` binary from the archive on your `PATH`.

`go install github.com/korurlabs/oba-cli@latest` is not supported: the module path names no repo that holds the source.

## Build and test

```sh
make build   # bin/oba
go vet ./...
make lint    # golangci-lint run; CI pins v2.13.2 and uses the default linter set
make test    # go test -race ./...
```

Building from source needs access to this monorepo; put `bin/oba` on your `PATH`.

## Sign-in

```sh
oba login
oba logout
```

`oba login` runs the device authorization grant against the public Keycloak client `oba-cli`: it opens the browser, waits for the sign-in, then calls `GET /users/me` (and `POST /users/me` when your Oba user does not exist yet). It prints the API, your e-mail and the current organization. `oba logout` deletes the stored session and keeps the chosen organization.

The production defaults are the API `https://api.korur.com`, the issuer `https://auth.korur.com/realms/sensey` and the client `oba-cli`. Three environment variables override them:

| Variable | Default |
|---|---|
| `OBA_API_URL` | `https://api.korur.com` |
| `OBA_ISSUER` | `https://auth.korur.com/realms/sensey` |
| `OBA_CLIENT_ID` | `oba-cli` |
| `OBA_KAZAN_WEB_URL` | none |

`oba login` stores the values it used with the session, and later commands reuse them. A variable that is set still wins over the stored value.

`OBA_KAZAN_WEB_URL` is the origin of the Kazan web app, such as `https://kazan.example.com`. The `oba kazan pr` commands print a `Kazan:` link to a share only when it is set; it has no default, since the API does not name the web origin.

Against the local Oba stack (`projects/oba/scripts/start.sh`):

```sh
OBA_API_URL=http://localhost:8080 OBA_ISSUER=http://localhost:8086/realms/sensey bin/oba login
```

The local realm file `projects/oba/backend/src/main/resources/podman/keycloak/realm/sensey-realm.json` holds the client `oba-cli`, but Keycloak imports it only when the realm is created. On a local Keycloak created before that client existed, add `OBA_CLIENT_ID=kaya-cli`, a device-grant client of the same realm, until the Keycloak container is recreated.

## Organization

```sh
oba org list           # * marks the current organization
oba org use <name|id>  # match the id exactly, else the name without regard to case
```

The current organization is the one chosen with `oba org use`, stored as `org-id` in `~/.config/oba/config.json` (mode 0600), else the default organization of the sign-in. App commands act in it.

## Global flags and OBA_TOKEN

Every command takes these flags:

| Flag | Effect |
|---|---|
| `--json` | stdout holds only JSON: the `data` of the command's last API call as the API sent it, a list read across several pages or calls as one array, `null` when the command made no call. Tables, messages and prompts go to stderr. `oba otak find`, `contact fill` and `icp brainstorm` print an array of the data of every record they created. A failed command prints no JSON; its exit status (2 for 404, 3 for 403, 1 otherwise) and the error on stderr tell why. |
| `--org <name\|id>` | runs this one command in another of your organizations; the organization chosen with `oba org use` stays stored. An id is sent as it is; a name is matched like `oba org use` matches it. |
| `--debug` | logs every HTTP request and response (method, URL, headers, status, time) to stderr. The `Authorization` and cookie headers and the signature of signed download URLs are redacted; bodies are not logged. |

With `OBA_TOKEN` set, every call sends that value as the bearer token and the stored session is not read, as scripts and agents need. The token is never refreshed; an expired one fails with exit 1. Without `--org` or a chosen organization, commands act in the default organization `GET /users/me` names. `oba login` refuses to run while `OBA_TOKEN` is set.

```sh
OBA_TOKEN=<token> oba otak relation list --org Korur --json
```

## Admin

```sh
oba admin org create --help   # the fields, name required, read from the live API
oba admin org create <<'EOF'
{"name": "Korur B.V.", "country": "NLD", "website": "https://korur.com/"}
EOF
oba admin org list            # the same table as oba org list
oba admin org update <<'EOF'  # the current organization; only the given keys change
{"description": "Cybersecurity for SMEs", "website": null}
EOF
oba admin invite create ayse@example.com --role admin   # ADMIN, MEMBER (default) or GUEST
oba admin invite list
oba admin invite revoke <id> --yes
oba admin member list
oba admin member role ayse@example.com GUEST
oba admin member remove ayse@example.com --yes
```

`org create` and `org list` work without a current organization; `org update`, `invite` and `member` act in the current one (`oba org use`). Every rule on who may do what is the API's, checked against your role in that organization; the CLI only refuses OWNER as a role before any call, since the API never assigns it.

A person is added with `invite create`, never directly. The same call serves someone who already has an Oba account and someone who has none: they get an e-mail and join when they accept in the account app. Until then they appear in `invite list`, not in `member list`, which covers only the people who accepted. An invited ADMIN, MEMBER or GUEST needs an app grant, set in the admin web app, before they can open an app such as Otak; the owner has every app.

- `org create` checks the keys against the live `OrganizationCreateRequest` schema and sends the object unchanged in one `POST /organizations`, without an organization header. You become the owner of the new organization; switch to it with `oba org use <id>`. `country` is an ISO 3166-1 alpha-3 code.
- `org list` prints `  NAME  ROLE  STATUS  ID` for the organizations you belong to, `*` marking the current one, or `You belong to no organization.`.
- `org update` changes the profile of the current organization (name, description, industry, company size, country, website) and takes no id. Only the given keys change: it checks them against the live `OrganizationUpdateRequest` schema, reads `GET /organizations/{id}`, lays the keys over the current values and sends every field in one `PUT /organizations/{id}`, since the API replaces them all. `null` clears an optional field; `name` cannot be cleared, and a blank or empty value fails with 422 `GEN_VAL_001`. It prints `Updated organization <name> (<id>)`; when every key already has its value it sends nothing and prints `Nothing to update: <name> already has these values.`, and `{}` fails with `Nothing to update.`. `--help` lists the fields. It needs the ADMIN role.
- Deleting, disabling and enabling an organization are left out of the CLI on purpose: delete cannot be undone, and both change who can use the organization. Do them in the Oba admin web app.
- `invite create <email> [--role ADMIN|MEMBER|GUEST]` sends `POST /invitations` with `{"email","role"}` and prints `Invited <email> as <ROLE> to <org>. They join when they accept in Oba; the invitation expires <day>.`, the day in this machine's time zone. The role is matched without regard to case; OWNER or any other value fails with `unknown role "<x>"; allowed: ADMIN, MEMBER, GUEST` before any call. An invitation expires after 7 days. It needs the ADMIN role.
- `invite list` prints `EMAIL  ROLE  INVITED BY  EXPIRES  ID` for the pending invitations, or `No pending invitations.`. It needs the ADMIN role.
- `invite revoke <id>` asks `Revoke the invitation of <email>?` and sends `DELETE /invitations/{id}`; without a terminal pass `--yes`. An id that is not among the pending invitations is not asked about; the API answers it.
- `member list` prints `NAME  EMAIL  ROLE  STATUS  USER ID`, owners first, then ADMIN, MEMBER and GUEST, then by e-mail, or `No members.`. Any member may run it.
- `member role <email|userId> <ROLE>` and `member remove <email|userId>` find the member by user id, else by e-mail without regard to case, and fail with exit status 2 when nobody matches. `remove` asks `Remove <email> from <org>?`; without a terminal pass `--yes`. Both need the ADMIN role; an ADMIN cannot change or remove an OWNER, and nobody changes their own role.

Every printed cell is sanitized onto one line. Errors and exit statuses are as for Otak relations: 422 `INV_VAL_001` for someone already a member, `INV_VAL_002` for a pending invitation to the same e-mail, `INV_VAL_007` for your own e-mail, `INV_VAL_010` for revoking an invitation that is no longer pending, `GEN_VAL_001` for an invalid value; 404 `INV_NOT_001` for an unknown invitation; 403 `ORG_SEC_003` below ADMIN, `ORG_SEC_004` for your own role and `ORG_SEC_006` for an ADMIN acting on an OWNER.

## Otak relations

```sh
oba otak relation list --search acme   # every type and stage whose name contains "acme"
oba otak relation list --type lead --stage new,warm
oba otak relation list --event <eventId>   # the relations linked to one event
oba otak relation create --help   # the fields, required ones and allowed values, read from the live API
oba otak relation create <<'EOF'
{"name": "Acme B.V.", "type": "LEAD"}
EOF
oba otak relation create < relation.json
oba otak relation update <id> <<'EOF'
{"website": null, "stage": "QUALIFIED"}
EOF
oba otak relation delete <id> --yes
```

The JSON object uses the API's field names and values; the CLI translates nothing. For `create` and `update`, its keys are checked against the request schemas of `GET /v3/api-docs` before any write, and an unknown key fails with the closest known name. Run `--help` for the current field list rather than relying on the example above.

- `list` prints one table `NAME  TYPE  STAGE  WEBSITE  OWNER  CONTACT  CONTACT MAIL  COMPANY MAIL  ID`, so that you can find the id of an existing relation. The API needs a type on every call, so without `--type` it reads every type of the live `type` enum, one paged series of `GET /otak/relations` per type (50 per call, every page), grouped in that enum's order and last change first within a type. Without `--stage` every stage is shown, LOST and PARKED leads and CLOSED and DIRECT customers included: the CLI names every live stage on the lead and customer calls, since the API leaves LOST and PARKED out otherwise. `--stage` (repeated or a comma list) only narrows, to leads and customers: without `--type` suppliers are not read, and `--type SUPPLIER --stage …` is refused, since suppliers have no stage. A stage of the other type, such as `--type lead --stage closed`, matches nothing. `--search` is part of the name, case ignored. `--event <id>` keeps the relations linked to that event (`eventId` on every call); an unknown event id lists nothing. CONTACT and CONTACT MAIL are the name and email of the relation's main person contact, COMPANY MAIL the email of its main business contact (such as info@); each is `-` when there is no such contact or address. Type and stage values are matched case-insensitively against the live API, and an unknown one fails before any call. STAGE is `-` for a supplier; a failed call prints no partial table.
- `create` sends the object unchanged in one `POST /otak/relations`. A lead may carry its `stage`. `eventId` links the relation to an event (`oba otak event list` prints the ids); `null` in an update clears the link, and an unknown event fails with 404 `OTK_NOT_009`.
- `update` changes only the given keys; `null` clears a field. It reads the relation, sends `PUT` when a key other than `type` and `stage` was given, then `PATCH .../type` and `PATCH .../stage` when those differ. A failing step stops the rest, and the error names the steps already applied.
- `delete` asks for confirmation; without a terminal pass `--yes`. It needs the ADMIN role.

Agents and scripts use the quoted heredoc: stdin does not show in `ps`, and the quoted `'EOF'` stops `$` and backtick expansion. People typing by hand use a file (`< relation.json`), because a typed heredoc lands in the shell history.

An API error prints `Error: <code>: <message>` and one `  <field>: <message>` line per field to stderr. Exit status: 0 on success, 2 for an API 404, 3 for an API 403, 1 for any other error.

## Otak contacts

```sh
oba otak contact list <relationId>
oba otak contact create --help                       # the fields, required ones marked, read from the live API
oba otak contact create <relationId> <<'EOF'
{"name": "Jan de Vries", "role": "Managing director", "email": "jan@acme.example", "sourceUrl": "https://www.acme.example/team"}
EOF
oba otak contact update <relationId> <contactId> < changes.json
oba otak contact delete <relationId> <contactId> --yes
```

A contact is a person at a relation. The relation id is printed by `oba otak relation create` and `oba otak find`, and listed by `oba otak relation list --search <name>`.

A contact has a `kind`: `PERSON` (a person at the relation) or `BUSINESS` (the company's general details, such as info@ or sales@). The kind is independent of `emailType`. `create` requires `kind`, `decisionMaker`, `mainPersonContact` and `mainBusinessContact`; an `update` without them sends the stored values back.

Every relation has exactly one main contact of each kind it has contacts of: the main person contact (`mainPersonContact`), the one to talk to first, and the main business contact (`mainBusinessContact`), whose address the relation list shows. The first contact of a kind becomes main of that kind whatever its flag says. `{"mainPersonContact": true}` or `{"mainBusinessContact": true}` on `create` or `update` makes that contact main of its kind and unmarks the previous one. `false` in the flag of its kind on the main contact of that kind is refused with 422 `GEN_VAL_001` (`Mark another contact as main instead`), so the main contact changes only by marking another contact of the kind; `false` on another contact changes nothing. A true flag of the other kind is refused with 422 `OTK_VAL_013`. Deleting a main contact, or changing its kind, makes the oldest remaining contact of the old kind main. There is no separate command:

```sh
oba otak contact update <relationId> <contactId> <<'EOF'
{"mainPersonContact": true}
EOF
```

- `list` prints `NAME  KIND  MAIN  ROLE  EMAIL  EMAIL TYPE  PHONE  SOURCE  ID`, KIND `person` or `business`, the main person contact first and the main business contact second, each marked `yes` under MAIN, every cell sanitized onto one line and `-` for an empty value, or `No contacts.`. `EMAIL TYPE` is `emailType`: `COMPANY` (the person's own address at their company), `SHARED` (a team inbox such as info@) or `PERSONAL` (a private address).
- `create` checks the keys against the live `ContactCreateRequest` schema and sends the object unchanged in one `POST /otak/relations/{relationId}/contacts`. `sourceUrl` is the page the person's details were found on: the person must be told the source at first contact, so it is set on create only.
- `update` changes only the given keys; `null` clears a field. It reads the relation's contacts, lays the keys over the contact's current values and sends the full `PUT` (`ContactUpdateRequest`, which has no `sourceUrl`), `kind`, `decisionMaker` and both main flags included. A `sourceUrl` key is refused with `sourceUrl is set on create only`; `{}` prints `Nothing to update.`, and keys that already hold their values send nothing. A contact id not on the relation fails with exit status 2. `mainPersonContact` and `mainBusinessContact` are merged like every other field, so an update that does not name them sends the stored flags back and leaves the main contacts as they are. A `kind` change sends the flag of the kind the contact leaves as `false` unless that key was given, since the API refuses it on the other kind: `{"kind": "BUSINESS"}` on the main person contact sends `mainPersonContact: false`, and the API makes the oldest other person main. The API refuses an `emailType` without an `email`, so when the merged `email` is `null` or blank and no `emailType` key was given, `update` sends `emailType: null` too: `{"email": null}` clears both.
- `delete` asks `Delete contact <id>?`; without a terminal pass `--yes`. Any member may delete a contact, so any member can honour an erasure request.

Errors and exit statuses are as for relations: 404 `OTK_NOT_001` for an unknown relation, `OTK_NOT_002` for an unknown contact, 422 `GEN_VAL_001` for an invalid value, 422 `OTK_VAL_005` for an `emailType` without an `email`, 422 `OTK_VAL_013` for a main flag of the other kind, 403 for a guest.

## Otak events

```sh
oba otak event list --period upcoming   # last day today or later, soonest first
oba otak event list --period past --search expo
oba otak event create --help            # the fields, required ones and allowed values, read from the live API
oba otak event create <<'EOF'
{"name": "Textile Expo 2027", "type": "EXPO", "startDate": "2027-03-09", "endDate": "2027-03-11", "city": "Amsterdam", "country": "NL"}
EOF
oba otak event update <id> <<'EOF'
{"endDate": "2027-03-12"}
EOF
oba otak event delete <id> --yes
```

An event is an expo, conference, meetup or webinar where the organization meets companies. A relation links to one through `eventId` (`oba otak relation update <id>` with `{"eventId": "<eventId>"}`), and `oba otak relation list --event <eventId>` lists the linked relations.

- `list` prints `NAME  TYPE  START  END  PLACE  ID`, PLACE as `city, country`, every cell sanitized onto one line and `-` for an empty value, or `No events.`. Every page of `GET /otak/events` is read. `--period upcoming` lists the events whose last day (the end date, else the start date) is today or later, soonest first; `--period past` lists the others, most recent first. Today is this machine's calendar day, sent as `today=YYYY-MM-DD`, because the API cannot know the user's day. Without `--period` every event is listed, most recent start first. `--period` is matched without regard to case, and any other value fails before any call. `--search` is part of the name, case ignored.
- `create` checks the keys against the live `EventCreateRequest` schema and sends the object unchanged in one `POST /otak/events`. Dates are calendar days, `YYYY-MM-DD`.
- `update` changes only the given keys; `null` clears a field. It reads the event, lays the keys over its current values and sends the full `PUT` (`EventUpdateRequest`).
- `delete` asks `Delete event <id>?`; without a terminal pass `--yes`. It needs the ADMIN role; the linked relations stay and lose the link.

Errors and exit statuses are as for relations: 404 `OTK_NOT_009` for an unknown event, 409 `OTK_STA_013` for a name the organization already uses (case ignored), 422 `OTK_VAL_010` for an end date before the start, 422 `GEN_VAL_001` for an invalid value, 403 for a guest, and on `delete` for a member.

## Otak ICPs

```sh
oba otak icp list
oba otak icp create                                   # in a terminal: describe, review the draft, confirm
oba otak icp create < icp.json                        # one JSON object, without Claude Code
oba otak icp brainstorm                               # in a terminal: work out ICPs with Claude Code
oba otak icp update <id> < changes.json
oba otak icp delete <id> --yes
```

In a terminal, `create` asks you to describe your ideal customer and has Claude Code draft the ICP from it with the fields and allowed values of the live API (the Claude Code setup is under Find leads). Type or paste the description over as many lines as you like, blank lines included, and finish with Ctrl-D on a line of its own; Ctrl-D with nothing typed cancels, and Ctrl-C stops the command at any point without creating anything. The draft is printed field by field, then `Create this ICP? (y/n)` accepts only `y`, `yes`, `n` or `no` in any case and asks again on anything else. Before each ask it discards the input already waiting, so the rest of a paste can never answer it.

`brainstorm` works out ICPs in a conversation, in three numbered steps. It first reads what Oba knows about your company: the organization profile (`GET /organizations/{id}`, which needs the admin or owner role) and the existing ICPs, of which it sends only name, industry, country, size range and role. Step 1 shows the profile fields with their values (`-   (empty)` when empty, the website marked as the site Claude reads) and each existing ICP on one line, and notes when the description is empty. Step 2 asks what you sell, at what price, to whom and where, after a `> ` marker; type or paste several lines and press Ctrl-D on an empty line to send, or Ctrl-D right away to go on with what Oba knows. What you type is used for this brainstorm only and is never written to the organization profile. With an empty profile and no ICPs, step 1 says that Oba has no profile yet, step 2 asks you to tell about your company, and Ctrl-D right away cancels. Step 3 is the conversation with Claude Code. The opening reads like this:

```text
Brainstorm ICPs with Claude: find out who your ideal customers are.

Step 1 of 3 · What Oba knows about Korur
  description:   -   (empty)
  industry:      technology
  company size:  1-10
  country:       TUR
  website:       https://korur.com   (Claude will read this site)
  ICPs:          none yet

  ! No description yet. You can add one in the next step.
    It is used for this brainstorm only; to keep it, add it to the Korur profile in Oba.

Step 2 of 3 · Tell Claude more (optional)
  What do you sell, at what price, to whom, and where?
  Example: On-site Cyberscan, 85 checks, €695, for Dutch MKB in Zuid-Holland.
  Type or paste, then press Ctrl-D on an empty line to send.
  Press Ctrl-D right away to skip.
> 

Step 3 of 3 · Claude asks a few questions, then proposes ICPs.
  You pick one to create; nothing is saved without your "y".
```

A failing read of the profile or the ICPs stops the command with the API error. It then runs Claude Code once per turn with web search and page fetch only and no Claude session: oba keeps the conversation in memory and sends all of it with each turn. Claude Code reads the website of the profile, summarises what it understood and asks focused questions (offering, price, best customers, region, size, decision makers, bad fits) until it can propose, then proposes two or three distinct ICPs with the fields and allowed values of the live `IcpCreateRequest`. Answer its questions the same way, after a `> ` marker; Ctrl-D right away ends the brainstorm. A proposal with a field the API does not know or without a required field is skipped with a notice. At `Number to create (1-N), r to refine, q to quit` a number shows that proposal as a draft and creates it after `Create this ICP? (y/n)`; several can be created one after the other. `r` sends your feedback and Claude Code revises the proposals; `q` or Ctrl-D ends. A name already taken or another refusal of the proposal is shown and the brainstorm goes on; a missing role or sign-in ends it. One brainstorm has at most 12 Claude Code turns. It refuses to run with stdin or stdout redirected.

With one JSON object on stdin, `create` sends it unchanged, and `update` changes only the given keys as for relations. `delete` asks for confirmation; without a terminal pass `--yes`. It needs the ADMIN role.

Every y/n question and every menu (the `delete` confirmations, the ICP and candidate picks and the confirmation of `find`, the brainstorm menu) discards the input already waiting before it asks, so a line pasted or typed ahead never answers it. A y/n question accepts only `y`, `yes`, `n` or `no` and asks again on anything else; the end of input is a no. A menu asks again on an answer it does not know.

## Find leads

```sh
oba otak find                       # asks for the ICP and an optional focus
oba otak find --icp <name|id>       # match the id exactly, else the name without regard to case
oba otak find --focus "<text>"      # narrow the search without being asked; --focus "" skips it
```

`oba otak find` has Claude Code search the web for new companies that fit one ICP (Ideal Customer Profile) of the current organization, then creates the ones you pick as leads, each with the contact persons found for it (kind `PERSON`). ICPs are kept in the Otak web app. The command is interactive: stdin and stdout must be a terminal; there is no non-interactive mode.

It needs Claude Code on your `PATH` (https://claude.com/claude-code), signed in once by running `claude`. Claude Code runs on your machine under your own Claude account, which its use is billed to. It runs headless with web search and page fetch only (`--restricted`, no MCP server), in an empty temporary directory, with the brief on stdin. The brief holds the ICP, the focus and the names and websites of the organization's leads (every stage), customers and suppliers, never contacts or notes.

The command runs in four steps, after the line `Find leads with Claude: search the web for companies that fit an ICP.`:

1. `Step 1 of 4 · Choose an ICP` lists the ICPs as `N. name  industry  country  size range  role` (an open field shows `any`) and asks for a number at `> `; an empty answer or Ctrl-D prints `Cancelled.`. With `--icp` the step is `Step 1 of 4 · ICP` with the matched row, and nothing is asked.
2. `Step 2 of 4 · Focus the search (optional)` asks for text that narrows the search, for example `Only Zuid-Holland, webshops with their own payment flow.` It may span lines (at most 64 KiB); press Ctrl-D on an empty line to send it, or right away to skip. With `--focus` the step is `Step 2 of 4 · Focus: <text>`, and nothing is asked.
3. `Step 3 of 4 · Claude searches the web` also looks, per company, for at most 2 business contacts (see Contacts below). It prints one line per web search (`Searching: <query>`) and page fetch (`Reading: <url>`). A search takes about 1 to 3 minutes. Ctrl-C stops Claude Code and ends the command with `stopped; nothing was created`.
4. `Step 4 of 4 · Pick the leads to create` lists the candidates as `N. name  host  summary`: the host is the website without scheme, `www.` and path, the summary one line of at most 80 characters. A candidate whose name (case and spacing ignored) or website host matches a known relation shows `already in Otak as <name>` in place of its summary and cannot be picked. Under every other candidate, each contact shows as `contact: <name> · <role> · <email> · <phone>` (missing parts left out) and `source: <sourceUrl>`, or the line `no contact found`.

At the menu `Numbers to create (1,3 or 1-3 or all), r to search again with feedback, q to quit` you answer:

- numbers such as `1,3`, `1-3` or `all` (every candidate that can be picked), then `y` at `Create N leads and M contacts in <org>? (y/n)` (`Create N leads in <org>? (y/n)` when the picks have no contact). `n` or Ctrl-D prints `Not created.` and goes back to the menu.
- `r` and your feedback: Claude Code searches again (step 3) with every earlier candidate list and feedback in its brief, and step 4 shows the new list. Ctrl-D right away goes back to the menu. One run has at most 5 searches.
- `q` or Ctrl-D: `Nothing created.`, exit 0. An empty answer asks again.

When there is no candidate, or every one is already in Otak, the command prints `Claude found no new companies.` and offers `r` and `q` only.

After `y` each pick is created with `POST /otak/relations`, in pick order, as type `LEAD`, stage `NEW`, source `RESEARCH`, the ICP as `icpId` and `Found by oba otak find: <reason>` as its notes, with the website, country, city and size Claude Code found. A website the API would refuse, a country code or size it does not know and a city over 100 characters are left out. Each pick prints `✓ <name> (<id>)`, `✓ <name> (created, but the response could not be read: <error>)` or `✗ <name>: <API error>`; a failed create does not stop the others.

Right after its lead, each contact is created with `POST /otak/relations/{id}/contacts` as `{name, role, email, phone, linkedinUrl: null, decisionMaker: false, kind: PERSON, mainPersonContact: false, mainBusinessContact: false, sourceUrl}` (the first person of the lead still becomes its main person contact) and prints `+ contact <name> (<contactId>)` or `✗ contact <name>: <API error>`. A failed contact keeps its lead and does not stop the next contact or pick. When the lead was created but its id cannot be read, its contacts are not attempted: `✗ M contacts not created: the lead id is unknown`, and they count as failed. The contacts of a lead whose create failed are not attempted and not counted.

The command then prints `Created N leads and M contacts. They are in Otak as LEAD, stage NEW, linked to "<ICP>".` (without the contacts part when there were none) and ends. When something failed, the part that failed reads `K of N leads` or `C of M contacts`, and the command exits 1 with `F of N leads and G of M contacts failed; see the lines above`, leaving out a part without failures. After a failed contact it also prints `Add the missing contacts with 'oba otak contact create <relationId>'.` Ctrl-C at a prompt or during the creates ends the command; the leads and contacts created by then stay.

### Contacts

The brief asks Claude Code for at most 2 people per company, named in a business role on the company's own website (team, about, contact or imprint page) or in a public business register, preferring the ICP's target person role. Per person it may give only the name, the work role, the business e-mail and phone the source publishes for that person, and `sourceUrl`, the exact page. It must not collect private addresses, e-mail addresses or phone numbers, dates of birth, photos, social media profiles (LinkedIn included), family, health, opinions or any special category of personal data, must not use people-search sites, data brokers, leak sites or paid databases, and must not guess an e-mail address from a pattern.

Before a contact is shown, the CLI checks it, so that what is shown is what is created: a contact without a name, with a name over 200 characters, or without a valid `http(s)` source URL of at most 500 characters is dropped; a role over 100 characters is left out; an e-mail address is kept only when its domain is the company's website host, a subdomain of it or its parent domain; a phone number is kept only when it has at most 50 characters of digits, spaces and `+()-./`; a repeated name keeps its first contact. The source URL is stored on the contact and cannot be changed afterwards, so that the person can be told where their details came from at first contact. Every contact a search creates, here and in `oba otak contact fill`, has kind `PERSON`.

Terminal escape sequences, control characters and bidi controls are removed from what Claude Code returns before it is shown (carriage returns, vertical tabs, form feeds and the Unicode line and paragraph separators become line breaks, which a row shows as spaces, so the words around them stay apart), and a company proposed twice in one search (same website host, else same name with case and spacing ignored) is shown once, as first proposed.

Web search results can be wrong: check each candidate before you pick it.

## Otak document templates

```sh
oba otak template list --type nda --lang nl
oba otak template push legal/nda/nda_nl.html --type nda --lang nl --name "NDA"
oba otak template push legal/nda/nda_nl.html --id <templateId>       # update; type, language and name stay unless given
oba otak template pull <templateId> -o work/
```

A template is reusable content of the organization, an `.html` file of type PROPOSAL, NDA, TERMS, REPORT, SERVICE_SHEET, PRESENTATION or OTHER with its rendered PDF. A mail or a relation gets a copy of that PDF as an attachment (`oba otak attachment add --template`, `oba otak outreach create --attach`), which later template changes do not touch. A mail is no template: MAIL as `--type` and a `.md` file are refused with a pointer to `oba otak mail-template` (below).

On push the local images of the HTML (`<img src="../../images/logo-korur.png">`, relative to the file, absolute or `file:`) are inlined as base64 data URIs, so that the uploaded HTML renders the same anywhere; `https:`, `data:` and `//` sources stay as they are, and a local image that cannot be read stops the push. The inlined HTML is rendered to PDF with the `weasyprint` command, the engine the document library renders with, run as `weasyprint --base-url <source file> <html> <pdf>` in a temporary directory so that nothing is written next to the source. The HTML and the PDF go up together in one multipart `PUT .../files`, parts `html` (`text/html; charset=utf-8`) and `pdf` (`application/pdf`). A pull downloads both from the signed URLs the API returns, without sending the session token there, as `<name>.html` and `<name>.pdf`.

WeasyPrint must be on your `PATH`: `brew install weasyprint` on macOS, `pip install weasyprint` elsewhere. Without it, a push stops before any call.

`--lang` is `nl`, `tr`, `en`, or `us` for English, case ignored. `--type` is matched without regard to case, with `-` read as `_` (`service-sheet`).

- `template list` prints `NAME  TYPE  LANGUAGE  READY  UPDATED  ID`, every page. READY is `no` for a template whose files are not uploaded yet.
- `template push` without `--id` needs `--type` and `--lang`; the name defaults to the file name without extension. It sends `POST /otak/document-templates`, then `PUT .../{id}/files`. With `--id` it reads the template, keeps its type, language and name unless given (a different `--type` changes the type), and sends `PUT .../{id}`, then the files. The file is read and rendered before the first write. When the files fail after a create, the error names the new id: push again with `--id`. A name already taken in that language answers `OTK_STA_009`.
- `template pull` writes into `-o` (default the current directory) as `<name>.html` and `<name>.pdf`, the name reduced to letters, digits, `-`, `_` and `.`. An existing file is not overwritten without `--force`. A template without files yet fails with `has no files yet`.

Writes need the ADMIN role; a member can list and pull.

## Otak mail templates

```sh
oba otak mail-template list --lang nl --search intro
oba otak mail-template push mails/first-contact.md --lang en
oba otak mail-template push mails/first-contact.md --id <mailTemplateId>   # update; language and name stay unless given
oba otak mail-template pull <mailTemplateId> -o work/
```

A mail template is a reusable mail of the organization, which a MAIL outreach copies into its own mail. It is a `.md` file: the subject on the first line, a blank line, then the body as plain text. Blank lines after the subject and trailing white space are dropped; Windows line endings and a byte order mark are accepted. Any other extension is refused before any call.

- `list` prints `NAME  LANGUAGE  SUBJECT  UPDATED  ID`, every cell on one line and `-` for an empty value, or `No mail templates.`. `--lang` narrows to one language, `--search` to the names that contain the text, case ignored. The ids are what `outreach create --type mail --template` takes.
- `push` without `--id` needs `--lang`; the name defaults to the file name without extension unless `--name` gives one. It sends `POST /otak/mail-templates` and prints `Created mail template <id> (<name>, <LANGUAGE>)`. With `--id` it reads the stored mail template only when `--lang` or `--name` is missing, takes subject and body from the file and sends the full `PUT /otak/mail-templates/{id}`. The name is unique per language in the organization. Outreaches already copied from a mail template keep their own mail when it changes.
- `pull` writes `<name>.md` into `-o` (default the current directory) in the form `push` reads; an existing file is not overwritten without `--force`.

`--lang` is `nl`, `tr`, `en`, or `us` for English, case ignored. Writes need the ADMIN role; a member can list and pull.

## Otak outreaches

```sh
oba otak outreach create --relation <relationId> --contact <contactId> --type mail --title "First contact" \
  --template <mailTemplateId> --attach <documentTemplateId> --attach <documentTemplateId>
oba otak outreach create --relation <relationId> --contact <contactId> --type mail --title "Follow-up" \
  --mail-file followup.md --lang nl
oba otak outreach create --relation <relationId> --contact <contactId> --type call --title "Intro call"
oba otak outreach complete <outreachId>
oba otak outreach list --relation <relationId> --status draft
```

`outreach create` stores one DRAFT reach-out to one contact of a relation in `POST /otak/outreaches`; nothing is sent. `--relation`, `--contact`, `--title` and `--type` are required. `--type` is the channel: `mail`, `whatsapp` or `call`, case ignored. It prints `Created outreach <id> (<title>, <TYPE>, DRAFT)`, and for a MAIL also `Mail <mailId>: <subject>`.

- A MAIL is sent from the lead page of the Otak web app, where you choose the mailbox. Its mail comes from exactly one of `--template`, a mail template the API copies into the outreach's own mail, and `--mail-file`, a `.md` mail file as above, whose language `--lang` sets (`NL` without it; the API requires a language). `--lang` without `--mail-file` is refused, since a mail template keeps its own language. `--attach` is repeated or a comma list of document template ids (`oba otak template list`), sent as `documentTemplateIds`: the API copies each template's PDF onto the new mail as an attachment, in the same transaction. A template without its PDF answers `OTK_VAL_012`, one of another organization `OTK_NOT_007`, a contact of another relation `OTK_NOT_002`. Other files are added after create with `oba otak attachment add --outreach`.
- A WHATSAPP or CALL happens outside Oba and takes none of `--template`, `--mail-file`, `--lang` and `--attach`; given ones are named and refused before any call.

`outreach complete <outreachId>` records a DRAFT WHATSAPP or CALL as done with `POST /otak/outreaches/{id}/complete`: it becomes SENT, now, to the contact's phone, and the command prints `Recorded outreach <id> as done (<title>, <TYPE>, SENT to <phone>)`. The API refuses a MAIL with `OTK_VAL_014`, an outreach already done with `OTK_STA_011` and a contact without a phone with `OTK_VAL_015`.

`outreach list` prints `TITLE  TYPE  STATUS  CONTACT  SENT TO  SENT AT  SENDER  ID`, newest first, every page; `--relation` and `--status` (DRAFT, SENT, FAILED) narrow it. SENT TO is the mail address of a MAIL and the phone of a WHATSAPP or CALL.

## Otak attachments

```sh
oba otak attachment list --outreach <outreachId>
oba otak attachment add --outreach <outreachId> --file voorstel.pdf --file prijzen.xlsx
oba otak attachment add --relation <relationId> --template <documentTemplateId>
oba otak attachment delete --relation <relationId> --id <attachmentId>
```

Every subcommand takes exactly one of `--outreach`, the own mail of a MAIL outreach (read with `GET /otak/outreaches/{id}` for its `mail.id`; a WHATSAPP or CALL has no mail and is refused), and `--relation`. The calls go to `/storage/attachments` with `entityType` `MAIL` or `RELATION` and the `X-Organization-ID` of the current organization.

- `list` prints `NAME  SIZE  TYPE  CREATED  ID` in upload order, or `No attachments.`.
- `add` takes exactly one of `--file`, repeatable, and `--template`. `--file` reads and checks every file before any call (a leading `~` is the home directory, a blank path is skipped; a regular, non-empty file, at most 12 MB together, no line break in its name, no two with the same content) and uploads them in one multipart `POST /storage/attachments`: a `metadata` JSON part with `{fileName, entityType, entityId}` per file and one `files` part per file, its media type from the extension, else from the content. `--template` sends `POST /otak/document-templates/{id}/attachments` `{entityType, entityId}`; the API copies the template's PDF as `<template name>.pdf`. Content already on the mail or relation answers `STG_VAL_005`, a template without its PDF `OTK_VAL_012`.
- `delete --id` checks that the id is an attachment of the given outreach or relation, asks for confirmation (without a terminal pass `--yes`) and sends `DELETE /storage/attachments/{id}`.

A mail's attachments change only while its outreach is not SENT (`OTK_STA_011`) and need the ADMIN role; a relation's need the MEMBER role.

## Kaya

`oba kaya` keeps the agent config of Claude Code with Kaya: it seeds an organization's agent config from its repositories (`seed`), delivers that config to Claude Code sessions as signed releases (`install`, `launch`, `sync`, `refresh`, `status`, `watch`, `hook`), proposes changes of that config for review (`propose`), shares lessons between sessions and repositories (`lessons`), and records Claude Code sessions for Kaya (`hook record`, `sender`, `statusline`).

`seed` and `signing-keys` act as you, with the sign-in of `oba login` (or `OBA_TOKEN`) in the current organization. Everything that runs inside sessions or in the background sends the machine token instead (see Kaya config delivery).

### Moving from the kaya CLI

The `kaya` binary is gone; its commands are `oba kaya ...`. Once per machine:

1. `brew uninstall korurlabs/tap/kaya-cli` (or remove `kaya` and `kaya-launch` from your `PATH`), then install `oba` as above.
2. `oba login`: `oba kaya seed` and `oba kaya signing-keys` use the Oba sign-in now, not `~/.config/kaya/credentials.json`, which you can delete.
3. `oba kaya install`: it moves the VS Code wrapper to `oba-kaya-launch`, the `claude()` function to `oba kaya launch` and the Claude Code hooks to `oba kaya hook`, and removes what `kaya install` wrote. The link `~/.local/share/kaya/bin/kaya-launch` is no longer used.

The queue, the blob cache and the checkouts' `.kaya/` stay where they are, so nothing recorded or delivered is lost. `KAYA_API_URL` is no longer read: set `OBA_API_URL`.

## Kaya seed

```sh
oba kaya seed --agents ~/src/agents --repo ~/src/village --repo ~/src/acme
oba kaya seed --repo . --dry-run                  # print what would be sent; no sign-in, no network
oba kaya seed --repo . --json                     # the seed run as JSON
oba kaya seed --repo . --pack kagi=projects/kagi  # also mount the pack kagi in the folder projects/kagi
```

Only an OWNER seeds, signed in with `oba login` (or `OBA_TOKEN`) in the organization `--org` names, else the chosen one. The seed reads the packs of a `korurlabs/agents` clone and, per repository, its packs from `.agents/.managed.json` and its own files at the paths Kaya delivers, uploads only the blobs the server is missing, posts the seed run, prints its report and compares each repository's release with the repository on disk. `oba kaya seed --help` describes the pack tree, the family pack of a repository (`--repo-pack`), mounted packs (`--pack`) and the cutover (`--after-cutover`).

Exit status: 0 when the run is PASSED and every release equals its repository; 2 when the run is FAILED or a release differs; 1 on any other error. With `--json` the run is printed in each case.

## Kaya config delivery

A checkout is delivered when it holds a committed `kaya.yaml` and no `.agents/.managed.json` (until the cutover, korurlabs/agents still syncs it and Kaya leaves it alone). `kaya.yaml` may set `remoteUrl`, when the origin remote does not name the repository, and `organization`, the organization id every release must be signed for; without it the first verified release pins it.

```sh
oba kaya install    # once per machine; --dry-run shows the changes
oba kaya sync       # fetch, verify and switch; exits 1 without a verified release
oba kaya refresh    # switch even while other sessions of the checkout run
oba kaya status     # local state only; --json for scripts, as with sync and refresh
```

- `oba kaya install` sets `claudeCode.claudeProcessWrapper` in the VS Code user settings to `oba-kaya-launch` (the same binary under that name; Homebrew and `install.sh` install the link, otherwise `~/.local/share/oba/bin/oba-kaya-launch`), adds `claude() { oba kaya launch claude "$@"; }` to `~/.bashrc` and `~/.zshrc`, and registers `oba kaya hook <event>` for `SessionStart`, `SessionEnd`, `PreToolUse`, `UserPromptSubmit` and `PostToolUse` in the Claude Code user settings. It replaces what the former `kaya` CLI registered: its `# >>> kaya >>>` shell block, its `kaya hook <event>` hooks and the `ConfigChange` hook of an older version. Each changed file is first copied to `<file>.oba-backup-<time>`. A VS Code settings file with comments is left alone; the command prints the line to add.
- `oba kaya launch <command>` syncs (3 s online at most, then the cache), registers the process under `.kaya/sessions/`, starts `oba kaya watch`, sets `KAYA_RELEASE`, `KAYA_RELEASE_STATE`, `KAYA_LAUNCHED=1` and `KAYA_CHECKOUT`, and execs the command. As `oba-kaya-launch` it takes an absolute executable first argument as the command (the extension's bundled Claude), otherwise it runs `claude` with the arguments.
- Delivered paths: `AGENTS.md`, `.agents/AGENTS.md`, `.claude/settings.json`, everything under `.agents/{rules,skills,roles,workflows,templates,hooks}/` and `.claude/{agents,skills}/`, the Markdown files under `docs/private/conventions/`, and the `AGENTS.md` of a project folder, one to four folders deep with no folder starting with a dot (`projects/kagi/AGENTS.md`). A project's other files live at the namespace paths of the table under Seed, at the repository root.
- Start folder: Claude Code reads `.claude/settings.json` only from the folder a session starts in and does not load the delivered rules, whose links lead out of a sub-folder. A session started below the checkout root (`projects/kagi`) therefore runs without the delivered hooks, guardrail deny rules and rules; `oba kaya hook session-start` puts a warning into its context that names the folder and asks for a restart at the checkout root.
- Trust: a release is used only when its Ed25519 signature verifies with a key compiled into `releaseKeys` (`internal/apps/kaya/delivery/keys.go`), its manifest hashes to `manifestSha256`, it names the checkout's organization and normalised remote, its number is not below the highest verified here, every path passes the server's allow-list and link rules, and every blob hashes to its name. Only then is anything written. For the local stack, `oba kaya signing-keys` prints a `KAYA_DEV_TRUSTED_KEYS` line, honored only while the API is a loopback address.
- Layout: `.kaya/versions/<n>/` holds each built release, `.kaya/current` points at the active one (switched by one rename), and every delivered path is a relative link into `.kaya/current`, except `.claude/settings.json`, a regular file with the guardrail deny rules merged into `permissions.deny`. `.agents/.kaya-release.json` names the active release. The blob cache is `$XDG_CACHE_HOME/kaya/blobs/<sha256>`, shared by the machine's checkouts. The newest three versions stay.
- Pinning: while another session of the checkout runs, a newer release is staged (state STALE) and activated by `oba kaya hook session-end` of the last session, or by `oba kaya refresh`.
- States: FRESH (the newest release the server named), CACHED (Kaya not reached, or its newest release not taken), STALE (a newer release waits), NONE (no verified release; the guard refuses `Bash`, `Edit`, `Write`, `MultiEdit`, `NotebookEdit`, `Skill` and `Agent`), UNVERIFIED (the active release fails its check and cannot be restored). `oba kaya hook session-start` writes the state, the release and `deliveredBy` (LAUNCHER or HOOK) to `$XDG_STATE_HOME/kaya/queue/sessions/<session id>/config-release.json`, which the sender adds to the session's batch.
- Hooks read only local state, except `session-start`: in a session the launcher did not start it syncs, switches when no always-on file changed (`reloadSkills`), and otherwise stages the release and asks for a restart; in every session it asks Kaya for plan feedback and lessons (below). `pre-tool-use` also refuses edits of delivered paths and runs `.agents/hooks/dispatch.sh` of the checkout when neither the project settings nor the korurlabs/agents user-level launcher run it.
- Watcher: `oba kaya watch` asks `/kaya/delivery/current` every minute and builds a newer release for the next session, without switching.
- Plan feedback: every 15 seconds `oba kaya watch` fetches the reviewer feedback on plans whose latest version came from a live session of the checkout (`GET /kaya/delivery/plan-feedback?sessionId=`) into `.kaya/inbox/<session id>.json`, leaving out what a hook already delivered, and posts `.kaya/inbox/<session id>.acks.json` to `POST /kaya/delivery/plan-feedback/acks`, deleting each id after a 2xx (the last round runs when the last session ended). `oba kaya hook user-prompt-submit` and `post-tool-use` read only the inbox: they add the items as `additionalContext` (10 000 characters at most; the rest is named as "and N more on the Kaya plan page <url>" and waits for the next hook) and move the added ids to the acks file. No inbox gives no output. `session-start` asks `GET /kaya/delivery/plan-feedback?remoteUrl=&branch=` for the feedback a session that ended on the checkout's branch left, for 2 seconds at most, adds it after the release lines, and acknowledges it with the new session id (queued in the acks file when Kaya is not reached); offline it adds nothing and the feedback stays pending. `session-end` removes the session's inbox.
- Lessons: `session-start` asks `GET /kaya/delivery/lessons?remoteUrl=&since=` with `If-None-Match`, for 2 seconds at most (see Lessons).
- Machine token: `KAYA_RECORDER_TOKEN`, else `kagi run --personal --path /kaya/recorder`. The machine calls go to `OBA_API_URL`, else the production API; they never read the session of `oba login`, so a hook neither waits on nor prompts for the keyring. On the local stack, set `OBA_API_URL=http://localhost:8080`.

## Kaya capture

`oba kaya` records Claude Code sessions for Kaya: their events, summary, plans, handoff snapshots, user, account and usage limits. Nothing else on the machine records them.

```sh
oba kaya hook record    # one hook event on stdin into the local queue
oba kaya statusline     # the status line: usage limits into the queue, then your own status line
oba kaya sender --once  # hidden: send the queue once; hooks start the sender by themselves
```

- Registration: the repository's `.claude/settings.json`, not `oba kaya install`. It runs `oba kaya hook record` for `PreToolUse` (`Agent|Skill|ExitPlanMode`), `PostToolUse` and `PostToolUseFailure` (`Agent|Skill|Bash|Edit|Write|MultiEdit`), `UserPromptSubmit`, `UserPromptExpansion`, `SessionStart`, `SubagentStart`, `SubagentStop`, `Stop` and `SessionEnd`, and `oba kaya statusline` as its `statusLine`. Without `kaya` on the `PATH`, each hook prints `kaya recorder: kaya not found, nothing recorded` on stderr and exits 0, and the status line shows `oba kaya status line: kaya not found`; the session itself is not affected.
- Scope: an event is recorded only in a checkout with a committed `kaya.yaml`. In any other checkout the hook writes nothing.
- `oba kaya hook record` reads no network, prints nothing on stdout, runs git only on `SessionStart`, `Stop` and `SessionEnd`, and exits 0 on every error with one `oba kaya hook: nothing recorded: ...` line on stderr. Prompts, command arguments, commands and messages are redacted before they are written; plan and handoff snapshots are redacted when the sender builds the batch. After it queued an event it starts `oba kaya sender` unless one runs or `KAYA_SENDER=off` is set.
- `oba kaya sender` is one process per machine (`sender.lock`). It reads the machine token once (as config delivery does), sends only over https or to a loopback address, follows no redirect, and every 2 seconds sends each session one batch to `POST /kaya/ingest/batches`. A 401 stops it until the token changes; 400, 409, 413 and 422 park the session until the token changes; 403 `KAY_SEC_007` (no Kaya user holds the session's git email), 429, 5xx and network errors back off from 2 seconds to 5 minutes and keep the queue. It exits after 10 idle minutes and removes an ended, acknowledged session after 7 days.
- `oba kaya statusline` reads the session JSON Claude Code passes. When `rate_limits` holds a `five_hour` or `seven_day` window (a percentage from 0 to 100, a reset within 8 days) that differs from the last one, it writes the usage file, which the sender adds to the session's next batch. It then runs your own `statusLine` command from the Claude Code user settings (`$CLAUDE_CONFIG_DIR/settings.json`, else `~/.claude/settings.json`) with the same input, `KAYA_STATUSLINE=1` and a 2 second limit, and prints its output unchanged. Without one, the line is `<model> | 5h 23% | 7d 41%`; when your command fails or times out, that line ends with a pointer to `recorder.log`. With `KAYA_STATUSLINE=1` it neither captures nor wraps, so a command that calls `oba kaya statusline` cannot recurse.

The local state lives in `$XDG_STATE_HOME/kaya` (`~/.local/state/kaya` without it, on macOS as well), mode 0700. Its format belongs to this CLI alone.

| Path | Holds |
|---|---|
| `queue/sessions/<id>/events.jsonl` | the session's events, one JSON line each, with a per-session `seq` from 1 |
| `queue/sessions/<id>/summary.json`, `session.json` | the run summary, and the session's machine, Claude Code version, model, remote, git author email and Claude account |
| `queue/sessions/<id>/config-release.json` | the release report of `oba kaya hook session-start` |
| `queue/sessions/<id>/plans/`, `handoffs/` | plan and handoff snapshots waiting to be sent |
| `queue/sessions/<id>/cursor.json`, `.lock` | what Kaya acknowledged, and the lock every write of the queue holds |
| `queue/usage/<id>.json` | the latest usage limits the status line saw |
| `state.json` | what the sender last learned about its token: a fingerprint, never the token |
| `sender.lock`, `sender.started` | the sender's lock, and its last start by a hook (the back-off of restarts) |
| `statusline-logged.json` | which status line problems were already logged for which session |
| `recorder.log`, `recorder.log.1` | the sender's and the status line's log, moved to `.1` at 1 MB |

## Kaya propose

```sh
oba kaya propose --reason "Allow the oba CLI" .claude/settings.json
oba kaya propose --dry-run                     # every changed delivered path and new config file, checked, not sent
oba kaya propose --reason "..." --from /tmp/edit .agents/rules/core.md
oba kaya propose --reason "..." --remove .agents/rules/old.md
```

- A proposal is made on the release the checkout runs (`oba kaya status`); the checkout needs a verified active release. Each named path is compared with that release: a delivered file edited through its link or replaced by a regular file, a changed link target or mode, a removed path, or a new file on the allow-list, including a new Markdown file under `docs/private/conventions/`. A directory stands for every config file below it; a folder of the checkout such as `projects/kagi` stands for its own `AGENTS.md` and the delivered paths below it, never for the nested agent files that stay in git. Without paths, every changed delivered path and every new config file is proposed; a new `<root>/AGENTS.md` of the checkout is proposed by naming it or its folder.
- `--from <dir>` reads the named paths (relative to `<dir>`, laid out like the repository) instead of the checkout. It is the route of a session agent, which `oba kaya hook pre-tool-use` keeps from editing delivered paths in place; `--remove` names a delivered path to remove.
- Refused locally, before anything is sent: paths off the allow-list, a link at a `SKILL.md`, under `.agents/hooks/` or at `.claude/settings.json`, links that leave the repository or end at nothing (also after a removal), paths below a delivered link, paths that differ only in letter case, settings that are not a JSON object, more than 200 files or a body over 8 MiB, a reason outside 10 to 1000 characters, and a file over a size limit (5 MiB, a `SKILL.md` under 1500 lines, description plus `when_to_use` at most 1536 characters) unless the release already held it over the limit and it does not grow. Frontmatter keys and the dispatcher check are Kaya's alone.
- The guardrail deny rules merged into `.claude/settings.json` are taken out of a proposed settings file, so that no proposal copies them into the pack.
- Without `--reason`, the command asks for one in a terminal and refuses otherwise.
- On success it prints the proposal's number, the name of its pack or project and the review URL. Kaya's refusals are explained: KAY_CFG_002 (a proposed path changed in Kaya since the release; keep your edits, sync and propose again), KAY_CFG_019 (paths of several packs or projects; one command per owner is printed), KAY_CFG_001 and KAY_CFG_003 (each violation), KAY_CFG_015 (another change of the pack or project in review), KAY_CFG_016 (nothing changes), KAY_NF_001, the token and membership refusals.
- Until a release carries the change, `oba kaya sync` puts the delivered files back, which drops edits made in place.

## Kaya lessons

The Kaya app holds the lessons of an organization, each for one repository or for the whole organization. `oba kaya lessons` reads and records them with the machine token, in any git checkout.

```sh
oba kaya lessons add --title "<the rule as a sentence>" --body-file /tmp/lesson.md [--repo-wide] [--session <id>]
oba kaya lessons list [--search <topic>]   # --json for scripts
oba kaya lessons show <id>
oba kaya lessons remove <id>
oba kaya lessons import <file> [--repo-wide]
```

- A title is the rule, at most 300 characters on one line; the body, at most 4000 characters, says what went wrong, why, and the correct approach. `add` prints the lesson's id and web URL. Without `--repo-wide` the lesson belongs to the checkout's repository.
- The session of a lesson is `--session`, else the session record of the Claude Code process that runs the command, else the only live session; with none known the lesson has no session link.
- `list` prints `- <id> <title>` lines for the checkout's repository and the organization-wide lessons, newest first, at most 100. `--search` matches title and text and prints the text as well. `show` prints a whole lesson. `remove` deletes one for good; only its author, an ADMIN or an OWNER may.
- `import` splits a file at lines starting `### ` outside code fences, heading as title and the rest as body, and adds each block unless the same title exists in the same scope. It checks every block first and refuses the whole file when one breaks a limit. Run twice, it creates nothing the second time.
- Every failed call exits non-zero.

`oba kaya hook session-start` keeps a delivered checkout up to date, and never fails on this step:

- It sends the stored `lessonsEtag` of `.kaya/state.json` as `If-None-Match` and the stored `lessonsSeenAt` as `since`, for 2 seconds at most.
- On a 200 it rewrites `.kaya/lessons.md`, the titles of the list, and adds to the session's context `Kaya lessons new or edited since your last session here:` with the changed titles, then `and N more, see oba kaya lessons list` for every lesson of the list it did not name. Lessons come last in the 10 000 characters: titles that do not fit count as "more". Unchanged lessons add nothing.
- `lessonsSeenAt` is the newest `updatedAt` Kaya answered, not the machine's clock, so a skewed clock neither misses nor repeats a lesson. It belongs to the checkout, so people who share a checkout share it. A checkout's first answer marks nothing as changed; it fills the file. When the answer is a list of no lessons, the first lessons added later are all named.
- `lessonsEtag`, `lessonsSeenAt` and the file are stored together once the context is written. On a 304, offline, or on any refusal (an archived repository answers `KAY_VAL_002`) the cached file and the stored values stay, the error goes to stderr and the session starts. Changed titles that find no room at all are stored by a later session, which names them.

## Token storage

The session (access and refresh tokens, expiry, API URL, issuer, client id, user id and default organization id) is stored in the OS secret service through go-keyring: GNOME Keyring or KWallet through Secret Service on Linux, the Keychain on macOS, under service `oba-cli` and user `default`. Tokens are refreshed when they expire and are never printed.

Without a reachable secret service, the session goes to `~/.config/oba/credentials` (mode 0600, directory 0700), and the CLI prints a one-time warning that it stores credentials in plaintext. Once the keyring has been used on a machine, the marker `~/.config/oba/keyring-active` makes a later unreachable keyring an error instead of a silent fall back to the file.

## Layout

The packages are grouped as in the Oba backend: `dojo` is the technical foundation, `shrine` identity, sign-in and the organization, `connect` the integrations with outside systems, and `apps/<app>/<domain>` the products.

| Path | Holds |
|---|---|
| `main.go` | entry point; the version comes in through `-ldflags "-X main.version=..."` |
| `cmd/` | the root command; it adds the platform commands and one command group per app, and `oba admin` |
| `cmd/shrine/` | the platform commands `login`, `logout` and `org` |
| `cmd/apps/<app>/` | `NewCmd()` builds `oba <app>` and adds the commands of each domain (`cmd/apps/otak/`, `cmd/apps/kaya/`) |
| `cmd/apps/<app>/<domain>/` | the commands of one domain, one file per command; `NewCmd()` builds `oba <app> <domain>` (`cmd/apps/otak/relation/`, `contact/`, `icp/`, `event/`, `find/`, `template/`, `outreach/`, `attachment/`; `cmd/apps/admin/org/`, `member/`, `invite/`; `cmd/apps/kaya/delivery/` (sync, refresh, status, launch, watch), `hook/`, `statusline/`, `install/`, `seed/`, `propose/`, `lessons/`, `sender/`, `signingkey/`) |
| `internal/apps/<app>/<domain>/` | the logic of one domain of an app (`internal/apps/otak/relation/`, `contact/`, `icp/`, `event/`, `find/`, `template/`, `outreach/`, `attachment/`; `internal/apps/admin/org/`, `member/`, `invite/`, and `role/`, the role check that `invite` and `member` share) |
| `internal/apps/otak/pdf/` | inlining local images as data URIs and rendering HTML to PDF with `weasyprint` |
| `internal/apps/otak/docfile/` | the file forms the template, mail-template and outreach domains share: the MAIL file, `--lang` and `--type`, the extension check, loading a file and writing a pull |
| `internal/apps/kaya/<domain>/` | the logic of Kaya's domains: `delivery/` (trusted keys, manifests, the blob cache, versions and the switch, sessions, the launcher and the watcher, the outcome texts), `hook/`, `statusline/`, `install/`, `seed/`, `propose/`, `lessons/`, `capture/` (one hook event into the local queue) and `sender/` (the queue to Kaya) |
| `internal/apps/kaya/client/` | the Kaya API client: every call takes a context, user calls are signed through `internal/shrine/session`, the others with the machine token |
| `internal/apps/kaya/machine/` | the machine token (`KAYA_RECORDER_TOKEN`, else Kagi), the API URL of the machine calls (`OBA_API_URL`, else production, never the stored session), the https-or-loopback check and a client with the token |
| `internal/apps/kaya/redact/` | what looks like a secret: `Text` redacts it, `Find` names the lines that hold one |
| `internal/apps/kaya/kayatest/` | test helpers of the Kaya tests: a fake delivery and lessons API with a signing key, a scratch delivered checkout, the command runner; imported by tests only |
| `internal/shrine/auth/` | the device authorization grant and the token store |
| `internal/shrine/organization/` | the organization table that `oba org list` and `oba admin org list` share, the lookup of a member by user id or e-mail that `oba admin member` and `oba kazan` share, and the `--author` list of `oba kazan updates` and `pr`, where `me` is the signed-in user |
| `internal/shrine/session/` | the signed-in client for the current organization, the bearer (API URL, organization, refreshing token) of a client of its own such as Kaya's, the current user and its id, the organization name and the API URL |
| `internal/connect/claude/` | running Claude Code headless and reading its stream-json output; returns the result as JSON |
| `internal/dojo/config/` | API URL, issuer and client id resolution, `config.json` and the current organization |
| `internal/dojo/client/` | the API client: the `CustomResponse` envelope, `APIError`, refresh and retry, paged lists, exit codes, multipart uploads with a Content-Type per part, and downloads from signed URLs without the token |
| `internal/dojo/attachment/` | the attachments of any storage parent that Otak and Kazan share: reading and checking local files, list, table, find, upload and delete |
| `internal/dojo/apidocs/` | reading the request schemas from `/v3/api-docs`, the key check and the field help |
| `internal/dojo/guardrail/` | `Sanitize` and `CleanJSON`: remove terminal controls from text the CLI did not write; `OneLine` and `PersonName` for table cells |
| `internal/dojo/dates/` | typed days (YYYY-MM-DD) and instants turned into the API's date and RFC 3339 values, and instants shown in the viewer's time zone |
| `internal/dojo/userinput/` | the record id (UUID) check and the split of a comma-separated flag value |
| `internal/dojo/jsonedit/` | key-level edits of JSON settings files that keep every other key as written |
| `internal/dojo/exitcode/` | an exit status of the command's own, after it printed its outcome; the root prints nothing for it |
| `internal/dojo/terminal/` | prompts, reading a JSON object from stdin, and the messages the commands share |
| `internal/dojo/clitest/` | test helpers shared by the command tests (a mock API, a temporary home, typed terminal input, the shared API document fixture); imported by tests only |
| `e2e/kaya/` | the end-to-end test of Kaya against the local stack and the built binary; skipped unless `KAYA_E2E=1` |

Rules:

- `cmd/apps/<app>/<domain>` is wiring only: flags, arguments and stdin, one call into `internal/apps/<app>/<domain>`, and printing. `cmd/apps/<app>` only adds the domain commands.
- `internal/apps/<app>/<domain>` holds the logic and reaches the API only through `internal/dojo/client`.
- Domains do not import each other. Shared needs go to a platform package (`internal/dojo/client`, `internal/dojo/apidocs`, `internal/dojo/terminal`, `internal/shrine/session`), not from one domain to another. Within Otak, `internal/apps/otak/pdf` and `internal/apps/otak/docfile` are such shared packages, not domains: they hold no command and import no domain. The command packages of one app do not import each other either.
- Kaya differs in two ways. Its domains reach the API through `internal/apps/kaya/client`, not `internal/dojo/client`: the hooks bound every call with a context deadline, most calls send the machine token rather than a user's sign-in, and delivery reads ETags and raw blobs. And `delivery`, `capture`, `client`, `machine` and `redact` are Kaya's shared packages, as `pdf` and `docfile` are Otak's. Some Kaya domains still use another's exported rules, as they did in the former kaya CLI: the delivered paths of `seed` (in `delivery`, `propose` and `sender`), the settings paths of `install` (in `hook` and `statusline`), and the sender start (in `hook`).
- `internal/connect/claude` knows no app: the caller passes the brief and the JSON Schema and decodes the `json.RawMessage` it gets back.
- Everything Claude Code returns is built from web pages. `claude.Run` passes its result through `guardrail.CleanJSON`, and its progress lines and error messages through `guardrail.Sanitize`, so that no caller can print escape sequences or bidi controls from a page. `guardrail` imports nothing from `claude` or an app.
- Tests sit next to the code they test, and a fixture next to the tests that read it; a fixture or helper that several packages share lives in `internal/dojo/clitest`. Test seams (`claudeInstalled`, `runClaude`, `stdinIsTerminal`, `stdoutIsTerminal`) are package-level variables in the package of the command that uses them. Tests never need WeasyPrint: `clitest.StubWeasyPrint` puts a stub `weasyprint` first on `PATH`, and `clitest.NoWeasyPrint` leaves it off.
- A new app, such as `oba kalkan`, becomes `cmd/apps/kalkan/` with one package per domain under it and `internal/apps/kalkan/<domain>/`, and `cmd/root.go` gains one `AddCommand` line.
