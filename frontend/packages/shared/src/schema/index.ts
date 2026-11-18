import z from "zod";

import { instantSchema } from "../lib/date-schemas";

export const UuidSchema = z.uuidv4();
export const BaseEntitySchema = z.object({
  id: UuidSchema,
  createdAt: instantSchema,
  updatedAt: instantSchema,
});

/** The pagination block of a paged `CustomResponse`; `currentPage` is 1-based. */
export const PaginationSchema = z.object({
  totalElements: z.number(),
  totalPages: z.number(),
  currentPage: z.number(),
  pageSize: z.number(),
  first: z.boolean(),
  last: z.boolean(),
  empty: z.boolean(),
});

export const ErrorSchema = z.object({
  code: z.string(),
  message: z.string().nullish(),
  errorValues: z.record(z.string(), z.any()).nullish(),
});

/**
 * The backend serializes every `CustomResponse` field, so a success carries
 * `pagination: null` and `error: null` and a refusal `data: null`; `.optional()`
 * alone would reject every real response.
 */
export const BaseApiResponseSchema = z.object({
  success: z.boolean(),
  message: z.string().nullish(),
  data: z.any().nullish(),
  pagination: PaginationSchema.nullish(),
  error: ErrorSchema.nullish(),
});

export const createApiResponseSchema = <T extends z.ZodType>(dataSchema: T) =>
  z.object({
    success: z.boolean(),
    message: z.string().nullish(),
    data: dataSchema.nullish(),
    pagination: PaginationSchema.nullish(),
    error: ErrorSchema.nullish(),
  });

export type Pagination = z.infer<typeof PaginationSchema>;

export type ApiError = z.infer<typeof ErrorSchema>;

export interface ApiResponse<T = unknown> {
  success: boolean;
  message: string;
  data?: T;
  pagination?: Pagination;
  error?: ApiError;
}
