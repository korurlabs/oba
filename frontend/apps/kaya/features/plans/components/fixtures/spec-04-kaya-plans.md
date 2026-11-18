# 04 Kaya plans

Status: draft for approval. Project: Kaya (`projects/kaya/`). Scope: recorder,
backend, CLI, frontend; the agent runner is not affected. Builds on `01_KAYA.md`
(sessions, ingest, delivery, `kaya watch`), `03_KAYA_PROJECTS.md` (PROJECT sets
with a `rootPath`) and the chat to session rename of `05_KAYA_SESSIONS.md`.
Cited code uses the session names as built by that rename.

## 1. Summary

"Plans" makes the plans Claude Code writes before it implements visible and
reviewable in Kaya, and carries the reviewer's answer back to Claude. A plan is
one of: a spec file written by a session under a plans location
(`docs/private/plans/kaya/04_KAYA_PLANS.md`, `docs/claude/plans/kagi/07_X.md`),
kind `SPEC`; or an in-session plan, kind `SESSION`, which is the `plan` of an
`ExitPlanMode` call or an assistant message in the core plan format (Goal,
Steps, Verification). The planning handoff `handoffs/{feature}/planning.md` is
not a plan of its own: it is shown on the spec it names, with its status, open
questions and assumptions, and the feature's other handoffs give the progress of
the implementation units.

Every capture of changed content is a new version. A member reads a plan as an
outline (goal, steps, verification, risks; for a spec its stories, units with
status and assumptions to confirm) or as the line-numbered document, compares
versions, and gives feedback: approve, request changes, or comment on a line
range. Feedback reaches Claude through the `kaya` CLI hooks: in the running
session with the next prompt or tool call, otherwise at the start of the next
session on the same branch.

Roles: any MEMBER reads plans, comments and requests changes; the author of the
plan's session or an ADMIN or OWNER approves. Success: a spec written by `/plan`
appears under Plans within 5 s of the write, linked to its session, repository
and project; "Request changes" on lines 40 to 44 reaches the running session as
context within 30 s of its next tool call or prompt, naming the file, lines and
reviewer; the plan page shows "Delivered to session <id> at <time>".

In scope: capture (recorder, `.claude/settings.json` rows), ingest, the `plan`
domain, the delivery endpoints, CLI delivery through `kaya watch` and two new
hook events, the Plans page and the plan page, a "Plans" panel on the session
page. Out of scope: editing a plan in Kaya, plans of sessions without the
recorder, waking an idle session, Kazan ticket creation from units, and
changing the managed `plan`, `implement` and `orchestrator` commands (section 6
names the follow-up).

## 2. User stories and acceptance criteria

PL1. As a MEMBER, I want spec files a session writes to appear as plans, so that
I can review them without opening the worktree.

- [ ] A Write, Edit or MultiEdit of a path whose repository-relative form
      (worktree prefix `.claude/worktrees/<name>/` removed) matches
      `^docs/(.+/)?plans/.+\.md$` is captured with the file's full content after
      the write; so is such a path named in a successful Bash command.
- [ ] The plan's identity is (repository, path): a second session that edits the
      same spec adds a version to the same plan; unchanged content (same SHA-256
      as the latest version) adds none.
- [ ] Content over 200 000 characters is not captured; the recorder logs the
      path and the session's next batch is unaffected.

PL2. As a MEMBER, I want in-session plans captured, so that plan-mode plans are
reviewable too.

- [ ] An `ExitPlanMode` call is captured from its PreToolUse event with
      `tool_input.plan`, before the developer answers it in the terminal.
- [ ] A Stop event whose full assistant message has the plan format (a `Goal`
      line and a `Steps` line, bold or heading) is captured; other messages are
      not.
- [ ] A session has at most one SESSION plan; each capture with new content is
      its next version.

PL3. As a MEMBER, I want a "Plans" page in the sidebar, so that I see what waits
for review.

- [ ] The sidebar shows "Plans" at `/plans` between "Sessions" and "Authors";
      the page lists plans newest change first, 25 a page, with title, kind,
      repository, project, author, review state, versions, open feedback and
      updated.
- [ ] Filters, URL-synced: repository, project, kind, review state (default
      "Open"), author, search on title and path.
- [ ] An empty organization shows "No plans recorded yet. Plans appear when a
      session writes a spec or leaves plan mode."

PL4. As a MEMBER, I want to read a plan in a structured way, so that I grasp it
quickly.

- [ ] The Outline tab of a SESSION plan shows Goal, Steps (numbered, each with
      its file paths as code), Verification and Risks; a missing section shows
      "Not in this plan".
- [ ] The Outline tab of a SPEC shows the summary, stories with their acceptance
      checkboxes, the implementation units table with a status per unit, and
      section 10's assumptions to confirm as a checklist.
- [ ] A unit's status is the status line of the feature handoff whose "Scope
      given" names `unit <N>`; with none it shows "No handoff yet"; all handoffs
      of the feature are listed below the table with their status.
- [ ] The planning handoff whose content contains the spec's path is shown in a
      side panel with its status, "Open questions and items" and "Next step".
- [ ] Text from the plan is rendered as text, never as HTML.

PL5. As a MEMBER, I want versions, so that I see what Claude changed after my
feedback.

- [ ] A version select lists number, source (file, plan mode, message), session
      and time; "Changes" shows the unified diff of the selected version against
      the one before.
- [ ] Feedback is shown on the version it was given on; on a later version it is
      marked "on version N".

PL6. As a MEMBER, I want to give feedback on a plan, so that Claude acts on it.

- [ ] In the Document tab, selecting lines in the gutter opens a comment form
      anchored to that range; the comment quotes up to 500 characters of the
      selected text.
- [ ] "Request changes" and "Comment" need a comment of 1 to 4000 characters;
      "Approve" takes an optional one.
- [ ] Feedback on a version that is not the latest is accepted only as a
      COMMENT; an APPROVE or REQUEST_CHANGES there is 409 `KAY_PLN_001`.
- [ ] The review state is the last APPROVE or REQUEST_CHANGES on the latest
      version, else OPEN; a new version sets it back to OPEN.
- [ ] Approve is shown and allowed only to the author of the latest version's
      session, an ADMIN or an OWNER; others get 403 `GEN_SEC_003`.
- [ ] The reviewer may withdraw their own feedback while it is not delivered.

PL7. As a developer, I want feedback to reach my running session, so that Claude
revises the plan without me copying text.

- [ ] While the session of the plan's latest version runs (not ended),
      `kaya watch` fetches its pending feedback every 15 s and the next
      UserPromptSubmit or PostToolUse hook of that session adds it as context,
      then acknowledges it.
- [ ] The context names Kaya, the reviewer, the decision, the plan path or "the
      plan of this session", the version, the line range and the quote, and fits
      10 000 characters; more feedback says "and N more on the Kaya plan page
      <url>".
- [ ] The plan page shows each item as "Waiting for the session" or "Delivered
      to <session> at <time>"; acknowledgement is idempotent.
- [ ] Without network the hook adds nothing and the feedback stays pending; the
      hook never blocks the session.

PL8. As a developer, I want feedback to reach the next session when the planning
session ended, so that nothing is lost.

- [ ] At SessionStart of a session of the same author, repository and branch,
      the hook adds the pending feedback of plans whose latest version came from
      an ended session, then acknowledges it with the new session id.

PL9. As any user, I want plans kept to my organization, so that no one outside
it reads them.

- [ ] A plan, version or feedback of another organization answers 404
      `KAY_NF_001`; an agent token reads only feedback for sessions of its own
      author.

## 3. Code references

Backend paths are under `projects/kaya/backend/src/main/java/com/sensey/kaya/`
(`B/`); others under `projects/kaya/` unless they start with `.claude/`.

| Item                                                                                                                                 | Kind     | Where                                                                                                                                                          |
| ------------------------------------------------------------------------------------------------------------------------------------ | -------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Recorder hook rows: PreToolUse `Agent\|Skill`, PostToolUse and PostToolUseFailure `Agent\|Skill\|Bash\|Edit\|Write\|MultiEdit`, Stop | existing | `.claude/settings.json:37`, `:94`, `:105`, `:134`                                                                                                              |
| `event_from`, `WRITE_TOOLS`, `_WORKTREE`, `snapshot_handoff`, `_apply`, recorder prints nothing                                      | existing | `recorder/record.py:239`, `:67`, `:82`, `:321`, `:618`, `:20`                                                                                                  |
| `changed_handoffs`, `build_batch`, `acknowledge`, `IDLE_EXIT_SECONDS`                                                                | existing | `recorder/sender.py:261`, `:369`, `:421`, `:52`                                                                                                                |
| `record.snapshot_plan`, `sender.changed_plans`                                                                                       | new      | modelled on `snapshot_handoff` and `changed_handoffs`                                                                                                          |
| `IngestBatchRequest`, `IngestHandoffRequest`                                                                                         | existing | `B/domain/ingest/dto/request/IngestBatchRequest.java:27`, `IngestHandoffRequest.java:18`                                                                       |
| `IngestPlanRequest`                                                                                                                  | new      | modelled on `IngestHandoffRequest`                                                                                                                             |
| `IngestServiceImpl.ingestBatch`, its `applyBatch` call                                                                               | existing | `B/domain/ingest/service/impl/IngestServiceImpl.java:65`, `:223`                                                                                               |
| `SessionIngestServiceImpl.applyBatch`, `storeHandoffs`                                                                               | existing | `B/domain/session/service/impl/SessionIngestServiceImpl.java:113`, `:183`                                                                                      |
| `SessionHandoffSnapshot`, `SessionHandoffSnapshotRepository.findBySession_Id`, `HandoffStatusParser.statusOf`                        | existing | `B/domain/session/entity/SessionHandoffSnapshot.java:40`; `repository/SessionHandoffSnapshotRepository.java:42`; `helper/HandoffStatusParser.java:34`          |
| `SessionQueryService.getEntityBySessionId`, `getHandoffs`                                                                            | existing | `B/domain/session/service/SessionQueryService.java:87`, `:77`                                                                                                  |
| `SessionQueryService.findFeatureHandoffs`                                                                                            | new      | modelled on `getHandoffs`                                                                                                                                      |
| `Session` (`repositoryId`, `authorId`, `branch`, `ended`, `features`)                                                                | existing | `B/domain/session/entity/Session.java:59`, `:62`, `:83`, `:124`, `:134`                                                                                        |
| `SessionController` (paging, `@RequiredRole`, delete "ADMIN or author")                                                              | existing | `B/domain/session/controller/SessionController.java:79`, `:269`                                                                                                |
| `AuthorQueryService.requireAdminOrSelf`                                                                                              | existing | `B/domain/author/service/AuthorQueryService.java:128`                                                                                                          |
| `ConfigReview` entity, `ConfigReviewDecision`, review endpoint                                                                       | existing | `B/domain/config/review/entity/ConfigReview.java:39`; `enums/ConfigReviewDecision.java:15`; `controller/ConfigReviewController.java:65`                        |
| `ConfigUnifiedDiff.diff(path, old, new)`                                                                                             | existing | `B/domain/config/change/helper/ConfigUnifiedDiff.java:43`                                                                                                      |
| `ConfigSetRepository.findByOrganizationIdAndRepositoryIdAndKindOrderByRootPathAsc`, `ConfigSet.rootPath`                             | existing | `B/domain/config/repository/ConfigSetRepository.java:50`; `entity/ConfigSet.java:69`                                                                           |
| `ConfigSetQueryService.findProjects`                                                                                                 | new      | next to `findWorkspaceSet`, `B/domain/config/service/ConfigSetQueryService.java:60`                                                                            |
| `DeliveryController` (agent token, acks)                                                                                             | existing | `B/domain/config/delivery/controller/DeliveryController.java:67`, `:198`                                                                                       |
| `SecurityConstant.AGENT_TOKEN_PATHS` (`/ingest/**`, `/delivery/**`)                                                                  | existing | `B/core/security/constant/SecurityConstant.java:32`                                                                                                            |
| `RequiredRole`, `ExceptionDomainKayaError`                                                                                           | existing | `B/core/security/annotation/RequiredRole.java:38`; `B/core/exception/enums/ExceptionDomainKayaError.java:12`                                                   |
| `DeliveryQueryService.getRepository(organizationId, remoteUrl)`, `AgentPrincipal`                                                    | existing | `B/domain/config/delivery/service/DeliveryQueryService.java:90`; `B/domain/agent/dto/AgentPrincipal.java:22`                                                   |
| `ExceptionCategory` (SEC 403, NOT 404, VAL 422, STA 409), `ForbiddenException`, `ConflictException`                                  | existing | `B/core/exception/enums/ExceptionCategory.java:39`; `B/core/exception/internal/auth/ForbiddenException.java:18`; `internal/business/ConflictException.java:18` |
| Recorder `MESSAGE_LIMIT = 2000` (too short for a plan, so plans are snapshotted to files), `redact`                                  | existing | `recorder/record.py:53`, `:286`                                                                                                                                |
| `domain/plan/**` (entity, repository, dto, mapper, service, controller, constant)                                                    | new      | modelled on `domain/config/review/**`                                                                                                                          |
| `V1_2_0__Plan_Create_Domain.sql`                                                                                                     | new      | modelled on `V1_1_3__Config_Create_Reviews.sql`                                                                                                                |
| CLI `hookCmd`, `hookInput`, `sessionStart`, `writeSessionContext`, `maxContext`                                                      | existing | `cli/cmd/hook.go:46`, `:86`, `:127`, `:272`, `:26`                                                                                                             |
| CLI `hookEvents`                                                                                                                     | existing | `cli/internal/install/install.go:222`                                                                                                                          |
| CLI `watch`, `watchInterval`, `Checkout.LiveSessions`                                                                                | existing | `cli/cmd/watch.go:60`, `:21`; `cli/internal/delivery/sessions.go:75`                                                                                           |
| CLI `Client.CurrentRelease`, `AckRevocations`                                                                                        | existing | `cli/internal/client/delivery.go:51`, `:108`                                                                                                                   |
| CLI `internal/client/plans.go`, `internal/delivery/inbox.go`                                                                         | new      | modelled on `delivery.go` and `sessions.go`                                                                                                                    |
| Web `NAV_ITEMS`                                                                                                                      | existing | `frontend/components/layout/kaya-sidebar.tsx:42`                                                                                                               |
| Web changes route and list                                                                                                           | existing | `frontend/app/(dashboard)/changes/page.tsx`; `features/changes/components/changes-page.tsx:46`                                                                 |
| Web `ReviewButtons`, `CommentForm`, `FileDiff`, `diffLines`                                                                          | existing | `features/changes/components/review-controls.tsx:139`, `:183`; `diff-view.tsx:30`; `change-data.ts:160`                                                        |
| Web `Markdown`, `parseMarkdown` (no tables today)                                                                                    | existing | `features/pipeline/components/markdown.tsx:93`; `markdown-blocks.ts:24`                                                                                        |
| Web `SessionView`; `parseHandoff`, `handoffSection`; `API_ROUTES`                                                                    | existing | `features/pipeline/components/session-view.tsx:68`; `lib/handoff.ts:48`, `:98`; `lib/api-routes.ts:2`                                                          |
| Web `app/(dashboard)/plans/**`, `features/plans/**`                                                                                  | new      | modelled on the changes route and `features/changes/**`                                                                                                        |

## 4. Technical design

### 4.1 Domain

A new module `B/domain/plan/` (package `com.sensey.kaya.domain.plan`, schema
`plan`), laid out like `domain/config/review/**`. Placement candidates:

- Own module (taken): a SPEC plan spans sessions (its identity is repository and
  path), has its own review lifecycle and outlives a deleted session.
- Inside `domain/session` next to the handoff snapshots: fewer files and one
  ingest service, but it ties versions to one session.
- Inside `domain/config/review`: reuses the review shape, but config reviews
  release signed config, with ADMIN-only approval; plan feedback is a different
  lifecycle.

Interactions: `IngestServiceImpl.ingestBatch` calls
`PlanIngestService.storePlans` after `applyBatch`, in the same transaction; the
plan module reads sessions and feature handoffs through `SessionQueryService`,
approval rights through `AuthorQueryService.requireAdminOrSelf` (OWNER passes:
`B/domain/author/helper/AuthorAccessHelper.java:53` checks `isAtLeast(ADMIN)`),
projects through `ConfigSetQueryService.findProjects`, and diffs through
`ConfigUnifiedDiff.diff`. No other module reads the plan module, except the
session page's Plans panel through the plan API.

### 4.2 Data model

Every entity extends `BaseEntity` (`id`, `created_at`, `updated_at`) with
`@SuperBuilder`, as `ConfigReview` does.

`Plan` (`plan.plans`): `organization_id` UUID, `repository_id` UUID, `kind`
`PlanKind` (SPEC, SESSION), `path` VARCHAR(500) (SPEC only,
repository-relative), `session_id` UUID (SESSION only, the session it belongs
to), `title` VARCHAR(300), `project_set_id` UUID null, `feature` VARCHAR(100)
null (the `handoffs/{feature}/` directory), `review_state` `PlanReviewState`
(OPEN, APPROVED, CHANGES_REQUESTED), `latest_version_number` INT,
`latest_session_id` UUID null and `author_id` UUID (the session and author of
the latest version), `open_feedback_count` INT, `last_changed_at` TIMESTAMPTZ.
The title is the first `# ` heading of a SPEC (else its file name) or the Goal
line of a SESSION plan, cut to 300 characters.

`PlanVersion` (`plan.plan_versions`): `plan_id` UUID, `number` INT (1, 2, ...),
`source` `PlanVersionSource` (FILE, PLAN_MODE, MESSAGE), `session_id` UUID null
(the writing session), `content` TEXT (1 to 200 000 characters),
`content_sha256` CHAR(64), `line_count` INT, `captured_at` TIMESTAMPTZ.

`PlanFeedback` (`plan.plan_feedback`): `plan_id` UUID, `version_id` UUID,
`reviewer_id` UUID (a user), `decision` `PlanFeedbackDecision` (APPROVE,
REQUEST_CHANGES, COMMENT), `comment` VARCHAR(4000) null, `line_start`,
`line_end` INT null, `quote` VARCHAR(500) null, `delivered_at` TIMESTAMPTZ null,
`delivered_session_id` VARCHAR(100) null. `open_feedback_count` counts the rows
of a plan with `delivered_at` null.

### 4.3 Migrations

One file, `V1_2_0__Plan_Create_Domain.sql` (after
`V1_1_8__Config_Create_Projects.sql`; take the next free number if spec 05's
migration lands first), following `flyway-migration-convention`:

1. Schema `plan`. Enums `plan_plan_kind_enum`, `plan_plan_review_state_enum`,
   `plan_plan_version_source_enum`, `plan_plan_feedback_decision_enum` with the
   values above.
2. `plan.plans`: foreign keys `organization_id` to `identity.organizations`
   (CASCADE), `repository_id` to `workspace.repositories` (CASCADE),
   `session_id` to `session.sessions` (CASCADE: a deleted session takes its
   session plan), `latest_session_id` to `session.sessions` (SET NULL),
   `author_id` to `author.authors`, `project_set_id` to `config.config_sets`
   (SET NULL). Check `plans_kind_ck`: SPEC has `path` and no `session_id`,
   SESSION the reverse. Counts default 0, `review_state` default OPEN.
3. `plan.plan_versions`: `plan_id` CASCADE, `session_id` to `session.sessions`
   SET NULL; unique `plan_versions_plan_id_number_uk`; checks `number >= 1`,
   `char_length(content) BETWEEN 1 AND 200000`.
4. `plan.plan_feedback`: `plan_id` and `version_id` CASCADE, `reviewer_id` to
   `identity.users` with no delete rule (as `config_reviews`); checks: lines
   both null or `1 <= line_start <= line_end`; `comment` not null unless
   APPROVE, and 1 to 4000 characters; `delivered_at` and `delivered_session_id`
   both null or both set.
5. Indexes: partial unique `plans_repository_id_path_uk` (kind SPEC) and
   `plans_session_id_uk` (kind SESSION);
   `plans_organization_id_last_changed_at_idx`; one index per other foreign key;
   `plan_feedback_plan_id_created_at_idx`; partial `plan_feedback_pending_idx`
   on `plan_id` where `delivered_at IS NULL`.

### 4.4 API contract

Member endpoints (`PlanController`, new): JWT and `X-Organization-ID`,
`@RequiredRole(OrganizationRole.MEMBER)`, `CustomResponse` through
`ResponseUtil`, `@Operation` and `@ApiResponses` as in `SessionController`. Ids
are UUIDs; a malformed plan or feedback id is 400, as in the other Kaya
controllers
(`GlobalExceptionHandler.handleMethodArgumentTypeMismatchException`); a version
`number` below 1 is 422 `GEN_VAL_001`; a plan, version or feedback outside the
organization is 404 `KAY_NF_001` (`NOT_PLAN_FOUND`, new).

| Method and path                                | Request                                                                                                                                                                                                                                                                                          | Response                                                                                                                                                                                                                                                                                                     |
| ---------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `GET /plans`                                   | `PlanQueryRequest extends QueryRequest` as `SessionQueryRequest` (`B/domain/session/dto/request/SessionQueryRequest.java:32`): `page` (1-based), `amount` (25, max 100), `repositoryId`, `projectId`, `kind`, `reviewState`, `authorId`, `sessionId`, `search` (max 200, matches title and path) | paged `PlanListResponse`: `id`, `kind`, `title`, `path`, `repository` {id, name}, `project` {id, name} or null, `author` {id, name}, `reviewState`, `latestVersionNumber`, `openFeedbackCount`, `lastChangedAt`                                                                                              |
| `GET /plans/filters`                           | none                                                                                                                                                                                                                                                                                             | `repositories`, `projects`, `authors` that have plans: lists of {id, name}                                                                                                                                                                                                                                   |
| `GET /plans/{planId}`                          | none                                                                                                                                                                                                                                                                                             | `PlanDetailResponse`: the list fields, `latestSession` {sessionId, title, ended, branch} or null, `versions` [{number, source, sessionId, capturedAt, lineCount}], `feedback` [`PlanFeedbackResponse`], `handoffs` [{path, status, unitNumber, content}] of the feature, `planningHandoffPath`, `canApprove` |
| `GET /plans/{planId}/versions/{number}`        | none                                                                                                                                                                                                                                                                                             | {`number`, `content`}                                                                                                                                                                                                                                                                                        |
| `GET /plans/{planId}/versions/{number}/diff`   | none                                                                                                                                                                                                                                                                                             | {`number`, `diff`}: unified diff against version `number - 1`, empty for version 1                                                                                                                                                                                                                           |
| `POST /plans/{planId}/feedback`                | `PlanFeedbackRequest`                                                                                                                                                                                                                                                                            | `PlanFeedbackResponse`: `id`, `versionNumber`, `reviewer` {id, name}, `decision`, `comment`, `lineStart`, `lineEnd`, `quote`, `createdAt`, `deliveredAt`, `deliveredSessionId`                                                                                                                               |
| `DELETE /plans/{planId}/feedback/{feedbackId}` | none                                                                                                                                                                                                                                                                                             | none; withdraws                                                                                                                                                                                                                                                                                              |

```java
public record PlanFeedbackRequest(
        @NotNull @Min(1) Integer versionNumber,
        @NotNull PlanFeedbackDecision decision,
        @Size(min = 1, max = 4000) String comment,          // required unless APPROVE
        @Min(1) Integer lineStart, @Min(1) Integer lineEnd,  // both or neither
        @Size(max = 500) String quote) { }
```

Feedback errors: 422 `GEN_VAL_001` for the bean rules above; 422 `KAY_PLN_002`
(VAL, new) for a missing comment on REQUEST_CHANGES or COMMENT, one line bound
without the other, `lineEnd` before `lineStart`, or a range past the version's
`line_count`; 409 `KAY_PLN_001` (STA, new) for APPROVE or REQUEST_CHANGES on a
version that is not the latest; 403 `GEN_SEC_003` for APPROVE by a member who is
neither the plan's author nor ADMIN or OWNER. Withdraw errors: 403 `GEN_SEC_003`
for another member's feedback; 409 `KAY_PLN_003` (STA, new) once it is
delivered.

Agent endpoints (`PlanDeliveryController`, new,
`@RequestMapping("/delivery/plan-feedback")`): agent token, covered by
`SecurityConstant.AGENT_TOKEN_PATHS`; the principal's organization and author
scope every query.

| Method and path                     | Request                                                                                                                                                                                                                                                         | Response                                                                                                                                                                                                                                        |
| ----------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `GET /delivery/plan-feedback`       | `sessionId` (repeated, 1 to 20): pending feedback of plans whose latest version came from these sessions of the token's author; or `remoteUrl` and `branch`: pending feedback of plans whose latest session is ended, of the same author, repository and branch | [`PlanFeedbackDeliveryResponse`]: `id`, `planId`, `planUrl`, `kind`, `path`, `versionNumber`, `latestVersionNumber`, `sessionId`, `reviewerName`, `decision`, `comment`, `lineStart`, `lineEnd`, `quote`, `createdAt`; oldest first, at most 50 |
| `POST /delivery/plan-feedback/acks` | {`sessionId` (session id pattern), `feedbackIds` (1 to 50 UUIDs)}                                                                                                                                                                                               | {`acknowledged`}: rows newly marked; delivered rows and ids outside the token's author are skipped                                                                                                                                              |

Neither or both query forms is 400 `KAY_PLN_004` (API, new); an unknown
`remoteUrl` answers an empty list. `IngestBatchRequest` gains `plans`:
`List<@Valid IngestPlanRequest>`, at most 20 (`IngestConstant.PLANS_MAX`, new).
`IngestPlanRequest`: `kind` (`PlanKind`), `source` (`PlanVersionSource`), `path`
(repository-relative, max 500, required for SPEC, null for SESSION, must match
the plans pattern of PL1), `content` (1 to 200 000), `capturedAt`; a violation
is answered as an invalid `IngestHandoffRequest` entry is today.

### 4.5 Service layer

Interface and impl pairs per `service-cqrs`; commands `@Transactional`, queries
`@Transactional(readOnly = true)`; logs through `LoggingService` with constants
per `constant-usage-convention`.

```java
// PlanIngestService: @Transactional(propagation = Propagation.MANDATORY), joined from ingestBatch
void storePlans(Session session, List<IngestPlanRequest> plans);
void linkFeature(Session session, String handoffPath, String content);   // called from storeHandoffs
// PlanService
PlanFeedbackResponse giveFeedback(User user, UUID organizationId, UUID planId, PlanFeedbackRequest request);
void withdrawFeedback(User user, UUID organizationId, UUID planId, UUID feedbackId);
int acknowledge(AgentPrincipal principal, String sessionId, List<UUID> feedbackIds);
```

`PlanQueryService`: `listPlans(organizationId, request)` as a `Page`,
`getFilters`, `getPlan(user, organizationId, planId)`, `getVersion`, `getDiff`,
`pendingForSessions(principal, sessionIds)`,
`pendingForBranch(principal, remoteUrl, branch)`,
`getEntity(organizationId, planId)`.

Rules:

- `storePlans`: a SPEC plan is found by (repository of the session, path), a
  SESSION plan by the session; it is created when missing. A content whose
  SHA-256 equals the latest version's adds nothing. Otherwise the next version
  is stored with the session, and the plan's title, `latest_version_number`,
  `latest_session_id`, `author_id` (the session's author), `last_changed_at` are
  set and `review_state` returns to OPEN. The project is the PROJECT set of the
  repository whose `rootPath` is a prefix of the path, else whose `rootPath`
  ends in the directory after `plans/` (`docs/private/plans/kaya/` to
  `projects/kaya`), else none; a SESSION plan has none. Then the session's
  handoff snapshots named `<feature>/planning.md` are checked as in
  `linkFeature`.
- `linkFeature`: when a `planning.md` snapshot contains the path of a SPEC plan
  of the same repository, that plan's `feature` becomes the snapshot's
  directory; the newest snapshot wins.
- `giveFeedback`: checks in order the version, the PL6 rules (4.4 codes), and
  for APPROVE `requireAdminOrSelf(user, organizationId, plan.authorId)`. APPROVE
  sets APPROVED, REQUEST_CHANGES sets CHANGES_REQUESTED, COMMENT leaves the
  state; `open_feedback_count` is raised. A plan row is locked
  (`PESSIMISTIC_WRITE`) while a version or a decision is stored, so a decision
  never lands on a version that was just replaced.
- `withdrawFeedback`: own row, not delivered; deletes it, lowers the count, and
  when it was the deciding row the state is recomputed from the remaining
  decisions on the latest version.
- `getPlan`: `handoffs` are the newest snapshot per path below
  `handoffs/{feature}/` among the repository's sessions
  (`SessionQueryService.findFeatureHandoffs(organizationId, repositoryId, feature)`,
  new), with `HandoffStatusParser.statusOf` and `unitNumber` from the first
  `unit <N>` in "Scope given"; `canApprove` uses the same check as APPROVE
  without throwing.
- `pendingForSessions` and `pendingForBranch` read only rows with `delivered_at`
  null whose plan's `author_id` is the principal's author; the branch form takes
  the repository from `DeliveryQueryService.getRepository` and needs the latest
  session `ended`.
- `acknowledge` sets `delivered_at` now and `delivered_session_id` on the rows
  still pending, in one update, so a second call changes nothing; it lowers
  `open_feedback_count` by the rows changed.

### 4.6 Client

Recorder (`recorder/`):

- `.claude/settings.json:37`: the PreToolUse matcher becomes
  `Agent|Skill|ExitPlanMode`. `event_from` records `tool_name` only for
  `ExitPlanMode`; the plan text never goes into the event stream.
- `record.snapshot_plan(run_dir, hook_input, root, cwd)` (new), called from
  `_apply`: for a PostToolUse of `WRITE_TOOLS` whose path (worktree prefix
  removed with `_WORKTREE`) matches the PL1 pattern, and for each such path in a
  successful Bash command, it copies the file to `<run_dir>/plans/files/<path>`
  with `_copy_snapshot`; for PreToolUse `ExitPlanMode` it writes
  `tool_input.plan` to `<run_dir>/plans/session.md` with `source` PLAN_MODE in
  `plans/session.json`; for Stop it does the same with the full
  `last_assistant_message` and source MESSAGE when the message has a `Goal` and
  a `Steps` line. Over 200 000 characters: logged with `warn`, not copied.
- `sender.changed_plans` (new) and `build_batch` (`recorder/sender.py:369`):
  unacknowledged snapshots go into `plans`, redacted before they are cut, as
  `_handoff` does; acknowledged ones are tracked like handoffs.

CLI (`cli/`):

- `internal/client/plans.go` (new): `PendingFeedback(ctx, sessionIDs)`,
  `PendingFeedbackForBranch(ctx, remoteURL, branch)`,
  `AckFeedback(ctx, sessionID, ids)`, through `doJSON` as `AckRevocations` does.
- `internal/delivery/inbox.go` (new): `.kaya/inbox/<sessionId>.json` (pending
  items) and `.kaya/inbox/<sessionId>.acks.json` (delivered ids), written
  atomically.
- `watch`: besides the 60 s revocation poll, every 15 s it fetches pending
  feedback of the live sessions into their inbox files and posts the ack files,
  deleting each after a 2xx.
- `hook`: new subcommands `user-prompt-submit` and `post-tool-use` read the
  session's inbox (no network), print
  `{"hookSpecificOutput": {"hookEventName": ..., "additionalContext": ...}}`
  within `maxContext`, and move the delivered ids to the ack file.
  `sessionStart` additionally calls `PendingFeedbackForBranch` with a 2 s
  timeout, adds the items after the release lines, and acks them with the new
  session id.
- `install.hookEvents`: add `UserPromptSubmit` `user-prompt-submit` (5 s) and
  `PostToolUse` `post-tool-use` (matcher `*`, 5 s).
- Context text, per item:
  `Kaya plan feedback from <reviewer> on <path | the plan of this session> version <N>: <Approved | Changes requested | Comment>. Lines <a>-<b>: "<quote>". <comment>`;
  first line `Revise the plan before you implement.` when any item requests
  changes.

Web (`frontend/`), files per `frontend-new-feature-checklist`, modelled on
`features/changes/**`:

- `NAV_ITEMS`: `{ label: "Plans", href: "/plans", icon: ClipboardList }` after
  the sessions entry. `API_ROUTES.plans` with `list`, `filters`, `detail(id)`,
  `version(id, n)`, `diff(id, n)`, `feedback(id)`, `feedbackItem(id, fid)`.
- `app/(dashboard)/plans/page.tsx` and `plans/[planId]/page.tsx`;
  `features/plans/{api,schemas,hooks,components}`. Schemas mirror 4.4 with the
  same limits (comment 1 to 4000, quote 500, both line bounds or none).
- `plans-page.tsx`: table with columns Title (link), Kind ("Spec" or "Session
  plan"), Repository, Project, Author, Review ("Open", "Approved", "Changes
  requested"), Versions, Open feedback, Updated; filters through
  `use-plan-filter-params.ts` (nuqs), defaults `reviewState=OPEN`, page size 25;
  empty text of PL3.
- `plan-detail.tsx`: header (title, path, state, session link, `canApprove`),
  version select, tabs Outline, Document, Changes; feedback list with "Waiting
  for the session" or "Delivered to <session> at <time>" and "Withdraw" on own
  undelivered rows; side panel for the planning handoff with `parseHandoff`.
  `plan-outline.ts` parses the sections of PL4 itself (the units table too, as
  `parseMarkdown` has no tables); `plan-document.tsx` shows numbered source
  lines with gutter selection; Changes reuses `FileDiff`; the form reuses
  `ReviewButtons` and `CommentForm` where their props fit.
- Session page: a `session-plans-panel.tsx` in `SessionView` lists
  `GET /plans?sessionId=`.
- Hooks: `usePlans`, `usePlanFilters`, `usePlan` (`refetchInterval` 10 s while
  open), `usePlanVersion`, `usePlanDiff`, `useGiveFeedback`,
  `useWithdrawFeedback`; mutations invalidate the plan detail and the plan lists
  and toast on error.

## 5. Access control

| Operation                                 | Who                                                                               | Backend                                                                      | Web                                                       |
| ----------------------------------------- | --------------------------------------------------------------------------------- | ---------------------------------------------------------------------------- | --------------------------------------------------------- |
| List, filters, read a plan, version, diff | MEMBER                                                                            | `@RequiredRole(MEMBER)`; every query by `organization_id`                    | Plans entry for every member                              |
| Comment, request changes                  | MEMBER                                                                            | as above                                                                     | form for every member; Approve hidden unless `canApprove` |
| Approve                                   | the plan's author (the user of the latest version's session author), ADMIN, OWNER | `requireAdminOrSelf(user, organizationId, plan.authorId)`, 403 `GEN_SEC_003` | button shown only with `canApprove`                       |
| Withdraw feedback                         | its reviewer, while not delivered                                                 | reviewer check, 403 `GEN_SEC_003`; 409 `KAY_PLN_003`                         | "Withdraw" only on own pending rows                       |
| Fetch and ack pending feedback            | agent token of the plan's author                                                  | `AGENT_TOKEN_PATHS`; `organizationId` and `authorId` of `AgentPrincipal`     | none                                                      |
| Ingest plans                              | recorder token of the session                                                     | existing ingest rules of `IngestServiceImpl.ingestBatch`                     | none                                                      |

Tenants: a plan's `organization_id` is the session's; a lookup of another
organization's id answers 404 `KAY_NF_001`, never 403, so ids leak nothing.

## 6. Integration points

- Ingest and session: `IngestBatchRequest.plans`,
  `IngestServiceImpl.ingestBatch`, `SessionIngestServiceImpl.storeHandoffs`
  (calls `linkFeature`), `SessionQueryService.findFeatureHandoffs` (new).
- Config: `ConfigSetQueryService.findProjects(organizationId, repositoryId)`
  (new, wraps `findByOrganizationIdAndRepositoryIdAndKindOrderByRootPathAsc`
  with PROJECT), `ConfigUnifiedDiff.diff`; `DeliveryQueryService.getRepository`.
- Author: `AuthorQueryService.requireAdminOrSelf`.
- Spec 05: the plan module builds on the renamed session domain (`Session`,
  `session.sessions`, `SessionView`, `SessionQueryService`).
- Claude Code: the hook events PreToolUse (ExitPlanMode), PostToolUse, Stop,
  UserPromptSubmit and SessionStart, and `additionalContext` output; no external
  service.
- Follow-up outside this repo: the managed `plan`, `implement` and
  `orchestrator` commands (`senseylabs/agents`) could tell Claude to wait for a
  Kaya approval; this spec does not change them.

## 7. Implementation units

| #   | Role                        | Scope    | Unit                                                                                                    | Files or modules                                                                                                                    | Needs unit |
| --- | --------------------------- | -------- | ------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------- | ---------- |
| 1   | database-migration-engineer | backend  | Plan schema                                                                                             | `V1_2_0__Plan_Create_Domain.sql`                                                                                                    | none       |
| 2   | coding-engineer             | backend  | Entities, enums, repositories, constants, error codes                                                   | `domain/plan/{entity,enums,repository,constant}`, `ExceptionDomainKayaError`                                                        | 1          |
| 3   | coding-engineer             | backend  | Ingest: `IngestPlanRequest`, `PlanIngestService`, calls from ingest and `storeHandoffs`, `findProjects` | `domain/ingest/**`, `domain/plan/service/**`, `SessionIngestServiceImpl`, `ConfigSetQueryService`                                   | 2          |
| 4   | coding-engineer             | backend  | Member API: `PlanQueryService`, `PlanService`, mapper, `PlanController`, `findFeatureHandoffs`          | `domain/plan/{dto,mapper,service,controller}`, `SessionQueryService`                                                                | 2          |
| 5   | coding-engineer             | backend  | Agent API: `PlanDeliveryController`, pending and ack                                                    | `domain/plan/controller`, `domain/plan/service`                                                                                     | 4          |
| 6   | coding-engineer             | recorder | `snapshot_plan`, `changed_plans`, settings matcher                                                      | `recorder/record.py`, `recorder/sender.py`, `.claude/settings.json`                                                                 | 3          |
| 7   | coding-engineer             | CLI      | client, inbox, watch tick, two hooks, SessionStart, install                                             | `cli/internal/client/plans.go`, `cli/internal/delivery/inbox.go`, `cli/cmd/{watch,hook}.go`, `cli/internal/install/install.go`      | 5          |
| 8   | coding-engineer             | frontend | Routes, schemas, API, hooks, Plans list, sidebar                                                        | `features/plans/{api,schemas,hooks}`, `plans-page.tsx`, `app/(dashboard)/plans/page.tsx`, `kaya-sidebar.tsx`, `lib/api-routes.ts`   | 4          |
| 9   | coding-engineer             | frontend | Plan page (outline, document, changes, feedback, handoff panel), session panel                          | `plans/[planId]/page.tsx`, `plan-detail.tsx`, `plan-outline.ts`, `plan-document.tsx`, `session-plans-panel.tsx`, `session-view.tsx` | 8          |
| 10  | full-stack-test-engineer    | all      | Tests of section 8                                                                                      | test directories of each scope                                                                                                      | 3 to 9     |

Units 6, 7 and 8 can run in parallel once their backend unit is done.

## 8. Testing

Backend (unit, Mockito; integration against the test database as the existing
ingest and review tests do):

- Ingest: a new SPEC path creates plan and version 1; the same content adds
  nothing; changed content adds version 2 and resets an APPROVED plan to OPEN; a
  second session's write joins the same plan; SESSION plan per session; project
  by `rootPath` prefix and by the `plans/<dir>/` rule; `linkFeature` from a
  planning handoff in the same and in a later batch; a path outside the pattern
  is refused.
- Feedback: each decision and the resulting state; APPROVE by author, ADMIN,
  OWNER allowed and by another MEMBER 403; APPROVE or REQUEST_CHANGES on an old
  version 409 `KAY_PLN_001`, COMMENT there accepted; line range rules 422
  `KAY_PLN_002`; withdraw own pending, another's 403, delivered 409
  `KAY_PLN_003`, state recomputed.
- Delivery: pending by sessions only for the token's author; by branch only for
  ended latest sessions; ack idempotent and lowers the count once; other
  organization 404 on member endpoints and empty on agent endpoints.
- Migration: checks reject SPEC without path and lines with
  `line_end < line_start`.

Recorder (pytest): Write, Edit and Bash captures; worktree prefix; ExitPlanMode
and Stop plan format detection; a non-plan Stop message ignored; 200 000 limit;
redaction before the cut; batches carry and acknowledge plans.

CLI (`make test`): inbox write and read; hook output JSON per event and the
`maxContext` cut with "and N more"; no inbox gives no output; ack file posted
and deleted; SessionStart offline adds nothing and returns quickly; `install`
registers the two new events.

Web (`bun test`): schemas and limits; outline parsing of a core-format plan and
of this spec's sections 2, 7 and 10; gutter selection to a line range; Approve
hidden without `canApprove`; mutation invalidation.

Manual, per story: PL1 write a spec with `/plan` in a worktree and see it under
Plans in 5 s; PL2 leave plan mode and see a Session plan; PL3 filters and empty
state as a MEMBER; PL4 outline of a spec with units and handoffs; PL5 edit the
spec and compare versions; PL6 as MEMBER request changes on lines, as another
MEMBER try Approve (hidden, 403 by API), as the author approve; PL7 the running
session receives the feedback with its next tool call and the page shows
"Delivered"; PL8 end the session, start a new one on the branch and see the
feedback; PL9 a member of another organization gets 404.

## 9. Non-functional requirements

- Performance: hooks that read the inbox finish in under 50 ms with no network;
  SessionStart's feedback call is bounded at 2 s; the list query uses
  `plans_organization_id_last_changed_at_idx` and stays under 200 ms at 10 000
  plans.
- Security: plan text is redacted by the recorder before it leaves the machine,
  as handoffs are; the web renders plan text as text only (no
  `dangerouslySetInnerHTML`); agent endpoints scope by token author; content is
  capped at 200 000 characters and 20 plans per batch.
- Context: the injected text names its source as Kaya and the reviewer, so
  Claude can weigh it as review input, not as an instruction from the user.
- Accessibility: gutter selection also works with the keyboard (focus a line,
  Shift and arrows, Enter opens the form); states use text, not only color.

## 10. Open questions and risks

No open questions. Assumptions to confirm:

1. How feedback reaches Claude: as hook `additionalContext` on the session's
   next prompt or tool call (taken). Alternative: block `ExitPlanMode` in
   PreToolUse until Kaya has a decision; that gives a true approval gate but
   stalls the terminal and needs the managed commands changed.
2. What counts as a plan: spec files under `docs/**/plans/**.md` and plan-mode
   or core-format messages; the planning handoff is shown on its spec (taken).
   Alternative: also treat every `planning.md` as a plan of its own, which
   doubles entries for one feature.
3. Versions: every captured content change is a version, and a new version
   reopens review (taken). Alternative: versions only at an explicit "submit for
   review", which needs a CLI command and loses the trail between submits.
4. Who may approve: the plan's author, ADMIN or OWNER (taken). Alternative:
   ADMIN only, as config reviews; that blocks a developer approving their own
   plan, which is the common case.
5. Delivery needs a checkout delivered by Kaya (`kaya watch` and the CLI hooks
   run only there). Feedback on plans from other checkouts stays visible in Kaya
   but is not injected. Alternative: register the hooks for every checkout with
   a registered machine.
6. SessionStart calls Kaya for feedback even under `kaya launch`, which today
   keeps that hook offline (bounded at 2 s).

Risks:

- The migration number may collide with spec 05's migration; take the next free
  number at implementation.
- Detecting a plan in a Stop message by its Goal and Steps lines can catch
  ordinary answers in that format; the effect is an extra Session plan, not a
  lost one.

## Revisions

- 2026-10-25: adopted the chat to session rename of `05_KAYA_SESSIONS.md` as
  built (its A4 and A5). The cited code now names `B/domain/session/**`,
  `Session`, `SessionHandoffSnapshot`, `SessionIngestServiceImpl`,
  `SessionQueryService`, `SessionQueryRequest`, `SessionController`,
  `SessionView` (`session-view.tsx`) and the table `session.sessions`; the plan
  columns `plans.session_chat_id`, `plans.latest_chat_id` and
  `plan_versions.chat_id` became `session_id`, `latest_session_id` and
  `session_id`, and the index `plans_session_chat_id_uk` became
  `plans_session_id_uk`. The risk that both specs rename the same names is
  resolved and was removed from the risks.
- 2026-10-25: 4.4 now answers a malformed plan or feedback path id with 400
  instead of 422 `GEN_VAL_001`, the status every other Kaya controller gives a
  path variable that does not convert. 422 `GEN_VAL_001` stays for a version
  `number` below 1.
