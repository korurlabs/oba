"use client";

import { useCallback } from "react";
import { useTranslations } from "next-intl";
import { Tabs, TabsList, TabsTrigger } from "@oba/ui/sky/tabs";
import { MailSendersView } from "@/features/mail-senders/components/mail-senders-view";
import { useOutreachFilterParams } from "../hooks/use-outreach-filter-params";
import { useOutreachPageTab } from "../hooks/use-outreach-page-tab";
import { isOutreachPageTab, OUTREACH_PAGE_TABS } from "../lib/outreach-tabs";
import { OutreachesView } from "./outreaches-view";

/**
 * The Outreaches page: the organization's reach-outs and the mailboxes they are
 * sent from, one tab each, with the tab in the URL. Only the open tab is
 * mounted, so only its list loads.
 */
export function OutreachesPageView() {
  const t = useTranslations("outreaches");
  const [{ tab }, setTab] = useOutreachPageTab();
  const [, setFilters] = useOutreachFilterParams();

  const handleTabChange = useCallback(
    (value: string) => {
      if (!isOutreachPageTab(value)) return;
      // The status filter and page belong to the outreaches tab; a tab change starts it afresh.
      void setFilters({ status: null, page: null });
      void setTab({ tab: value });
    },
    [setFilters, setTab]
  );

  const tabs = (
    <Tabs value={tab} onValueChange={handleTabChange}>
      <TabsList aria-label={t("tabsLabel")}>
        {OUTREACH_PAGE_TABS.map((pageTab) => (
          <TabsTrigger key={pageTab} value={pageTab}>
            {t(`tabs.${pageTab}`)}
          </TabsTrigger>
        ))}
      </TabsList>
    </Tabs>
  );

  return tab === "senders" ? <MailSendersView tabs={tabs} /> : <OutreachesView tabs={tabs} />;
}
