import { createApiClient } from "@oba/shared/api/axios";

// The API origin is resolved per request from the run-time config.
export const api = createApiClient();
