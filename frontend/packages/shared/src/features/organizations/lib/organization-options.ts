import { CheckCircle, XCircle, type LucideIcon } from "lucide-react";
import { ORGANIZATION_ROLE_ICONS } from "@oba/shared/components/organization-role-badge";
import type { SelectOption } from "@oba/shared/features/form";
import type { OrganizationMemberStatus, OrganizationRole } from "../schemas";

/**
 * Organization role options with icons for role selectors.
 */
export const ROLE_OPTIONS: { value: OrganizationRole; icon: LucideIcon }[] = [
  { value: "OWNER", icon: ORGANIZATION_ROLE_ICONS.OWNER },
  { value: "ADMIN", icon: ORGANIZATION_ROLE_ICONS.ADMIN },
  { value: "MEMBER", icon: ORGANIZATION_ROLE_ICONS.MEMBER },
  { value: "GUEST", icon: ORGANIZATION_ROLE_ICONS.GUEST },
];

/**
 * Organization member status options with icons for status selectors.
 */
export const STATUS_OPTIONS: { value: OrganizationMemberStatus; icon: LucideIcon }[] = [
  { value: "ACTIVE", icon: CheckCircle },
  { value: "DISABLED", icon: XCircle },
];

/**
 * Organization form select options.
 *
 * These option definitions are shared between the organization detail page
 * and the create-organization page. The `t` function (from `onboarding`
 * namespace) is passed in so the labels are translated at render time.
 */

type TranslationFn = (key: string) => string;

export function getIndustryOptions(t: TranslationFn): SelectOption[] {
  return [
    { label: t("industries.technology"), value: "technology" },
    { label: t("industries.healthcare"), value: "healthcare" },
    { label: t("industries.finance"), value: "finance" },
    { label: t("industries.education"), value: "education" },
    { label: t("industries.manufacturing"), value: "manufacturing" },
    { label: t("industries.retail"), value: "retail" },
    { label: t("industries.construction"), value: "construction" },
    { label: t("industries.consulting"), value: "consulting" },
    { label: t("industries.other"), value: "other" },
  ];
}

export function getCompanySizeOptions(t: TranslationFn): SelectOption[] {
  return [
    { label: t("companySizes.1-10"), value: "1-10" },
    { label: t("companySizes.11-50"), value: "11-50" },
    { label: t("companySizes.51-200"), value: "51-200" },
    { label: t("companySizes.201-500"), value: "201-500" },
    { label: t("companySizes.500+"), value: "500+" },
  ];
}

export function getCountryOptions(t: TranslationFn): SelectOption[] {
  return [
    { label: t("countries.NLD"), value: "NLD" },
    { label: t("countries.TUR"), value: "TUR" },
    { label: t("countries.DEU"), value: "DEU" },
    { label: t("countries.GBR"), value: "GBR" },
    { label: t("countries.USA"), value: "USA" },
    { label: t("countries.FRA"), value: "FRA" },
    { label: t("countries.BEL"), value: "BEL" },
  ];
}
