import { useQueryClient } from "@tanstack/react-query";
import { BellRing, CalendarOff, Clock, PartyPopper, ShieldOff } from "lucide-react-native";
import * as React from "react";
import { ActivityIndicator, View } from "react-native";

import { BottomSheet } from "@/components/kaizen/BottomSheet";
import { EmptyState } from "@/components/kaizen/EmptyState";
import { ListRow, ListSection } from "@/components/kaizen/List";
import { SegmentedControl } from "@/components/ui/segmented-control";
import { markNudged, useNudgedMembers } from "@/components/updates/nudgeStore";
import {
  canSeeNotPosted,
  minutesUntilDeadline,
  missingMemberName,
  nudgeRefusal,
  splitMissingMembers,
  type NudgeRefusal,
} from "@/components/updates/updateModel";
import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { toast } from "@/components/ui/toast";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useChatMeId } from "@/hooks/queries/chat";
import { updateKeys, useMissingUpdates, useNudgeMember } from "@/hooks/queries/updates";
import { useDates } from "@/hooks/useDates";
import { useOrganizationRole } from "@/hooks/useOrganizationRole";
import { useTranslation } from "@/i18n";
import { isApiError } from "@/lib/api";
import { addDaysToDateOnly, businessToday } from "@/lib/dates";
import { useActiveOrganizationId } from "@/lib/organizationStore";
import { UPDATE_DEADLINE_TIME, type UpdateMissingMember } from "@/lib/schemas/updates";

type NotPostedDay = "yesterday" | "today";

const DAYS: readonly NotPostedDay[] = ["yesterday", "today"];

/** The countdown to the deadline is redrawn this often. */
const COUNTDOWN_TICK_MS = 30_000;

/** The current time, redrawn every `COUNTDOWN_TICK_MS` while `active`. */
function useNow(active: boolean): Date {
  const [now, setNow] = React.useState<Date>(() => new Date());
  React.useEffect(() => {
    if (!active) return;
    setNow(new Date());
    const timer = setInterval(() => setNow(new Date()), COUNTDOWN_TICK_MS);
    return () => clearInterval(timer);
  }, [active]);
  return now;
}

/**
 * Who has not posted a daily update by the 10:30 deadline (Turkey time),
 * today or yesterday, and who posted late. An organization owner or admin
 * may nudge a member who has not posted today: one push, at most once per
 * member and day, whoever sends it.
 */
export default function NotPostedSheet() {
  const { t } = useTranslation();
  const role: string | null = useOrganizationRole();
  const allowed: boolean = canSeeNotPosted(role);
  const [which, setWhich] = React.useState<NotPostedDay>("today");
  const today: string = businessToday();
  const day: string = which === "today" ? today : addDaysToDateOnly(today, -1);

  return (
    <BottomSheet title={t("updNotPostedTitle")} cancelLabel={t("chatClose")} scroll>
      {allowed ? (
        <View className="pb-8">
          <View className="px-4 pt-2">
            <SegmentedControl
              values={DAYS.map((value) => t(value === "today" ? "dateToday" : "chatYesterday"))}
              selectedIndex={DAYS.indexOf(which)}
              onChange={(index: number) => {
                const next: NotPostedDay | undefined = DAYS[index];
                if (next) setWhich(next);
              }}
            />
          </View>
          <NotPostedDayReport day={day} isToday={which === "today"} />
        </View>
      ) : (
        <EmptyState icon={ShieldOff} title={t("updNotPostedNotAllowed")} />
      )}
    </BottomSheet>
  );
}

function NotPostedDayReport({ day, isToday }: { day: string; isToday: boolean }) {
  const { t } = useTranslation();
  const colors = useThemeColors();
  const dates = useDates();
  const queryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  const meId: string | undefined = useChatMeId();
  const report = useMissingUpdates(day, true);
  const nudged: ReadonlySet<string> = useNudgedMembers(orgId, day);
  const nudge = useNudgeMember();
  const deadlinePending: boolean = isToday && !!report.data && !report.data.deadlinePassed;
  const now: Date = useNow(deadlinePending);

  const send = (member: UpdateMissingMember): void => {
    const name: string = missingMemberName(member);
    nudge.mutate(member.userId, {
      onSuccess: () => {
        if (orgId) markNudged(orgId, day, member.userId);
        toast.success(t("updNudgeSent", { name }));
      },
      onError: (error: Error) => {
        // The request is silent for the app's error listener, so every refusal is shown here.
        const refusal: NudgeRefusal = nudgeRefusal(isApiError(error) ? error.code : undefined);
        if (refusal.markNudged && orgId) markNudged(orgId, day, member.userId);
        if (refusal.refresh && orgId) {
          queryClient
            .invalidateQueries({ queryKey: updateKeys.missingAll(orgId) })
            .catch((refreshError: unknown) => {
              // The list keeps what it shows; reopening the sheet reads it again.
              console.warn(
                "[updates] refresh after a refused nudge failed",
                refreshError instanceof Error ? refreshError.message : refreshError
              );
            });
        }
        if (refusal.markNudged) toast.info(t(refusal.messageKey, { name }));
        else toast.error(t(refusal.messageKey, { name }));
      },
    });
  };

  if (report.isPending) {
    return <ActivityIndicator className="py-8" color={colors.mutedForeground} />;
  }
  if (report.isError) {
    return (
      <EmptyState
        variant="error"
        title={t("updNotPostedError")}
        onAction={() => void report.refetch()}
        actionLabel={t("chatRetry")}
      />
    );
  }

  const data = report.data;
  const minutesLeft: number | null = deadlinePending
    ? minutesUntilDeadline(data.deadline, now)
    : null;
  const deadline = (
    <View className="mx-4 mt-3 flex-row items-center gap-2 rounded-lg bg-warning-light px-3 py-2.5">
      <Icon as={Clock} size={16} className="text-warning" />
      <Text variant="footnote" className="flex-1">
        {t("updNotPostedDeadline", { time: UPDATE_DEADLINE_TIME })}
      </Text>
      {minutesLeft !== null ? (
        <Text variant="footnote" className="font-sans-semibold text-foreground">
          {t("updNotPostedMinutesLeft", { count: minutesLeft })}
        </Text>
      ) : null}
    </View>
  );

  if (!data.workday) {
    return (
      <View>
        {deadline}
        <EmptyState icon={CalendarOff} title={t("updNotPostedWeekend")} />
      </View>
    );
  }

  const { notPosted, late } = splitMissingMembers(data);
  if (notPosted.length === 0 && late.length === 0) {
    return (
      <View>
        {deadline}
        <EmptyState icon={PartyPopper} title={t("updNotPostedAllPosted")} />
      </View>
    );
  }

  return (
    <View>
      {deadline}
      {notPosted.length > 0 ? (
        <ListSection
          header={t(data.deadlinePassed ? "updNotPostedTitle" : "updNotPostedPending")}
          footer={isToday ? t("updNudgeFooter") : undefined}
        >
          {notPosted.map((member) => {
            const name: string = missingMemberName(member);
            const self: boolean = member.userId === meId;
            const done: boolean = nudged.has(member.userId);
            const trailing: React.ReactNode =
              !isToday || self ? null : done ? (
                <Text variant="footnote" className="text-muted-foreground">
                  {t("updNudged")}
                </Text>
              ) : (
                <Button
                  variant="outline"
                  size="sm"
                  icon={BellRing}
                  loading={nudge.isPending && nudge.variables === member.userId}
                  disabled={nudge.isPending}
                  accessibilityLabel={t("updNudgeA11y", { name })}
                  onPress={() => send(member)}
                >
                  {t("updNudge")}
                </Button>
              );
            return (
              <ListRow
                key={member.userId}
                title={name}
                subtitle={member.firstName || member.lastName ? member.email : undefined}
                avatar={{ name }}
                trailing={trailing}
                accessory="none"
              />
            );
          })}
        </ListSection>
      ) : null}
      {late.length > 0 ? (
        <ListSection header={t("updNotPostedLate")}>
          {late.map((member) => {
            const name: string = missingMemberName(member);
            const time: string | null = member.postedAt ? dates.time(member.postedAt) : null;
            return (
              <ListRow
                key={member.userId}
                title={name}
                avatar={{ name }}
                value={time ? t("updNotPostedPostedAt", { time }) : undefined}
                accessory="none"
              />
            );
          })}
        </ListSection>
      ) : null}
    </View>
  );
}
