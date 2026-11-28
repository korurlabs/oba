"use client";

import { useEffect, useId, useMemo, useRef, useState, type ReactNode } from "react";
import { useTranslations } from "next-intl";
import { Send } from "lucide-react";
import { toast } from "sonner";
import { SenseySearchInput } from "@oba/shared/components/sensey-search-input";
import { useRouter } from "@oba/shared/libs/i18n/navigation";
import { AttachmentUpload } from "@oba/shared/features/attachments/components";
import { useUploadAttachments } from "@oba/shared/features/attachments/hooks";
import { StorageEntityType } from "@oba/shared/features/attachments/schemas";
import { Button } from "@oba/ui/sky/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@oba/ui/sky/dialog";
import { Field, Input, NativeSelect, Textarea } from "@oba/ui/sky/field";
import { LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { FilterLabel } from "@/components/page-states";
import { useCampaignOptions } from "@/features/campaigns/hooks/use-campaigns";
import { useContacts } from "@/features/contacts/hooks/use-contacts";
import { contactErrorText } from "@/features/contacts/lib/contact-error";
import type { ContactListItem } from "@/features/contacts/schemas";
import { DocumentLanguageSchema } from "@/features/document-templates/schemas";
import {
  useMailTemplate,
  useMailTemplates,
} from "@/features/mail-templates/hooks/use-mail-templates";
import { mailTemplateErrorText } from "@/features/mail-templates/lib/mail-template-error";
import type { MailTemplateQueryRequest } from "@/features/mail-templates/schemas";
import { cn } from "@/lib/utils";
import { useCreateOutreach } from "../hooks/use-outreaches";
import { outreachErrorText, outreachFieldErrors } from "../lib/outreach-error";
import {
  applyTemplate,
  emptyReachOutSelection,
  isStepComplete,
  nextReachOutStep,
  previousReachOutStep,
  reachableContacts,
  reachOutSteps,
  startFromScratch,
  toReachOutRequest,
  type ReachOutSelection,
  type ReachOutStep,
} from "../lib/reach-out";
import { OUTREACH_LIMITS, OutreachType, OutreachTypeSchema } from "../schemas";

/** The id of the Contacts card heading on the relation page, which the empty state points to. */
const CONTACTS_SECTION_ID = "contacts";

/** The most mail templates one search shows; the server caps a page at 50. */
const TEMPLATE_CHOICES = 50;

/** The radio value of a mail written by hand, next to the template ids. */
const SCRATCH = "scratch";

export interface ReachOutDialogProps {
  relationId: string;
  relationName: string;
  /**
   * Opens the dialog from outside. When set, the dialog renders no Reach
   * out button of its own, as the caller opens it once a lead is chosen.
   */
  open?: boolean;
  onOpenChange?: (open: boolean) => void;
  /**
   * Where "Go to Contacts" leads when the lead has no reachable contact. Left
   * out on the lead's own page, where the dialog scrolls to its Contacts card.
   */
  contactsHref?: string;
}

/**
 * Prepares a reach-out to a lead: the channel, one contact, and a title. A
 * mail goes to a contact with a business address and takes a mail template
 * or a text written by hand, plus optional files of the user's own; the
 * relation's documents are added to the mail once it is saved. A WhatsApp
 * message or a call goes to a contact with a phone number. Saving stores a DRAFT; sending a mail, or marking a WhatsApp
 * message or a call as done, is a separate action on the outreach.
 */
export function ReachOutDialog({
  relationId,
  relationName,
  open: openProp,
  onOpenChange,
  contactsHref,
}: ReachOutDialogProps) {
  const t = useTranslations("outreaches.reachOut");
  const router = useRouter();
  const [ownOpen, setOwnOpen] = useState(false);
  const isControlled = openProp !== undefined;
  const open = isControlled ? openProp : ownOpen;
  const setOpen = (next: boolean) => (isControlled ? onOpenChange?.(next) : setOwnOpen(next));
  const [saving, setSaving] = useState(false);
  // Set when the user leaves for the Contacts card, so closing scrolls there
  // instead of returning focus to the button at the top of the page.
  const goToContacts = useRef(false);

  return (
    <Dialog open={open} onOpenChange={(next) => !saving && setOpen(next)}>
      {!isControlled && (
        <DialogTrigger asChild>
          <Button variant="outline">
            <Send aria-hidden="true" />
            {t("button")}
          </Button>
        </DialogTrigger>
      )}
      <DialogContent
        className="sm:max-w-2xl"
        onCloseAutoFocus={(event) => {
          if (!goToContacts.current) return;
          goToContacts.current = false;
          event.preventDefault();
          const section = document.getElementById(CONTACTS_SECTION_ID);
          section?.scrollIntoView({ behavior: "smooth", block: "start" });
        }}
      >
        <DialogHeader>
          <DialogTitle>{t("title", { name: relationName })}</DialogTitle>
          <DialogDescription>{t("description")}</DialogDescription>
        </DialogHeader>
        {/* Mounted per open, so a reopened dialog starts from the first step. */}
        {open && (
          <ReachOutSteps
            relationId={relationId}
            onSavingChange={setSaving}
            onDone={() => setOpen(false)}
            onGoToContacts={() => {
              setOpen(false);
              if (contactsHref) {
                router.push(contactsHref);
                return;
              }
              goToContacts.current = true;
            }}
          />
        )}
      </DialogContent>
    </Dialog>
  );
}

function ReachOutSteps({
  relationId,
  onSavingChange,
  onDone,
  onGoToContacts,
}: {
  relationId: string;
  onSavingChange: (saving: boolean) => void;
  onDone: () => void;
  onGoToContacts: () => void;
}) {
  const t = useTranslations("outreaches.reachOut");
  const tForm = useTranslations("outreaches.form");
  const tContacts = useTranslations("contacts");
  const tErrors = useTranslations("errors");
  const tOutreaches = useTranslations("outreaches");
  const contacts = useContacts(relationId);
  const createOutreach = useCreateOutreach();
  // The create toast is the one result toast; a failed upload gets its own below.
  const uploadAttachments = useUploadAttachments({ suppressErrorToast: () => true });
  // Files picked for the mail; uploaded once the outreach and its mail exist.
  const [stagedFiles, setStagedFiles] = useState<File[]>([]);
  const [step, setStep] = useState<ReachOutStep>("channel");
  const [selection, setSelection] = useState<ReachOutSelection>(emptyReachOutSelection);
  const isMail = selection.type === OutreachType.MAIL;
  const reachable = useMemo(
    () => reachableContacts(contacts.data?.data ?? [], selection.type),
    [contacts.data, selection.type]
  );
  const [titleError, setTitleError] = useState<string | null>(null);
  const [campaignError, setCampaignError] = useState<string | null>(null);
  const campaigns = useCampaignOptions();
  const select = (change: Partial<ReachOutSelection>) =>
    setSelection((current) => ({ ...current, ...change }));

  const next = nextReachOutStep(step, selection.type);
  const previous = previousReachOutStep(step, selection.type);
  const canContinue = isStepComplete(step, selection, reachable);
  const contact = reachable.find((candidate) => candidate.id === selection.contactId);
  const isPending = createOutreach.isPending || uploadAttachments.isPending;

  useEffect(() => onSavingChange(isPending), [isPending, onSavingChange]);

  const save = () => {
    setTitleError(null);
    createOutreach.mutate(toReachOutRequest(relationId, selection), {
      onSuccess: (response) => {
        const mailId = response.data?.mail?.id;
        if (selection.type !== OutreachType.MAIL || stagedFiles.length === 0 || !mailId) {
          onDone();
          return;
        }
        // The outreach is saved either way, so the dialog closes; a failed
        // upload says so, and the files can be added on the outreach again.
        uploadAttachments.mutate(
          {
            entityType: StorageEntityType.MAIL,
            entityId: mailId,
            files: stagedFiles,
            silentSuccess: true,
          },
          {
            onError: (error) => {
              console.error("[reach-out] attachment upload after create failed:", error);
              toast.error(outreachErrorText(error, tOutreaches, "toast.attachmentsUploadError"));
            },
            onSettled: onDone,
          }
        );
      },
      onError: (error) => {
        const fields = outreachFieldErrors(error, tOutreaches);
        if (fields.title) setTitleError(fields.title);
        if (fields.campaignId) setCampaignError(fields.campaignId);
      },
    });
  };

  return (
    <>
      <ol className="flex flex-wrap gap-x-4 gap-y-1 text-sm" aria-label={t("stepsLabel")}>
        {reachOutSteps(selection.type).map((candidate, index) => (
          <li
            key={candidate}
            aria-current={candidate === step ? "step" : undefined}
            className={cn(
              "tabular-nums",
              candidate === step ? "text-foreground font-medium" : "text-muted-foreground"
            )}
          >
            {index + 1}. {t(`steps.${candidate}`)}
          </li>
        ))}
      </ol>

      <div className="min-h-40">
        {step === "channel" && (
          <ChoiceList
            legend={t("channelLegend")}
            value={selection.type}
            onChange={(value) => {
              const type = OutreachTypeSchema.safeParse(value);
              if (type.success) select({ type: type.data });
            }}
            options={OutreachTypeSchema.options.map((type) => ({
              value: type,
              label: (
                <span className="flex min-w-0 flex-col">
                  <span className="font-medium">{tOutreaches(`types.${type}`)}</span>
                  <span className="text-muted-foreground text-sm">{t(`channelHints.${type}`)}</span>
                </span>
              ),
            }))}
          />
        )}
        {step === "contact" &&
          (contacts.isError && contacts.data === undefined ? (
            <LoadError
              title={tContacts("loadError")}
              message={contactErrorText(contacts.error, tContacts, "errors.loadHint")}
              retryLabel={tErrors("tryAgain")}
              onRetry={() => void contacts.refetch()}
            />
          ) : contacts.data === undefined ? (
            <TableLoading label={tContacts("loading")} />
          ) : reachable.length === 0 ? (
            <div className="flex flex-col items-start gap-3">
              <p className="font-medium">
                {isMail ? t("noContactsTitle") : t("noPhoneContactsTitle")}
              </p>
              <p className="text-muted-foreground text-sm">
                {isMail ? t("noContactsHint") : t("noPhoneContactsHint")}
              </p>
              <Button variant="outline" onClick={onGoToContacts}>
                {t("goToContacts")}
              </Button>
            </div>
          ) : (
            <ChoiceList
              legend={isMail ? t("contactLegend") : t("phoneContactLegend")}
              value={selection.contactId}
              onChange={(contactId) => select({ contactId })}
              options={reachable.map((candidate) => ({
                value: candidate.id,
                label: <ContactLabel contact={candidate} isMail={isMail} />,
              }))}
            />
          ))}
        {step === "mail" && <MailStep selection={selection} onChange={setSelection} />}
        {/* Kept mounted while the mail is the channel, so the staged files
            survive a step back and forth; hidden on the other steps. */}
        {isMail && (
          <div className={cn("flex flex-col gap-6", step !== "attachments" && "hidden")}>
            <div className="flex flex-col gap-2">
              <p className="text-sm font-medium">{tOutreaches("attachments.filesLegend")}</p>
              <p className="text-muted-foreground text-sm">
                {tOutreaches("attachments.filesHint")}
              </p>
              <AttachmentUpload
                entityType={StorageEntityType.MAIL}
                onStagedFilesChange={setStagedFiles}
              />
            </div>
          </div>
        )}
        {step === "save" && (
          <div className="flex flex-col gap-4">
            <Field
              label={tForm("title")}
              hint={t("titleHint")}
              error={titleError}
              className="w-full"
            >
              <Input
                value={selection.title}
                maxLength={OUTREACH_LIMITS.titleMax}
                placeholder={tForm("titlePlaceholder")}
                disabled={isPending}
                onChange={(event) => {
                  setTitleError(null);
                  select({ title: event.target.value });
                }}
              />
            </Field>
            <Field
              label={tForm("campaign")}
              hint={campaigns.isError ? tForm("campaignsLoadError") : undefined}
              error={campaignError}
              className="w-full"
            >
              <NativeSelect
                value={selection.campaignId ?? ""}
                disabled={isPending || (campaigns.isPending && campaigns.fetchStatus !== "idle")}
                onChange={(event) => {
                  setCampaignError(null);
                  select({ campaignId: event.target.value || null });
                }}
                className="w-full"
              >
                <option value="">{tForm("campaignPlaceholder")}</option>
                {(campaigns.data ?? []).map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </NativeSelect>
            </Field>
            <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-sm">
              <dt className="text-muted-foreground">{t("summary.channel")}</dt>
              <dd>{tOutreaches(`types.${selection.type}`)}</dd>
              <dt className="text-muted-foreground">{t("summary.contact")}</dt>
              <dd className="break-words">
                {contact ? `${contact.name} (${isMail ? contact.email : contact.phone})` : ""}
              </dd>
              {isMail && (
                <>
                  <dt className="text-muted-foreground">{t("summary.mail")}</dt>
                  <dd className="break-words">
                    {selection.source === null || selection.source === SCRATCH
                      ? t("fromScratch")
                      : selection.source.name}
                  </dd>
                  <dt className="text-muted-foreground">{t("summary.subject")}</dt>
                  <dd className="break-words">{selection.subject}</dd>
                  <dt className="text-muted-foreground">{t("summary.attachments")}</dt>
                  <dd>
                    {t("summary.attachmentCount", {
                      count: stagedFiles.length,
                    })}
                  </dd>
                </>
              )}
            </dl>
          </div>
        )}
      </div>

      <DialogFooter>
        {previous && (
          <Button variant="outline" disabled={isPending} onClick={() => setStep(previous)}>
            {t("back")}
          </Button>
        )}
        {next ? (
          <Button disabled={!canContinue} onClick={() => setStep(next)}>
            {t("next")}
          </Button>
        ) : (
          <Button disabled={!canContinue || isPending} onClick={save}>
            {isPending ? tForm("saving") : t("saveDraft")}
          </Button>
        )}
      </DialogFooter>
    </>
  );
}

/**
 * Picks a mail template or "Write from scratch", then shows the subject and
 * body to edit. A template's text loads once picked; the language follows
 * the template, and only a mail written by hand takes one of its own.
 */
function MailStep({
  selection,
  onChange,
}: {
  selection: ReachOutSelection;
  onChange: (update: (current: ReachOutSelection) => ReachOutSelection) => void;
}) {
  const t = useTranslations("outreaches.reachOut");
  const tForm = useTranslations("outreaches.form");
  const tTemplates = useTranslations("documentTemplates");
  const tMailTemplates = useTranslations("mailTemplates");
  const tErrors = useTranslations("errors");
  const [search, setSearch] = useState<string | null>(null);
  const [pickedId, setPickedId] = useState<string | null>(
    selection.source === null ? null : selection.source === SCRATCH ? SCRATCH : selection.source.id
  );
  const query = useMemo(
    (): MailTemplateQueryRequest => ({
      search: search ?? undefined,
      page: 1,
      amount: TEMPLATE_CHOICES,
    }),
    [search]
  );
  const templates = useMailTemplates(query);
  const pickedTemplateId = pickedId !== null && pickedId !== SCRATCH ? pickedId : "";
  const template = useMailTemplate(pickedTemplateId, { enabled: pickedTemplateId !== "" });
  const loaded = template.data?.data;
  const sourceId =
    selection.source === null || selection.source === SCRATCH ? null : selection.source.id;

  // A picked template's text replaces the mail once it arrives.
  useEffect(() => {
    if (loaded && loaded.id === pickedTemplateId && sourceId !== loaded.id) {
      onChange((current) => applyTemplate(current, loaded));
    }
  }, [loaded, pickedTemplateId, sourceId, onChange]);

  const rows = templates.data?.data ?? [];
  const isScratch = selection.source === SCRATCH;
  const waitingForTemplate = pickedTemplateId !== "" && sourceId !== pickedTemplateId;
  const languageOptions = DocumentLanguageSchema.options;

  return (
    <div className="flex flex-col gap-4">
      <FilterLabel label={tMailTemplates("filters.searchLabel")} className="w-full">
        <SenseySearchInput
          value={search}
          placeholder={t("templateSearchPlaceholder")}
          onSearchChange={(value) => setSearch(value ?? null)}
          className="w-full"
        />
      </FilterLabel>
      {templates.isError && templates.data === undefined ? (
        <LoadError
          title={tMailTemplates("loadError")}
          message={mailTemplateErrorText(templates.error, tMailTemplates, "errors.loadHint")}
          retryLabel={tErrors("tryAgain")}
          onRetry={() => void templates.refetch()}
        />
      ) : templates.data === undefined ? (
        <TableLoading label={tMailTemplates("loading")} />
      ) : (
        <div className="max-h-[30vh] overflow-y-auto">
          <ChoiceList
            legend={t("mailLegend")}
            value={pickedId}
            onChange={(value) => {
              setPickedId(value);
              if (value === SCRATCH) onChange(startFromScratch);
            }}
            options={[
              { value: SCRATCH, label: <span className="font-medium">{t("fromScratch")}</span> },
              ...rows.map((row) => ({
                value: row.id,
                label: (
                  <span className="flex min-w-0 flex-1 items-center justify-between gap-3">
                    <span className="truncate">{row.name}</span>
                    <span className="text-muted-foreground text-sm whitespace-nowrap">
                      {tTemplates(`languages.${row.language}`)}
                    </span>
                  </span>
                ),
              })),
            ]}
          />
          {rows.length === 0 && (
            <p className="text-muted-foreground mt-2 text-sm" role="status">
              {search ? tMailTemplates("emptyFiltered") : t("noTemplates")}
            </p>
          )}
        </div>
      )}

      {pickedTemplateId !== "" && template.isError && loaded === undefined ? (
        <LoadError
          title={tMailTemplates("loadOneError")}
          message={mailTemplateErrorText(template.error, tMailTemplates, "errors.loadHint")}
          retryLabel={tErrors("tryAgain")}
          onRetry={() => void template.refetch()}
        />
      ) : waitingForTemplate ? (
        <TableLoading label={tMailTemplates("loadingOne")} />
      ) : (
        selection.source !== null && (
          <div className="flex flex-col gap-3">
            <Field label={tForm("subject")} className="w-full">
              <Input
                value={selection.subject}
                maxLength={OUTREACH_LIMITS.subjectMax}
                placeholder={tForm("subjectPlaceholder")}
                onChange={(event) => {
                  const subject = event.target.value;
                  onChange((current) => ({ ...current, subject }));
                }}
              />
            </Field>
            <Field label={tForm("body")} className="w-full">
              <Textarea
                value={selection.body}
                rows={10}
                maxLength={OUTREACH_LIMITS.bodyMax}
                placeholder={tForm("bodyPlaceholder")}
                onChange={(event) => {
                  const body = event.target.value;
                  onChange((current) => ({ ...current, body }));
                }}
              />
            </Field>
            {isScratch ? (
              <Field label={tForm("language")} hint={t("languageHint")} className="w-full sm:w-48">
                <NativeSelect
                  value={selection.language}
                  onChange={(event) => {
                    const language = DocumentLanguageSchema.safeParse(event.target.value);
                    if (language.success) {
                      onChange((current) => ({ ...current, language: language.data }));
                    }
                  }}
                >
                  {languageOptions.map((language) => (
                    <option key={language} value={language}>
                      {tTemplates(`languages.${language}`)}
                    </option>
                  ))}
                </NativeSelect>
              </Field>
            ) : (
              <p className="text-muted-foreground text-sm">
                {t("templateLanguage", {
                  language: tTemplates(`languages.${selection.language}`),
                })}
              </p>
            )}
          </div>
        )
      )}
    </div>
  );
}

/** A contact with the address a mail goes to, or the phone number of a WhatsApp message or a call. */
function ContactLabel({ contact, isMail }: { contact: ContactListItem; isMail: boolean }) {
  return (
    <span className="flex min-w-0 flex-col">
      <span className="break-words">
        {contact.name}
        {contact.role && <span className="text-muted-foreground"> · {contact.role}</span>}
      </span>
      <span className="text-muted-foreground text-sm break-all">
        {isMail ? contact.email : contact.phone}
      </span>
    </span>
  );
}

/** One choice out of several, as native radio buttons so the keyboard and screen readers know them. */
function ChoiceList({
  legend,
  value,
  onChange,
  options,
}: {
  legend: string;
  value: string | null;
  onChange: (value: string) => void;
  options: { value: string; label: ReactNode }[];
}) {
  const name = useId();
  return (
    <fieldset className="flex flex-col gap-2">
      <legend className="mb-2 text-sm font-medium">{legend}</legend>
      {options.map((option) => (
        <label
          key={option.value}
          className={cn(
            "flex cursor-pointer items-start gap-3 rounded-lg border p-3",
            option.value === value && "border-primary bg-accent"
          )}
        >
          <input
            type="radio"
            name={name}
            value={option.value}
            checked={option.value === value}
            onChange={() => onChange(option.value)}
            className="mt-1"
          />
          {option.label}
        </label>
      ))}
    </fieldset>
  );
}
