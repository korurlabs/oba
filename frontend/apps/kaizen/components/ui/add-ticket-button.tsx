import { Plus } from "lucide-react";
import { Button, ButtonProps } from "./button";
import { useTranslations } from "next-intl";

export function AddTicketButton(props: ButtonProps) {
  const t = useTranslations();
  return (
    <Button type="button" size="sm" className="gap-1" {...props}>
      <Plus className="h-4 w-4" />
      <span className="hidden md:inline">{t("ticket.create")}</span>
      <kbd className="bg-card/10 hidden rounded border border-white/20 px-1 py-0.5 font-mono text-[10px] font-normal md:inline">
        ⌥N
      </kbd>
    </Button>
  );
}
