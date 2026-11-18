"use client";

import { useCallback, useState } from "react";
import { useTranslations } from "next-intl";
import { Plus } from "lucide-react";
import { Button } from "@oba/ui/sky/button";
import { Card, CardAction, CardContent, CardHeader } from "@oba/ui/sky/card";
import { LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { DataTable } from "@/components/data-table";
import { useContactColumns } from "../hooks/use-contact-columns";
import { useContacts, useDeleteContact, useUpdateContact } from "../hooks/use-contacts";
import { contactErrorText } from "../lib/contact-error";
import {
  ContactKind,
  contactToFormValues,
  toContactRequest,
  type ContactListItem,
} from "../schemas";
import { ContactFormDialog } from "./contact-form-dialog";

/** The people to talk to at a relation: a table, a dialog to add or edit, and delete. */
export function ContactsCard({ relationId }: { relationId: string }) {
  const t = useTranslations("contacts");
  const tErrors = useTranslations("errors");
  const contacts = useContacts(relationId);
  const deleteContact = useDeleteContact(relationId);
  const { mutate: updateContact } = useUpdateContact(relationId);
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<ContactListItem | null>(null);
  const [deleting, setDeleting] = useState<ContactListItem | null>(null);

  const handleEdit = useCallback((contact: ContactListItem) => {
    setEditing(contact);
    setFormOpen(true);
  }, []);
  const handleDelete = useCallback((contact: ContactListItem) => setDeleting(contact), []);
  // The normal full update with the stored fields and the flag of the contact's own kind;
  // the server unmarks the previous main contact of that kind.
  const handleMakeMain = useCallback(
    (contact: ContactListItem) => {
      const stored = toContactRequest(contactToFormValues(contact));
      updateContact({
        contactId: contact.id,
        data:
          contact.kind === ContactKind.BUSINESS
            ? { ...stored, mainBusinessContact: true }
            : { ...stored, mainPersonContact: true },
      });
    },
    [updateContact]
  );
  const columns = useContactColumns({
    onEdit: handleEdit,
    onDelete: handleDelete,
    onMakeMain: handleMakeMain,
  });

  const rows = contacts.data?.data ?? [];

  return (
    <Card role="region" aria-labelledby="contacts" className="gap-4">
      <CardHeader>
        <h2 id="contacts" className="text-lg leading-none">
          {t("title")}
        </h2>
        <CardAction>
          <Button
            variant="outline"
            size="sm"
            onClick={() => {
              setEditing(null);
              setFormOpen(true);
            }}
          >
            <Plus aria-hidden="true" />
            {t("new")}
          </Button>
        </CardAction>
      </CardHeader>
      <CardContent>
        {contacts.isError && contacts.data === undefined ? (
          <LoadError
            title={t("loadError")}
            message={contactErrorText(contacts.error, t, "errors.loadHint")}
            retryLabel={tErrors("tryAgain")}
            onRetry={() => void contacts.refetch()}
          />
        ) : contacts.data === undefined ? (
          <TableLoading label={t("loading")} />
        ) : rows.length === 0 ? (
          <p className="text-muted-foreground text-sm">{t("empty")}</p>
        ) : (
          <DataTable<ContactListItem>
            label={t("tableLabel")}
            columns={columns}
            data={rows}
            getRowId={(contact) => contact.id}
          />
        )}
      </CardContent>

      <ContactFormDialog
        relationId={relationId}
        contact={editing}
        open={formOpen}
        onOpenChange={setFormOpen}
      />
      <ConfirmDialog
        open={deleting !== null}
        onOpenChange={(open) => !open && setDeleting(null)}
        title={t("deleteTitle")}
        description={t("deleteConfirm", { name: deleting?.name ?? "" })}
        confirmLabel={t("deleteAction")}
        cancelLabel={t("form.cancel")}
        destructive
        onConfirm={() => {
          if (deleting) deleteContact.mutate(deleting.id);
          setDeleting(null);
        }}
      />
    </Card>
  );
}
