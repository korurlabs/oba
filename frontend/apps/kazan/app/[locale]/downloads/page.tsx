import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { Link } from "@oba/shared/libs/i18n/navigation";

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

export default async function DownloadsPage() {
  const t = await getTranslations("downloads");

  return (
    <main id="main-content" className="bg-background text-foreground min-h-screen">
      <div className="mx-auto flex max-w-4xl flex-col gap-10 px-5 py-10 sm:px-8 sm:py-16">
        <header className="flex flex-col gap-5">
          <Link href="/" className="text-primary w-fit underline underline-offset-4">
            {t("openKazan")}
          </Link>
          <h1 className="text-4xl font-semibold tracking-tight sm:text-5xl">{t("title")}</h1>
          <p className="text-muted-foreground max-w-2xl text-lg leading-relaxed">
            {t("description")}
          </p>
        </header>
        <div className="flex flex-col gap-10">
          {(["homebrew", "script"] as const).map((method) => (
            <section
              key={method}
              aria-labelledby={`install-${method}`}
              className="flex flex-col gap-3"
            >
              <h2 id={`install-${method}`} className="text-2xl font-semibold">
                {t(`${method}.title`)}
              </h2>
              <p className="text-muted-foreground max-w-2xl leading-relaxed">
                {t(`${method}.description`)}
              </p>
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
            <h2 id="direct-download" className="text-2xl font-semibold">
              {t("direct.title")}
            </h2>
            <p className="text-muted-foreground max-w-2xl leading-relaxed">
              {t("direct.description")}
            </p>
            <a href={RELEASES} className="text-primary w-fit underline underline-offset-4">
              {t("direct.link")}
            </a>
            <p className="text-muted-foreground max-w-2xl text-sm leading-relaxed">
              {t("direct.checksums")}
            </p>
          </section>
          <section aria-labelledby="getting-started" className="flex flex-col gap-3">
            <h2 id="getting-started" className="text-2xl font-semibold">
              {t("verify.title")}
            </h2>
            <pre className="bg-muted overflow-x-auto rounded-lg border p-4 text-sm leading-7">
              <code>{COMMANDS.verify}</code>
            </pre>
            <p className="text-muted-foreground max-w-2xl leading-relaxed">
              {t("verify.description")}
            </p>
          </section>
        </div>
      </div>
    </main>
  );
}
