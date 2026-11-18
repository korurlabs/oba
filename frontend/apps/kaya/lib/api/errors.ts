import { isAxiosError } from "axios";

import type { ApiResponse } from "@/types/api";

/** Error codes the client reacts to by name. */
export const ERROR_CODES = {
  /** No valid token: sign in again. */
  UNAUTHENTICATED: "GEN_AUT_001",
  /** The user's account is disabled. */
  USER_DISABLED: "GEN_SEC_002",
  /** Kaya's own tenant check: the membership or its organization is disabled. */
  KAYA_MEMBERSHIP_DISABLED: "KAY_SEC_002",
  /**
   * The platform's organization check: the user holds no active membership in
   * the organization asked for, or that organization does not exist.
   */
  NOT_MEMBER: "ORG_SEC_001",
  /** The platform's membership of the user in the organization is disabled. */
  MEMBERSHIP_DISABLED: "ORG_SEC_002",
  /** The platform's organization is disabled. */
  ORGANIZATION_DISABLED: "ORG_SEC_005",
  /** The organization asked for does not exist on the platform. */
  ORGANIZATION_NOT_FOUND: "ORG_NOT_001",
  /** The user holds no membership in the organization asked for. */
  MEMBERSHIP_NOT_FOUND: "ORG_NOT_002",
  /** No such resource in this organization, or one of another organization. */
  NOT_FOUND: "KAY_NF_001",
  /** The user is a member of the organization, but holds no grant of Kaya there. */
  APP_ACCESS_DENIED: "APP_SEC_001",
} as const;

/** A refused or failed call to Kaya's API: the HTTP status, the error code and its values. */
export class KayaApiError extends Error {
  readonly status: number | null;
  readonly code: string | null;
  readonly errorValues: Readonly<Record<string, unknown>>;

  constructor(
    message: string,
    status: number | null,
    code: string | null,
    errorValues: Readonly<Record<string, unknown>> = {}
  ) {
    super(message);
    this.name = "KayaApiError";
    this.status = status;
    this.code = code;
    this.errorValues = errorValues;
  }

  /**
   * Whether the refusal means the user may no longer open the organization:
   * the membership is gone or disabled, the organization is unknown or
   * disabled (403), or either is gone (404). The organization check of every
   * call answers `ORG_SEC_001` and `ORG_SEC_005`; the other codes come from
   * services that check the membership themselves.
   */
  get isNotMember(): boolean {
    if (this.status === 403) {
      return (
        this.code === ERROR_CODES.KAYA_MEMBERSHIP_DISABLED ||
        this.code === ERROR_CODES.NOT_MEMBER ||
        this.code === ERROR_CODES.MEMBERSHIP_DISABLED ||
        this.code === ERROR_CODES.ORGANIZATION_DISABLED
      );
    }
    if (this.status === 404) {
      return (
        this.code === ERROR_CODES.ORGANIZATION_NOT_FOUND ||
        this.code === ERROR_CODES.MEMBERSHIP_NOT_FOUND
      );
    }
    return false;
  }
}

/**
 * Turns what axios rejected with into a KayaApiError: the envelope's error
 * when the server answered with one, else the status, else the network
 * failure's own message.
 */
export function toKayaApiError(error: unknown): KayaApiError {
  if (error instanceof KayaApiError) {
    return error;
  }
  if (isAxiosError<ApiResponse<unknown>>(error)) {
    const status = error.response?.status ?? null;
    const body = error.response?.data?.error;
    if (body !== undefined && body !== null) {
      return new KayaApiError(
        body.message ?? `Kaya answered ${status ?? "without a status"} (${body.code})`,
        status,
        body.code,
        body.errorValues ?? {}
      );
    }
    if (status !== null) {
      return new KayaApiError(`Kaya answered ${status}`, status, null);
    }
    return new KayaApiError(`Kaya could not be reached: ${error.message}`, null, null);
  }
  return new KayaApiError(error instanceof Error ? error.message : String(error), null, null);
}
