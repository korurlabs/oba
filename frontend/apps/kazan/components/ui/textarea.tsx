import * as React from 'react';

import { cn } from '@/lib/utils';

const Textarea = React.forwardRef<
  HTMLTextAreaElement,
  React.ComponentProps<'textarea'>
>(({ className, ...props }, ref) => {
  return (
    <textarea
      data-slot="textarea"
      className={cn(
        'flex min-h-[60px] w-full border border-input bg-background dark:bg-input/30 px-4 py-3 text-sm text-foreground transition-colors duration-150',
          'placeholder:text-muted-foreground',
          'hover:border-border dark:hover:bg-input/50',
          'focus:outline-none focus:border-primary',
          'disabled:cursor-not-allowed disabled:opacity-50 disabled:bg-muted',
        className
      )}
      ref={ref}
      {...props}
    />
  );
});
Textarea.displayName = 'Textarea';

export { Textarea };
