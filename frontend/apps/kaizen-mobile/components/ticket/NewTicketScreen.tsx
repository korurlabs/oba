import { usePreventRemove } from "@react-navigation/native";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { File } from "expo-file-system";
import { Stack, useLocalSearchParams, useNavigation, useRouter } from "expo-router";
import * as React from "react";
import { Keyboard, type TextInput } from "react-native";

import { HeaderSaveButton } from "@/components/account/HeaderSaveButton";
import { canWriteBoard, flattenBoardTree, type BoardRow } from "@/components/boards/boardModel";
import { confirmDiscardChanges } from "@/components/kaizen/ActionSheet";
import { InlineError } from "@/components/kaizen/EmptyState";
import { ListRow, ListSection } from "@/components/kaizen/List";
import type { UploadedImage } from "@/components/ticket/descriptionImagesModel";
import {
  publishDraftTicket,
  withdrawDraftTicket,
  type DraftTicketChange,
  type DraftTicketSession,
} from "@/components/ticket/draftTicketStore";
import { requiresIncidentClose } from "@/components/ticket/incidentCloseModel";
import {
  forgetIncidentCloseDraft,
  NEW_INCIDENT_DRAFT,
} from "@/components/ticket/incidentCloseStore";
import {
  BACKLOG_PLACEMENT,
  canPickStatus,
  createTicketBody,
  defaultBoardId,
  initialPlacement,
  openSprints,
  priorityForType,
  rankOrDefault,
  statusOrDefault,
  typeOrDefault,
  type TicketCreateBody,
} from "@/components/ticket/newTicketModel";
import { TicketAttachments } from "@/components/ticket/TicketAttachments";
import { TicketForm, type TicketFormHandle } from "@/components/ticket/TicketForm";
import {
  ticketAttachmentErrorMessage,
  type TicketAttachmentMessage,
} from "@/components/ticket/ticketAttachmentsModel";
import {
  commitCreatedDescription,
  createFieldErrors,
  draftProblems,
  draftTicketView,
  duplicateAttachmentNames,
  firstProblemField,
  imageFailureMessage,
  newTicketResultKey,
  NO_CREATE_ERRORS,
  onBoard,
  type CreatedDescriptionOutcome,
  type CreateFieldErrors,
  type DraftProblemField,
  type DraftProblems,
  type NewTicketFollowUpMessage,
  type NewTicketResultKey,
  type PerBoard,
} from "@/components/ticket/ticketDraftModel";
import type { TicketField } from "@/components/ticket/ticketFieldsModel";
import {
  boardPicker,
  milestonePicker,
  priorityPicker,
  projectPicker,
  rankPicker,
  sprintPicker,
  statusPicker,
  typePicker,
  type TicketPicker,
} from "@/components/ticket/ticketPickers";
import {
  newTicketSheetHref,
  parseNewTicketPreset,
  ticketHref,
  type NewTicketSheet,
} from "@/components/ticket/ticketRoutes";
import { useDueDatePicker } from "@/components/ticket/useDueDatePicker";
import { useIncidentCloseGuard } from "@/components/ticket/useIncidentCloseGuard";
import { useTicketPicker } from "@/components/ticket/useTicketPicker";
import { useWriteFailure } from "@/components/ticket/useWriteFailure";
import { Text } from "@/components/ui/text";
import { toast } from "@/components/ui/toast";
import {
  invalidateTicketAttachments,
  ticketAttachmentExists,
  uploadTicketAttachments,
} from "@/hooks/queries/attachments";
import type { TicketUpdateBody } from "@/hooks/queries/ticketActions";
import {
  useBacklog,
  useBoardLabels,
  useBoardMembers,
  useBoardMilestones,
  useBoardProjects,
  useBoardSprints,
  useBoardTree,
} from "@/hooks/queries/boards";
import { useCreateTicket } from "@/hooks/queries/ticketCreate";
import { useTicketRef } from "@/hooks/queries/ticketDetail";
import { invalidateTicketViews, ticketKeys } from "@/hooks/queries/tickets";
import { useMe } from "@/hooks/queries/users";
import { useOnlineWriteGuard } from "@/hooks/useOnlineWriteGuard";
import { useOrganizationRole } from "@/hooks/useOrganizationRole";
import { useRecentBoards } from "@/hooks/useRecentBoards";
import { useTranslation, type TranslationKey } from "@/i18n";
import { api, readData, toApiError, type ApiError } from "@/lib/api";
import { API_ROUTES } from "@/lib/apiRoutes";
import { useActiveOrganizationId } from "@/lib/organizationStore";
import type { BoardOption, Milestone, Sprint } from "@/lib/schemas/boards";
import type { ChatAttachmentResponse, ChatLocalFile } from "@/lib/schemas/chat";
import {
  TICKET_DESCRIPTION_MAX,
  TICKET_TITLE_MAX,
  ticketDetailSchema,
  type PendingImage,
  type TicketDetail,
} from "@/lib/schemas/tickets";

/** Every field a new ticket's picker can set. */
const DRAFT_FIELDS: readonly TicketField[] = [
  "assignees",
  "labels",
  "sprint",
  "milestone",
  "project",
  "dueDate",
];
const NO_FIELDS: readonly TicketField[] = [];
const NO_IMAGES: readonly PendingImage[] = [];
const NO_FILES: readonly ChatLocalFile[] = [];

/** What Create is doing: checking staged files before the ticket exists, or adding them after. */
type CreatePhase = "idle" | "checking" | "finishing";

type TranslateFn = ReturnType<typeof useTranslation>["t"];

/** The text of a follow-up failure; a refusal without a file name speaks of "one of the files". */
function followUpText(t: TranslateFn, message: NewTicketFollowUpMessage | TicketAttachmentMessage) {
  const key: TranslationKey = message.key;
  return t(key, {
    name: message.params.name ?? t("ticketAttachmentsSomeFile"),
    max: message.params.max,
  });
}

function errorText(error: unknown): unknown {
  return error instanceof Error ? error.message : error;
}

/** The stored attachment of a one-file upload. */
function storedImage(attachments: readonly ChatAttachmentResponse[]): UploadedImage {
  const attachment: ChatAttachmentResponse | undefined = attachments[0];
  if (!attachment) throw new Error("The upload returned no attachment");
  return { id: attachment.id, url: attachment.url };
}

/**
 * `PUT /kaizen/boards/{boardId}/tickets/{ticketId}` with only the
 * description, for the ticket Create has just made, once its images carry
 * their links. Sent with `_silentErrors`: Create reports the outcome in its
 * one result toast. The answer replaces the cached ticket, so the ticket
 * screen that opens next shows the images and never the temporary tags.
 */
function useSaveCreatedDescription() {
  const queryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  return useMutation<TicketDetail, Error, { ticket: TicketDetail; description: string }>({
    mutationFn: async ({ ticket, description }) => {
      const body: TicketUpdateBody = { description };
      const response = await api.put(API_ROUTES.tickets.byId(ticket.board.id, ticket.id), body, {
        _silentErrors: true,
      });
      return readData(ticketDetailSchema, response.data);
    },
    onSuccess: (saved) => {
      if (orgId) {
        queryClient.setQueryData<TicketDetail>(
          ticketKeys.detail(orgId, saved.board.id, saved.id),
          saved
        );
      }
    },
    onError: (error) =>
      console.warn("[ticket] saving the new ticket's description failed", error.message),
    onSettled: () => invalidateTicketViews(queryClient, orgId),
  });
}

/**
 * The ticket screen without a ticket: New ticket. It shows the same header,
 * field rows and sheets as a saved ticket, but every pick stays in a draft
 * until Create. It starts on the board, sprint, status, parent, type and
 * priority of its route params, else on the board opened last, the backlog,
 * Todo, a Feature at Medium and rank M, assigned to the signed-in user. A
 * backlog ticket is always Todo, whatever status it was asked to start in.
 * A sub-ticket goes to its parent's board and lands where the parent is. The
 * title and the mission rank are required, as on the web: Create without one
 * sends nothing, marks every missing field and brings the first into view,
 * and each mark clears once its field is filled. Images added to the
 * description and files staged under Attachments need the ticket's id, so,
 * as on the web, Create first checks that no staged file is already attached
 * to a ticket, creates the ticket with the images' temporary tags, then
 * uploads the images and saves the description with their links while the
 * files upload, and reports the result in one toast. A failed step leaves
 * the ticket created and names what it lacks. A created ticket opens in
 * place of this screen, unless "Create another after this one" keeps it open
 * for the next title. Leaving with a title, a description or staged files
 * asks first.
 */
export function NewTicketScreen() {
  const { t } = useTranslation();
  const router = useRouter();
  const navigation = useNavigation();
  const fail = useWriteFailure();
  const askIncidentClose = useIncidentCloseGuard();
  const online = useOnlineWriteGuard();
  const params = useLocalSearchParams<Record<string, string | string[]>>();
  const preset = React.useMemo(() => parseNewTicketPreset(params), [params]);

  const meId: string | undefined = useMe(true).data?.id;
  const organizationRole: string | null = useOrganizationRole();
  const tree = useBoardTree();
  const { recent } = useRecentBoards();
  const parentRef = useTicketRef(preset.parentKey);
  const create = useCreateTicket();
  const saveDescription = useSaveCreatedDescription();
  const queryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  const { picker, setPicker, closePicker, withOptions, openChoice } = useTicketPicker();
  const rows: BoardRow[] = React.useMemo(() => flattenBoardTree(tree.data ?? []), [tree.data]);

  const isSubTicket: boolean = preset.parentKey !== null;
  const [pickedBoardId, setPickedBoardId] = React.useState<string | null>(null);
  const boardId: string | null = isSubTicket
    ? (parentRef.data?.boardId ?? null)
    : (pickedBoardId ?? defaultBoardId(preset.boardId, recent, tree.isSuccess ? rows : []));
  const board: BoardRow | undefined = rows.find((row) => row.id === boardId);
  const boardKey: string | undefined = boardId ?? undefined;

  const sprints = useBoardSprints(boardKey);
  const backlog = useBacklog(isSubTicket ? undefined : boardKey);
  const members = useBoardMembers(boardKey);
  const labels = useBoardLabels(boardKey);
  const milestones = useBoardMilestones(boardKey);
  const projects = useBoardProjects(boardKey);
  const boardRole: string | null =
    members.data?.find((member) => member.userId === meId)?.role ?? null;
  const canWrite: boolean = canWriteBoard(organizationRole, boardRole);

  const [type, setType] = React.useState<string>(() => typeOrDefault(preset.type));
  const [priority, setPriority] = React.useState<string>(() =>
    priorityForType(typeOrDefault(preset.type), preset.priority)
  );
  const [status, setStatus] = React.useState<string>(() => statusOrDefault(preset.status));
  const [rank, setRank] = React.useState<string>(() => rankOrDefault(null));
  const [title, setTitle] = React.useState<string>("");
  const [description, setDescription] = React.useState<string>("");
  const [images, setImages] = React.useState<readonly PendingImage[]>(NO_IMAGES);
  const [stagedFiles, setStagedFiles] = React.useState<readonly ChatLocalFile[]>(NO_FILES);
  const [phase, setPhase] = React.useState<CreatePhase>("idle");
  const [placementChoice, setPlacementChoice] = React.useState<PerBoard<string> | null>(null);
  const [labelChoice, setLabelChoice] = React.useState<PerBoard<string[]> | null>(null);
  const [milestoneChoice, setMilestoneChoice] = React.useState<PerBoard<string | null> | null>(
    null
  );
  const [projectChoice, setProjectChoice] = React.useState<PerBoard<string | null> | null>(null);
  const [pickedAssignees, setPickedAssignees] = React.useState<string[] | null>(null);
  const [dueDay, setDueDay] = React.useState<string | null>(null);
  const [createAnother, setCreateAnother] = React.useState<boolean>(false);
  const [errors, setErrors] = React.useState<CreateFieldErrors>(NO_CREATE_ERRORS);
  const [created, setCreated] = React.useState<TicketDetail | null>(null);
  // Bumped after "Create another", so the title field remounts empty and focused.
  const [round, setRound] = React.useState<number>(0);
  const form = React.useRef<TicketFormHandle>(null);
  const titleRef = React.useRef<TextInput>(null);

  const sprintList: Sprint[] = React.useMemo(() => openSprints(sprints.data ?? []), [sprints.data]);
  const placement: string = onBoard(
    placementChoice,
    boardId,
    initialPlacement(sprintList, boardId === preset.boardId ? preset.sprintId : null)
  );
  const labelIds: string[] = onBoard(labelChoice, boardId, []);
  const milestoneId: string | null = onBoard(milestoneChoice, boardId, null);
  const projectId: string | null = onBoard(projectChoice, boardId, null);
  const memberIds: Set<string> | null = members.data
    ? new Set(members.data.map((member) => member.userId))
    : null;
  // Assignees must be people of the chosen board; a board switch drops the others.
  const assigneeIds: string[] = (pickedAssignees ?? (meId ? [meId] : [])).filter(
    (id) => !memberIds || memberIds.has(id)
  );
  const statusPickable: boolean = canPickStatus(placement, isSubTicket);

  const view = draftTicketView(
    {
      title,
      type,
      priority,
      status,
      missionRank: rank,
      placement,
      assigneeIds,
      labelIds,
      milestoneId,
      projectId,
      dueDay,
      isSubTicket,
    },
    {
      members: members.data ?? [],
      labels: labels.data ?? [],
      sprints: sprintList,
      milestones: milestones.data ?? [],
      projects: projects.data ?? [],
    }
  );

  const clearRowError = React.useCallback((field: TicketField) => {
    setErrors((current) =>
      current.rows[field] ? { ...current, rows: { ...current.rows, [field]: undefined } } : current
    );
  }, []);

  // The field sheets are routes of their own; they read and change the draft here.
  const applyChange = React.useRef<(patch: DraftTicketChange) => void>(() => undefined);
  applyChange.current = (patch: DraftTicketChange) => {
    if (patch.assigneeIds) {
      setPickedAssignees([...patch.assigneeIds]);
      clearRowError("assignees");
    }
    if (patch.labelIds && boardId) {
      setLabelChoice({ boardId, value: [...patch.labelIds] });
      clearRowError("labels");
    }
    if (patch.description !== undefined) {
      setDescription(patch.description);
      setErrors((current) => ({ ...current, description: null }));
    }
    if (patch.images !== undefined) setImages(patch.images);
  };
  const assigneeKey: string = assigneeIds.join(",");
  const labelKey: string = labelIds.join(",");
  const session = React.useRef<DraftTicketSession | null>(null);
  React.useEffect(() => {
    const next: DraftTicketSession = {
      fields: {
        boardId,
        assigneeIds: assigneeKey ? assigneeKey.split(",") : [],
        labelIds: labelKey ? labelKey.split(",") : [],
        description,
        images,
      },
      change: (patch) => applyChange.current(patch),
    };
    session.current = next;
    publishDraftTicket(next);
  }, [assigneeKey, boardId, description, images, labelKey]);
  React.useEffect(() => () => withdrawDraftTicket(session.current), []);
  // Texts typed for an incident that was never created do not carry over to the next one.
  React.useEffect(() => () => forgetIncidentCloseDraft(NEW_INCIDENT_DRAFT), []);

  // The draft's images are files on the phone; the description card shows them from data URIs.
  const imagesRef = React.useRef<readonly PendingImage[]>(images);
  imagesRef.current = images;
  const previews = React.useRef<Map<string, string>>(new Map());
  const resolveImage = React.useCallback(async (src: string): Promise<string | null> => {
    const cached: string | undefined = previews.current.get(src);
    if (cached) return cached;
    const image: PendingImage | undefined = imagesRef.current.find((item) => item.src === src);
    if (!image) return null;
    try {
      const base64: string = await new File(image.file.uri).base64();
      const uri = `data:${image.file.mimeType ?? "image/jpeg"};base64,${base64}`;
      previews.current.set(src, uri);
      return uri;
    } catch (error) {
      // The card shows the image's box with its name instead; Create still
      // uploads the file, or names it in the result toast when it is gone.
      console.warn("[ticket] reading a draft description image failed", errorText(error));
      return null;
    }
  }, []);

  // Once the ticket exists, leaving no longer discards anything.
  const dirty: boolean =
    created === null &&
    phase !== "finishing" &&
    (title.trim() !== "" || description.trim() !== "" || stagedFiles.length > 0);
  const leaving = React.useRef<boolean>(false);
  usePreventRemove(dirty, ({ data }) => {
    if (leaving.current) {
      navigation.dispatch(data.action);
      return;
    }
    confirmDiscardChanges(() => {
      leaving.current = true;
      navigation.dispatch(data.action);
    });
  });

  // Open the ticket only once the screen is no longer dirty, so leaving does not ask to discard.
  React.useEffect(() => {
    if (created) router.replace(ticketHref(created.key));
  }, [created, router]);

  const placementReady: boolean = isSubTicket
    ? parentRef.isSuccess
    : sprints.isSuccess && (placement !== BACKLOG_PLACEMENT || backlog.isSuccess);
  const ready: boolean = !!boardId && canWrite && placementReady;
  const editable: boolean = !!boardId && canWrite;

  const clearDueDay = React.useCallback(() => setDueDay(null), []);
  const pickDueDay = React.useCallback(
    (day: string) => {
      setDueDay(day);
      clearRowError("dueDate");
    },
    [clearRowError]
  );
  const duePicker = useDueDatePicker({
    value: dueDay,
    title: t("ticketFieldDueDate"),
    onPick: pickDueDay,
    onClear: clearDueDay,
  });

  const openSheet = (sheet: NewTicketSheet) => router.push(newTicketSheetHref(sheet));

  const changeType = (next: string) => {
    setType(next);
    setPriority((current) => priorityForType(next, current));
  };

  const changeRank = (next: string) => {
    setRank(next);
    setErrors((current) => ({ ...current, missionRank: null }));
  };

  const onEditField = (field: TicketField) => {
    if (!boardId) return;
    if (field === "assignees") openSheet("assignees");
    else if (field === "labels") openSheet("labels");
    else if (field === "dueDate") duePicker.open();
    else if (field === "sprint") {
      withOptions(sprints, (list: Sprint[]) =>
        setPicker(
          sprintPicker(t, list, placement, (choice) => {
            setPlacementChoice({ boardId, value: choice.id });
            clearRowError("sprint");
          })
        )
      );
    } else if (field === "milestone") {
      withOptions(milestones, (list: Milestone[]) => {
        const next: TicketPicker | null = milestonePicker(
          t,
          list,
          milestoneId,
          (milestone) => {
            setMilestoneChoice({ boardId, value: milestone.id });
            clearRowError("milestone");
          },
          () => setMilestoneChoice({ boardId, value: null })
        );
        if (next) setPicker(next);
        else toast.info(t("ticketMilestonesEmpty"));
      });
    } else if (field === "project") {
      withOptions(projects, (list: BoardOption[]) => {
        const next: TicketPicker | null = projectPicker(
          t,
          list,
          projectId,
          (project) => {
            setProjectChoice({ boardId, value: project.id });
            clearRowError("project");
          },
          () => setProjectChoice({ boardId, value: null })
        );
        if (next) setPicker(next);
        else toast.info(t("ticketProjectsEmpty"));
      });
    }
  };

  // Marks every field Create cannot send yet, and brings the topmost one into view.
  const showProblems = (problems: DraftProblems) => {
    setErrors({
      ...NO_CREATE_ERRORS,
      title:
        problems.title === "required"
          ? t("newTicketTitleRequired")
          : problems.title === "too-long"
            ? t("newTicketTitleTooLong", { max: TICKET_TITLE_MAX })
            : null,
      description: problems.description
        ? t("newTicketDescriptionTooLong", { max: TICKET_DESCRIPTION_MAX })
        : null,
      missionRank: problems.missionRank ? t("newTicketMissionRankRequired") : null,
    });
    const first: DraftProblemField | null = firstProblemField(problems);
    if (first === "title") {
      form.current?.scrollTo("header");
      titleRef.current?.focus();
    } else if (first) {
      Keyboard.dismiss();
      form.current?.scrollTo(first === "missionRank" ? "header" : "description");
    }
  };

  /**
   * Whether Create may go on with the staged files: none is already attached
   * to a ticket. A duplicate is named in a toast and nothing is created.
   */
  const noDuplicateFiles = async (files: readonly ChatLocalFile[]): Promise<boolean> => {
    if (files.length === 0) return true;
    setPhase("checking");
    try {
      const names: string[] = await duplicateAttachmentNames(
        files,
        ticketAttachmentExists,
        // An unknown answer does not block Create; the upload after it meets
        // the server's own refusal, which the result toast names.
        (file, error) =>
          console.warn(
            `[ticket] the duplicate check of ${file.name} failed; creating anyway`,
            errorText(error)
          )
      );
      if (names.length === 0) return true;
      toast.error(t("newTicketAttachmentsDuplicate", { names: names.join(", ") }));
      return false;
    } finally {
      setPhase("idle");
    }
  };

  const submit = () => {
    if (!ready || !boardId || phase !== "idle") return;
    if (!online()) return;
    const problems: DraftProblems | null = draftProblems({
      title,
      description,
      missionRank: rank,
    });
    if (problems) {
      showProblems(problems);
      return;
    }
    const body: TicketCreateBody | null = createTicketBody(
      {
        title,
        description,
        type,
        priority,
        status,
        missionRank: rank,
        placement,
        assigneeIds,
        labelIds,
        dueDay,
        milestoneId,
        projectId,
      },
      {
        backlogId: backlog.data?.id ?? null,
        parentTicketId: isSubTicket ? (parentRef.data?.ticketId ?? null) : null,
      }
    );
    if (!body) {
      toast.error(t("newTicketBacklogUnavailable"));
      return;
    }
    void noDuplicateFiles(stagedFiles).then((clear: boolean) => {
      if (!clear) return;
      // An incident created as Done must already document its cause, so its
      // root cause analysis and lessons learned are asked for first; a cancel
      // creates nothing.
      if (requiresIncidentClose(body.type, undefined, body.status)) {
        void askIncidentClose().then((texts) => {
          if (texts) send(boardId, { ...body, ...texts });
        });
        return;
      }
      send(boardId, body);
    });
  };

  /** Uploads the staged files to the new ticket; gives the failure, or null. */
  const uploadStagedFiles = async (
    ticketId: string,
    files: readonly ChatLocalFile[]
  ): Promise<unknown> => {
    if (files.length === 0) return null;
    try {
      await uploadTicketAttachments(ticketId, files);
      return null;
    } catch (error) {
      console.warn("[ticket] uploading the new ticket's attachments failed", errorText(error));
      return error;
    }
  };

  /** The one result toast of Create: success, or a warning naming what the ticket lacks and why. */
  const reportCreated = (
    ticket: TicketDetail,
    images: CreatedDescriptionOutcome,
    attachmentError: unknown
  ) => {
    const key: NewTicketResultKey = newTicketResultKey(
      images.imageError !== null || images.saveError !== null,
      attachmentError !== null
    );
    if (key === "newTicketCreated") {
      toast.success(t(key, { key: ticket.key }));
      return;
    }
    const reasons: string[] = [];
    if (images.imageError !== null) {
      console.warn("[ticket] adding the new ticket's images failed", errorText(images.imageError));
      reasons.push(followUpText(t, imageFailureMessage(images.imageError)));
    }
    if (images.saveError !== null) reasons.push(t("newTicketDescriptionSaveFailed"));
    if (attachmentError !== null) {
      reasons.push(followUpText(t, ticketAttachmentErrorMessage(attachmentError, "upload")));
    }
    toast.warning(t(key, { key: ticket.key }), { description: reasons.join("\n") });
  };

  /**
   * What Create does once the ticket exists: the description images and the
   * staged files, side by side, as both need only the ticket's id. The
   * ticket stays created whatever they do, so Create is not offered again.
   */
  const finishCreate = async (
    ticket: TicketDetail,
    draft: {
      description: string;
      images: readonly PendingImage[];
      files: readonly ChatLocalFile[];
      another: boolean;
    }
  ) => {
    setPhase("finishing");
    forgetIncidentCloseDraft(NEW_INCIDENT_DRAFT);
    const [imageOutcome, attachmentError] = await Promise.all([
      commitCreatedDescription({
        markdown: draft.description,
        images: draft.images,
        upload: async (image: PendingImage) =>
          storedImage(await uploadTicketAttachments(ticket.id, [image.file])),
        save: async (text: string) => {
          await saveDescription.mutateAsync({ ticket, description: text });
        },
      }),
      uploadStagedFiles(ticket.id, draft.files),
    ]);
    // The uploads bypass the attachment hooks, so a cached list would be stale.
    if (draft.images.length > 0 || draft.files.length > 0) {
      void invalidateTicketAttachments(queryClient, orgId, ticket.id);
    }
    reportCreated(ticket, imageOutcome, attachmentError);
    setPhase("idle");
    if (draft.another) {
      setTitle("");
      setDescription("");
      setImages(NO_IMAGES);
      setStagedFiles(NO_FILES);
      previews.current.clear();
      setErrors(NO_CREATE_ERRORS);
      setRound((value) => value + 1);
    } else {
      setCreated(ticket);
    }
  };

  const send = (targetBoardId: string, body: TicketCreateBody) => {
    create.mutate(
      { boardId: targetBoardId, body },
      {
        onSuccess: (ticket: TicketDetail) => {
          void finishCreate(ticket, {
            description,
            images,
            files: stagedFiles,
            another: createAnother,
          });
        },
        onError: (failure: Error) => {
          const apiError: ApiError = toApiError(failure);
          if (apiError.kind !== "validation") {
            fail(apiError, "post", submit);
            return;
          }
          // Refused fields show under their field or row; the type, priority or status in a toast.
          const sorted: CreateFieldErrors = createFieldErrors(apiError.fieldErrors);
          setErrors(sorted);
          const shownInPlace: boolean =
            !!sorted.title ||
            !!sorted.description ||
            !!sorted.missionRank ||
            Object.keys(sorted.rows).length > 0;
          if (sorted.others.length > 0 || !shownInPlace) {
            toast.error(sorted.others.join("\n") || apiError.message);
          }
        },
      }
    );
  };

  const optionsFailed: boolean =
    tree.isError ||
    sprints.isError ||
    backlog.isError ||
    members.isError ||
    labels.isError ||
    milestones.isError ||
    projects.isError;
  const retryOptions = () => {
    if (tree.isError) void tree.refetch();
    if (sprints.isError) void sprints.refetch();
    if (backlog.isError) void backlog.refetch();
    if (members.isError) void members.refetch();
    if (labels.isError) void labels.refetch();
    if (milestones.isError) void milestones.refetch();
    if (projects.isError) void projects.refetch();
  };
  const readOnlyBoard: boolean = members.isSuccess && !!meId && !canWrite;
  const noBoards: boolean = !isSubTicket && tree.isSuccess && rows.length === 0;
  const boardFixed: boolean = isSubTicket || !tree.isSuccess || rows.length === 0;

  const headerRight = () => (
    <HeaderSaveButton
      label={t("newTicketCreate")}
      accessibilityLabel={t("newTicketCreate")}
      disabled={!ready}
      saving={create.isPending || phase !== "idle" || created !== null}
      onPress={submit}
    />
  );

  return (
    <>
      <Stack.Screen options={{ title: t("newTicketTitle"), headerRight }} />
      <TicketForm
        ref={form}
        headerKey={round}
        header={{
          ticket: view.header,
          titleInput: {
            value: title,
            onChange: (text) => {
              setTitle(text);
              setErrors((current) => ({ ...current, title: null }));
            },
            error: errors.title,
            inputRef: titleRef,
          },
          notice: type === "INCIDENT" ? t("newTicketIncidentHint") : undefined,
          onChangeType: () => openChoice(typePicker(t, type, changeType)),
          onChangePriority: () => openChoice(priorityPicker(t, type, priority, setPriority)),
          // In the backlog the picker shows why the status stays Todo.
          onChangeStatus: () =>
            openChoice(statusPicker(t, view.header.status, setStatus, !statusPickable)),
          onOpenSizing: editable ? () => openChoice(rankPicker(t, rank, changeRank)) : undefined,
          rankError: errors.missionRank,
        }}
        fields={{
          ticket: view.fields,
          onEditField: editable ? onEditField : undefined,
          editableFields: editable ? DRAFT_FIELDS : NO_FIELDS,
          board: {
            name: board?.name,
            note: readOnlyBoard ? t("newTicketBoardReadOnly") : undefined,
            onPress: boardFixed
              ? undefined
              : () => setPicker(boardPicker(t, rows, boardId, setPickedBoardId)),
          },
          parentKey: preset.parentKey,
          errors: errors.rows,
          showReporter: false,
        }}
        dueCalendar={duePicker.calendar}
        description={{
          description,
          onEdit: editable ? () => openSheet("description") : undefined,
          error: errors.description,
          resolveImage,
        }}
        picker={picker}
        onClosePicker={closePicker}
        top={
          <>
            {parentRef.isError ? (
              <InlineError
                message={t("newTicketParentError")}
                onRetry={() => void parentRef.refetch()}
                className="mx-4 mt-4"
              />
            ) : null}
            {optionsFailed ? (
              <InlineError
                message={t("newTicketOptionsError")}
                onRetry={retryOptions}
                className="mx-4 mt-4"
              />
            ) : null}
            {noBoards ? (
              <Text className="px-6 pt-4 font-sans text-subhead text-muted-foreground">
                {t("newTicketNoBoards")}
              </Text>
            ) : null}
          </>
        }
      >
        {/* Remounted after "Create another", so the next ticket starts without staged files. */}
        <TicketAttachments key={round} canWrite={editable} onStagedFilesChange={setStagedFiles} />
        <ListSection className="mt-0">
          <ListRow
            title={t("newTicketCreateAnother")}
            accessory="switch"
            checked={createAnother}
            onCheckedChange={setCreateAnother}
            wrap
          />
        </ListSection>
      </TicketForm>
    </>
  );
}
