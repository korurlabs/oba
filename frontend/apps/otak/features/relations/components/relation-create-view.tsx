"use client";

import { useMemo } from "react";
import { useTranslations } from "next-intl";
import { parseAsStringEnum, useQueryState } from "nuqs";
import { ArrowLeft } from "lucide-react";
import { Link, useRouter } from "@oba/shared/libs/i18n/navigation";
import { Button } from "@oba/ui/sky/button";
import { PageHeading } from "@/components/page-states";
import { useCreateRelation } from "../hooks/use-relations";
import { emptyRelationFormValues, RelationType, toRelationCreateRequest } from "../schemas";
import { applyRelationFieldErrors, RelationForm } from "./relation-form";

/**
 * The new-relation page. The type starts as Lead, the relation most often
 * added, unless the link names one (`?type=`, as the battlefield's does); a
 * saved relation opens on its own page.
 */
export function RelationCreateView() {
  const t = useTranslations("relations");
  const router = useRouter();
  const [type] = useQueryState(
    "type",
    parseAsStringEnum(Object.values(RelationType)).withDefault(RelationType.LEAD)
  );
  const createRelation = useCreateRelation();

  const initialValues = useMemo(() => emptyRelationFormValues(type), [type]);
  const backHref = "/relations";

  return (
    <div className="flex flex-col gap-6">
      <Button asChild variant="ghost" size="sm" className="w-fit">
        <Link href={backHref}>
          <ArrowLeft aria-hidden="true" />
          {t("newPage.back")}
        </Link>
      </Button>

      <PageHeading title={t("newPage.title")} description={t("newPage.subtitle")} />

      <RelationForm
        mode="create"
        initialValues={initialValues}
        isPending={createRelation.isPending || createRelation.isSuccess}
        onSubmit={(values, form) => {
          // The schema refuses an empty type, so this only narrows it.
          if (values.type === "") return;
          createRelation.mutate(toRelationCreateRequest(values, values.type), {
            onSuccess: (response) => {
              if (response.data) {
                router.push(`/relations/${response.data.id}`);
              } else {
                router.push(backHref);
              }
            },
            onError: (error) => {
              applyRelationFieldErrors(form, error, t);
            },
          });
        }}
        secondaryAction={
          <Button asChild variant="outline">
            <Link href={backHref}>{t("form.cancel")}</Link>
          </Button>
        }
      />
    </div>
  );
}
