"use client";

import { useCallback } from "react";
import { useTranslations } from "next-intl";
import { Tabs, TabsList, TabsTrigger } from "@oba/ui/sky/tabs";
import { MailSendersView } from "@/features/mail-senders/components/mail-senders-view";
import { useMailPageTab } from "../hooks/use-mail-page-tab";
import { useMailTemplateFilterParams } from "../hooks/use-mail-template-filter-params";
import { isMailPageTab, MAIL_PAGE_TABS } from "../lib/mail-page-tabs";
import { MailTemplatesView } from "./mail-templates-view";

/**
 * The Mail page: the organization's mail templates and the mailboxes its
 * outreach mail is sent from, one tab each, with the tab in the URL. Each tab
 * is its own list with its own columns and create button, so they are page
 * tabs rather than a filter. Only the open tab is mounted, so only its list
 * loads.
 */
export function MailPageView() {
  const t = useTranslations("mailPage");
  const [{ tab }, setTab] = useMailPageTab();
  const [, setTemplateFilters] = useMailTemplateFilterParams();

  const handleTabChange = useCallback(
    (value: string) => {
      if (!isMailPageTab(value)) return;
      // The search, language and page belong to the list that was open; the
      // other tab starts afresh. The senders read the same search param.
      void setTemplateFilters({ search: null, language: null, page: null });
      void setTab({ tab: value });
    },
    [setTemplateFilters, setTab]
  );

  const tabs = (
    <Tabs value={tab} onValueChange={handleTabChange}>
      <TabsList aria-label={t("tabsLabel")}>
        {MAIL_PAGE_TABS.map((pageTab) => (
          <TabsTrigger key={pageTab} value={pageTab}>
            {t(`tabs.${pageTab}`)}
          </TabsTrigger>
        ))}
      </TabsList>
    </Tabs>
  );

  return tab === "senders" ? <MailSendersView tabs={tabs} /> : <MailTemplatesView tabs={tabs} />;
}
