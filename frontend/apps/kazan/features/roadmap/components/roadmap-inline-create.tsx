"use client";

import { useState, type CSSProperties } from "react";
import { useTranslations } from "next-intl";
import { addDaysToDateOnly, todayDateOnly } from "@oba/shared/lib/date-only";
import { Loader2, Plus } from "lucide-react";
import { useCreateMilestone } from "@/features/milestones/hooks/use-milestones";
import { MILESTONE_NAME_MAX } from "@/features/milestones/schemas";

/** Days from the viewer's today to the target date of a milestone created inline. */
const INLINE_TARGET_OFFSET_DAYS = 14;

interface RoadmapInlineCreateProps {
  boardId: string;
  nameColumnWidth: number;
  height: number;
  rowStyle: CSSProperties;
}

/**
 * The last row of the timeline: a "+" that turns into a name input. Enter
 * creates a milestone on the page's board, due in two weeks and without a
 * start date, which can then be dragged into place; Escape cancels.
 */
export function RoadmapInlineCreate({
  boardId,
  nameColumnWidth,
  height,
  rowStyle,
}: RoadmapInlineCreateProps) {
  const t = useTranslations("roadmap");
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState("");
  const createMilestone = useCreateMilestone();

  const submit = () => {
    const trimmed = name.trim();
    if (!trimmed) return;
    createMilestone.mutate(
      {
        boardId,
        data: {
          name: trimmed,
          // A default the viewer can drag, so it counts from the viewer's own
          // day; the server judges health on its own calendar.
          targetDate: addDaysToDateOnly(todayDateOnly(), INLINE_TARGET_OFFSET_DAYS),
        },
      },
      {
        onSuccess: () => {
          setName("");
          setEditing(false);
        },
      },
    );
  };

  return (
    <div className="flex" style={{ ...rowStyle, height }}>
      <div
        className="border-border sticky left-0 z-20 flex shrink-0 items-center border-r px-2"
        style={{ ...rowStyle, width: nameColumnWidth }}
      >
        {editing ? (
          <div className="flex w-full items-center gap-2">
            <input
              autoFocus
              value={name}
              maxLength={MILESTONE_NAME_MAX}
              disabled={createMilestone.isPending}
              onChange={(event) => setName(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter") {
                  event.preventDefault();
                  submit();
                } else if (event.key === "Escape") {
                  setName("");
                  setEditing(false);
                }
              }}
              onBlur={() => {
                if (!name.trim() && !createMilestone.isPending) setEditing(false);
              }}
              placeholder={t("inlineCreate.placeholder")}
              aria-label={t("inlineCreate.placeholder")}
              className="border-input bg-background focus-visible:ring-ring h-7 w-full min-w-0 rounded-md border px-2 text-xs focus-visible:ring-2 focus-visible:outline-none"
            />
            {createMilestone.isPending && (
              <Loader2 className="text-muted-foreground h-3.5 w-3.5 shrink-0 animate-spin" />
            )}
          </div>
        ) : (
          <button
            type="button"
            onClick={() => setEditing(true)}
            className="text-muted-foreground hover:text-foreground hover:bg-accent focus-visible:ring-ring flex h-7 w-full items-center gap-2 rounded-md px-1.5 text-xs focus-visible:ring-2 focus-visible:outline-none"
          >
            <Plus className="h-4 w-4" aria-hidden />
            {t("inlineCreate.action")}
          </button>
        )}
      </div>
      <div className="flex-1" />
    </div>
  );
}
