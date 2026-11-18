import * as React from "react";
import { Platform, View } from "react-native";

import { filterConfirmState } from "@/components/boards/filters/filterSheetModel";
import { Button } from "@/components/ui/button";
import { Text } from "@/components/ui/text";
import { useTranslation } from "@/i18n";

type FiltersFooterProps = {
  /** The tickets the chosen filters show, or null while they load. */
  count: number | null;
  /** How many filters are set; Reset is off at 0. */
  activeCount: number;
  onApply: () => void;
  onReset: () => void;
};

/**
 * The pinned actions of the Filters sheet: "Show N tickets" is the one
 * confirm, with the live count, and it is off with a short hint when nothing
 * matches. iOS keeps Reset in the sheet header; Android has no header
 * actions, so Reset sits here as a ghost button beside the confirm.
 */
function FiltersFooter({ count, activeCount, onApply, onReset }: FiltersFooterProps) {
  const { t } = useTranslation();
  const confirm = filterConfirmState(count);
  const label: string =
    count === null ? t("boardFiltersShowPending") : t("boardFiltersShow", { count });

  const apply = (
    <Button
      block={Platform.OS !== "android"}
      className={Platform.OS === "android" ? "flex-1" : undefined}
      disabled={confirm.disabled}
      onPress={onApply}
    >
      {label}
    </Button>
  );

  return (
    <View className="gap-2">
      {confirm.noMatch ? (
        <Text
          accessibilityLiveRegion="polite"
          className="text-center font-sans text-footnote text-muted-foreground"
        >
          {t("boardFiltersNoMatch")}
        </Text>
      ) : null}
      {Platform.OS === "android" ? (
        <View className="flex-row gap-3">
          <Button variant="ghost" disabled={activeCount === 0} onPress={onReset}>
            {t("boardFiltersReset")}
          </Button>
          {apply}
        </View>
      ) : (
        apply
      )}
    </View>
  );
}

export { FiltersFooter };
export type { FiltersFooterProps };
