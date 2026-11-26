'use client';

import { Link } from '@oba/shared/libs/i18n/navigation';
import { MoreVertical } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { boardPath } from '@/features/boards/lib/board-route';
import { useTranslations } from 'next-intl';

interface BoardCardProps {
  id: string;
  slug: string;
  name: string;
  prefix: string;
  color?: string;
  icon?: string | null;
}

const colorToGradient: Record<string, string> = {
  '#4AA3FF': 'from-korur-blue-medium to-korur-blue',
  '#FFB7C5': 'from-[#FFD4E0] to-[#FFB7C5]',
  '#C084FC': 'from-purple-200 to-purple-400',
  '#60A5FA': 'from-blue-200 to-blue-400',
  '#4ADE80': 'from-green-200 to-green-400',
  '#FBBF24': 'from-amber-200 to-amber-400',
  '#9CA3AF': 'from-gray-200 to-gray-400',
};

function getGradientClass(color?: string): string {
  if (color && colorToGradient[color]) {
    return colorToGradient[color];
  }
  return 'from-korur-blue-medium to-korur-blue';
}

export function BoardCard({
  slug,
  name,
  prefix,
  color,
}: BoardCardProps) {
  const gradientClass = getGradientClass(color);

  return (
    <Link href={boardPath(slug)}>
      <div className="group relative bg-card text-card-foreground rounded-2xl border border-border overflow-hidden cursor-pointer transition-all duration-150 ease-in-out hover:-translate-y-1 hover:shadow-lg hover:border-transparent">
        {/* Gradient Bar */}
        <div className={`h-2 bg-gradient-to-r ${gradientClass}`} />

        {/* Content */}
        <div className="p-5">
          <div className="flex items-start justify-between mb-4">
            <div className="flex-1 min-w-0">
              <h3 className="text-base font-semibold text-foreground truncate group-hover:text-primary transition-colors">
                {name}
              </h3>
              <p className="mt-1 text-sm text-muted-foreground">{prefix}</p>
            </div>

            <Button
              variant="ghost"
              size="icon-sm"
              className="opacity-0 group-hover:opacity-100 transition-opacity -mr-2"
              onClick={(e) => {
                e.preventDefault();
                e.stopPropagation();
              }}
            >
              <MoreVertical className="h-4 w-4 text-muted-foreground" />
            </Button>
          </div>
        </div>
      </div>
    </Link>
  );
}

/**
 * Create Board Card - Special card for creating new boards
 */
export function CreateBoardCard({ onClick }: { onClick: () => void }) {
  const t = useTranslations('board');

  return (
    <div
      onClick={onClick}
      className="group relative h-[180px] bg-card rounded-2xl border-2 border-dashed border-border overflow-hidden cursor-pointer transition-all duration-200 hover:border-primary hover:bg-primary/10 flex flex-col items-center justify-center"
    >
      {/* Icon */}
      <div className="w-12 h-12 rounded-xl bg-muted group-hover:bg-primary/20 flex items-center justify-center mb-3 transition-colors">
        <svg
          xmlns="http://www.w3.org/2000/svg"
          width="24"
          height="24"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
          className="text-muted-foreground group-hover:text-primary transition-colors"
        >
          <path d="M5 12h14" />
          <path d="M12 5v14" />
        </svg>
      </div>

      {/* Text */}
      <h3 className="text-base font-semibold text-foreground/80 group-hover:text-primary transition-colors">
        {t('create')}
      </h3>
      <p className="text-sm text-muted-foreground mt-1">{t('startNewProject')}</p>
    </div>
  );
}
