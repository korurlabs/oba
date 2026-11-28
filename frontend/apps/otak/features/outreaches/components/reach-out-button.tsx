"use client";

import { useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import { Send } from "lucide-react";
import { SelectFieldStandalone, type SelectOption } from "@oba/shared/features/form";
import { Button } from "@oba/ui/sky/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@oba/ui/sky/dialog";
import { useRelations } from "@/features/relations/hooks/use-relations";
import { relationErrorText } from "@/features/relations/lib/relation-error";
import { leadPickerQuery } from "../lib/reach-out";
import { ReachOutDialog } from "./reach-out-dialog";

/** The lead the user chose, kept with its name for the reach-out dialog's title. */
interface PickedLead {
  id: string;
  name: string;
}

/**
 * Reach out from the Outreaches page: the user first picks a lead, searched
 * by name on the server, and then prepares the outreach in the same dialog a
 * lead's own page opens. "Go to Contacts" there leads to the lead's page,
 * since this page has no Contacts card.
 */
export function ReachOutButton() {
  const t = useTranslations("outreaches.reachOut");
  const [pickerOpen, setPickerOpen] = useState(false);
  const [lead, setLead] = useState<PickedLead | null>(null);
  const [reachOutOpen, setReachOutOpen] = useState(false);

  return (
    <>
      <Button onClick={() => setPickerOpen(true)}>
        <Send aria-hidden="true" />
        {t("button")}
      </Button>
      <Dialog open={pickerOpen} onOpenChange={setPickerOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{t("leadTitle")}</DialogTitle>
            <DialogDescription>{t("leadDescription")}</DialogDescription>
          </DialogHeader>
          {/* Mounted per open, so a reopened picker starts empty with a fresh search. */}
          {pickerOpen && (
            <LeadPicker
              onCancel={() => setPickerOpen(false)}
              onPick={(picked) => {
                setLead(picked);
                setPickerOpen(false);
                setReachOutOpen(true);
              }}
            />
          )}
        </DialogContent>
      </Dialog>
      {lead && (
        <ReachOutDialog
          relationId={lead.id}
          relationName={lead.name}
          open={reachOutOpen}
          onOpenChange={setReachOutOpen}
          contactsHref={`/relations/${lead.id}#contacts`}
        />
      )}
    </>
  );
}

function LeadPicker({
  onCancel,
  onPick,
}: {
  onCancel: () => void;
  onPick: (lead: PickedLead) => void;
}) {
  const t = useTranslations("outreaches.reachOut");
  const tRelations = useTranslations("relations");
  const [search, setSearch] = useState("");
  const [picked, setPicked] = useState<PickedLead | null>(null);
  const query = useMemo(() => leadPickerQuery(search), [search]);
  const leads = useRelations(query);

  const options = useMemo((): SelectOption[] => {
    const found = (leads.data?.data ?? []).map((relation) => ({
      value: relation.id,
      label: relation.name,
    }));
    // The select shows only the label of an option, so the picked lead stays
    // among the options when a later search no longer finds it.
    if (picked && !found.some((option) => option.value === picked.id)) {
      found.unshift({ value: picked.id, label: picked.name });
    }
    return found;
  }, [leads.data?.data, picked]);

  return (
    <>
      <div className="flex flex-col gap-1">
        <SelectFieldStandalone
          label={t("leadLabel")}
          placeholder={t("leadPlaceholder")}
          searchPlaceholder={t("leadSearchPlaceholder")}
          value={picked?.id ?? ""}
          onValueChange={(value) => {
            const option = options.find((candidate) => candidate.value === value);
            setPicked(option ? { id: option.value, name: option.label } : null);
          }}
          options={options}
          onSearchChange={setSearch}
          isLoading={leads.data === undefined && leads.isFetching}
          required
        />
        {leads.isError && (
          <p className="text-destructive text-sm" role="alert">
            {relationErrorText(leads.error, tRelations, "errors.loadHint")}
          </p>
        )}
      </div>
      <DialogFooter>
        <Button variant="outline" onClick={onCancel}>
          {t("cancel")}
        </Button>
        <Button disabled={picked === null} onClick={() => picked && onPick(picked)}>
          {t("next")}
        </Button>
      </DialogFooter>
    </>
  );
}
