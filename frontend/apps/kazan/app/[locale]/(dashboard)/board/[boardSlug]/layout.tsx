import { Suspense } from 'react';
import { BoardTabs } from '@/components/layout/board-tabs';
import { BoardRoleProvider } from '@/features/boards/providers/board-role-provider';
import { findBoardRoute } from '@/features/boards/lib/board-route';
import { Skeleton } from '@/components/ui/skeleton';
import { isKazanClosedOnServer } from '@/components/layout/kazan-access-server';
import { UnknownBoardGate } from './unknown-board-gate';

interface BoardLayoutProps {
  children: React.ReactNode;
  params: Promise<{ boardSlug: string }>;
}

/**
 * Resolves the board of the URL (its slug, or its id in a link made before
 * slugs) for every page under it. An unknown or inaccessible board renders
 * the not-found page, except under the ticket page, which finds the ticket
 * by its key alone (see `UnknownBoardGate`). The pages resolve the same
 * value, and the lookup is shared per request.
 *
 * When the selected organization does not grant Kazan, the API would refuse
 * the lookup and the no-access panel takes the place of the board, so the
 * layout skips both the lookup and the board chrome.
 */
export default async function BoardLayout({
  children,
  params,
}: BoardLayoutProps) {
  if (await isKazanClosedOnServer()) {
    return <>{children}</>;
  }

  const { boardSlug } = await params;
  const resolution = await findBoardRoute(boardSlug);
  if (!resolution) {
    return <UnknownBoardGate>{children}</UnknownBoardGate>;
  }
  const { board, legacy } = resolution;

  // Every page under an id-based URL redirects to its slug URL, so the
  // board chrome is not rendered for the moment it would show.
  if (legacy) {
    return <>{children}</>;
  }

  return (
    <BoardRoleProvider boardId={board.id}>
      <div className="flex h-full flex-col">
        {/* Board Tabs Navigation (includes breadcrumb and actions) */}
        <Suspense fallback={<BoardTabsSkeleton />}>
          <BoardTabs boardId={board.id} />
        </Suspense>

        <div className="flex-1 overflow-hidden">
          <Suspense fallback={<ContentSkeleton />}>{children}</Suspense>
        </div>
      </div>
    </BoardRoleProvider>
  );
}

function BoardTabsSkeleton() {
  return (
    <div className="border-b border-border bg-background px-6 py-3">
      {/* Mirrors the phone layout of BoardTabs: below `md` the tabs take a
        row of their own under the breadcrumb and the actions, so the
        placeholder fits a phone and the header does not jump when it loads. */}
      <div className="flex items-center justify-between max-md:flex-wrap max-md:justify-start max-md:gap-x-3 max-md:gap-y-3">
        <div className="flex items-center gap-3 max-md:contents">
          {/* Placeholder for the sidebar collapse toggle + its separator, so
            the breadcrumb does not shift left when BoardTabs resolves. */}
          <div className="flex items-center gap-2">
            <Skeleton className="size-7 rounded-md" />
            <Skeleton className="h-4 w-px" />
          </div>
          <div className="flex items-center gap-1.5">
            <Skeleton className="h-4 w-4" />
            <Skeleton className="h-4 w-4" />
            <Skeleton className="h-4 w-24" />
          </div>
          <Skeleton className="h-5 w-px max-md:hidden" />
          <div className="flex items-center gap-6 max-md:order-last max-md:basis-full">
            <Skeleton className="h-4 w-16" />
            <Skeleton className="h-4 w-12" />
          </div>
        </div>
        <div className="flex items-center gap-2 max-md:ml-auto">
          <Skeleton className="h-8 w-8 rounded-md" />
          <Skeleton className="h-8 w-20 rounded-md" />
        </div>
      </div>
    </div>
  );
}

function ContentSkeleton() {
  return (
    <div className="h-full w-full p-6">
      <div className="space-y-4">
        <Skeleton className="h-12 w-full" />
        <Skeleton className="h-64 w-full" />
        <Skeleton className="h-64 w-full" />
      </div>
    </div>
  );
}
