"use client";

import { useMemo, useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { Trash2, UserPlus } from "lucide-react";
import { SelectFieldStandalone } from "@oba/shared/features/form";
import type { OrganizationMember } from "@oba/shared/features/organizations/schemas";
import { Link } from "@oba/shared/libs/i18n/navigation";
import { Alert, AlertDescription, AlertTitle } from "@oba/ui/sky/alert";
import { Badge } from "@oba/ui/sky/badge";
import { Button } from "@oba/ui/sky/button";
import { Card, CardContent, CardHeader } from "@oba/ui/sky/card";
import { PageHeading, type PageHeadingBack } from "@oba/ui/sky/page-heading";
import { EmptyState, LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@oba/ui/sky/table";
import { pageHref, pagePermissionsHref } from "@/lib/routes";
import { useOrganizationMembers } from "../hooks/use-organization-members";
import { usePagePermissions, useReplacePagePermissions } from "../hooks/use-page-permissions";
import { usePage } from "../hooks/use-pages";
import { pageErrorText } from "../lib/page-error";
import { contentLocaleOf } from "../lib/page-tree";
import { KalkanLevel, type PageDetail, type PagePermission } from "../schemas";
import {
  addableMembers,
  addGrant,
  changeGrantLevel,
  draftsChanged,
  draftsFromPermissions,
  grantableLevels,
  memberName,
  removeGrant,
  toPermissionRequests,
  type PermissionDraft,
} from "../schemas/permission-form";

/**
 * The access screen of a section's root page: who may view, edit or manage
 * the section and every page under it. The list is edited here and saved at
 * once, because the server replaces all grants with the list it is given.
 * Only a MANAGE holder reaches the list; anyone else sees why not.
 */
export function PagePermissionsView({ pageId }: { pageId: string }) {
  const t = useTranslations("permissions");
  const tPages = useTranslations("pages");
  const locale = useLocale();
  const page = usePage(pageId);
  const detail = page.data?.data ?? null;
  const canManage = detail !== null && detail.parentId === null && detail.level === "MANAGE";

  const back: PageHeadingBack = { href: pageHref(pageId), label: t("backToPage") };

  if (page.isPending) {
    return <TableLoading label={tPages("view.loading")} />;
  }
  if (page.isError || detail === null) {
    return (
      <LoadError
        title={tPages("view.loadError")}
        message={pageErrorText(page.error, tPages, "view.loadError")}
        onRetry={() => void page.refetch()}
        retryLabel={t("retry")}
      />
    );
  }

  const title =
    detail.translations.find((translation) => translation.locale === contentLocaleOf(locale))
      ?.title ??
    detail.translations[0]?.title ??
    detail.slug;

  const heading = (
    <PageHeading
      title={t("title", { title })}
      description={t("subtitle")}
      back={back}
      linkComponent={Link}
    />
  );

  if (detail.parentId !== null) {
    return (
      <div className="flex flex-col gap-6">
        {heading}
        <EmptyState
          title={t("onChild.title")}
          description={t("onChild.description")}
          action={
            detail.rootId ? (
              <Button asChild variant="outline">
                <Link href={pagePermissionsHref(detail.rootId)}>{t("onChild.action")}</Link>
              </Button>
            ) : undefined
          }
        />
      </div>
    );
  }
  if (!canManage) {
    return (
      <div className="flex flex-col gap-6">
        {heading}
        <EmptyState title={t("noAccess.title")} description={t("noAccess.description")} />
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-6">
      {heading}
      <PermissionsEditor page={detail} />
    </div>
  );
}

/** Loads the grants and the members, then hands both to the list editor. */
function PermissionsEditor({ page }: { page: PageDetail }) {
  const t = useTranslations("permissions");
  const tPages = useTranslations("pages");
  const permissions = usePagePermissions(page.id);
  const members = useOrganizationMembers();

  if (permissions.isError && permissions.data === undefined) {
    return (
      <LoadError
        title={t("loadError")}
        message={pageErrorText(permissions.error, tPages, "toast.permissionsError")}
        onRetry={() => void permissions.refetch()}
        retryLabel={t("retry")}
      />
    );
  }
  if (permissions.data === undefined) {
    return <TableLoading label={t("loading")} />;
  }

  return (
    <GrantListEditor
      pageId={page.id}
      stored={permissions.data.data ?? []}
      members={members.data?.data ?? []}
      membersLoading={members.isPending && members.fetchStatus !== "idle"}
      membersError={members.isError}
    />
  );
}

function GrantListEditor({
  pageId,
  stored,
  members,
  membersLoading,
  membersError,
}: {
  pageId: string;
  stored: PagePermission[];
  members: OrganizationMember[];
  membersLoading: boolean;
  membersError: boolean;
}) {
  const t = useTranslations("permissions");
  const tPages = useTranslations("pages");
  const replace = useReplacePagePermissions();
  // The saved list and the edited one. The saved list follows the server only
  // after a save, so a background refresh never drops unsaved rows.
  const [saved, setSaved] = useState<PermissionDraft[]>(() => draftsFromPermissions(stored));
  const [drafts, setDrafts] = useState<PermissionDraft[]>(saved);
  const [memberToAdd, setMemberToAdd] = useState("");
  const [levelToAdd, setLevelToAdd] = useState<KalkanLevel>(KalkanLevel.VIEW);
  const [saveError, setSaveError] = useState<string | null>(null);

  const membersById = useMemo(
    () => new Map(members.map((member) => [member.userId, member])),
    [members]
  );
  const memberOptions = useMemo(
    () =>
      addableMembers(members, drafts).map((member) => ({
        value: member.userId,
        label: memberName(member),
      })),
    [members, drafts]
  );
  const levelLabel = (level: KalkanLevel) => t(`levels.${level}`);
  const levelOptions = (levels: KalkanLevel[]) =>
    levels.map((level) => ({ value: level, label: levelLabel(level) }));

  const selectedMember = membersById.get(memberToAdd);
  const addLevels = grantableLevels(selectedMember?.role);
  const dirty = draftsChanged(drafts, saved);

  const add = () => {
    if (!selectedMember) return;
    setDrafts(addGrant(drafts, selectedMember, levelToAdd));
    setMemberToAdd("");
    setLevelToAdd(KalkanLevel.VIEW);
  };

  const save = () => {
    setSaveError(null);
    replace.mutate(
      { rootPageId: pageId, grants: toPermissionRequests(drafts) },
      {
        onSuccess: (response) => {
          const next = draftsFromPermissions(response.data ?? []);
          setSaved(next);
          setDrafts(next);
        },
        onError: (error) => {
          setSaveError(pageErrorText(error, tPages, "toast.permissionsError"));
        },
      }
    );
  };

  return (
    <div className="flex flex-col gap-6">
      <Card role="region" aria-labelledby="permission-add" className="gap-4">
        <CardHeader>
          <h2 id="permission-add" className="text-lg leading-none">
            {t("add.title")}
          </h2>
        </CardHeader>
        <CardContent className="flex flex-col gap-3">
          <div className="grid grid-cols-1 items-end gap-3 md:grid-cols-[minmax(0,2fr)_minmax(0,1fr)_auto]">
            <SelectFieldStandalone
              label={t("add.member")}
              placeholder={t("add.memberPlaceholder")}
              searchPlaceholder={t("add.memberSearch")}
              options={memberOptions}
              value={memberToAdd}
              onValueChange={(value) => {
                setMemberToAdd(value);
                const member = membersById.get(value);
                if (!grantableLevels(member?.role).includes(levelToAdd)) {
                  setLevelToAdd(KalkanLevel.VIEW);
                }
              }}
              isLoading={membersLoading}
              disabled={replace.isPending}
            />
            <SelectFieldStandalone
              label={t("add.level")}
              options={levelOptions(addLevels)}
              value={levelToAdd}
              onValueChange={(value) => setLevelToAdd(value as KalkanLevel)}
              disabled={replace.isPending}
            />
            <Button type="button" onClick={add} disabled={!selectedMember || replace.isPending}>
              <UserPlus aria-hidden="true" />
              {t("add.action")}
            </Button>
          </div>
          {membersError ? (
            <p className="text-destructive text-sm" role="alert">
              {t("add.membersLoadError")}
            </p>
          ) : null}
          {selectedMember?.role === "GUEST" ? (
            <p className="text-muted-foreground text-sm">{t("add.guestHint")}</p>
          ) : null}
        </CardContent>
      </Card>

      {drafts.length === 0 ? (
        <EmptyState title={t("list.emptyTitle")} description={t("list.emptyDescription")} />
      ) : (
        <Card role="region" aria-labelledby="permission-list" className="gap-4">
          <CardHeader>
            <h2 id="permission-list" className="text-lg leading-none">
              {t("list.title")}
            </h2>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t("list.subject")}</TableHead>
                  <TableHead className="w-48">{t("list.level")}</TableHead>
                  <TableHead className="w-12">
                    <span className="sr-only">{t("list.actions")}</span>
                  </TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {drafts.map((draft) => {
                  const member = draft.userId ? membersById.get(draft.userId) : undefined;
                  return (
                    <TableRow key={draft.key}>
                      <TableCell>
                        <div className="flex flex-wrap items-center gap-2">
                          <span className="font-medium">{draft.displayName}</span>
                          {draft.groupId !== null ? (
                            <Badge variant="secondary">{t("list.group")}</Badge>
                          ) : null}
                          {member?.role === "GUEST" ? (
                            <Badge variant="secondary">{t("list.guest")}</Badge>
                          ) : null}
                        </div>
                      </TableCell>
                      <TableCell>
                        <SelectFieldStandalone
                          options={levelOptions(
                            draft.groupId !== null
                              ? grantableLevels(undefined)
                              : grantableLevels(member?.role)
                          )}
                          value={draft.level}
                          onValueChange={(value) =>
                            setDrafts(changeGrantLevel(drafts, draft.key, value as KalkanLevel))
                          }
                          disabled={replace.isPending}
                        />
                      </TableCell>
                      <TableCell>
                        <Button
                          type="button"
                          variant="ghost"
                          size="icon"
                          className="text-destructive hover:text-destructive"
                          aria-label={t("list.remove", { name: draft.displayName })}
                          disabled={replace.isPending}
                          onClick={() => setDrafts(removeGrant(drafts, draft.key))}
                        >
                          <Trash2 aria-hidden="true" />
                        </Button>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      )}

      {saveError ? (
        <Alert variant="destructive">
          <AlertTitle>{t("saveErrorTitle")}</AlertTitle>
          <AlertDescription>{saveError}</AlertDescription>
        </Alert>
      ) : null}

      <div className="bg-card/95 sticky bottom-4 z-10 flex flex-wrap items-center justify-end gap-2 rounded-xl border px-4 py-3 shadow-md backdrop-blur">
        <p className="text-warning-text mr-auto text-sm font-medium" role="status">
          {dirty ? t("unsavedChanges") : null}
        </p>
        <Button
          type="button"
          variant="outline"
          disabled={!dirty || replace.isPending}
          onClick={() => {
            setDrafts(saved);
            setSaveError(null);
          }}
        >
          {t("discard")}
        </Button>
        <Button type="button" disabled={!dirty || replace.isPending} onClick={save}>
          {replace.isPending ? t("saving") : t("save")}
        </Button>
      </div>
    </div>
  );
}
