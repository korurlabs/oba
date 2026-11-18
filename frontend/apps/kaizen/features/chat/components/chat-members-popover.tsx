"use client";

import { useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import { AlertCircle, Loader2, Users } from "lucide-react";
import { cn } from "@oba/ui/lib/utils";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { getDisplayName, UserAvatar } from "@/components/ui/user-avatar";
import { useChatMembers } from "../hooks/use-chat-messages";
import {
  ChatChannelVisibility,
  type ChatChannelResponse,
  type ChatMemberResponse,
} from "../schemas";

function memberUser(member: ChatMemberResponse) {
  return {
    firstName: member.firstName ?? undefined,
    lastName: member.lastName ?? undefined,
    email: member.email ?? undefined,
  };
}

/**
 * The "Members" control of the channel header: who reads the open channel,
 * online first, each with a green dot while one of their sockets is
 * connected. A public channel is read by the organization's active members; a
 * private one by its member list. The server sorts the same way, but PRESENCE
 * signals patch `online` in place, so the groups are rebuilt here on every
 * change.
 */
export function ChatMembersPopover({ channel }: { channel: ChatChannelResponse }) {
  const t = useTranslations("chat.members");
  const [open, setOpen] = useState(false);

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          size="sm"
          className="border-border h-8 gap-1.5 text-xs"
          aria-label={t("button")}
        >
          <Users className="h-3.5 w-3.5" />
          <span className="hidden sm:inline">{t("button")}</span>
        </Button>
      </PopoverTrigger>
      <PopoverContent
        align="end"
        className="flex w-[min(20rem,calc(100vw-2rem))] flex-col gap-2 p-3"
      >
        <h2 className="text-foreground text-sm font-semibold">{t("title")}</h2>
        <MemberList
          enabled={open}
          channelId={channel.visibility === ChatChannelVisibility.PRIVATE ? channel.id : null}
        />
      </PopoverContent>
    </Popover>
  );
}

function MemberList({ enabled, channelId }: { enabled: boolean; channelId: string | null }) {
  const t = useTranslations("chat.members");
  const {
    data: listed,
    error,
    isPending,
    isError,
    isFetching,
    refetch,
  } = useChatMembers({ enabled, channelId });
  // PRESENCE signals patch the organization's list; a private channel's list takes `online` from it.
  const { data: organizationMembers } = useChatMembers({ enabled: enabled && channelId !== null });
  const members = useMemo(() => {
    if (channelId === null || !listed || !organizationMembers) return listed;
    const online = new Map(organizationMembers.map((member) => [member.userId, member.online]));
    return listed.map((member) => ({
      ...member,
      online: online.get(member.userId) ?? member.online,
    }));
  }, [channelId, listed, organizationMembers]);

  const groups = useMemo(() => {
    const byName = (a: ChatMemberResponse, b: ChatMemberResponse) =>
      getDisplayName(memberUser(a)).localeCompare(getDisplayName(memberUser(b)), undefined, {
        sensitivity: "base",
      });
    const all = members ?? [];
    return {
      online: all.filter((member) => member.online).sort(byName),
      offline: all.filter((member) => !member.online).sort(byName),
    };
  }, [members]);

  if (isPending) {
    return (
      <div className="text-muted-foreground flex items-center justify-center gap-2 py-8 text-xs">
        <Loader2 className="h-4 w-4 animate-spin" />
        {t("loading")}
      </div>
    );
  }

  if (isError && !members) {
    return (
      <div role="alert" className="flex flex-col items-center gap-2 py-6 text-center">
        <AlertCircle className="text-destructive h-5 w-5" />
        <p className="text-foreground text-xs font-medium">{t("loadError")}</p>
        {error?.message && <p className="text-muted-foreground text-xs">{error.message}</p>}
        <Button
          variant="outline"
          size="sm"
          className="h-7"
          disabled={isFetching}
          onClick={() => void refetch()}
        >
          {t("retry")}
        </Button>
      </div>
    );
  }

  if (!members || members.length === 0) {
    return <p className="text-muted-foreground py-8 text-center text-xs">{t("empty")}</p>;
  }

  return (
    <div className="flex max-h-[60vh] flex-col gap-3 overflow-y-auto">
      {groups.online.length > 0 && (
        <MemberGroup title={t("online", { count: groups.online.length })} members={groups.online} />
      )}
      {groups.offline.length > 0 && (
        <MemberGroup
          title={t("offline", { count: groups.offline.length })}
          members={groups.offline}
        />
      )}
    </div>
  );
}

function MemberGroup({ title, members }: { title: string; members: ChatMemberResponse[] }) {
  const t = useTranslations("chat.members");
  const tRoles = useTranslations("organization.members.roles");

  return (
    <section className="flex flex-col gap-0.5">
      <h3 className="text-muted-foreground px-2 text-xs font-semibold tracking-wide uppercase">
        {title}
      </h3>
      <ul className="flex flex-col">
        {members.map((member) => {
          const user = memberUser(member);
          const name = getDisplayName(user);
          return (
            <li key={member.userId} className="flex items-center gap-2.5 rounded-md px-2 py-1.5">
              <span className="relative inline-flex shrink-0">
                <UserAvatar user={user} size="sm" showName={false} />
                <span
                  className={cn(
                    "ring-popover absolute -right-0.5 -bottom-0.5 size-2.5 rounded-full ring-2",
                    member.online ? "bg-emerald-500" : "bg-muted-foreground/40"
                  )}
                  aria-hidden
                />
              </span>
              <span className={cn("flex min-w-0 flex-1 flex-col", !member.online && "opacity-70")}>
                <span className="text-foreground truncate text-sm font-medium">
                  {name}
                  <span className="sr-only">
                    , {member.online ? t("statusOnline") : t("statusOffline")}
                  </span>
                </span>
                {member.email && member.email !== name && (
                  <span className="text-muted-foreground truncate text-xs">{member.email}</span>
                )}
              </span>
              {member.role !== "MEMBER" && (
                <Badge variant="outline" className="shrink-0 px-1.5 py-0 text-[10px] font-medium">
                  {tRoles(member.role)}
                </Badge>
              )}
            </li>
          );
        })}
      </ul>
    </section>
  );
}
