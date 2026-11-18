import { z } from "zod";
import { boardListResponseSchema } from "@/features/boards/schemas";
import { TicketRelocationActionSchema } from "@/features/tickets/schemas";
import { calendarDaysBetween } from "../lib/sprint-recurrence";

// ── Enum Schemas ───────────────────────────────────────────────────

export const SprintStatusSchema = z.enum(["PLANNED", "ACTIVE", "COMPLETED"]);
export type SprintStatus = z.infer<typeof SprintStatusSchema>;
export const SprintStatus = SprintStatusSchema.enum;

export const SprintRecurrenceTicketActionSchema = z.enum([
  "MOVE_TO_NEXT_SPRINT",
  "MOVE_TO_BACKLOG",
]);
export type SprintRecurrenceTicketAction = z.infer<
  typeof SprintRecurrenceTicketActionSchema
>;
export const SprintRecurrenceTicketAction =
  SprintRecurrenceTicketActionSchema.enum;

export const SprintRecurrenceCreationModeSchema = z.enum([
  "AT_END",
  "DAYS_BEFORE",
]);
export type SprintRecurrenceCreationMode = z.infer<
  typeof SprintRecurrenceCreationModeSchema
>;
export const SprintRecurrenceCreationMode =
  SprintRecurrenceCreationModeSchema.enum;

export const SprintRecurrenceRunStatusSchema = z.enum([
  "OK",
  "BLOCKED",
  "FAILED",
]);
export type SprintRecurrenceRunStatus = z.infer<
  typeof SprintRecurrenceRunStatusSchema
>;
export const SprintRecurrenceRunStatus = SprintRecurrenceRunStatusSchema.enum;

/** Sprint length limits the server enforces on an automated sprint. */
export const SPRINT_RECURRENCE_MIN_LENGTH_DAYS = 1;
export const SPRINT_RECURRENCE_MAX_LENGTH_DAYS = 365;

/**
 * Longest series name the server accepts, so "{name} {round}" still fits the
 * 255-character sprint name.
 */
export const SPRINT_SERIES_NAME_MAX_LENGTH = 240;

/** Most child boards the server links in one sprint create. */
export const SPRINT_MAX_CHILD_BOARDS = 50;

// ── Request Schemas ─────────────────────────────────────────────────

/**
 * Full replace of the settings of a sprint's series. It carries no name: the
 * series name comes from the sprint name, or from `seriesName` on a sprint
 * update.
 */
export const sprintSeriesRequestSchema = z.object({
  enabled: z.boolean(),
  lengthDays: z.number().int(),
  unfinishedTicketAction: SprintRecurrenceTicketActionSchema,
  creationMode: SprintRecurrenceCreationModeSchema,
  createDaysBefore: z.number().int().nullable().optional(),
});

export const sprintCreateRequestSchema = z.object({
  name: z.string().min(1),
  goal: z.string().optional(),
  startDate: z.string().optional(),
  endDate: z.string().optional(),
  /**
   * With `enabled` true, `name` is the series name and the sprint is named
   * "{name} 1". Omitted or disabled creates no series.
   */
  series: sprintSeriesRequestSchema.optional(),
  /**
   * Descendant boards that get the new sprint too. Omitted or empty links
   * only the board the sprint is created on.
   */
  childBoardIds: z.array(z.string()).max(SPRINT_MAX_CHILD_BOARDS).optional(),
  /**
   * True closes every active sprint on the new sprint's boards, moves their
   * unfinished tickets into it and starts it. Omitted or false only plans it.
   */
  takeOver: z.boolean().optional(),
});

export const sprintUpdateRequestSchema = z.object({
  name: z.string().optional(),
  goal: z.string().optional(),
  startDate: z.string().optional(),
  endDate: z.string().optional(),
  /**
   * Renames the series of a sprint in an enabled series, and with it every
   * unfinished sprint of the series. `name` is then omitted.
   */
  seriesName: z.string().optional(),
});

export const sprintBulkLinkRequestSchema = z.object({
  boardIds: z.array(z.string()).min(1).max(50),
});

export const sprintUnlinkTransferRequestSchema = z.object({
  boardId: z.string(),
  sprintId: z.string().optional(),
  backlogId: z.string().optional(),
});

export const sprintUnlinkRequestSchema = z.object({
  transfer: sprintUnlinkTransferRequestSchema.optional(),
  deleteTickets: z.boolean().optional(),
});

export const sprintCompleteRequestSchema = z.object({
  ticketAction: TicketRelocationActionSchema,
  targetSprintId: z.string().optional(),
  targetBoardId: z.string().optional(),
  /**
   * Moves the unfinished tickets into the next round of the sprint's series,
   * on their own boards. Needs `MOVE_TO_SPRINT` with no target sprint or
   * board, and a sprint of an enabled series; else 422 `KAI_VAL_061`.
   */
  moveToNextSprint: z.boolean().optional(),
});
export type SprintCompleteRequest = z.infer<typeof sprintCompleteRequestSchema>;

/** Body of a sprint start. Omitted or `takeOver` false starts only when nothing blocks. */
export const sprintStartRequestSchema = z.object({
  takeOver: z.boolean().optional(),
});
export type SprintStartRequest = z.infer<typeof sprintStartRequestSchema>;

/**
 * The complete dialog's choice for unfinished tickets. `MOVE_TO_NEXT_SPRINT`
 * exists only in the form: it is sent as `MOVE_TO_SPRINT` with
 * `moveToNextSprint`, since the server picks the next round itself.
 */
export const CompleteSprintTicketActionSchema = z.enum([
  ...TicketRelocationActionSchema.options,
  "MOVE_TO_NEXT_SPRINT",
]);
export type CompleteSprintTicketAction = z.infer<
  typeof CompleteSprintTicketActionSchema
>;

export const completeSprintFormSchema = z
  .object({
    ticketAction: CompleteSprintTicketActionSchema,
    targetSprintId: z.string().optional(),
    targetBoardId: z.string().optional(),
  })
  .superRefine((data, ctx) => {
    if (data.ticketAction === "MOVE_TO_SPRINT" && !data.targetSprintId) {
      ctx.addIssue({
        code: "custom",
        path: ["targetSprintId"],
        message: "required",
      });
    }
  });
export type CompleteSprintFormValues = z.infer<typeof completeSprintFormSchema>;

// ── Response Schemas ────────────────────────────────────────────────

export const sprintSeriesResponseSchema = z.object({
  id: z.string(),
  name: z.string(),
  /** The round the series gives its next sprint. */
  nextRound: z.number(),
  /** "{name} {nextRound}", e.g. "Atasprint 2". */
  nextSprintName: z.string(),
  enabled: z.boolean(),
  lengthDays: z.number(),
  unfinishedTicketAction: SprintRecurrenceTicketActionSchema,
  creationMode: SprintRecurrenceCreationModeSchema,
  createDaysBefore: z.number().nullable().optional(),
  nextRolloverAt: z.string().nullable().optional(),
  nextCreationAt: z.string().nullable().optional(),
  lastRunAt: z.string().nullable().optional(),
  lastRunStatus: SprintRecurrenceRunStatusSchema.nullable().optional(),
  lastRunMessage: z.string().nullable().optional(),
  timeZone: z.string(),
});

export const sprintDetailResponseSchema = z.object({
  id: z.string(),
  createdAt: z.string(),
  updatedAt: z.string(),
  name: z.string(),
  goal: z.string().optional(),
  startDate: z.string().optional(),
  endDate: z.string().optional(),
  status: SprintStatusSchema,
  /** The sprint's round in its series; null outside any series. */
  round: z.number().nullable().optional(),
  boards: z.array(boardListResponseSchema),
  /** Null when the sprint belongs to no series. */
  series: sprintSeriesResponseSchema.nullable().optional(),
});

export const sprintListResponseSchema = z.object({
  id: z.string(),
  name: z.string(),
  /**
   * URL segment of the sprint on the board the list was read for. A sprint
   * linked to several boards has a slug on each, so it is never compared
   * across boards.
   */
  slug: z.string(),
  status: SprintStatusSchema,
  createdAt: z.string(),
  startDate: z.string().optional(),
  endDate: z.string().optional(),
  /**
   * Set on an ACTIVE sprint whose closing leaves its repeating series nothing
   * to run, which switches the series off.
   */
  endsSeriesName: z.string().nullable().optional(),
});

/**
 * A descendant board the new sprint can be linked to. The list comes
 * depth-first, each parent before its children. `parentBoardId` is null when
 * the user is not a member of the parent board; a direct child always keeps
 * it. A non-null `parentBoardId` can still name a board that is not in the
 * list, because boards the user is not a member of are left out.
 */
export const sprintChildBoardResponseSchema = z.object({
  id: z.string(),
  name: z.string(),
  prefix: z.string(),
  color: z.string().nullable().optional(),
  parentBoardId: z.string().nullable(),
  /** 1 for a direct child. */
  depth: z.number(),
  /** True when the user is ADMIN or OWNER of this board. */
  canLink: z.boolean(),
  activeSprintId: z.string().nullable().optional(),
  activeSprintName: z.string().nullable().optional(),
  /** The repeating series that stops when the active sprint is closed. */
  activeSprintEndsSeriesName: z.string().nullable().optional(),
});

/**
 * One board that holds an active sprint blocking the start of another sprint.
 * A blocking sprint linked to several boards gives one row per board the user
 * is a member of.
 */
export const sprintStartConflictResponseSchema = z.object({
  boardId: z.string(),
  boardName: z.string(),
  boardPrefix: z.string(),
  sprintId: z.string(),
  sprintName: z.string(),
  /** Tickets of the blocking sprint on this board that are not DONE. */
  unfinishedTicketCount: z.number(),
  /** True when the board is not linked to the sprint being started. */
  movesToBacklog: z.boolean(),
  /** True when the user is ADMIN or OWNER of this board. */
  canManage: z.boolean(),
  /**
   * The repeating series that stops when the blocking sprint is closed,
   * because it has no other running or planned sprint.
   */
  endsSeriesName: z.string().nullable().optional(),
});

/**
 * What a start would close. Empty `conflicts` means a plain start works.
 * `canTakeOver` also covers boards the user cannot see, so it can be false
 * while every listed row has `canManage`.
 */
export const sprintStartPreviewResponseSchema = z.object({
  canTakeOver: z.boolean(),
  conflicts: z.array(sprintStartConflictResponseSchema),
});

// ── Form Schemas ────────────────────────────────────────────────────

/** The automation fields shared by the create dialog and the Automation tab. */
export const sprintRecurrenceFieldsSchema = z.object({
  enabled: z.boolean(),
  /**
   * Length of each new sprint of the series. A number `<input>` like
   * `createDaysBefore` below, so an empty field becomes null and reaches the
   * range check.
   */
  lengthDays: z
    .preprocess((value: number | null | undefined) => {
      const text = value == null ? "" : String(value).trim();
      return text === "" ? null : Number(text);
    }, z.number().int("sprint.recurrence.errors.seriesLengthRange").nullable())
    .optional(),
  unfinishedTicketAction: SprintRecurrenceTicketActionSchema,
  creationMode: SprintRecurrenceCreationModeSchema,
  /**
   * Edited in a number `<input>`, which reports a string at runtime although
   * the form types it as a number (`ObaForm` needs the same input and
   * output type). An empty field becomes null, so it reaches the "required"
   * check instead of failing as a type error. The message is an i18n key,
   * resolved by `FormMessage`.
   */
  createDaysBefore: z
    .preprocess((value: number | null | undefined) => {
      const text = value == null ? "" : String(value).trim();
      return text === "" ? null : Number(text);
    }, z.number().int("sprint.recurrence.errors.daysBeforeRange").nullable())
    .optional(),
});
export type SprintRecurrenceFieldsValues = z.infer<
  typeof sprintRecurrenceFieldsSchema
>;

export const DEFAULT_SPRINT_RECURRENCE_FIELDS: SprintRecurrenceFieldsValues = {
  enabled: false,
  lengthDays: null,
  unfinishedTicketAction: "MOVE_TO_NEXT_SPRINT",
  creationMode: "AT_END",
  createDaysBefore: null,
};

/**
 * Mirrors the server checks on an enabled request, so the form shows the
 * error on the field before the request is sent: the sprint's own dates must
 * span 1-365 days, the series length must be 1-365 days, and "days before"
 * must be 1..series length-1. A disabled request is never checked, as on the
 * server.
 *
 * `datesPath` is where a length error is shown; `lengthDays` is null when a
 * date is missing.
 */
function refineRecurrence(
  recurrence: SprintRecurrenceFieldsValues,
  lengthDays: number | null,
  ctx: z.RefinementCtx,
  datesPath: (string | number)[],
) {
  if (!recurrence.enabled) return;

  if (
    lengthDays === null ||
    lengthDays < SPRINT_RECURRENCE_MIN_LENGTH_DAYS ||
    lengthDays > SPRINT_RECURRENCE_MAX_LENGTH_DAYS
  ) {
    ctx.addIssue({
      code: "custom",
      path: datesPath,
      message: "sprint.recurrence.errors.lengthRange",
    });
    return;
  }

  const seriesLength = recurrence.lengthDays;
  if (
    seriesLength === null ||
    seriesLength === undefined ||
    seriesLength < SPRINT_RECURRENCE_MIN_LENGTH_DAYS ||
    seriesLength > SPRINT_RECURRENCE_MAX_LENGTH_DAYS
  ) {
    ctx.addIssue({
      code: "custom",
      path: ["recurrence", "lengthDays"],
      message: "sprint.recurrence.errors.seriesLengthRange",
    });
    return;
  }

  if (recurrence.creationMode !== "DAYS_BEFORE") return;

  const daysBefore = recurrence.createDaysBefore;
  if (daysBefore === null || daysBefore === undefined) {
    ctx.addIssue({
      code: "custom",
      path: ["recurrence", "createDaysBefore"],
      message: "sprint.recurrence.errors.daysBeforeRequired",
    });
  } else if (daysBefore < 1 || daysBefore > seriesLength - 1) {
    ctx.addIssue({
      code: "custom",
      path: ["recurrence", "createDaysBefore"],
      message: "sprint.recurrence.errors.daysBeforeRange",
    });
  }
}

export const createSprintFormSchema = z
  .object({
    name: z.string().min(1, "sprint.nameRequired"),
    goal: z.string().optional(),
    startDate: z.date(),
    endDate: z.date(),
    recurrence: sprintRecurrenceFieldsSchema,
    childBoardIds: z
      .array(z.string())
      .max(SPRINT_MAX_CHILD_BOARDS, "sprint.childBoards.tooMany"),
    /**
     * Close the active sprints of the selected boards and start the new one.
     * Sent only while one of those boards has an active sprint.
     */
    takeOver: z.boolean(),
  })
  .superRefine((data, ctx) => {
    // With repeat on the name is the series name, which the server limits
    // to 240 characters after trimming.
    if (
      data.recurrence.enabled &&
      data.name.trim().length > SPRINT_SERIES_NAME_MAX_LENGTH
    ) {
      ctx.addIssue({
        code: "custom",
        path: ["name"],
        message: "sprint.seriesNameTooLong",
      });
    }
    if (!(data.endDate > data.startDate)) {
      ctx.addIssue({
        code: "custom",
        path: ["endDate"],
        message: "sprint.endDateAfterStart",
      });
      return;
    }
    refineRecurrence(
      data.recurrence,
      calendarDaysBetween(data.startDate, data.endDate),
      ctx,
      ["endDate"],
    );
  });

export type CreateSprintFormValues = z.infer<typeof createSprintFormSchema>;

// ── Edit Sprint Form Schema ──────────────────────────────────────────

/**
 * `seriesNameField` is true when the name field holds the series name of a
 * sprint in an enabled series, which the server limits to 240 characters.
 */
export function buildEditSprintFormSchema(seriesNameField: boolean) {
  return z
    .object({
      name: z.string().min(1, "sprint.nameRequired"),
      goal: z.string().optional(),
      startDate: z.date(),
      endDate: z.date(),
    })
    .superRefine((data, ctx) => {
      if (
        seriesNameField &&
        data.name.trim().length > SPRINT_SERIES_NAME_MAX_LENGTH
      ) {
        ctx.addIssue({
          code: "custom",
          path: ["name"],
          message: "sprint.seriesNameTooLong",
        });
      }
      if (!(data.endDate > data.startDate)) {
        ctx.addIssue({
          code: "custom",
          path: ["endDate"],
          message: "sprint.endDateAfterStart",
        });
      }
    });
}

export type EditSprintFormValues = z.infer<
  ReturnType<typeof buildEditSprintFormSchema>
>;

// ── Sprint Automation Form Schema ────────────────────────────────────

/**
 * Form of the edit dialog's Automation tab. The sprint's dates are edited in
 * the General tab, so the length is a fixed input here and a length error is
 * shown on the switch.
 */
export function buildSprintRecurrenceFormSchema(lengthDays: number | null) {
  return z
    .object({ recurrence: sprintRecurrenceFieldsSchema })
    .superRefine((data, ctx) => {
      refineRecurrence(data.recurrence, lengthDays, ctx, [
        "recurrence",
        "enabled",
      ]);
    });
}

export type SprintRecurrenceFormValues = z.infer<
  ReturnType<typeof buildSprintRecurrenceFormSchema>
>;

// ── Delete Sprint Form Schema ────────────────────────────────────────

export const deleteSprintFormSchema = z
  .object({
    ticketAction: TicketRelocationActionSchema,
    targetSprintId: z.string().optional(),
  })
  .superRefine((data, ctx) => {
    if (data.ticketAction === "MOVE_TO_SPRINT" && !data.targetSprintId) {
      ctx.addIssue({
        code: "custom",
        path: ["targetSprintId"],
        message: "required",
      });
    }
  });

export type DeleteSprintFormValues = z.infer<typeof deleteSprintFormSchema>;

// ── Unlink Sprint Form Schema ────────────────────────────────────────

export const UnlinkTicketActionSchema = z.enum([
  "MOVE_TO_BACKLOG",
  "MOVE_TO_SPRINT",
  "DELETE",
]);
export type UnlinkTicketAction = z.infer<typeof UnlinkTicketActionSchema>;

export const unlinkSprintFormSchema = z
  .object({
    ticketAction: UnlinkTicketActionSchema,
    targetBoardId: z.string().optional(),
    targetSprintId: z.string().optional(),
  })
  .superRefine((data, ctx) => {
    if (data.ticketAction === "MOVE_TO_SPRINT" && !data.targetSprintId) {
      ctx.addIssue({
        code: "custom",
        path: ["targetSprintId"],
        message: "required",
      });
    }
  });

export type UnlinkSprintFormValues = z.infer<typeof unlinkSprintFormSchema>;

// ── Inferred Types ──────────────────────────────────────────────────

export type SprintCreateRequest = z.infer<typeof sprintCreateRequestSchema>;
export type SprintUpdateRequest = z.infer<typeof sprintUpdateRequestSchema>;
export type SprintBulkLinkRequest = z.infer<typeof sprintBulkLinkRequestSchema>;
export type SprintUnlinkTransferRequest = z.infer<
  typeof sprintUnlinkTransferRequestSchema
>;
export type SprintUnlinkRequest = z.infer<typeof sprintUnlinkRequestSchema>;
export type SprintDetailResponse = z.infer<typeof sprintDetailResponseSchema>;
export type SprintSeriesRequest = z.infer<typeof sprintSeriesRequestSchema>;
export type SprintSeriesResponse = z.infer<typeof sprintSeriesResponseSchema>;
export type SprintListResponse = z.infer<typeof sprintListResponseSchema>;
export type SprintChildBoardResponse = z.infer<
  typeof sprintChildBoardResponseSchema
>;
export type SprintStartConflictResponse = z.infer<
  typeof sprintStartConflictResponseSchema
>;
export type SprintStartPreviewResponse = z.infer<
  typeof sprintStartPreviewResponseSchema
>;
