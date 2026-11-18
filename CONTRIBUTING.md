# Contributing to Oba

Thank you for helping. Open an issue first for anything larger than a small fix, so we can agree on the approach before you write it.

## Pull requests

- Branch from `main` and keep one change per pull request.
- Run the checks of the parts you changed before you push; they are the same ones CI runs (see the README, "Checks"):
  - backend: `mvn clean verify` in `backend`, with the services of `backend/docker-compose.ci.yaml` running;
  - frontend: `bun install --frozen-lockfile`, `bun run build`, `bun turbo run typecheck` and `bun turbo run test` in `frontend`;
  - a CLI: `make build`, `go vet ./...`, `make test` and `make lint` in its directory.
- A database change is a new Flyway migration in `backend/src/main/resources/db/migration`; never edit a migration that is already on `main`.
- Never commit a secret or an `.env` file. Only `.env.example` files with placeholder values belong in the repository.
- Pull requests from forks run CI with a read-only token and no secrets, and a maintainer approves the first run.

## Licence

By contributing you agree that your contribution is licensed under the licence of the directory it changes: the GNU Affero General Public License v3.0 for the repository, and the MIT License for `cli/oba`.
