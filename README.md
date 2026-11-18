# Oba

Oba is an application platform: one API and one web workspace that host several apps sharing the same sign-in, organizations and members.

| App | What it does |
|---|---|
| Kaizen | Workflow management: projects, boards, tickets, sprints, milestones, a roadmap and incidents. |
| Kaya | The agent configuration and session log of AI coding agents: rules, skills and lessons delivered to sessions as signed releases, and the sessions recorded back. |
| Kalkan | A docs app: pages that developers and agents read and write from the browser or the terminal. |
| Otak | Relations: leads, customers and suppliers, their contacts, events, document and mail templates, and outreach. |
| Admin | Platform administration of organizations. |
| Account | A user's profile, organizations, invitations and onboarding. |

## Stack

- Backend: Spring Boot 3.5, Java 25, Maven; PostgreSQL 16 with Flyway migrations; Keycloak for sign-in; Azure Blob Storage (Azurite locally) for files.
- Frontend: a Bun and Turborepo workspace of Next.js 16 apps with React 19 and Tailwind CSS v4, sharing the packages `@oba/ui` and `@oba/shared`; `kaizen-mobile` is an Expo app.
- CLIs: Go 1.26 modules.

## Layout

```
backend/             the Oba API (package com.korur.oba): apps under apps.<app>, platform packages dojo, shrine, connect
  docker-compose.yaml    PostgreSQL, Keycloak (realm "sensey", imported on start) and Azurite for local development
frontend/            the Bun workspace
  apps/              kaizen, kaya, kalkan, otak, admin, account, kaizen-mobile
  packages/          ui, shared
  tooling/           eslint, prettier and typescript configs
cli/
  oba/               `oba`, the Oba apps from the terminal
  kaya/              `kaya`, delivers agent config to sessions and records them
  kalkan/            `kalkan`, reads and writes Kalkan pages
scripts/             local start and stop scripts
devstack.yaml        the local stack for devstack
```

## Run locally

You need Java 25, Maven, Bun 1.3, Node.js 22, Go 1.26 for the CLIs, and Docker or Podman with Compose.

### Services

```sh
cd backend
docker compose up -d
```

This starts PostgreSQL on `127.0.0.1:5438`, Keycloak on `http://localhost:8086` with the realm `sensey` imported from `backend/src/main/resources/podman/keycloak/realm/`, and Azurite on `127.0.0.1:10000`, and creates the blob container.

### Backend

```sh
cd backend
export ENCRYPTION_SECRET="$(openssl rand -hex 32)"
export AZURE_STORAGE_BLOB_BASE_URL=http://127.0.0.1:10000/devstoreaccount1
export SPRING_PROFILES_ACTIVE=local,test
mvn spring-boot:run
```

The API listens on `http://localhost:8080` and Flyway migrates the database on start. The defaults in `backend/src/main/resources/application.properties` point at the services above; every value can be overridden with an environment variable. The API refuses to start without `ENCRYPTION_SECRET` and `AZURE_STORAGE_BLOB_BASE_URL`; keep the same `ENCRYPTION_SECRET` between restarts, or data encrypted earlier cannot be read. The `test` profile lets the API sign Kaya releases with a key it generates on each start; to keep one key, set `CONFIG_SIGNING_KEY` and `CONFIG_SIGNING_KEY_ID` instead and drop that profile.

### Frontend

```sh
cd frontend
bun install
cd apps/kaizen
bun run dev
```

Each web app has a fixed dev port (`kaizen` 3002, `kaya` 3014, `admin` 3016, `account` 3017, `otak` 3018, `kalkan` 3019). An app reads its settings from the environment at run time: `API_BASE_URL` (`http://localhost:8080`), `AUTH_URL` and `BASE_URL` (the app's own URL), `AUTH_SECRET` (any random string; Kaizen, Admin, Account, Otak and Kalkan share one session cookie, so they need the same value), and `AUTH_KEYCLOAK_ISSUER` (`http://localhost:8086/realms/sensey`), `AUTH_KEYCLOAK_ID` (`oba`) and `AUTH_KEYCLOAK_SECRET` (the secret of the `oba` client in the imported realm). The URLs of the other apps go in `KAIZEN_URL`, `KAYA_URL`, `KALKAN_URL`, `OTAK_URL`, `ADMIN_URL` and `ACCOUNT_URL`.

### With devstack

[devstack](https://github.com/korurlabs/devstack) starts the whole stack from `devstack.yaml` on ports of its own, so several checkouts can run side by side: `devstack up`. Its app units read their secrets through Kagi, so without a Kagi account use the steps above.

### CLIs

```sh
cd cli/oba       # or cli/kaya, cli/kalkan
make build       # bin/<name>
make test
```

## Checks

Every pull request and every push to `main` runs the workflows in `.github/workflows/` for the parts it changes:

- `backend.yml`: `mvn clean verify`, the unit and integration tests against PostgreSQL and Azurite.
- `frontend.yml`: `bun install --frozen-lockfile`, `bun run build`, the type check and the tests of every workspace.
- `cli-oba.yml`, `cli-kaya.yml`, `cli-kalkan.yml`: `make build`, `go vet`, `make test` and golangci-lint.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## Licence

Oba is free software under the GNU Affero General Public License v3.0; see [LICENSE](LICENSE). The three CLIs are under the MIT License: `oba` in `cli/oba` ([cli/oba/LICENSE](cli/oba/LICENSE)), `kaya` in `cli/kaya` ([cli/kaya/LICENSE](cli/kaya/LICENSE)) and `kalkan` in `cli/kalkan` ([cli/kalkan/LICENSE](cli/kalkan/LICENSE)). Everything else in this repository is under the AGPL-3.0.
