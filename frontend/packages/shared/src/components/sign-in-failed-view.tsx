import { Button } from "@oba/ui/components/button";
import { AlertTriangle, LogIn } from "lucide-react";

interface SignInFailedViewProps {
  title: string;
  description: string;
  /** Label of the sign-in link. */
  action: string;
  /** The login route, back to the deep link; built by `retrySignInOnce`. */
  signInUrl: string;
  /** The translated error code line, when the code is shown at all. */
  detail?: string;
}

/**
 * Root-page screen for a sign-in that failed again after its automatic retry.
 * The user starts the next sign-in, so a persistent failure cannot loop
 * through Keycloak. The strings come from the app, which owns its messages.
 *
 * The action is a plain link, not a client-side Link: the login route answers
 * with a redirect to Keycloak and must be loaded as a full navigation.
 *
 * The screen is the page's main landmark, so an app's "Skip to main content"
 * link has a target here too.
 */
export function SignInFailedView({ title, description, action, signInUrl, detail }: SignInFailedViewProps) {
  return (
    <main
      id="main-content"
      tabIndex={-1}
      className="bg-background flex min-h-screen items-center justify-center p-4 outline-none"
    >
      <div className="w-full max-w-md space-y-6 text-center">
        <div className="flex justify-center">
          <div className="bg-destructive/10 rounded-full p-4">
            <AlertTriangle className="text-destructive h-12 w-12" aria-hidden="true" />
          </div>
        </div>

        <div className="space-y-2">
          <h1 className="text-foreground text-2xl font-bold tracking-tight">{title}</h1>
          <p className="text-muted-foreground text-sm">{description}</p>
          {detail && <p className="text-muted-foreground font-mono text-xs">{detail}</p>}
        </div>

        <Button asChild className="gap-2">
          <a href={signInUrl}>
            <LogIn className="h-4 w-4" aria-hidden="true" />
            {action}
          </a>
        </Button>
      </div>
    </main>
  );
}
