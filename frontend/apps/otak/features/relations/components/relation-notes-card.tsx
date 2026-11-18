"use client";

import { useTranslations } from "next-intl";
import { Card, CardContent, CardHeader } from "@oba/ui/sky/card";

/**
 * The relation's free text as saved, with its line breaks kept. It is edited
 * in the Notes field of the form above, so this card only reads it.
 */
export function RelationNotesCard({ notes }: { notes: string | null }) {
  const t = useTranslations("relations.notesCard");

  return (
    <Card role="region" aria-labelledby="notes" className="gap-4">
      <CardHeader>
        <h2 id="notes" className="text-lg leading-none">
          {t("title")}
        </h2>
      </CardHeader>
      <CardContent>
        {notes ? (
          <p className="text-sm break-words whitespace-pre-wrap">{notes}</p>
        ) : (
          <p className="text-muted-foreground text-sm">{t("empty")}</p>
        )}
      </CardContent>
    </Card>
  );
}
