import { Badge } from "@/components/ui/badge";
import { BoardDot } from "@/components/ui/board-dot";
import { cn } from "@/lib/utils";

interface AlertRouteBoardLabelProps {
  name: string;
  prefix?: string;
  color?: string | null;
  boardId: string;
  className?: string;
}

/**
 * A board as the alert map shows it: colour dot, name, prefix and the board
 * id. The id is shown because boards are entered per environment and an admin
 * checks the mapping against the id, not only the name, which may repeat.
 */
export function AlertRouteBoardLabel({
  name,
  prefix,
  color,
  boardId,
  className,
}: AlertRouteBoardLabelProps) {
  return (
    <span className={cn("flex min-w-0 items-center gap-1.5", className)}>
      <BoardDot color={color} />
      <span className="min-w-0 truncate font-medium">{name}</span>
      {prefix && (
        <Badge variant="secondary" className="shrink-0 px-1.5 py-0 text-[10px]">
          {prefix}
        </Badge>
      )}
      <span
        className="text-muted-foreground min-w-0 truncate font-mono text-xs"
        title={boardId}
      >
        {boardId}
      </span>
    </span>
  );
}
