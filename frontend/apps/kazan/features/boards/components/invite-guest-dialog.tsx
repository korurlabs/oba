"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { UserPlus, Eye, Edit, Info } from "lucide-react";

interface InviteGuestDialogProps {
  boardId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/**
 * TODO: Backend addBoardMember endpoint requires a userId (UUID), but this dialog
 * collects an email address. These are incompatible. To wire this up properly,
 * either:
 * 1. A backend endpoint to look up userId by email is needed, or
 * 2. The backend addBoardMember endpoint needs to accept email instead of userId.
 *
 * Until one of those is available, the form is disabled and shows a "coming soon" notice.
 */
export function InviteGuestDialog({
  boardId: _boardId,
  open,
  onOpenChange,
}: InviteGuestDialogProps) {
  const t = useTranslations();
  const [email, setEmail] = useState("");
  const [role, setRole] = useState<"VIEWER" | "MEMBER">("VIEWER");

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[425px]">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <UserPlus className="text-primary h-5 w-5" />
            {t("guest.invite")}
          </DialogTitle>
          <DialogDescription>{t("guest.inviteDescription")}</DialogDescription>
        </DialogHeader>

        <div className="bg-muted border-border text-muted-foreground flex items-start gap-2 rounded-lg border p-3 text-sm">
          <Info className="mt-0.5 h-4 w-4 shrink-0" />
          <span>{t("guest.comingSoon")}</span>
        </div>

        <form className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="email">{t("guest.email")}</Label>
            <Input
              id="email"
              type="email"
              placeholder={t("guest.emailPlaceholder")}
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              disabled
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="role">{t("guest.role")}</Label>
            <Select value={role} onValueChange={(v) => setRole(v as "VIEWER" | "MEMBER")} disabled>
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="VIEWER">
                  <div className="flex items-center gap-2">
                    <Eye className="h-4 w-4" />
                    <div className="flex flex-col items-start justify-start">
                      <div className="font-medium">{t("guest.viewer")}</div>
                      <div className="text-muted-foreground text-xs">{t("guest.viewerDesc")}</div>
                    </div>
                  </div>
                </SelectItem>
                <SelectItem value="MEMBER">
                  <div className="flex items-center gap-2">
                    <Edit className="h-4 w-4" />
                    <div className="flex flex-col items-start justify-start">
                      <div className="font-medium">{t("guest.member")}</div>
                      <div className="text-muted-foreground text-xs">{t("guest.memberDesc")}</div>
                    </div>
                  </div>
                </SelectItem>
              </SelectContent>
            </Select>
          </div>

          <DialogFooter>
            <Button type="button" variant="ghost" onClick={() => onOpenChange(false)}>
              {t("common.cancel")}
            </Button>
            <Button type="button" disabled>
              {t("guest.sendInvite")}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
