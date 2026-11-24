import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";

/** The oba CLI publishes its release assets, install.sh included, in this public repository. */
const RELEASES = "https://github.com/korurlabs/oba-cli/releases/latest";
const INSTALLER = `${RELEASES}/download/install.sh`;
const COMMANDS = {
  homebrew: "brew install korurlabs/tap/oba-cli",
  script: `curl -sSfL ${INSTALLER} -o oba-install.sh\nsh oba-install.sh`,
  userInstall: 'OBA_INSTALL_DIR="$HOME/bin" sh oba-install.sh',
  version: "oba --version",
  signIn: "oba login\noba org list\noba org use <name|id>\noba whoami",
  board: "oba kazan board list\noba kazan board use <board>",
  json: "oba kazan ticket mine --json",
} as const;

/** A short, real set of the commands a board member runs every day. */
const EVERYDAY = [
  { id: "mine", command: "oba kazan ticket mine" },
  { id: "get", command: "oba kazan ticket get OPS-42" },
  {
    id: "create",
    command:
      'oba kazan ticket create --title "Login crash" --type BUG --priority HIGH \\\n  --status TODO --mission-rank S',
  },
  { id: "update", command: "oba kazan ticket update OPS-42 --status IN_REVIEW" },
  { id: "comment", command: 'oba kazan comment add OPS-42 --content "Deployed to staging."' },
  { id: "updates", command: "oba kazan updates create" },
  {
    id: "pr",
    command: "oba kazan pr share https://github.com/acme/shop/pull/688 --status IN_REVIEW",
  },
] as const;

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("cli");
  return { title: t("title"), description: t("description") };
}

function CodeBlock({ children }: { children: string }) {
  return (
    <pre className="bg-muted overflow-x-auto rounded-lg border p-4 text-sm leading-7">
      <code>{children}</code>
    </pre>
  );
}

/**
 * CLI Page
 *
 * How to work with Kazan from a terminal through the oba CLI: install it, sign
 * in and pick the organization, choose the default board, and the everyday
 * `oba kazan` commands. It sits among the settings screens so the user stays
 * inside Kazan while reading it. The page is static; the dashboard layout
 * already gates auth.
 */
export default async function CliPage() {
  const t = await getTranslations("cli");

  return (
    <div className="mx-auto max-w-3xl px-8 py-10">
      <div className="mb-8">
        <h1 className="text-foreground text-3xl font-bold tracking-tight">{t("title")}</h1>
        <p className="text-muted-foreground mt-2 text-base">{t("description")}</p>
      </div>
      <div className="flex flex-col gap-8">
        <section aria-labelledby="cli-install" className="flex flex-col gap-3">
          <h2 id="cli-install" className="text-foreground text-lg font-semibold">
            {t("install.title")}
          </h2>
          <p className="text-muted-foreground leading-relaxed">{t("install.homebrew")}</p>
          <CodeBlock>{COMMANDS.homebrew}</CodeBlock>
          <p className="text-muted-foreground leading-relaxed">{t("install.script")}</p>
          <CodeBlock>{COMMANDS.script}</CodeBlock>
          <p className="text-muted-foreground leading-relaxed">{t("install.userInstall")}</p>
          <CodeBlock>{COMMANDS.userInstall}</CodeBlock>
          <p className="text-muted-foreground leading-relaxed">{t("install.direct")}</p>
          <a href={RELEASES} className="text-primary w-fit underline underline-offset-4">
            {t("install.link")}
          </a>
          <p className="text-muted-foreground leading-relaxed">{t("install.version")}</p>
          <CodeBlock>{COMMANDS.version}</CodeBlock>
        </section>
        <section aria-labelledby="cli-sign-in" className="flex flex-col gap-3">
          <h2 id="cli-sign-in" className="text-foreground text-lg font-semibold">
            {t("signIn.title")}
          </h2>
          <p className="text-muted-foreground leading-relaxed">{t("signIn.description")}</p>
          <CodeBlock>{COMMANDS.signIn}</CodeBlock>
          <p className="text-muted-foreground text-sm leading-relaxed">{t("signIn.org")}</p>
        </section>
        <section aria-labelledby="cli-board" className="flex flex-col gap-3">
          <h2 id="cli-board" className="text-foreground text-lg font-semibold">
            {t("board.title")}
          </h2>
          <p className="text-muted-foreground leading-relaxed">{t("board.description")}</p>
          <CodeBlock>{COMMANDS.board}</CodeBlock>
          <p className="text-muted-foreground text-sm leading-relaxed">{t("board.override")}</p>
        </section>
        <section aria-labelledby="cli-everyday" className="flex flex-col gap-3">
          <h2 id="cli-everyday" className="text-foreground text-lg font-semibold">
            {t("everyday.title")}
          </h2>
          <p className="text-muted-foreground leading-relaxed">{t("everyday.description")}</p>
          {EVERYDAY.map(({ id, command }) => (
            <div key={id} className="flex flex-col gap-2">
              <p className="text-muted-foreground text-sm leading-relaxed">
                {t(`everyday.items.${id}`)}
              </p>
              <CodeBlock>{command}</CodeBlock>
            </div>
          ))}
          <p className="text-muted-foreground text-sm leading-relaxed">{t("everyday.help")}</p>
        </section>
        <section aria-labelledby="cli-json" className="flex flex-col gap-3">
          <h2 id="cli-json" className="text-foreground text-lg font-semibold">
            {t("json.title")}
          </h2>
          <p className="text-muted-foreground leading-relaxed">{t("json.description")}</p>
          <CodeBlock>{COMMANDS.json}</CodeBlock>
        </section>
      </div>
    </div>
  );
}
