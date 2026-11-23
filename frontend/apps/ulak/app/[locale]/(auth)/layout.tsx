import type { ReactNode } from "react";
import { getTranslations } from "next-intl/server";
import { AuthLayoutActions } from "@/components/layout/auth-layout-actions";

/**
 * Auth Layout
 *
 * Frames a screen that renders without the Ulak shell: a centred content area
 * on a decorated background, the language switcher and logout button in the
 * top-right corner, and the brand badge underneath. Onboarding itself lives in
 * the account app.
 */
export default async function AuthLayout({ children }: { children: ReactNode }) {
  const t = await getTranslations("onboarding");

  return (
    <div className="relative flex min-h-screen flex-col items-center px-5 py-10">
      <AuthLayoutActions />
      {/* Decorative gradient circles */}
      <div className="bg-gradient-radial from-korur-blue-medium/50 pointer-events-none fixed top-[-100px] right-[-100px] h-[300px] w-[300px] rounded-full to-transparent" />
      <div className="bg-gradient-radial from-korur-blue-light/50 pointer-events-none fixed bottom-[-50px] left-[-50px] h-[200px] w-[200px] rounded-full to-transparent" />

      {/* Main content */}
      <div className="relative z-10 flex w-full max-w-4xl flex-1 items-center justify-center">{children}</div>

      {/* Brand badge */}
      <div className="relative z-10 mt-6 flex items-center gap-2 text-sm text-muted-foreground">
        {t.rich("poweredBy", {
          brand: (chunks) => <span className="font-semibold text-primary">{chunks}</span>,
        })}
      </div>
    </div>
  );
}
