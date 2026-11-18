import { z } from "zod";

/**
 * The AI provider an account belongs to. It lives in its own module because
 * the machine and user schemas embed it while the account schemas embed
 * theirs, and a shared module keeps those imports free of a cycle.
 */
export const AccountProviderSchema = z.enum(["CLAUDE", "CHATGPT", "GEMINI", "GROK", "DEEPSEEK"]);
export type AccountProvider = z.infer<typeof AccountProviderSchema>;
export const AccountProvider = AccountProviderSchema.enum;

/** Product names, as the pages show them. */
export const PROVIDER_LABELS: Readonly<Record<AccountProvider, string>> = {
  CLAUDE: "Claude",
  CHATGPT: "ChatGPT",
  GEMINI: "Gemini",
  GROK: "Grok",
  DEEPSEEK: "DeepSeek",
};

/**
 * Whether the recorder reports 5-hour and 7-day usage for the provider. Those
 * limits are Claude Code's, and Kaya records only Claude Code, so every other
 * provider has no snapshot to show.
 */
export function providerReportsUsage(provider: AccountProvider): boolean {
  return provider === "CLAUDE";
}
