import { redirect } from '@oba/shared/libs/i18n/navigation';
import { BOARD_TAB, boardPath, resolveBoardRoute } from '@/features/boards/lib/board-route';
import { isKaizenClosedOnServer } from '@/components/layout/kaizen-access-server';
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
  if (await isKaizenClosedOnServer()) return null;
  const { board } = await resolveBoardRoute(boardSlug);

  // A board opens on the All view, which shows the sprint and the backlog
  // together. The target names the board by its slug, so a link by id lands
  // on the clean URL in the same hop.
  redirect({ href: withQuery(boardPath(board.slug, BOARD_TAB.all), await searchParams), locale });
}
