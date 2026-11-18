/**
 * Skip to Content Link
 *
 * Accessibility feature allowing keyboard users to skip navigation
 * WCAG 2.1 Level A requirement
 *
 * The label comes from the layout, which translates it on the server: the
 * link renders above the providers, outside the client translation context.
 */

'use client';

export function SkipToContent({ label }: { label: string }) {
  const handleClick = (e: React.MouseEvent<HTMLAnchorElement>) => {
    e.preventDefault();
    const main = document.querySelector('main');
    if (main) {
      main.focus();
      main.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  };

  return (
    <a
      href="#main-content"
      onClick={handleClick}
      className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:rounded-md focus:bg-primary focus:px-4 focus:py-2 focus:text-primary-foreground focus:shadow-lg focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2"
    >
      {label}
    </a>
  );
}
