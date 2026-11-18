import appEn from "@/locales/en.json";
import appNl from "@/locales/nl.json";
import appTr from "@/locales/tr.json";
import { createIntlRequest } from "@oba/shared/libs/i18n/request";

const intlRequest = createIntlRequest({
  en: appEn,
  nl: appNl,
  tr: appTr,
});

export default intlRequest;
