# Kaya web

The web app of Kaya, the agents log of Claude Code sessions, in
`projects/oba/frontend/apps/kaya/`, a member of the Oba frontend workspace. It
shows one tile per session and, per session, which subagents ran, which skills
they loaded and what they left behind. It also lists the organization's machines
with their Claude subscription usage, and the repository settings.

The app is a Next.js server. Users sign in through Keycloak (realm `sensey`,
client `oba`), and every page reads the Oba API under `/kaya/...` with the
user's bearer token and the chosen organization in `X-Organization-ID`. Users,
organizations and members belong to the Oba platform: Kaya reads them from
`/users/me` and `/organizations/{id}/members` and manages none of them; the
organization pages are in Admin (`ADMIN_URL`, read at run time). The `kaya` CLI records the sessions and sends
them to that API. The interface is in English and has no dark mode.

## Development

```bash
cd projects/oba/frontend
bun install
cd apps/kaya
kagi run -- bun run dev
```

The app then runs at <http://localhost:3014>. It needs the local Keycloak
(:8086) and the Oba backend (:8080). Its config comes only from the Kagi app
`/oba/kaya`, environment `local`, through this directory's `kagi.yaml`.

## Build

```bash
bun run build
bun run start
```

`bun run build` writes the standalone server into `.next/standalone/`, which the
workspace `Dockerfile` runs with `APP_NAME=kaya`. `bun run start` serves the build on port 3014.

## Checks

```bash
bun run typecheck   # TypeScript, strict
bun run lint        # ESLint
bun run test        # bun test
bun run format      # Prettier
bun run format:check
```

## Layout

| Directory            | Contents                                                      |
| -------------------- | ------------------------------------------------------------- |
| `app/`               | Routes and pages (App Router)                                 |
| `proxy.ts`           | Sends signed-out visitors to the sign-in                      |
| `components/ui/`     | shadcn primitives and shared UI pieces                        |
| `components/layout/` | Identity gate, sidebar, top bar and page frame                |
| `features/`          | One module per page: sessions, plans, config pages, settings  |
| `hooks/`             | Shared React hooks                                            |
| `lib/`               | Auth, API client, identity and the event and handoff parsers  |
| `types/`             | The recorded event model                                      |
| `fixtures/`          | Recorded runs the tests read                                  |
