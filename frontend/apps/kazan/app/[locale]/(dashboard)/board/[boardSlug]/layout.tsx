import { Suspense } from 'react';
import { BoardTabs } from '@/components/layout/board-tabs';
import { BoardBarSlotProvider } from '@/components/layout/board-bar-slot';
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
      <BoardBarSlotProvider>
        <div className="flex h-full flex-col">
          {/* The board bar: name, tabs, the page's view controls and settings */}
          <Suspense fallback={<BoardTabsSkeleton />}>
            <BoardTabs boardId={board.id} />
          </Suspense>

          <div className="flex-1 overflow-hidden">
            <Suspense fallback={<ContentSkeleton />}>{children}</Suspense>
          </div>
        </div>
      </BoardBarSlotProvider>
    </BoardRoleProvider>
  );
}

function BoardTabsSkeleton() {
  return (
    <div className="border-b border-border bg-card px-4 py-3 lg:px-6">
      {/* Mirrors BoardTabs: the name and the tabs on the left, the controls
        on the right, which wrap under the tabs when the row is narrow. */}
      <div className="flex flex-wrap items-center gap-x-6 gap-y-3">
        <div className="flex items-center gap-6">
          <Skeleton className="h-4 w-24" />
          <Skeleton className="h-4 w-14" />
          <Skeleton className="h-4 w-14" />
          <Skeleton className="h-4 w-14" />
          <Skeleton className="h-4 w-10" />
        </div>
        <div className="ml-auto flex items-center gap-2">
          <Skeleton className="h-8 w-32" />
          <Skeleton className="h-8 w-20" />
          <Skeleton className="h-8 w-8" />
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
