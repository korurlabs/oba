import { isApiError } from "@oba/shared/api/axios";
import { TICKET_ERROR_CODES } from "../schemas";

/** True when the server refused a create because the ticket had no Mission Rank. */
export function isMissionRankRejected(error: unknown): boolean {
  return isApiError(error, TICKET_ERROR_CODES.validation) && "missionRank" in error.errorValues;
}
