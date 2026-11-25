"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { Check, Copy, KeyRound } from "lucide-react";
import { Button } from "@oba/ui/sky/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@oba/ui/sky/dialog";
import { useDateFormat } from "@oba/shared/i18n/use-date-format";
import type { Agent, AgentTokenCreated } from "../schemas";
import { useIssueAgentToken, useRevokeAgentToken } from "../hooks/use-agents";

/** The issued token with a copy button; a failed copy asks the user to copy by hand. */
function TokenCopyBlock({ token }: { token: string }) {
  const t = useTranslations("agents");
  const [copied, setCopied] = useState(false);
  const [copyError, setCopyError] = useState(false);

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(token);
      setCopied(true);
      setCopyError(false);
    } catch (failure: unknown) {
      console.warn("[agents] Copy of the agent token to the clipboard failed", failure);
      setCopyError(true);
    }
  };

  return (
    <div className="flex flex-col gap-1.5">
      <span className="text-sm font-medium">{t("token.label")}</span>
      <div className="flex items-start gap-2">
        <code className="bg-muted min-w-0 flex-1 rounded-md px-2 py-1.5 font-mono text-xs break-all select-all">
          {token}
        </code>
        <Button
          variant="outline"
          size="icon-sm"
          onClick={() => void copy()}
          aria-label={t("token.copy")}
        >
          {copied ? (
            <Check className="size-4" aria-hidden />
          ) : (
            <Copy className="size-4" aria-hidden />
          )}
        </Button>
      </div>
      {copyError && (
        <span className="text-error text-xs" role="alert">
          {t("token.copyError")}
        </span>
      )}
    </div>
  );
}

interface AgentTokenDialogProps {
  organizationId: string;
  /** The ROUTINE agent whose token is managed; null while the dialog is closed. */
  agent: Agent | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/**
 * Issues, reissues and revokes the token of a ROUTINE agent. Oba keeps only
 * the token's hash, so a new token is shown once, here, and is held only in
 * this dialog's state: closing the dialog forgets it.
 */
export function AgentTokenDialog({
  organizationId,
  agent,
  open,
  onOpenChange,
}: AgentTokenDialogProps) {
  const t = useTranslations("agents");
  const { formatDateTime } = useDateFormat();
  const issueToken = useIssueAgentToken(organizationId);
  const revokeToken = useRevokeAgentToken(organizationId);
  const [issued, setIssued] = useState<AgentTokenCreated | null>(null);
  const [confirmRevoke, setConfirmRevoke] = useState(false);
  const isPending = issueToken.isPending || revokeToken.isPending;

  const forget = () => {
    setIssued(null);
    setConfirmRevoke(false);
  };

  const handleOpenChange = (isOpen: boolean) => {
    if (isPending) return;
    if (!isOpen) forget();
    onOpenChange(isOpen);
  };

  // A failure is shown by the hook's toast; the dialog stays as it was.
  const issue = () => {
    if (!agent) return;
    setConfirmRevoke(false);
    issueToken.mutate(agent.id, {
      onSuccess: (response) => {
        setIssued(response.data ?? null);
        // The mutation keeps its result until reset; the token lives only in
        // this dialog's state.
        issueToken.reset();
      },
    });
  };

  const revoke = () => {
    if (!agent) return;
    revokeToken.mutate(agent.id, { onSuccess: forget });
  };

  const hasToken = !!agent?.tokenPrefix;

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent
        className="sm:max-w-[520px]"
        onInteractOutside={(event) => {
          // A click beside the dialog must not throw away a token not yet copied.
          if (issued) event.preventDefault();
        }}
      >
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <KeyRound className="text-primary h-5 w-5" />
            {t("token.title", { name: agent?.name ?? "" })}
          </DialogTitle>
          <DialogDescription>{t("token.description")}</DialogDescription>
        </DialogHeader>

        {issued ? (
          <div className="flex flex-col gap-3">
            <p className="text-sm font-medium" role="status">
              {t("token.shownOnce")}
            </p>
            <TokenCopyBlock token={issued.token} />
            <p className="text-muted-foreground text-sm">
              {t("token.expires", { date: formatDateTime(issued.expiresAt) })}
            </p>
          </div>
        ) : hasToken && agent ? (
          <dl className="grid grid-cols-[8rem_minmax(0,1fr)] gap-x-3 gap-y-1.5 text-sm">
            <dt className="text-muted-foreground">{t("token.prefix")}</dt>
            <dd className="font-mono">{agent.tokenPrefix}…</dd>
            <dt className="text-muted-foreground">{t("token.lastUsedLabel")}</dt>
            <dd>
              {agent.tokenLastUsedAt ? formatDateTime(agent.tokenLastUsedAt) : t("token.neverUsed")}
            </dd>
            <dt className="text-muted-foreground">{t("token.expiresLabel")}</dt>
            <dd>{agent.tokenExpiresAt ? formatDateTime(agent.tokenExpiresAt) : "-"}</dd>
          </dl>
        ) : (
          <p className="text-muted-foreground text-sm">{t("token.noneDescription")}</p>
        )}

        {!issued && hasToken && (
          <p className="text-muted-foreground text-sm">{t("token.reissueNote")}</p>
        )}
        {confirmRevoke && (
          <p className="text-error text-sm" role="alert">
            {t("token.revokeConfirm")}
          </p>
        )}

        <DialogFooter>
          {issued ? (
            <Button onClick={() => handleOpenChange(false)}>{t("token.stored")}</Button>
          ) : (
            <>
              {hasToken &&
                (confirmRevoke ? (
                  <Button variant="destructive" onClick={revoke} disabled={isPending}>
                    {revokeToken.isPending ? t("token.revoking") : t("token.revokeConfirmButton")}
                  </Button>
                ) : (
                  <Button
                    variant="outline"
                    onClick={() => setConfirmRevoke(true)}
                    disabled={isPending}
                  >
                    {t("token.revoke")}
                  </Button>
                ))}
              <Button onClick={issue} disabled={isPending || !agent}>
                {issueToken.isPending
                  ? t("token.issuing")
                  : hasToken
                    ? t("token.reissue")
                    : t("token.issue")}
              </Button>
            </>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
