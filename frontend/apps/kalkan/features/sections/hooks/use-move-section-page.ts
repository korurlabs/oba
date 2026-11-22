"use client";

import { useMutation } from "@tanstack/react-query";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import { getPage, movePage } from "@/features/pages/api";
import { useInvalidatePages } from "@/features/pages/hooks/use-pages";
import { pageErrorText } from "@/features/pages/lib/page-error";
import type { MoveStep } from "../lib/tree-moves";

/**
 * Runs the position changes of one move in order. The tree carries no page
 * versions, so each step reads the page's current version right before its
 * PATCH; a page changed meanwhile still answers `KLK_STA_002`. One toast
 * reports the move, and the page queries refresh even after a failed step,
 * so the tree shows the order the server holds.
 */
export function useMoveSectionPage() {
  const invalidate = useInvalidatePages();
  const t = useTranslations("pages");

  return useMutation({
    mutationFn: async (steps: MoveStep[]) => {
      for (const step of steps) {
        const current = await getPage(step.pageId);
        const version = current.data?.version;
        if (version === undefined) {
          throw new Error(`Page ${step.pageId} answered without a version`);
        }
        await movePage(step.pageId, {
          parentId: step.parentId,
          position: step.position,
          version,
        });
      }
    },
    onSuccess: () => {
      toast.success(t("toast.moved"));
    },
    onError: (error: unknown) => {
      toast.error(pageErrorText(error, t, "toast.moveError"));
    },
    onSettled: () => {
      invalidate();
    },
  });
}
