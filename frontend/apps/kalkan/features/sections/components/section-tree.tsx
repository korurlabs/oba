"use client";

import { useState, type DragEvent } from "react";
import { useLocale, useTranslations } from "next-intl";
import {
  ArrowDown,
  ArrowLeftToLine,
  ArrowRightToLine,
  ArrowUp,
  ChevronDown,
  ChevronRight,
  FileText,
  FilePlus,
  GripVertical,
  MoreHorizontal,
  Pencil,
  Trash2,
} from "lucide-react";
import { Link } from "@oba/shared/libs/i18n/navigation";
import { cn } from "@oba/ui/lib/utils";
import { Badge } from "@oba/ui/sky/badge";
import { Button } from "@oba/ui/sky/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@oba/ui/sky/dropdown-menu";
import { DeletePageDialog } from "@/features/pages/components/delete-page-dialog";
import { useDeletePage } from "@/features/pages/hooks/use-pages";
import { pageTitle } from "@/features/pages/lib/page-tree";
import type { PageListItem } from "@/features/pages/schemas";
import { newPageHref, pageEditHref, pageHref } from "@/lib/routes";
import { useMoveSectionPage } from "../hooks/use-move-section-page";
import {
  buildNodes,
  descendantCountOf,
  dropTargetOf,
  moveTargetOf,
  planMove,
  type MoveKind,
  type MoveTarget,
  type PageNode,
} from "../lib/tree-moves";
import {
  collapsedOf,
  NO_FOLDS,
  searchKeyOf,
  syncFoldsToSearch,
  toggleFold,
  type TreeFolds,
} from "../lib/tree-search";

interface SectionTreeProps {
  /** The whole page tree the caller can see, flat, as the tree endpoint answers. */
  pages: readonly PageListItem[];
  root: PageListItem;
  /** Section title, for the tree's accessible name. */
  sectionTitle: string;
  /** EDIT on the section's root: the pages can be moved, deleted, edited and added to. */
  canEdit: boolean;
  /** The pages a search keeps, each hit with the pages above it; every page when unset. */
  visibleIds?: ReadonlySet<string>;
}

/**
 * The pages of a section as a tree below its root page, every level in
 * sibling order and expanded at first. With EDIT on the section a page can be
 * dragged before another page, moved up, down, into the page above or out one
 * level from its menu, edited, given a subpage or deleted with its subtree.
 * A search hides the other pages and opens every page above a hit, but moves
 * still place a page among all its siblings, as the order is kept on the
 * whole tree.
 */
export function SectionTree({ pages, root, sectionTitle, canEdit, visibleIds }: SectionTreeProps) {
  const t = useTranslations("sectionTree");
  const locale = useLocale();
  const move = useMoveSectionPage();
  const deletePage = useDeletePage();
  const [storedFolds, setFolds] = useState<TreeFolds>(NO_FOLDS);
  const [draggedId, setDraggedId] = useState<string | null>(null);
  const [overId, setOverId] = useState<string | null>(null);
  const [pendingDelete, setPendingDelete] = useState<PageListItem | null>(null);

  const nodes = buildNodes(pages, root.id);

  // A new search opens every page above its hits; the member's own folds
  // come back once the search is cleared.
  const searchKey = searchKeyOf(visibleIds);
  const folds = syncFoldsToSearch(storedFolds, searchKey);
  if (folds !== storedFolds) setFolds(folds);
  const collapsed = collapsedOf(folds);

  const toggle = (pageId: string) => {
    setFolds((current) => toggleFold(syncFoldsToSearch(current, searchKey), pageId));
  };

  const runMove = (pageId: string, target: MoveTarget | null) => {
    if (target === null || move.isPending) return;
    move.mutate(planMove(pages, pageId, target));
  };

  const endDrag = () => {
    setDraggedId(null);
    setOverId(null);
  };

  const dropTarget = (onto: PageListItem) =>
    draggedId === null ? null : dropTargetOf(pages, draggedId, onto);

  const confirmDelete = () => {
    if (pendingDelete === null) return;
    deletePage.mutate(pendingDelete.id, { onSuccess: () => setPendingDelete(null) });
  };

  const renderNodes = (level: PageNode[], depth: number) => (
    <ul
      role={depth === 1 ? "tree" : "group"}
      aria-label={depth === 1 ? t("label", { section: sectionTitle }) : undefined}
      aria-busy={depth === 1 && move.isPending ? true : undefined}
      className="flex flex-col gap-0.5"
    >
      {level.map(({ page, children: allChildren }) => {
        if (visibleIds && !visibleIds.has(page.id)) return null;
        const children = visibleIds
          ? allChildren.filter((child) => visibleIds.has(child.page.id))
          : allChildren;
        const title = pageTitle(page, locale);
        const hasChildren = children.length > 0;
        const open = !collapsed.has(page.id);
        const Chevron = open ? ChevronDown : ChevronRight;
        const isDropTarget = overId === page.id && dropTarget(page) !== null;

        return (
          <li
            key={page.id}
            role="treeitem"
            aria-level={depth}
            aria-expanded={hasChildren ? open : undefined}
            aria-selected={false}
            className="flex flex-col gap-0.5"
          >
            <div
              data-page-row={page.id}
              draggable={canEdit && !move.isPending}
              onDragStart={(event: DragEvent<HTMLDivElement>) => {
                event.dataTransfer.effectAllowed = "move";
                event.dataTransfer.setData("text/plain", page.id);
                setDraggedId(page.id);
              }}
              onDragOver={(event: DragEvent<HTMLDivElement>) => {
                if (dropTarget(page) === null) return;
                event.preventDefault();
                event.dataTransfer.dropEffect = "move";
                setOverId(page.id);
              }}
              onDragLeave={() => setOverId((current) => (current === page.id ? null : current))}
              onDrop={(event: DragEvent<HTMLDivElement>) => {
                event.preventDefault();
                if (draggedId !== null) runMove(draggedId, dropTarget(page));
                endDrag();
              }}
              onDragEnd={endDrag}
              style={{ paddingLeft: `${(depth - 1) * 1.25}rem` }}
              className={cn(
                "group hover:bg-muted flex min-h-10 items-center gap-1 rounded-md pr-1",
                isDropTarget && "border-primary border-t-2",
                draggedId === page.id && "opacity-50"
              )}
            >
              {hasChildren ? (
                <Button
                  variant="ghost"
                  size="icon-sm"
                  aria-label={open ? t("collapse", { title }) : t("expand", { title })}
                  onClick={() => toggle(page.id)}
                >
                  <Chevron aria-hidden="true" className="text-muted-foreground" />
                </Button>
              ) : (
                <span className="size-8 shrink-0" aria-hidden="true" />
              )}
              {canEdit && (
                <GripVertical
                  aria-label={t("dragHandle", { title })}
                  role="img"
                  className="text-muted-foreground size-4 shrink-0 cursor-grab opacity-0 group-hover:opacity-100"
                />
              )}
              <FileText aria-hidden="true" className="text-muted-foreground size-4 shrink-0" />
              <Link
                href={pageHref(page.id)}
                className="min-w-0 flex-1 truncate py-2 text-sm hover:underline"
              >
                {title}
              </Link>
              {page.compliance && <Badge variant="info">{t("compliance")}</Badge>}
              {canEdit && (
                <PageRowMenu
                  page={page}
                  title={title}
                  disabled={move.isPending}
                  targetOf={(kind) => moveTargetOf(pages, page, kind, root.id)}
                  onMove={(target) => runMove(page.id, target)}
                  onDelete={() => setPendingDelete(page)}
                />
              )}
            </div>
            {hasChildren && open && renderNodes(children, depth + 1)}
          </li>
        );
      })}
    </ul>
  );

  return (
    <div className="flex flex-col gap-3">
      {renderNodes(nodes, 1)}
      {canEdit && <p className="text-muted-foreground text-xs">{t("dropHint")}</p>}
      {move.isPending && (
        <p className="text-muted-foreground text-sm" role="status">
          {t("moving")}
        </p>
      )}
      <DeletePageDialog
        open={pendingDelete !== null}
        onOpenChange={(next) => {
          if (!next) setPendingDelete(null);
        }}
        title={pendingDelete ? pageTitle(pendingDelete, locale) : ""}
        descendantCount={pendingDelete ? descendantCountOf(pages, pendingDelete.id) : 0}
        isDeleting={deletePage.isPending}
        onConfirm={confirmDelete}
      />
    </div>
  );
}

const MOVES: {
  kind: MoveKind;
  icon: typeof ArrowUp;
  label: "moveUp" | "moveDown" | "indent" | "outdent";
}[] = [
  { kind: "up", icon: ArrowUp, label: "moveUp" },
  { kind: "down", icon: ArrowDown, label: "moveDown" },
  { kind: "indent", icon: ArrowRightToLine, label: "indent" },
  { kind: "outdent", icon: ArrowLeftToLine, label: "outdent" },
];

/** The actions of one page row: the four moves, edit, a new subpage and delete. */
function PageRowMenu({
  page,
  title,
  disabled,
  targetOf,
  onMove,
  onDelete,
}: {
  page: PageListItem;
  title: string;
  disabled: boolean;
  targetOf: (kind: MoveKind) => MoveTarget | null;
  onMove: (target: MoveTarget) => void;
  onDelete: () => void;
}) {
  const t = useTranslations("sectionTree");

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="icon-sm" aria-label={t("actions", { title })}>
          <MoreHorizontal aria-hidden="true" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuGroup>
          {MOVES.map(({ kind, icon: Icon, label }) => {
            const target = targetOf(kind);
            return (
              <DropdownMenuItem
                key={kind}
                disabled={disabled || target === null}
                onSelect={() => {
                  if (target !== null) onMove(target);
                }}
              >
                <Icon aria-hidden="true" />
                {t(label)}
              </DropdownMenuItem>
            );
          })}
        </DropdownMenuGroup>
        <DropdownMenuSeparator />
        <DropdownMenuGroup>
          <DropdownMenuItem asChild>
            <Link href={pageEditHref(page.id)}>
              <Pencil aria-hidden="true" />
              {t("edit")}
            </Link>
          </DropdownMenuItem>
          <DropdownMenuItem asChild>
            <Link href={newPageHref(page.id)}>
              <FilePlus aria-hidden="true" />
              {t("newChild")}
            </Link>
          </DropdownMenuItem>
        </DropdownMenuGroup>
        <DropdownMenuSeparator />
        <DropdownMenuGroup>
          <DropdownMenuItem variant="destructive" onSelect={onDelete}>
            <Trash2 aria-hidden="true" />
            {t("delete")}
          </DropdownMenuItem>
        </DropdownMenuGroup>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
