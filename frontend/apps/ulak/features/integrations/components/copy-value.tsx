"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { Check, Copy } from "lucide-react";
import { Button } from "@oba/ui/sky/button";

interface CopyValueProps {
  /** Names the value; also the copy button's accessible name. */
  label: string;
  value: string;
  /** A snippet keeps its line breaks and scrolls; a single value wraps. */
  multiline?: boolean;
}

/**
 * A setup value or snippet the admin pastes into Azure, with a copy button.
 * A failed copy says so, and the value stays selectable to copy by hand.
 */
export function CopyValue({ label, value, multiline = false }: CopyValueProps) {
  const t = useTranslations("integrations.azure.setup");
  const [copied, setCopied] = useState(false);
  const [copyError, setCopyError] = useState(false);

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
      setCopyError(false);
    } catch (failure: unknown) {
      console.warn(`[integrations] Copy of ${label} to the clipboard failed`, failure);
      setCopied(false);
      setCopyError(true);
    }
  };

  return (
    <div className="flex flex-col gap-1.5">
      {!multiline && <span className="text-sm font-medium">{label}</span>}
      <div className="flex items-start gap-2">
        {multiline ? (
          <pre className="bg-muted max-h-80 min-w-0 flex-1 overflow-auto rounded-md px-3 py-2 font-mono text-xs select-all">
            {value}
          </pre>
        ) : (
          <code className="bg-muted min-w-0 flex-1 rounded-md px-2 py-1.5 font-mono text-xs break-all select-all">
            {value}
          </code>
        )}
        <Button
          variant="outline"
          size="icon-sm"
          onClick={() => void copy()}
          aria-label={`${t("copy")}: ${label}`}
        >
          {copied ? (
            <Check className="size-4" aria-hidden />
          ) : (
            <Copy className="size-4" aria-hidden />
          )}
        </Button>
      </div>
      {copied && (
        <span className="text-muted-foreground text-xs" role="status">
          {t("copied")}
        </span>
      )}
      {copyError && (
        <span className="text-error text-xs" role="alert">
          {t("copyError")}
        </span>
      )}
    </div>
  );
}
