import { useRouter } from "expo-router";
import { AppWindow, Siren, Ticket } from "lucide-react-native";
import type * as React from "react";
import { View } from "react-native";

import { useHomeLinks } from "@/components/home/homeNavigation";
import { BottomSheet, useSheet } from "@/components/kazan/BottomSheet";
import { InlineError } from "@/components/kazan/EmptyState";
import { ListRow, ListSection } from "@/components/kazan/List";
import { Skeleton } from "@/components/ui/skeleton";
import { useHomeSprint, useHomeSummary } from "@/hooks/queries/home";
import { useTranslation, type TranslationKey } from "@/i18n";
import { KORUR_APPS } from "@/lib/browser";

/** The description of each Korur app, by its id in `KORUR_APPS`. */
const APP_DESCRIPTION: Record<string, TranslationKey> = {
  kagi: "homeLinksAppKagi",
  kaya: "homeLinksAppKaya",
  kazan: "homeLinksAppKazan",
  kalkan: "homeLinksAppKalkan",
  betiks: "homeLinksAppBetiks",
};

/** The Links sheet: Kazan shortcuts with live counts, and the other Korur apps. */
export default function LinksSheet() {
  const { t } = useTranslation();
  return (
    <BottomSheet title={t("homeLinks")} cancelLabel={t("homeClose")} scroll>
      <LinksContent />
    </BottomSheet>
  );
}

function LinksContent() {
  const { t } = useTranslation();
  const router = useRouter();
  const { dismiss } = useSheet();
  const { open, openBoardPage } = useHomeLinks();
  const summary = useHomeSummary();
  // The sprint the dashboard shows; the cached read is reused, not fetched again.
  const sprintBoardSlug: string | null = useHomeSprint(null).data?.boardSlug ?? null;

  const count = (value: number | undefined): string | undefined =>
    value === undefined ? undefined : String(value);
  // A placeholder where the count goes, so the row does not look empty or
  // jump while the first read is in flight.
  const countPlaceholder: React.ReactNode = summary.isPending ? (
    <Skeleton width={20} height={14} />
  ) : undefined;

  return (
    <View className="pb-6">
      <ListSection header={t("homeLinksKazanSection")}>
        <ListRow
          title={t("homeLinksOpenIncidents")}
          icon={Siren}
          iconTone="bg-destructive"
          value={count(summary.data?.openIncidents)}
          trailing={countPlaceholder}
          onPress={() => {
            dismiss();
            router.navigate("/incidents");
          }}
        />
        <ListRow
          title={t("homeLinksOpenSprintTickets")}
          icon={Ticket}
          value={count(summary.data?.openSprintTickets)}
          trailing={countPlaceholder}
          disabled={!sprintBoardSlug}
          onPress={sprintBoardSlug ? () => openBoardPage(sprintBoardSlug, "sprint") : undefined}
        />
      </ListSection>
      {summary.isError && !summary.data ? (
        <InlineError
          message={t("homeLinksCountError")}
          onRetry={() => void summary.refetch()}
          className="mx-4 mt-2"
        />
      ) : null}
      <ListSection header={t("homeLinksApps")} footer={t("homeLinksAppsFooter")}>
        {KORUR_APPS.map((app) => (
          <ListRow
            key={app.id}
            title={app.name}
            subtitle={APP_DESCRIPTION[app.id] ? t(APP_DESCRIPTION[app.id]) : undefined}
            icon={AppWindow}
            iconTone="bg-korur-blue-deep"
            accessory="external"
            onPress={() => open(app.url)}
          />
        ))}
      </ListSection>
    </View>
  );
}
