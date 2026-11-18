"use client";

import { useLocale, useTranslations } from "next-intl";
import { format, parseISO } from "date-fns";
import { getDateFnsLocale } from "@oba/shared/lib/date-fns-locale";
import { Button } from "@/components/ui/button";
import { DatePicker } from "@/components/ui/date-picker";
import { ChatChannelFilterPopover } from "@/features/chat/components/chat-channel-filter-popover";
import type { ChatChannelFilterCategory } from "@/features/chat/components/chat-channel-filter-popover";
import { ChatPersonFilterOptions } from "@/features/chat/components/chat-person-filter-options";
import { countUpdateFilters, useUpdateFilterParams } from "../hooks/use-update-filter-params";
import { resolveUpdateDay, updatePreviousDay, updateToday } from "../lib/update-day";

/** The categories of the panel; a later filter adds one member, one entry and one options component. */
type UpdateFilterCategory = "day" | "person";

const DATE_PARAM_FORMAT = "yyyy-MM-dd";

/**
 * The Day and Person filters of the daily updates channel, behind a funnel
 * badged with the number of filters that narrow the channel. The state is
 * the URL's (`day`, `person`), so a reload or a shared link shows the same
 * messages. The popup's options mount only while it is open, so a reader
 * who never opens Person sends no request for the channel's authors.
 */
export function UpdateFilterPanel({ channelId }: { channelId: string }) {
  const t = useTranslations("updates.filters");
  const tChatFilters = useTranslations("chat.filters");
  const [filters, setFilters] = useUpdateFilterParams();

  const today = updateToday();
  const day = resolveUpdateDay(filters.day);
  const activeCount = countUpdateFilters(filters);

  // Today is the default, so it is written as no `day` at all.
  const selectDay = (picked: string) => void setFilters({ day: picked === today ? null : picked });
  const togglePerson = (userId: string) =>
    void setFilters({ person: filters.person === userId ? null : userId });
  const clearAll = () => void setFilters({ day: null, person: null });

  const categories: (ChatChannelFilterCategory & { key: UpdateFilterCategory })[] = [
    { key: "day", label: t("day"), count: day === today ? 0 : 1 },
    { key: "person", label: tChatFilters("person"), count: filters.person ? 1 : 0 },
  ];

  const renderOptions = (key: string) =>
    key === "day" ? (
      <div className="flex-1 overflow-y-auto px-3 py-2">
        <UpdateDayOptions day={day} today={today} onSelect={selectDay} />
      </div>
    ) : (
      <ChatPersonFilterOptions channelId={channelId} chosen={filters.person} onToggle={togglePerson} />
    );

  return (
    <ChatChannelFilterPopover
      activeCount={activeCount}
      categories={categories}
      renderOptions={renderOptions}
      onClearAll={clearAll}
    />
  );
}

function UpdateDayOptions({
  day,
  today,
  onSelect,
}: {
  /** The day shown, YYYY-MM-DD. */
  day: string;
  today: string;
  onSelect: (day: string) => void;
}) {
  const t = useTranslations("updates.filters");
  const tFilters = useTranslations("filters");
  const locale = useLocale();
  const dateLocale = getDateFnsLocale(locale);
  const presets = [
    { key: today, label: tFilters("today") },
    { key: updatePreviousDay(today), label: t("yesterday") },
  ];

  return (
    <div className="space-y-3 px-1">
      <div className="flex flex-wrap gap-1.5">
        {presets.map((preset) => {
          const isActive = day === preset.key;
          return (
            <Button
              key={preset.key}
              type="button"
              variant={isActive ? "secondary" : "outline"}
              size="sm"
              className="h-7 text-xs"
              aria-pressed={isActive}
              onClick={() => onSelect(preset.key)}
            >
              {preset.label}
            </Button>
          );
        })}
      </div>
      <DatePicker
        label={t("day")}
        locale={dateLocale}
        value={parseISO(day)}
        onChange={(picked) => {
          if (picked) onSelect(format(picked, DATE_PARAM_FORMAT));
        }}
        toDate={parseISO(today)}
        isClearable={false}
        dateFormat="PP"
      />
    </div>
  );
}
