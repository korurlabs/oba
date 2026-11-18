import { z } from "zod";

// ── Constants ───────────────────────────────────────────────────────

/** Longest system value the alert map stores, the length of its database column. */
export const SYSTEM_KEY_MAX_LENGTH = 50;

/**
 * The one pattern client, server and database check: lowercase letters,
 * digits and hyphens, starting with a letter or digit. The server rejects any
 * other value, so the form trims and lowercases before it validates.
 */
export const SYSTEM_KEY_PATTERN = /^[a-z0-9][a-z0-9-]{0,49}$/;

// ── Request Schemas ─────────────────────────────────────────────────

export const alertRouteCreateRequestSchema = z.object({
  systemKey: z.string(),
  boardId: z.string(),
  isDefault: z.boolean(),
});

/** The system is fixed after creation, so an update carries only these two. */
export const alertRouteUpdateRequestSchema = z.object({
  boardId: z.string(),
  isDefault: z.boolean(),
});

// ── Form Schema ─────────────────────────────────────────────────────

/**
 * Builds the add and edit form schema with translated messages. `t` is the
 * `alertRoutes` translator.
 */
export const createAlertRouteFormSchema = (t: (key: string) => string) =>
  z.object({
    systemKey: z
      .string()
      .trim()
      .toLowerCase()
      .min(1, t("errors.systemRequired"))
      .max(SYSTEM_KEY_MAX_LENGTH, t("errors.systemPattern"))
      .regex(SYSTEM_KEY_PATTERN, t("errors.systemPattern")),
    boardId: z.guid(t("errors.boardRequired")),
    isDefault: z.boolean(),
  });

// ── Response Schemas ────────────────────────────────────────────────

export const alertRouteSchema = z.object({
  id: z.string(),
  systemKey: z.string(),
  boardId: z.string(),
  boardName: z.string(),
  boardPrefix: z.string(),
  boardColor: z.string().nullable(),
  isDefault: z.boolean(),
  updatedAt: z.string(),
});

// ── Inferred Types ──────────────────────────────────────────────────

export type AlertRouteCreateRequest = z.infer<typeof alertRouteCreateRequestSchema>;
export type AlertRouteUpdateRequest = z.infer<typeof alertRouteUpdateRequestSchema>;
export type AlertRouteFormValues = z.infer<ReturnType<typeof createAlertRouteFormSchema>>;
export type AlertRoute = z.infer<typeof alertRouteSchema>;
