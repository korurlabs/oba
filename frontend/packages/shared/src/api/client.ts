import { createApiClient } from "./axios";

/**
 * Shared singleton Axios client for cross-app features that live in
 * `packages/shared/`. Apps may continue to use their own per-app `api`
 * instance for app-scoped routes; this singleton exists for shared
 * features (e.g. attachments) that need to issue requests from inside
 * the shared package itself.
 */
export const api = createApiClient();
