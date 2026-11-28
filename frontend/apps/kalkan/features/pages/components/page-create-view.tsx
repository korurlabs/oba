"use client";

import { useMemo } from "react";
import { useLocale, useTranslations } from "next-intl";
import { parseAsString, useQueryState } from "nuqs";
import { Link, useRouter } from "@oba/shared/libs/i18n/navigation";
import { Button } from "@oba/ui/sky/button";
import { PageHeading } from "@oba/ui/sky/page-heading";
import { EmptyState, LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { useIsOrganizationMember } from "@/hooks/use-is-organization-admin";
import { pageHref } from "@/lib/routes";
import { useCreatePage, usePageTree } from "../hooks/use-pages";
import { pageErrorText } from "../lib/page-error";
import { contentLocaleOf } from "../lib/page-tree";
import { emptyPageFormValues, parentOptions, toPageCreateRequest } from "../schemas/page-form";
import { applyPageFieldErrors } from "./page-field-errors";
import { PageForm } from "./page-form";

/**
 * The new-page screen. The parent starts as the page the user came from
 * (`?parent=`) when they may add pages there; a saved page opens on its own
 * screen, where files can then be attached.
 */
export function PageCreateView() {
  const t = useTranslations("pageForm");
  const tPages = useTranslations("pages");
  const locale = useLocale();
  const router = useRouter();
  const [parent] = useQueryState("parent", parseAsString);
  const isMember = useIsOrganizationMember();
  const tree = usePageTree();
  const createPage = useCreatePage();

  const options = useMemo(
    () => parentOptions(tree.data?.data ?? [], locale),
    [tree.data?.data, locale]
  );
  const parentListed = parent !== null && options.some((option) => option.value === parent);
  const initialValues = useMemo(
    () => emptyPageFormValues(parentListed && parent !== null ? parent : ""),
    [parentListed, parent]
  );
  const backHref = parentListed && parent !== null ? pageHref(parent) : "/";

  const heading = (
    <PageHeading
      title={t("newTitle")}
      description={t("newSubtitle")}
      back={{ href: backHref, label: t("back") }}
      linkComponent={Link}
    />
  );

  if (tree.isPending) {
    return <TableLoading label={t("loading")} />;
  }
  if (tree.isError) {
    return (
      <LoadError
        title={t("treeLoadError")}
        message={pageErrorText(tree.error, tPages, "view.loadError")}
        onRetry={() => void tree.refetch()}
        retryLabel={t("retry")}
      />
    );
  }
  if (!isMember || options.length === 0) {
    return (
      <div className="flex flex-col gap-6">
        {heading}
        <EmptyState title={t("noParent.title")} description={t("noParent.description")} />
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-6">
      {heading}
      <PageForm
        mode="create"
        initialValues={initialValues}
        parentOptions={options}
        initialLocale={contentLocaleOf(locale)}
        isPending={createPage.isPending || createPage.isSuccess}
        onSubmit={(values, form) => {
          createPage.mutate(toPageCreateRequest(values), {
            onSuccess: (response) => {
              router.push(response.data ? pageHref(response.data.id) : backHref);
            },
            onError: (error) => {
              applyPageFieldErrors(form, error, tPages);
            },
          });
        }}
        secondaryAction={
          <Button asChild variant="outline">
            <Link href={backHref}>{t("cancel")}</Link>
          </Button>
        }
      />
    </div>
  );
}
