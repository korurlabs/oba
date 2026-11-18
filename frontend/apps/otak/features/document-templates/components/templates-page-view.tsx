"use client";

import { useCallback } from "react";
import { useTranslations } from "next-intl";
import { Tabs, TabsList, TabsTrigger } from "@oba/ui/sky/tabs";
import { MailTemplatesView } from "@/features/mail-templates/components/mail-templates-view";
import { useDocumentTemplateFilterParams } from "../hooks/use-document-template-filter-params";
import { useTemplatePageTab } from "../hooks/use-template-page-tab";
import { isTemplatePageTab, TEMPLATE_PAGE_TABS } from "../lib/template-tabs";
import { DocumentTemplatesView } from "./document-templates-view";

/**
 * The Templates page: the organization's document templates and its mail
 * templates, one tab each, with the tab in the URL. Only the open tab is
 * mounted, so only its list loads.
 */
export function TemplatesPageView() {
  const t = useTranslations("documentTemplates");
  const [{ tab }, setTab] = useTemplatePageTab();
  // Both lists keep their search, language and page under the same URL keys.
  const [, setFilters] = useDocumentTemplateFilterParams();

  const handleTabChange = useCallback(
    (value: string) => {
      if (!isTemplatePageTab(value)) return;
      // The filters belong to the open tab; a tab change starts the other one afresh.
      void setFilters({ search: null, type: null, language: null, page: null });
      void setTab({ tab: value });
    },
    [setFilters, setTab]
  );

  const tabs = (
    <Tabs value={tab} onValueChange={handleTabChange}>
      <TabsList aria-label={t("tabsLabel")}>
        {TEMPLATE_PAGE_TABS.map((pageTab) => (
          <TabsTrigger key={pageTab} value={pageTab}>
            {t(`tabs.${pageTab}`)}
          </TabsTrigger>
        ))}
      </TabsList>
    </Tabs>
  );

  return tab === "mail" ? <MailTemplatesView tabs={tabs} /> : <DocumentTemplatesView tabs={tabs} />;
}
