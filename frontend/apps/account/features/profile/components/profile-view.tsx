"use client";

import { useTranslations } from "next-intl";
import type { LucideIcon } from "lucide-react";
import { Lock, Mail, User } from "lucide-react";
import { useGetMe } from "@oba/shared/features/users/hooks/use-get-me";
import { Card, CardContent, CardDescription, CardHeader } from "@oba/ui/sky/card";
import { LoadError } from "@oba/ui/sky/page-states";
import { PageHeading, PanelTitle } from "@/components/layout/page-heading";
import { ProfileForm } from "./profile-form";

/**
 * Profile screen: the signed-in user's name and email, read-only, and the
 * form that edits the profile.
 */
export function ProfileView() {
  const t = useTranslations();
  const { data: me, isPending, isFetching, refetch } = useGetMe();

  // A failed refetch keeps the last loaded user in the cache, so a refetch
  // failing after a rejected save does not unmount the form and discard what
  // the user typed. Without a user, the first fetch failed.
  const user = me?.data;
  const displayName = [user?.profile?.firstName, user?.profile?.lastName].filter(Boolean).join(" ");

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-6">
      <PageHeading title={t("profile.title")} description={t("profile.subtitle")} />

      {isPending ? (
        <div className="flex flex-col gap-2" aria-busy="true">
          <span className="sr-only">{t("profile.loading")}</span>
          <div className="bg-muted h-40 animate-pulse rounded-xl" />
          <div className="bg-muted h-24 animate-pulse rounded-xl" />
        </div>
      ) : !user ? (
        // The current-user query failed before it ever loaded a user.
        <LoadError
          title={t("errors.somethingWentWrong")}
          message={t("errors.unexpectedError")}
          retryLabel={t("errors.tryAgain")}
          onRetry={() => {
            if (!isFetching) void refetch();
          }}
        />
      ) : (
        // Identity: the read-only name and email, then the edit form.
        <Card role="region" aria-labelledby="profile-identity-heading">
          <CardHeader>
            <PanelTitle id="profile-identity-heading">{t("profile.yourProfile")}</PanelTitle>
            <CardDescription>{t("profile.updatePersonalInfo")}</CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-6">
            <dl className="grid gap-3 md:grid-cols-2">
              <ReadOnlyField
                icon={User}
                label={t("profile.displayName")}
                value={displayName || "-"}
              />
              <ReadOnlyField icon={Mail} label={t("profile.emailAddress")} value={user.email} />
            </dl>
            <div className="border-t pt-6">
              <ProfileForm profile={user.profile} />
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}

/**
 * A field the user reads but cannot edit here: the name is derived from the
 * form below and the email belongs to the identity provider. A muted fill
 * and a lock mark it apart from the editable fields.
 */
function ReadOnlyField({
  icon: Icon,
  label,
  value,
}: {
  icon: LucideIcon;
  label: string;
  value: string;
}) {
  return (
    <div className="bg-muted flex items-center gap-3 rounded-md border px-4 py-3">
      <Icon className="text-muted-foreground size-4 shrink-0" aria-hidden="true" />
      <div className="min-w-0 flex-1">
        <dt className="text-muted-foreground text-xs font-medium">{label}</dt>
        <dd className="text-foreground mt-1 truncate text-sm font-medium">{value}</dd>
      </div>
      <Lock className="text-muted-foreground size-3.5 shrink-0" aria-hidden="true" />
    </div>
  );
}
