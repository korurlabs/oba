import { z } from "zod";

/** The page block of a paged `CustomResponse`; `currentPage` counts from 1. */
export const paginationSchema = z.object({
  totalElements: z.number(),
  totalPages: z.number(),
  currentPage: z.number(),
  pageSize: z.number(),
  first: z.boolean(),
  last: z.boolean(),
  empty: z.boolean(),
});
export type Pagination = z.infer<typeof paginationSchema>;

/** The error block of a refused call: a code such as `KAY_NF_001` and its values. */
export const apiErrorSchema = z.object({
  code: z.string(),
  message: z.string().nullish(),
  errorValues: z.record(z.string(), z.unknown()).nullish(),
});
export type ApiErrorBody = z.infer<typeof apiErrorSchema>;

/** The envelope every Kaya endpoint answers with, except the stream. */
export const createApiResponseSchema = <T extends z.ZodTypeAny>(data: T) =>
  z.object({
    success: z.boolean(),
    message: z.string().nullish(),
    data: data.nullish(),
    pagination: paginationSchema.nullish(),
    error: apiErrorSchema.nullish(),
  });

export type ApiResponse<T> = {
  success: boolean;
  message?: string | null;
  data?: T | null;
  pagination?: Pagination | null;
  error?: ApiErrorBody | null;
};
