---
description: Backend rules that no convention skill holds - soft delete, the test harness and the local migration check
paths: ["projects/*/backend/**"]
---

# Backend rules

Rules for the Kaizen and Kagi backends that no convention skill holds. The skills come first: `.agents/rules/09-skill-routing.md` names them.

## Soft delete

- Delete data that has audit significance (users, organizations, memberships and the like) by soft delete, never by removing the row.
- There are two strategies, and one entity never uses both: a status enum with a terminal value such as `DELETED` on a domain entity that extends `BaseEntity`, or Hibernate `@SoftDelete` with a boolean `deleted` column on a technical entity that extends `SoftDeleteBaseEntity`.
- The full convention, with the query and migration patterns, is `docs/private/conventions/backend/32-soft-delete-convention.md`.

## Tests

- Test type, naming and structure follow the `testing-patterns` skill.
- Kagi backend: a test that needs Spring extends `AbstractPostgresIntegrationTest`, which boots the full context (`@SpringBootTest`) on a PostgreSQL container and runs every Flyway migration. The code has no `@WebMvcTest` or `@DataJpaTest` slices; do not add one.
- Kaizen backend: the tests are unit tests on Mockito, and none needs a database.
- Kaizen backend: since the tests never run Flyway, a change that adds or changes a migration is done only when `mvn flyway:migrate -Dflyway.url=jdbc:postgresql://localhost:5438/village -Dflyway.user=village -Dflyway.password=village -Dflyway.ignoreMigrationPatterns='*:missing'` succeeds in `projects/kaizen/backend`; it skips the Hibernate schema check, so a mismatch with the entities shows only at the next backend start. Do not use `restart-backend.sh` for this: it kills whatever listens on :8080. If PostgreSQL :5438 is not running, report `BLOCKED` with `projects/kaizen/scripts/start.sh --no-frontend` for the user to run; do not start the database yourself.
