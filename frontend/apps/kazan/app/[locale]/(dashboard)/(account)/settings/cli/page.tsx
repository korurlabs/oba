import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";

const RELEASES = "https://github.com/korurlabs/kazan-cli/releases/latest";
const INSTALLER = `${RELEASES}/download/install.sh`;
const COMMANDS = {
  homebrew: "brew install korurlabs/tap/kazan-cli",
  script: `curl -sSfL ${INSTALLER} -o kazan-install.sh\nsh kazan-install.sh`,
  userInstall: 'KAZAN_INSTALL_DIR="$HOME/.local/bin" sh kazan-install.sh',
  verify: "kazan --version\nkazan login",
} as const;

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("downloads");
  return { title: t("title"), description: t("description") };
}

/**
 * Download CLI Page
 *
 * How to install the Kazan CLI: Homebrew, the install script, or a direct
 * download of a release. It sits among the settings screens so the user stays
 * inside Kazan while reading it. The page is static; the dashboard layout
 * already gates auth.
 */
export default async function DownloadCliPage() {
  const t = await getTranslations("downloads");

  return (
    <div className="mx-auto max-w-3xl px-8 py-10">
      <div className="mb-8">
        <h1 className="text-foreground text-3xl font-bold tracking-tight">{t("title")}</h1>
        <p className="text-muted-foreground mt-2 text-base">{t("description")}</p>
      </div>
      <div className="flex flex-col gap-8">
        {(["homebrew", "script"] as const).map((method) => (
          <section
            key={method}
            aria-labelledby={`install-${method}`}
            className="flex flex-col gap-3"
          >
            <h2 id={`install-${method}`} className="text-foreground text-lg font-semibold">
              {t(`${method}.title`)}
            </h2>
            <p className="text-muted-foreground leading-relaxed">{t(`${method}.description`)}</p>
            <pre className="bg-muted overflow-x-auto rounded-lg border p-4 text-sm leading-7">
              <code>{COMMANDS[method]}</code>
            </pre>
            {method === "script" && (
              <>
                <p className="text-muted-foreground leading-relaxed">{t("script.userInstall")}</p>
                <pre className="bg-muted overflow-x-auto rounded-lg border p-4 text-sm leading-7">
                  <code>{COMMANDS.userInstall}</code>
                </pre>
              </>
            )}
          </section>
        ))}
        <section aria-labelledby="direct-download" className="flex flex-col gap-3">
          <h2 id="direct-download" className="text-foreground text-lg font-semibold">
            {t("direct.title")}
          </h2>
          <p className="text-muted-foreground leading-relaxed">{t("direct.description")}</p>
          <a href={RELEASES} className="text-primary w-fit underline underline-offset-4">
            {t("direct.link")}
          </a>
          <p className="text-muted-foreground text-sm leading-relaxed">{t("direct.checksums")}</p>
        </section>
        <section aria-labelledby="getting-started" className="flex flex-col gap-3">
          <h2 id="getting-started" className="text-foreground text-lg font-semibold">
            {t("verify.title")}
          </h2>
          <pre className="bg-muted overflow-x-auto rounded-lg border p-4 text-sm leading-7">
            <code>{COMMANDS.verify}</code>
          </pre>
          <p className="text-muted-foreground leading-relaxed">{t("verify.description")}</p>
        </section>
      </div>
    </div>
  );
}
