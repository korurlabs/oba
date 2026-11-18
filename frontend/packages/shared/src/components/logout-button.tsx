"use client";
import { Button } from "@oba/ui/components/button";
import { LogOutIcon } from "lucide-react";
import { useTranslations } from "next-intl";
import { submitLogout } from "@oba/shared/libs/auth/logout-client";

export function LogoutButton() {
  const t = useTranslations();
  const handleLogout = () => {
    submitLogout();
  };
  return (
    <Button
      onClick={handleLogout}
      variant="default"
      className="from-korur-blue-hover to-korur-blue-strong bg-linear-to-br"
      size="default"
    >
      <LogOutIcon className="text-foreground h-4 w-4" />
      <span className="text-foreground">{t("logout")}</span>
    </Button>
  );
}
