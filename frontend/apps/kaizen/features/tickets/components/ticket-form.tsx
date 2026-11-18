/**
 * TicketForm
 *
 * The single ticket create/edit form, rendered in one of two shells:
 *
 *   variant="dialog" — the modal, layered over whatever list the user is on.
 *     Driven by the `t_ticket` search param on the LIST url (so a refresh
 *     keeps the user on their list with the dialog re-opened), rendered by
 *     `TicketModalHost` in the `@modal` slot. For a fast create/edit; pinned
 *     footer actions.
 *
 *   variant="page" — the standalone full-page shell at its own route,
 *     reached by the dialog's expand button, a shared link, or sub-ticket
 *     navigation from another full page. Same fields, with the content pane
 *     split into Details / Attachments tabs.
 *     Rendered by the ticket page under `app/[locale]/(dashboard)/board/[boardSlug]/ticket/`
 *     and by `app/[locale]/(dashboard)/ticket/new`.
 *
 * The form works on ids. A create url names its target by slugs and keys
 * instead; given `createTarget`, `TicketForm` resolves it first (see
 * `useTicketCreateTarget`).
 *
 * Both shells use the same two-pane layout, for create and edit alike:
 * title, description, attachments and sub-tickets on the left, a properties
 * panel of label-left rows on the right (stacked under the title on narrow
 * screens). Both share `useTicketForm` and `TicketFormFields`, so behaviour
 * cannot drift between them. Closing is a url change, not local state.
 */

"use client";

import { useCallback, useEffect, useMemo, useRef } from "react";
import { useParams } from "next/navigation";
import { useQueryStates } from "nuqs";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { ArrowLeft, Maximize2 } from "lucide-react";
import type { TicketListResponse } from "@/features/tickets/schemas";
import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Form } from "@/components/ui/form";
import { TicketTypeBadge } from "./ticket-type-badge";
import { TicketFormFields } from "./ticket-form-fields";
import type { TicketStatus } from "@/types/enums";
import { useTicketForm } from "../hooks/use-ticket-form";
import { IncidentCloseDialog } from "./incident-close-dialog";
import { MoveTicketBoardDialog } from "./move-ticket-board-dialog";
import {
  useTicketNavigation,
  TICKET_CREATE_PATH,
  TICKET_ROUTE_PARAM,
  ticketDetailPath,
  ticketModalParams,
} from "../hooks/use-ticket-navigation";
import { useTicketCreateTarget } from "../hooks/use-ticket-create-target";
import type { TicketCreateTarget } from "../lib/ticket-route-params";
import { BOARD_TAB, boardPath } from "@/features/boards/lib/board-route";
import type { BoardListWithChildrenResponse } from "@/features/boards/schemas";
import { devError } from "@oba/shared/helpers/dev-log";
import { consumeTicketDraft, saveTicketDraft } from "../lib/ticket-draft";
import { useBoardSelect } from "@/features/boards/hooks/use-board-select";
import { useAllTickets } from "@/features/all-tickets/hooks/use-all-tickets";
import type { AllTicketListResponse } from "@/features/all-tickets/schemas";
import { useDocumentTitle } from "@/hooks/use-document-title";

// ── Interfaces ────────────────────────────────────────────────────────

/** Where the board-less full page closes to when no list is remembered. */
const ALL_TICKETS_PATH = "/tickets";

/** The form for ticket ids the caller already holds. */
export interface TicketFormByIdProps {
  /** Which shell renders the form. */
  variant: "dialog" | "page";
  /**
   * Board the form is scoped to. In create mode this is only the INITIAL
   * board selection — the user can pick another one in the form, and the
   * submit uses that choice. Empty string means "no board context", which
   * makes the create form offer the full board tree.
   */
  boardId: string;
  /**
   * Slug of `boardId`, for the urls the form builds. Optional: the ticket's
   * own board slug wins once the ticket has loaded, and the page shell falls
   * back to the slug in its route.
   */
  boardSlug?: string | null;
  /** Present in edit mode. */
  ticketId?: string | null;
  /** Key of `ticketId`, which names a sub-ticket's parent in the expand url. */
  ticketKey?: string | null;
  /** Create as a sub-ticket of this ticket. */
  parentTicketId?: string | null;
  /** Key of `parentTicketId`, for the expand url. */
  parentTicketKey?: string | null;
  /** Initial status for create (the kanban column the user clicked). */
  initialStatus?: TicketStatus;
  /** Sprint placement for create. Mutually exclusive with `backlogId`. */
  sprintId?: string;
  /** Backlog placement for create. Mutually exclusive with `sprintId`. */
  backlogId?: string;
}

/** The create form for a target named in a url, by slugs and keys. */
export interface TicketFormCreateTargetProps {
  variant: "dialog" | "page";
  createTarget: TicketCreateTarget;
}

export type TicketFormProps = TicketFormByIdProps | TicketFormCreateTargetProps;

export function TicketForm(props: TicketFormProps) {
  if ("createTarget" in props) {
    return <TicketCreateTargetForm variant={props.variant} createTarget={props.createTarget} />;
  }
  return <TicketFormContent {...props} />;
}

/**
 * Resolves a url's create target to ids, then renders the form. Renders
 * nothing while the lookups run. A target that cannot be resolved is toasted
 * and closes the form, rather than creating the ticket somewhere the user did
 * not ask for (a root ticket instead of a sub-ticket, no sprint).
 *
 * A url holding a legacy id is rewritten to its slug or key first, and the
 * form mounts only on the clean url: the rewrite changes the target, which
 * would remount a mounted form and lose what the user typed or the draft it
 * just consumed. The dialog's write is shallow, so the list under it is not
 * reloaded; the create page reads its target from the server, so its write
 * has to reach the server.
 */
function TicketCreateTargetForm({ variant, createTarget }: TicketFormCreateTargetProps) {
  const t = useTranslations();
  const state = useTicketCreateTarget(createTarget);
  const { closeModal, closePage } = useTicketNavigation();
  const [, setCreateParams] = useQueryStates(ticketModalParams);

  const urlRewrite = state.status === "ready" ? state.urlRewrite : null;
  useEffect(() => {
    if (!urlRewrite) return;
    void setCreateParams(urlRewrite, { history: "replace", shallow: variant === "dialog" });
  }, [urlRewrite, variant, setCreateParams]);

  const failed =
    state.status === "failed" ||
    state.status === "parentNotFound" ||
    state.status === "targetNotFound";
  const reportedRef = useRef(false);
  useEffect(() => {
    if (!failed || reportedRef.current) return;
    reportedRef.current = true;
    if (state.status === "parentNotFound") {
      toast.error(t("kaizen.tickets.messages.ticketNotFound", { key: state.parentKey }));
    } else if (state.status === "targetNotFound") {
      devError("Ticket create target not found:", state.reason);
      toast.error(t("kaizen.tickets.messages.createTargetNotFound"));
    } else if (state.status === "failed") {
      devError("Ticket create target could not be resolved:", state.error);
      toast.error(t("errors.somethingWentWrong"));
    }
    if (variant === "page") {
      closePage(createTarget.boardSlug ? boardPath(createTarget.boardSlug, BOARD_TAB.all) : ALL_TICKETS_PATH);
    } else {
      closeModal();
    }
  }, [failed, state, variant, createTarget.boardSlug, closeModal, closePage, t]);

  if (state.status !== "ready" || urlRewrite) return null;
  const { target } = state;
  return (
    <TicketFormContent
      variant={variant}
      boardId={target.boardId}
      boardSlug={target.boardSlug}
      parentTicketId={target.parentTicketId}
      parentTicketKey={target.parentTicketKey}
      initialStatus={target.initialStatus}
      sprintId={target.sprintId}
      backlogId={target.backlogId}
    />
  );
}

function TicketFormContent({
  variant,
  boardId,
  boardSlug = null,
  ticketId = null,
  ticketKey = null,
  parentTicketId = null,
  parentTicketKey = null,
  initialStatus,
  sprintId,
  backlogId,
}: TicketFormByIdProps) {
  const t = useTranslations();
  const isPage = variant === "page";
  const { openTicket, openTicketPage, closeModal, closePage, expandToFullPage } =
    useTicketNavigation();
  const routeBoardSlug = useParams<{ boardSlug?: string }>()?.boardSlug ?? null;

  // Closing the form IS a url change. The dialog clears its own params (or
  // steps back over the entry that set them); the page may have been
  // deep-linked, so it needs a fallback route: the ticket's board.
  const fallbackBoardSlug = boardSlug ?? routeBoardSlug;
  const fallbackHref = fallbackBoardSlug
    ? boardPath(fallbackBoardSlug, BOARD_TAB.all)
    : ALL_TICKETS_PATH;
  const handleOpenChange = useCallback(
    (open: boolean) => {
      if (open) return;
      if (isPage) closePage(fallbackHref);
      else closeModal();
    },
    [isPage, closeModal, closePage, fallbackHref],
  );

  // Sub-tickets are opened by key, but the form reports the id of the one
  // clicked. The loaded sub-tickets map it back; a ref because the list
  // comes out of the same `useTicketForm` call that takes this callback.
  const subTicketsRef = useRef<readonly TicketListResponse[]>([]);
  const handleNavigateToTicket = useCallback(
    (nextTicketId: string) => {
      const next = subTicketsRef.current.find((sub) => sub.id === nextTicketId);
      if (!next) {
        devError("Sub-ticket to open is not among the loaded sub-tickets:", nextTicketId);
        toast.error(t("kaizen.tickets.messages.ticketLoadFailed"));
        return;
      }
      const nextBoard = next.board ?? null;
      // On the full page, opening a sub-ticket as a DIALOG would stack a
      // second copy of this same form over the one the user is reading. Stay
      // on the page shell instead; the dialog shell swaps the url param.
      if (isPage) {
        openTicketPage(next.key, nextBoard?.slug);
      } else {
        openTicket(
          next.key,
          nextBoard ? { key: next.key, id: next.id, board: nextBoard } : undefined,
        );
      }
    },
    [isPage, openTicketPage, openTicket, t],
  );

  const {
    mode,
    isSaving,
    ticket,
    isTicketLoading,
    subTickets,
    activeTicketId,
    selectedBoardId,
    hierarchicalBoards,
    viewerBoardIds,
    currentBoard,
    isRelatedBoardsLoading,
    availableUsers,
    activeParentId,
    hasPendingImages,
    pendingAttachments,
    setPendingAttachments,
    form,
    isCreatingSubTicket,
    isDeleting,
    isSubTicketReorderLocked,
    handleFormSubmit,
    handleDeleteTicket,
    handleCreateSubTicket,
    handleDeleteSubTicket,
    handleUpdateSubTicketTitle,
    handleToggleSubTicketComplete,
    handleReorderSubTicket,
    handleRequestCreateSubTicket,
    handleOpenSubTicket,
    handleFileUpload,
    addPendingImage,
    removePendingImageBySrc,
    isBacklogPending,
    incidentCloseDialogProps,
    subTicketIncidentCloseDialogProps,
    boardMove,
  } = useTicketForm({
    open: true,
    onOpenChange: handleOpenChange,
    boardId,
    ticketId,
    parentTicketId,
    initialStatus,
    sprintId,
    backlogId,
    onNavigateToTicket: handleNavigateToTicket,
  });

  subTicketsRef.current = subTickets;

  // Flatten hierarchical boards into SearchSelectOption[] with meta fields
  const {
    options: boardSelectOptions,
    renderBoardOption,
    renderBoardValue,
  } = useBoardSelect(hierarchicalBoards, {
    viewerBoardIds,
  });

  // Parent candidates for the "parent ticket" select. The list pages used to
  // hand their own ticket list down; the route shells have no list, so fetch
  // the selected board's ROOT tickets — the cross-board endpoint only returns
  // root tickets, which is exactly what may be a parent. Following
  // `selectedBoardId` keeps the options correct when the user switches board
  // in create mode.
  const availableParentTickets = useParentTicketCandidates(selectedBoardId, activeTicketId);

  // ── Draft carry-over ──────────────────────────────────────────────
  // The page shell consumes the draft the dialog wrote immediately before
  // its hard navigation. It must wait for `isTicketLoading` to clear: the
  // hook's own layout-effect reset (to server data) runs on the commit
  // where the form syncs, and would clobber an earlier restore.
  //
  // Read + apply live in ONE effect behind a ref guard so a StrictMode
  // double-invoke cannot consume the draft twice (the second read finds
  // nothing, because consuming removes the entry).
  const draftAppliedRef = useRef(!isPage);
  useEffect(() => {
    if (draftAppliedRef.current || isTicketLoading) return;
    draftAppliedRef.current = true;
    const draft = consumeTicketDraft({
      mode,
      boardId,
      ticketId: ticketId ?? null,
      parentId: activeParentId ?? null,
    });
    if (draft) form.reset(draft);
  }, [isTicketLoading, mode, boardId, ticketId, activeParentId, form]);

  const handleExpand = useCallback(() => {
    // Pending images live as `blob:` URLs + File handles owned by THIS
    // document; a hard navigation destroys both. Expanding anyway would
    // carry a description full of dead blob: URLs to the page, where
    // `hasPending` is false and Save would persist them to the API.
    if (hasPendingImages || pendingAttachments.length > 0) {
      toast.error(t("ticket.expandBlockedPendingImages"));
      return;
    }

    // `mode === "create"` with a non-null `ticketId` prop means the INTERNAL
    // sub-ticket create mode: `t_ticket` still points at the parent. The
    // create ROUTE is the correct target for it, carrying the parent, so
    // the draft is replayed into a create form and not into the parent's
    // edit page.
    const isInternalSubTicketCreate = mode === "create" && !!ticketId;
    const draftBoardId = mode === "create" ? selectedBoardId : boardId;

    const saved = saveTicketDraft(
      {
        // The mode is part of the key: a create draft must never resolve
        // against an edit form sharing the same ticket id.
        mode,
        // In create mode the page reads its board from `t_board`, so the
        // draft must be keyed by the board the user currently has selected.
        boardId: draftBoardId,
        ticketId: isInternalSubTicketCreate ? null : (ticketId ?? null),
        // Separates the sub-ticket create form from the plain create form,
        // which otherwise share `create:<board>:new`. Symmetric with the
        // consume side, where `activeParentId` comes from `t_parent`.
        parentId: activeParentId ?? null,
      },
      form.getValues(),
    );
    if (!saved) {
      // Navigating now would silently throw away everything they typed.
      toast.error(t("ticket.expandDraftFailed"));
      return;
    }

    // The dialog lives on the LIST url, so the expand target is always
    // spelled out: there is no "current url" that happens to be the ticket's.
    if (mode === "create") {
      // The page resolves the url back to the same ids the draft is keyed
      // by. Where a slug or key is not at hand, the id stands in: the create
      // url accepts both.
      const parentRef = isInternalSubTicketCreate
        ? (ticketKey ?? ticketId)
        : (parentTicketKey ?? activeParentId);
      expandToFullPage({
        targetPath: TICKET_CREATE_PATH,
        params: {
          [TICKET_ROUTE_PARAM.board]: selectedBoardId
            ? (findBoardSlug(hierarchicalBoards, selectedBoardId) ?? selectedBoardId)
            : null,
          [TICKET_ROUTE_PARAM.parent]: parentRef,
        },
      });
      return;
    }

    // The page loads the ticket on its own board, which is the board the
    // dialog loaded it on (`boardId`), so the draft keyed above is found.
    const expandKey = ticket?.key ?? ticketKey;
    const expandBoardSlug = ticket?.board?.slug ?? boardSlug;
    if (!expandKey || !expandBoardSlug) {
      // Nothing to expand to. Say so rather than navigating nowhere and
      // leaving the user with a saved draft they cannot see.
      toast.error(t("ticket.expandDraftFailed"));
      return;
    }
    expandToFullPage({ targetPath: ticketDetailPath(expandBoardSlug, expandKey) });
  }, [
    mode,
    selectedBoardId,
    hierarchicalBoards,
    boardId,
    boardSlug,
    ticketId,
    ticketKey,
    parentTicketKey,
    ticket?.key,
    ticket?.board?.slug,
    activeParentId,
    hasPendingImages,
    pendingAttachments,
    form,
    expandToFullPage,
    t,
  ]);

  // On the page these sit inline with the title, which is `flex-1` (basis 0)
  // and truncates first. `min-w-0` lets the row itself shrink once the title
  // is gone, and in the dialog it keeps the row inside the `pr-24` that clears
  // the expand / close buttons. The type badge and the key are `shrink-0`, so
  // the key cannot break at its hyphen; only the board chip gives way: it is
  // capped at `max-w-40` and truncates.
  // Renders nothing in create mode: an empty flex box would still contribute
  // its parent's gap, opening a hole between Back and the heading.
  const headerChips =
    mode === "edit" && ticket ? (
      <div className="flex min-w-0 items-center gap-2">
        <TicketTypeBadge type={ticket.type} className="shrink-0" />
        <span className="border-border bg-card text-muted-foreground shrink-0 rounded border px-1.5 py-0.5 font-mono text-xs">
          {ticket.key}
        </span>
        {currentBoard && (
          <span className="border-border bg-card text-muted-foreground hidden max-w-40 truncate rounded border px-1.5 py-0.5 text-xs sm:inline">
            {currentBoard.prefix} &middot; {currentBoard.name}
          </span>
        )}
      </div>
    ) : null;

  const titleText =
    mode === "create" ? t("ticket.create") : (ticket?.title ?? t("ticket.edit"));

  // Both shells name the ticket in the browser tab by key and title, to tell
  // open tickets apart; while the ticket loads, or in create mode, the tab
  // shows the translated heading. When the dialog closes, its title gives way
  // to the title of the list page underneath it.
  const tabTitle = mode === "edit" && ticket ? `${ticket.key} - ${ticket.title}` : titleText;
  useDocumentTitle(tabTitle);

  const fields = (
    <TicketFormFields
      mode={mode}
      withTabs={isPage}
      activeTicketId={activeTicketId}
      ticket={ticket}
      isTicketLoading={isTicketLoading}
      isSaving={isSaving}
      boardSelector={{
        selectedBoardId,
        // In edit mode another board opens the move dialog first.
        setSelectedBoardId: boardMove.requestBoardChange,
        boardSelectOptions,
        isLoading: isRelatedBoardsLoading,
      }}
      renderBoardOption={renderBoardOption}
      renderBoardValue={renderBoardValue}
      availableUsers={availableUsers}
      availableParentTickets={availableParentTickets}
      subTickets={subTickets}
      subTicketActions={{
        onCreate: handleCreateSubTicket,
        onUpdateTitle: handleUpdateSubTicketTitle,
        onToggleComplete: handleToggleSubTicketComplete,
        onDelete: handleDeleteSubTicket,
        onReorder: handleReorderSubTicket,
        onOpenSubTicket: handleOpenSubTicket,
        onRequestCreate: handleRequestCreateSubTicket,
      }}
      isCreatingSubTicket={isCreatingSubTicket}
      isSubTicketReorderLocked={isSubTicketReorderLocked}
      isDeleting={isDeleting}
      onDeleteTicket={handleDeleteTicket}
      onFileUpload={handleFileUpload}
      addPendingImage={addPendingImage}
      removePendingImageBySrc={removePendingImageBySrc}
      onOpenChange={handleOpenChange}
      onStagedAttachmentsChange={setPendingAttachments}
      currentBoard={currentBoard}
      isBacklog={!!backlogId}
      isBacklogPending={isBacklogPending}
    />
  );

  const formElement = (
    <Form {...form}>
      <form
        onSubmit={form.handleSubmit(handleFormSubmit)}
        // Both shells are fixed-height flex columns with their own scrolling
        // body. `min-h-0` is what lets this column shrink below its content
        // height so the body inside it actually overflows and scrolls.
        className={
          isPage
            ? "flex min-h-0 flex-1 flex-col"
            : "flex flex-1 flex-col overflow-hidden"
        }
      >
        {fields}
      </form>
    </Form>
  );

  // Rendered beside the form, never inside it: React bubbles a submit from a
  // portalled dialog through its React parents, so a dialog inside the
  // <form> would also submit the ticket form.
  const incidentCloseDialogs = (
    <>
      <IncidentCloseDialog {...incidentCloseDialogProps} />
      <IncidentCloseDialog {...subTicketIncidentCloseDialogProps} />
      {ticket && (
        <MoveTicketBoardDialog
          ticket={ticket}
          targetBoard={
            boardMove.pendingBoardId ? findBoard(hierarchicalBoards, boardMove.pendingBoardId) : null
          }
          onConfirm={boardMove.confirmBoardChange}
          onCancel={boardMove.cancelBoardChange}
        />
      )}
    </>
  );

  // ── Page shell ────────────────────────────────────────────────────
  // The shell owns the height: it fills its slot and never grows past it, so
  // the header and the fields' footer are pinned by the flex column and only
  // the body between them scrolls. It cannot rely on <main> scrolling instead
  // — under `board/[boardSlug]/layout.tsx` this route renders inside a
  // `flex-1 overflow-hidden` wrapper, which clips rather than scrolls.
  if (isPage) {
    return (
      <div className="bg-card text-card-foreground flex h-full flex-col overflow-hidden">
        {/*
          ONE compact band, not three. Back, the type badge, the key, the board
          chip and the title all sit on a single line: the page already carries
          the app breadcrumb row above it, and stacking a chip row and a
          separate heading row under that left the form squeezed between the
          header and the pinned footer.

          The title keeps the visual weight (it is the only `font-semibold`
          item) and is the only flex child allowed to shrink, so a narrow
          viewport truncates the title rather than wrapping the band onto a
          second line.
        */}
        <header className="border-border bg-card z-10 shrink-0 border-b px-4 py-2 md:px-6">
          <div className="mx-auto flex w-full max-w-6xl items-center gap-2">
            <Button
              type="button"
              variant="ghost"
              size="sm"
              className="-ml-2 h-7 shrink-0 gap-1.5 px-2"
              onClick={() => handleOpenChange(false)}
            >
              <ArrowLeft className="h-4 w-4" />
              {t("common.back")}
            </Button>
            {headerChips}
            <h1 className="text-foreground min-w-0 flex-1 truncate text-base font-semibold md:text-lg">
              {titleText}
            </h1>
          </div>
        </header>

        {formElement}
        {incidentCloseDialogs}
      </div>
    );
  }

  // ── Dialog shell ──────────────────────────────────────────────────
  // Width: the base DialogContent ships `max-w-lg`, far too narrow for the
  // form. The UNPREFIXED `max-w-[calc(100vw-2rem)]` is the class that beats
  // it in tailwind-merge, and it doubles as the phone cap (a 16px gutter each
  // side). This app overrides the breakpoints (`sm` = 320px, `md` = 744px,
  // `lg` = 1440px, `xl` = 1920px), so the widening starts at `md`, not `sm`:
  // `sm:` would apply on every phone and run the dialog edge to edge. From
  // there the variants widen it (2xl = 42rem, which fits inside 744px;
  // 5xl = 64rem; 6xl = 72rem): wide enough at `lg+` for the content pane plus
  // the 20rem properties panel, without ever overflowing the viewport.
  const DIALOG_WIDTH_CLASSES =
    "max-w-[calc(100vw-2rem)] md:max-w-2xl lg:max-w-5xl xl:max-w-6xl";

  return (
    <Dialog open onOpenChange={handleOpenChange}>
      <DialogContent
        aria-describedby={undefined}
        className={`flex max-h-[90vh] w-full flex-col gap-0 overflow-hidden p-0 ${DIALOG_WIDTH_CLASSES}`}
      >
        {/*
          Edit mode shows only the chips: the title is already the first,
          largest field of the form, and repeating it here showed it twice.
          The DialogTitle stays in the tree (screen-reader only) because Radix
          requires one to name the dialog. `min-h-16` keeps the chip row
          centred on the expand / close buttons, which sit at a fixed offset.
        */}
        {mode === "edit" ? (
          <div className="border-border bg-background flex min-h-16 shrink-0 items-center border-b px-4 py-3 pr-24 md:px-6 md:pr-24">
            {headerChips}
            <DialogTitle className="sr-only">{titleText}</DialogTitle>
          </div>
        ) : (
          <div className="border-border bg-background shrink-0 space-y-1.5 border-b px-4 py-3 pr-24 md:px-6 md:pr-24">
            <DialogTitle className="text-foreground flex items-center gap-2 text-lg font-semibold">
              <span className="truncate">{titleText}</span>
              <kbd className="text-muted-foreground border-border bg-muted shrink-0 rounded border px-1.5 py-0.5 font-mono text-[10px] font-normal">
                ⌥N
              </kbd>
            </DialogTitle>
          </div>
        )}

        {/* Expand to the full-page view. Sits left of the dialog's close X. */}
        <button
          type="button"
          onClick={handleExpand}
          aria-label={t("ticket.expandToFullPage")}
          title={t("ticket.expandToFullPage")}
          className="text-muted-foreground hover:bg-muted hover:text-foreground focus:ring-primary absolute top-4 right-14 flex h-8 w-8 items-center justify-center rounded-lg transition-all focus:ring-2 focus:ring-offset-2 focus:outline-none"
        >
          <Maximize2 className="h-4 w-4" />
        </button>

        {formElement}
        {incidentCloseDialogs}
      </DialogContent>
    </Dialog>
  );
}

/**
 * Root tickets on `boardId` that may be selected as the parent of the
 * ticket being edited/created. The ticket itself is excluded so it cannot
 * become its own parent. Disabled (empty) while no board is selected.
 */
function useParentTicketCandidates(
  boardId: string,
  activeTicketId: string | null,
): TicketListResponse[] {
  const params = useMemo(
    () =>
      boardId
        ? { boardIds: [boardId], sortBy: "displayOrder", sortDirection: "asc" }
        : undefined,
    [boardId],
  );
  const { data } = useAllTickets(params as Record<string, unknown> | undefined);
  return useMemo(() => {
    const tickets = (data?.data ?? []) as AllTicketListResponse[];
    return tickets.filter((candidate) => candidate.id !== activeTicketId);
  }, [data?.data, activeTicketId]);
}

/** The board with `boardId` anywhere in the board tree, or `null`. */
function findBoard(
  boards: readonly BoardListWithChildrenResponse[],
  boardId: string,
): BoardListWithChildrenResponse | null {
  for (const board of boards) {
    if (board.id === boardId) return board;
    const inChildren = findBoard(
      (board.childBoards ?? []) as BoardListWithChildrenResponse[],
      boardId,
    );
    if (inChildren) return inChildren;
  }
  return null;
}

/** Slug of the board with `boardId` anywhere in the board tree, or `null`. */
function findBoardSlug(
  boards: readonly BoardListWithChildrenResponse[],
  boardId: string,
): string | null {
  return findBoard(boards, boardId)?.slug ?? null;
}
