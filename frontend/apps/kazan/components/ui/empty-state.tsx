import { LucideIcon, Plus } from "lucide-react";
import { Button } from "./button";
import { useTranslations } from "next-intl";

interface EmptyStateAction {
  label: string;
  onClick: () => void;
  /** Button icon; a plus sign when left out. */
  Icon?: LucideIcon;
}

interface EmptyStateProps {
  /** The mark in the square above the title; a page with no fitting icon leaves it out. */
  Icon?: LucideIcon;
  title?: string;
  description?: string;
  action?: EmptyStateAction;
  /** A second, outlined button beside the main action. */
  secondaryAction?: EmptyStateAction;
}
export function EmptyState({ Icon, title, description, action, secondaryAction }: EmptyStateProps) {
  const t = useTranslations();
  const { label, onClick, Icon: ActionIcon = Plus } = action ?? {};
  const SecondaryIcon = secondaryAction?.Icon ?? Plus;
  return (
    <div className="flex h-full flex-col items-center justify-center p-6 text-center">
      {/* A dashed square, as an empty kanban slot: the place is ready, nothing is in it yet. */}
      {Icon && (
        <div className="border-primary/40 classic:rounded-full classic:border-0 classic:bg-primary/10 mb-6 flex h-20 w-20 items-center justify-center border border-dashed">
          <Icon className="text-primary h-10 w-10" />
        </div>
      )}
      <h2 className="text-foreground text-xl font-semibold">{title ?? t("empty.title")}</h2>
      <p className="text-muted-foreground mt-2 max-w-md text-sm">
        {description ?? t("empty.description")}
      </p>
      {(label || onClick || secondaryAction) && (
        <div className="mt-6 flex flex-wrap items-center justify-center gap-3">
          {(label || onClick) && (
            <Button type="button" className="gap-2" onClick={onClick}>
              <ActionIcon className="h-4 w-4" />
              <span>{label ?? t("empty.action")}</span>
            </Button>
          )}
          {secondaryAction && (
            <Button
              type="button"
              variant="outline"
              className="gap-2"
              onClick={secondaryAction.onClick}
            >
              <SecondaryIcon className="h-4 w-4" />
              <span>{secondaryAction.label}</span>
            </Button>
          )}
        </div>
      )}
    </div>
  );
}
