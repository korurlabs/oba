"use client";

const DEFAULT_COLOR = "#6B7280";

type ProjectBadgeProps = {
  project: { name: string; color?: string | null };
  size?: "sm" | "md";
};

export function ProjectBadge({ project, size = "md" }: ProjectBadgeProps) {
  const color = project.color || DEFAULT_COLOR;

  const sizeClasses =
    size === "sm"
      ? "text-[10px] px-1.5 py-0.5 gap-0.5"
      : "text-xs px-2 py-0.5 gap-1";

  const dotClasses = size === "sm" ? "h-1.5 w-1.5" : "h-2 w-2";

  return (
    <span
      className={`inline-flex items-center rounded-full font-medium border ${sizeClasses}`}
      style={{ borderColor: `${color}40` }}
    >
      <span
        className={`shrink-0 rounded-full ${dotClasses}`}
        style={{ backgroundColor: color }}
      />
      {project.name}
    </span>
  );
}
