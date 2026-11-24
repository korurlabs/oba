"use client";

import { useMemo, useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { useQueryClient } from "@tanstack/react-query";
import { format, parseISO } from "date-fns";
import { getDateFnsLocale } from "@oba/shared/lib/date-fns-locale";
import { HOUR_CYCLE } from "@oba/shared/i18n/use-date-format";
import { AlertCircle, Loader2, UserX } from "lucide-react";
import { useGetMe } from "@oba/shared/features/users/hooks/use-get-me";
import type { UserOrganization } from "@oba/shared/features/users/schema";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { DatePicker } from "@/components/ui/date-picker";
import { Label } from "@/components/ui/label";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { UserAvatar } from "@/components/ui/user-avatar";
import { UPDATE_KEYS, useMissingUpdates } from "../hooks/use-updates";
import { adminOrganizations, defaultAdminOrganizationId } from "../lib/organization-admin";
import { updatePreviousDay, updateToday } from "../lib/update-day";
import { UPDATE_DEADLINE_TIME_ZONE, type UpdateMissingMember } from "../schemas";

const DATE_PARAM_FORMAT = "yyyy-MM-dd";

function memberName(member: UpdateMissingMember): string {
  return [member.firstName, member.lastName].filter(Boolean).join(" ") || member.email;
}

/**
 * "Not posted" in the Updates toolbar: who in an organization the reader
 * administers has not posted a daily update by the deadline. Renders nothing
 * for a reader who is OWNER or ADMIN of no organization, so other readers
 * never send the request.
 */
export function MissingUpdatesButton() {
  const { data: meResponse } = useGetMe();
  const me = meResponse?.success ? meResponse.data : undefined;
  const organizations = useMemo(() => adminOrganizations(me), [me]);
  const defaultOrganizationId = defaultAdminOrganizationId(me);

  if (organizations.length === 0 || !defaultOrganizationId) return null;
  return (
    <MissingUpdatesPopover
      organizations={organizations}
      defaultOrganizationId={defaultOrganizationId}
    />
  );
}

interface MissingUpdatesPopoverProps {
  organizations: UserOrganization[];
  defaultOrganizationId: string;
}

function MissingUpdatesPopover({ organizations, defaultOrganizationId }: MissingUpdatesPopoverProps) {
  const t = useTranslations("updates");
  const locale = useLocale();
  const dateLocale = getDateFnsLocale(locale);
  const queryClient = useQueryClient();

  // A choice that is no longer administered (after "me" refetched) falls
  // back to the default.
  const [selectedOrganizationId, setSelectedOrganizationId] = useState<string | null>(null);
  const organizationId =
    selectedOrganizationId &&
    organizations.some((organization) => organization.id === selectedOrganizationId)
      ? selectedOrganizationId
      : defaultOrganizationId;

  // Null means today: the server then decides the day in the deadline's zone.
  const [date, setDate] = useState<string | null>(null);

  const query = useMissingUpdates(organizationId, date);
  const report = query.data?.data;
  const notPosted = report?.members.filter((member) => !member.postedAt) ?? [];
  const showCount = !!report && report.workday && !query.isError;

  const handleOpenChange = (open: boolean) => {
    // A list fetched before 10:30 is headed "not posted yet"; reopening it
    // later must show the passed deadline, so a stale list is refetched.
    if (open) {
      void queryClient.refetchQueries({
        queryKey: UPDATE_KEYS.missing(organizationId, date),
        type: "active",
        stale: true,
      });
    }
  };

  // The server refuses a day after today in the deadline's zone, so the picker stops there.
  const today = updateToday();
  const yesterday = updatePreviousDay(today);
  const selectedDay = date ?? today;

  return (
    <Popover onOpenChange={handleOpenChange}>
      <PopoverTrigger asChild>
        <Button variant="outline" size="sm" className="border-border h-8 gap-1.5 text-xs">
          <UserX className="h-3.5 w-3.5" />
          {t("missing.action")}
          {query.isError ? (
            <AlertCircle className="text-destructive h-3.5 w-3.5" aria-label={t("missing.loadError")} />
          ) : (
            showCount && (
              <Badge
                variant={report.deadlinePassed ? "destructive" : "secondary"}
                className="h-4 px-1.5 font-mono text-[10px] tabular-nums"
              >
                {notPosted.length}
              </Badge>
            )
          )}
        </Button>
      </PopoverTrigger>
      <PopoverContent align="end" className="w-80 space-y-3 p-3">
        {organizations.length > 1 && (
          <div className="space-y-2">
            <Label>{t("missing.organization")}</Label>
            <Select value={organizationId} onValueChange={setSelectedOrganizationId}>
              <SelectTrigger size="sm" className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {organizations.map((organization) => (
                  <SelectItem key={organization.id} value={organization.id}>
                    {organization.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        )}
        <DatePicker
          label={t("missing.date")}
          locale={dateLocale}
          value={parseISO(selectedDay)}
          onChange={(picked) => {
            const key = picked ? format(picked, DATE_PARAM_FORMAT) : null;
            setDate(key && key !== today ? key : null);
          }}
          toDate={parseISO(today)}
          isClearable={false}
          dateFormat="PP"
        />
        <div className="flex gap-2">
          {[
            { key: yesterday, label: t("missing.yesterday") },
            { key: today, label: t("missing.today") },
          ].map((shortcut) => {
            const active = selectedDay === shortcut.key;
            return (
              <Button
                key={shortcut.key}
                type="button"
                variant={active ? "default" : "outline"}
                size="sm"
                className="h-7 flex-1 text-xs"
                aria-pressed={active}
                onClick={() => setDate(shortcut.key === today ? null : shortcut.key)}
              >
                {shortcut.label}
              </Button>
            );
          })}
        </div>
        <p className="text-muted-foreground text-xs">{t("missing.deadline")}</p>
        <MissingUpdatesReport
          query={query}
          notPosted={notPosted}
          late={report?.members.filter((member) => !!member.postedAt) ?? []}
        />
      </PopoverContent>
    </Popover>
  );
}

interface MissingUpdatesReportProps {
  query: ReturnType<typeof useMissingUpdates>;
  notPosted: UpdateMissingMember[];
  late: UpdateMissingMember[];
}

function MissingUpdatesReport({ query, notPosted, late }: MissingUpdatesReportProps) {
  const t = useTranslations("updates");
  const report = query.data?.data;

  if (query.isError) {
    return (
      <div className="border-destructive/30 bg-destructive/5 space-y-2 rounded-md border p-2 text-sm">
        <div className="flex items-start gap-2">
          <AlertCircle className="text-destructive mt-0.5 h-4 w-4 shrink-0" />
          <div className="min-w-0 space-y-0.5">
            <p className="text-foreground font-medium">{t("missing.loadError")}</p>
            <p className="text-muted-foreground break-words text-xs">{query.error.message}</p>
          </div>
        </div>
        <Button
          variant="outline"
          size="sm"
          className="h-7 text-xs"
          onClick={() => void query.refetch()}
          disabled={query.isFetching}
        >
          {t("retry")}
        </Button>
      </div>
    );
  }

  if (!report) {
    return (
      <div className="flex justify-center py-4">
        <Loader2 className="text-muted-foreground h-4 w-4 animate-spin" />
      </div>
    );
  }

  if (!report.workday) {
    return <p className="text-muted-foreground py-2 text-sm">{t("missing.weekend")}</p>;
  }

  if (notPosted.length === 0 && late.length === 0) {
    return <p className="text-muted-foreground py-2 text-sm">{t("missing.allPosted")}</p>;
  }

  return (
    <div className="max-h-96 space-y-3 overflow-y-auto">
      {notPosted.length > 0 && (
        <MissingUpdatesSection
          title={report.deadlinePassed ? t("missing.title") : t("missing.titlePending")}
          countVariant={report.deadlinePassed ? "destructive" : "secondary"}
          members={notPosted}
        />
      )}
      {late.length > 0 && (
        <MissingUpdatesSection title={t("missing.late")} countVariant="secondary" members={late} />
      )}
    </div>
  );
}

interface MissingUpdatesSectionProps {
  title: string;
  countVariant: "destructive" | "secondary";
  members: UpdateMissingMember[];
}

function MissingUpdatesSection({ title, countVariant, members }: MissingUpdatesSectionProps) {
  const t = useTranslations("updates");
  const locale = useLocale();
  // The deadline rule is set in one zone, so posting times are shown there too.
  const timeFormat = useMemo(
    () =>
      new Intl.DateTimeFormat(locale, {
        hour: "2-digit",
        minute: "2-digit",
        hourCycle: HOUR_CYCLE,
        timeZone: UPDATE_DEADLINE_TIME_ZONE,
      }),
    [locale],
  );

  return (
    <section className="space-y-2">
      <div className="flex items-center gap-2">
        <h3 className="text-foreground text-sm font-medium">{title}</h3>
        <Badge variant={countVariant} className="h-4 px-1.5 font-mono text-[10px] tabular-nums">
          {members.length}
        </Badge>
      </div>
      <ul className="space-y-2">
        {members.map((member) => (
          <li key={member.userId} className="flex items-center gap-2">
            <UserAvatar
              user={{
                firstName: member.firstName ?? undefined,
                lastName: member.lastName ?? undefined,
                email: member.email,
              }}
              size="sm"
              showName={false}
            />
            <div className="min-w-0 flex-1">
              <p className="text-foreground truncate text-sm">{memberName(member)}</p>
              <p className="text-muted-foreground truncate text-xs">{member.email}</p>
            </div>
            {member.postedAt && (
              <span className="text-muted-foreground shrink-0 text-xs">
                {t("missing.postedAt", { time: timeFormat.format(new Date(member.postedAt)) })}
              </span>
            )}
          </li>
        ))}
      </ul>
    </section>
  );
}
