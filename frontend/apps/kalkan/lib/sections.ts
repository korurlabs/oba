/**
 * The sections of Kalkan, in the order and groups of the sidebar. A section
 * has no type of its own on the server: it is the root page whose slug equals
 * the route segment, so `/policies` shows the tree under the root `policies`.
 * Each slug is also the key of the section's label under `sidebar.sections`.
 */
export const SECTION_GROUPS = [
  {
    key: "compliance",
    slugs: ["frameworks", "controls", "policies", "risks", "evidence", "audits"],
  },
  { key: "inventory", slugs: ["assets", "suppliers", "access"] },
  {
    key: "knowledge",
    slugs: ["architecture", "protection", "monitoring", "infrastructure", "guides"],
  },
  { key: "legal", slugs: ["laws", "incentives"] },
] as const;

export type SectionGroupKey = (typeof SECTION_GROUPS)[number]["key"];
export type SectionSlug = (typeof SECTION_GROUPS)[number]["slugs"][number];

/** Every section slug, in sidebar order. */
export const SECTION_SLUGS: readonly SectionSlug[] = SECTION_GROUPS.flatMap((group) => group.slugs);

/** Whether `value` names a section, so a route can answer 404 for any other segment. */
export function isSectionSlug(value: string): value is SectionSlug {
  return (SECTION_SLUGS as readonly string[]).includes(value);
}

/**
 * The title a section's root page gets when an admin sets the section up, in
 * both content locales. Page content is bilingual whatever the interface
 * language is, so these are written out here instead of read from the
 * messages of the viewer's locale.
 */
export const SECTION_ROOT_TITLES: Record<SectionSlug, { TR: string; EN: string }> = {
  frameworks: { TR: "Çerçeve", EN: "Framework" },
  controls: { TR: "Kontrol", EN: "Control" },
  policies: { TR: "Politika", EN: "Policy" },
  risks: { TR: "Risk", EN: "Risk" },
  evidence: { TR: "Kanıt", EN: "Evidence" },
  audits: { TR: "Tetkik", EN: "Audit" },
  assets: { TR: "Varlık", EN: "Asset" },
  suppliers: { TR: "Tedarikçi", EN: "Supplier" },
  access: { TR: "Erişim", EN: "Access" },
  architecture: { TR: "Mimari", EN: "Architecture" },
  protection: { TR: "Koruma", EN: "Protection" },
  monitoring: { TR: "İzleme", EN: "Monitoring" },
  infrastructure: { TR: "Altyapı", EN: "Infrastructure" },
  guides: { TR: "Rehber", EN: "Guide" },
  laws: { TR: "Yasa", EN: "Law" },
  incentives: { TR: "Teşvik", EN: "Incentive" },
};
