"use client";

import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useTranslations } from "next-intl";
import { Plus, FolderTree } from "lucide-react";
import { cn } from "@/lib/utils";

interface AddChildChoiceDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onChoose: (mode: "new" | "existing") => void;
}

export function AddChildChoiceDialog({
  open,
  onOpenChange,
  onChoose,
}: AddChildChoiceDialogProps) {
  const t = useTranslations("board.addChildChoice");

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[440px]">
        <DialogHeader>
          <DialogTitle>{t("title")}</DialogTitle>
          <DialogDescription>{t("description")}</DialogDescription>
        </DialogHeader>

        <div className="grid grid-cols-2 gap-3 pt-2">
          <ChoiceCard
            icon={Plus}
            title={t("newOption")}
            description={t("newOptionDescription")}
            onClick={() => onChoose("new")}
          />
          <ChoiceCard
            icon={FolderTree}
            title={t("existingOption")}
            description={t("existingOptionDescription")}
            onClick={() => onChoose("existing")}
          />
        </div>
      </DialogContent>
    </Dialog>
  );
}

function ChoiceCard({
  icon: Icon,
  title,
  description,
  onClick,
}: {
  icon: typeof Plus;
  title: string;
  description: string;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "flex cursor-pointer flex-col items-center gap-3 rounded-lg border p-4 text-center transition-colors",
        "hover:border-primary/30 hover:bg-primary/5"
      )}
    >
      <div className="bg-primary/10 flex h-10 w-10 items-center justify-center rounded-lg">
        <Icon className="text-primary h-5 w-5" />
      </div>
      <div className="space-y-1">
        <p className="text-sm font-medium">{title}</p>
        <p className="text-muted-foreground text-xs">{description}</p>
      </div>
    </button>
  );
}
