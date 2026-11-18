import { useRouter } from "expo-router";
import { useCallback } from "react";

import { boardHref, NEW_BOARD_HREF } from "@/components/boards/boardRoutes";
import { ticketHref } from "@/components/ticket/ticketRoutes";
import { toast } from "@/components/ui/toast";
import { useResolveBoardSlug } from "@/hooks/queries/boards";
import { useTranslation } from "@/i18n";
import { openInAppBrowser } from "@/lib/browser";

/** The views of a board that Home links to. */
export type BoardPage = "sprint" | "roadmap";

/** The Kaizen web path of a board tab, in the app language. */
export function boardWebPath(language: string, boardSlug: string, page: BoardPage): string {
  return `/${language}/board/${encodeURIComponent(boardSlug)}/${page}`;
}

/**
 * Opens the pages Home links to. Boards and New board are native screens of
 * the Boards tab; a ticket opens the ticket screen above the tabs. A web page
 * that fails to open is logged and toasted here.
 */
export function useHomeLinks() {
  const { t } = useTranslation();
  const router = useRouter();
  const resolveBoardSlug = useResolveBoardSlug();

  const open = useCallback(
    (url: string): void => {
      openInAppBrowser(url).catch((error: unknown) => {
        console.warn("[home] link failed", error instanceof Error ? error.message : error);
        toast.error(t("homeOpenFailed"));
      });
    },
    [t]
  );

  const openTicket = useCallback(
    (ticketKey: string) => router.push(ticketHref(ticketKey)),
    [router]
  );

  // Home rows carry the board slug; the board screen is addressed by id.
  const openBoardPage = useCallback(
    (boardSlug: string, page: BoardPage) => {
      resolveBoardSlug(boardSlug)
        .then((boardId: string) => router.navigate(boardHref(boardId, { view: page })))
        .catch((error: unknown) => {
          console.warn("[home] board link failed", error instanceof Error ? error.message : error);
          toast.error(t("homeOpenFailed"));
        });
    },
    [resolveBoardSlug, router, t]
  );

  const openNewBoard = useCallback(() => router.navigate(NEW_BOARD_HREF), [router]);

  return { open, openTicket, openBoardPage, openNewBoard };
}
