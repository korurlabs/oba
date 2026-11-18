"use client";

import { createContext, useContext } from "react";
import { useMutation, useQuery, useQueryClient, type QueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { isApiError } from "@oba/shared/api/axios";
import { usePathname } from "@oba/shared/libs/i18n/navigation";
import {
  addChatChannelMembers,
  createChatChannel,
  deleteChatChannel,
  listChatChannels,
  removeChatChannelMember,
  requireChatData,
  updateChatChannel,
} from "../api";
import { useKaizenOpen } from "@/components/layout/use-kaizen-access";
import { chatRouteChannel } from "../lib/chat-route";
import {
  CHAT_CHANNEL_DIALOG_ERROR_CODES,
  CHAT_ERROR_CODES,
  type ChatChannelCreateRequest,
  type ChatChannelResponse,
  type ChatChannelUpdateRequest,
} from "../schemas";
import { CHAT_KEYS } from "./query-keys";
import { useChatMeId, useChatOrganizationId } from "./use-chat-messages";

// Channel writes show their result in the channel list and the open dialog,
// so they confirm nothing with a success toast. Every write is sent from a
// dialog that explains the refusals of `CHAT_CHANNEL_DIALOG_ERROR_CODES` in
// the reader's language next to the form; any other failure is toasted with
// the server's message, so each failure is shown once.

function toastError(error: Error): void {
  if (isApiError(error) && error.code && CHAT_CHANNEL_DIALOG_ERROR_CODES.has(error.code)) return;
  toast.error(error.message);
}

/**
 * The failure of a create or a change. A refused purpose means the cached
 * list missed another holder (it was set by someone else since), so the list
 * is refetched and the dialog then names that channel.
 */
function onChannelWriteError(queryClient: QueryClient, orgId: string | undefined, error: Error): void {
  toastError(error);
  if (orgId && isApiError(error) && error.code === CHAT_ERROR_CODES.channelPurposeTaken) {
    void queryClient.invalidateQueries({ queryKey: CHAT_KEYS.channels(orgId) });
  }
}

/** Organization of a mutation; a write without one would go to no organization. */
function requireOrgId(orgId: string | undefined): string {
  if (!orgId) throw new Error("No active organization for the chat");
  return orgId;
}

/** Puts the server's copy of a channel into the cached list, in place. */
function replaceChannel(queryClient: QueryClient, orgId: string, channel: ChatChannelResponse): void {
  queryClient.setQueryData<ChatChannelResponse[]>(CHAT_KEYS.channels(orgId), (channels) =>
    channels?.map((existing) => (existing.id === channel.id ? channel : existing)),
  );
}

/**
 * Drops the cached content of a channel the reader can no longer read
 * (deleted, or left): its message lists, pins, thread lists and member lists.
 * A later read of it is 404 `KAI_NOT_015`, so nothing is refetched.
 */
function removeChannelContent(queryClient: QueryClient, orgId: string, channelId: string): void {
  queryClient.removeQueries({ queryKey: CHAT_KEYS.channel(orgId, channelId) });
  queryClient.removeQueries({ queryKey: CHAT_KEYS.pins(orgId, channelId) });
  queryClient.removeQueries({ queryKey: CHAT_KEYS.channelThreads(orgId, channelId) });
  queryClient.removeQueries({ queryKey: CHAT_KEYS.channelMembers(orgId, channelId) });
}

/** The channel's member and author lists, whose content follows its member list and visibility. */
function invalidateChannelMembers(queryClient: QueryClient, orgId: string, channelId: string): Promise<void> {
  return queryClient.invalidateQueries({ queryKey: CHAT_KEYS.channelMembers(orgId, channelId) });
}

// ── Queries ─────────────────────────────────────────────────────────

/**
 * The channels the reader sees, in creation order: every public channel, the
 * private channels the reader is on, and for OWNER and ADMIN every private
 * channel (`member` false until they add themselves). The sidebar reads it on
 * every page, so it waits until Kaizen is open in the organization.
 */
export function useChatChannels() {
  const orgId = useChatOrganizationId();
  const kaizenOpen = useKaizenOpen();
  return useQuery({
    queryKey: CHAT_KEYS.channels(orgId ?? ""),
    queryFn: async () => requireChatData(await listChatChannels()),
    enabled: !!orgId && kaizenOpen,
  });
}

/**
 * The channel the chat view shows: the one its path names
 * (`/chats/<slug>`, `/daily-updates`, `/pull-requests`), looked up in the
 * cached channel list. Undefined until the list is loaded, on `/chats` and
 * any other path, and for a slug no listed channel holds; the chat view then
 * replaces the URL with the channel to open. Until then the queries of a
 * channel stay disabled, so no render reads a channel the reader would not
 * open.
 */
export function useChatOpenChannelId(): string | undefined {
  const pathname = usePathname();
  const { data: channels } = useChatChannels();
  return channels ? chatRouteChannel(pathname, channels)?.id : undefined;
}

const ChatReadableChannelContext = createContext<string | undefined>(undefined);

/** Given by the chat view: the ID of the open channel once the reader can read it. */
export const ChatReadableChannelProvider = ChatReadableChannelContext.Provider;

/**
 * The open channel the reader can read, for everything that writes to it
 * (send, attachments, typing, the outbox, marking read). Unlike
 * `useChatOpenChannelId` it is undefined while the path names a channel the
 * reader cannot read or has lost, so no write goes to a channel the view is
 * about to replace.
 */
export function useChatReadableChannelId(): string | undefined {
  return useContext(ChatReadableChannelContext);
}

// ── Mutations (OWNER and ADMIN, except leaving) ─────────────────────

export function useCreateChatChannel() {
  const queryClient = useQueryClient();
  const orgId = useChatOrganizationId();

  return useMutation({
    mutationFn: async (data: ChatChannelCreateRequest) => requireChatData(await createChatChannel(data)),
    onSuccess: async () => {
      const organizationId = requireOrgId(orgId);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: CHAT_KEYS.channels(organizationId) }),
        queryClient.invalidateQueries({ queryKey: CHAT_KEYS.unread(organizationId) }),
      ]);
    },
    onError: (error: Error) => onChannelWriteError(queryClient, orgId, error),
  });
}

export interface ChatChannelUpdateVariables {
  channelId: string;
  data: ChatChannelUpdateRequest;
}

/**
 * Renames a channel and sets its visibility and purpose. A visibility change
 * changes who reads the channel, so the list, the counts and the channel's
 * member lists are refetched.
 */
export function useUpdateChatChannel() {
  const queryClient = useQueryClient();
  const orgId = useChatOrganizationId();

  return useMutation({
    mutationFn: async ({ channelId, data }: ChatChannelUpdateVariables) =>
      requireChatData(await updateChatChannel(channelId, data)),
    onSuccess: async (channel: ChatChannelResponse) => {
      const organizationId = requireOrgId(orgId);
      replaceChannel(queryClient, organizationId, channel);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: CHAT_KEYS.channels(organizationId) }),
        queryClient.invalidateQueries({ queryKey: CHAT_KEYS.unread(organizationId) }),
        invalidateChannelMembers(queryClient, organizationId, channel.id),
      ]);
    },
    onError: (error: Error) => onChannelWriteError(queryClient, orgId, error),
  });
}

/** Deletes a channel for everyone; its content is hidden and kept on the server. */
export function useDeleteChatChannel() {
  const queryClient = useQueryClient();
  const orgId = useChatOrganizationId();

  return useMutation({
    mutationFn: async (channelId: string) => {
      await deleteChatChannel(channelId);
    },
    onSuccess: async (_: void, channelId: string) => {
      const organizationId = requireOrgId(orgId);
      removeChannelContent(queryClient, organizationId, channelId);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: CHAT_KEYS.channels(organizationId) }),
        queryClient.invalidateQueries({ queryKey: CHAT_KEYS.unread(organizationId) }),
        queryClient.invalidateQueries({ queryKey: CHAT_KEYS.searchAll(organizationId) }),
      ]);
    },
    onError: toastError,
  });
}

export interface ChatChannelMembersVariables {
  channelId: string;
  userIds: string[];
}

/** Adds users to a private channel's member list. */
export function useAddChatChannelMembers() {
  const queryClient = useQueryClient();
  const orgId = useChatOrganizationId();

  return useMutation({
    mutationFn: async ({ channelId, userIds }: ChatChannelMembersVariables) =>
      requireChatData(await addChatChannelMembers(channelId, { userIds })),
    onSuccess: async (channel: ChatChannelResponse) => {
      const organizationId = requireOrgId(orgId);
      replaceChannel(queryClient, organizationId, channel);
      // An admin who added themselves now reads the channel and has counts for it.
      await Promise.all([
        invalidateChannelMembers(queryClient, organizationId, channel.id),
        queryClient.invalidateQueries({ queryKey: CHAT_KEYS.unread(organizationId) }),
      ]);
    },
    onError: toastError,
  });
}

export interface ChatChannelMemberVariables {
  channelId: string;
  userId: string;
}

/**
 * Removes a user from a private channel's member list (OWNER and ADMIN), or
 * lets the reader leave it (`userId` is the reader). After leaving, the
 * reader's cached content of the channel is dropped.
 */
export function useRemoveChatChannelMember() {
  const queryClient = useQueryClient();
  const orgId = useChatOrganizationId();
  const meId = useChatMeId();

  return useMutation({
    mutationFn: async ({ channelId, userId }: ChatChannelMemberVariables) => {
      await removeChatChannelMember(channelId, userId);
    },
    onSuccess: async (_: void, { channelId, userId }: ChatChannelMemberVariables) => {
      const organizationId = requireOrgId(orgId);
      if (userId === meId) {
        removeChannelContent(queryClient, organizationId, channelId);
      } else {
        await invalidateChannelMembers(queryClient, organizationId, channelId);
      }
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: CHAT_KEYS.channels(organizationId) }),
        queryClient.invalidateQueries({ queryKey: CHAT_KEYS.unread(organizationId) }),
      ]);
    },
    onError: toastError,
  });
}
