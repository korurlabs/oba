import * as React from 'react';

import { cn } from '@/lib/utils';

export interface InputProps extends React.ComponentProps<'input'> {
  error?: boolean;
}

const Input = React.forwardRef<HTMLInputElement, InputProps>(
  ({ className, type, error, ...props }, ref) => {
    return (
      <input
        data-slot="input"
        type={type}
        className={cn(
          'flex h-11 w-full border border-input bg-background dark:bg-input/30 px-4 py-3 text-sm text-foreground transition-colors duration-150',
          'placeholder:text-muted-foreground',
          'hover:border-border dark:hover:bg-input/50',
          'focus:outline-none focus:border-primary',
          'disabled:cursor-not-allowed disabled:opacity-50 disabled:bg-muted',
          'file:border-0 file:bg-transparent file:text-sm file:font-medium file:text-foreground',
          error && 'border-error focus:border-error',
          className
        )}
        aria-invalid={error || undefined}
        ref={ref}
        {...props}
      />
    );
  }
);
Input.displayName = 'Input';

export { Input };
