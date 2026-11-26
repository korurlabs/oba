import { redirect } from '@oba/shared/libs/i18n/navigation';
import { boardDefaultTab, boardPath, resolveBoardRoute } from '@/features/boards/lib/board-route';
import { isKazanClosedOnServer } from '@/components/layout/kazan-access-server';
import { loadServerAppearance } from '@/components/layout/kazan-appearance-server';
import { withQuery, type RouteSearchParams } from './board-page-data';

export const dynamic = 'force-dynamic';

interface BoardPageProps {
  params: Promise<{ locale: string; boardSlug: string }>;
  searchParams: Promise<RouteSearchParams>;
}

export default async function BoardPage({ params, searchParams }: BoardPageProps) {
  const { locale, boardSlug } = await params;
  // The no-access panel takes the place of the page, and the API would refuse
  // the lookup, so the page renders nothing (see `AppAccessGate`).
  if (await isKazanClosedOnServer()) return null;
  const { board } = await resolveBoardRoute(boardSlug);

  // A board opens on the first tab of the user's layout: the current sprint
  // in the modern layout, All in the classic one. The target names the board
  // by its slug, so a link by id lands on the clean URL in the same hop.
  const { layout } = await loadServerAppearance();
  redirect({ href: withQuery(boardPath(board.slug, boardDefaultTab(layout)), await searchParams), locale });
}
