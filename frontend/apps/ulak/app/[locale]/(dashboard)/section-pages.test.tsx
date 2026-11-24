import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import type { QueryClient } from "@tanstack/react-query";
import type { ReactElement } from "react";
import type { OrganizationSection } from "@/features/organizations/components/current-organization-page";

// Each page of an organization area prefetches its section on the server and
// hydrates it around the shared client page. The prefetch is a recorder that
// puts one entry in the cache the page hands to the boundary; the page, its
// metadata and the boundary are real.
let prefetched: OrganizationSection[];

const actualPrefetch = { ...(await import("./_lib/prefetch-current-organization")) };
const actualServerIntl = { ...(await import("next-intl/server")) };

mock.module("./_lib/prefetch-current-organization", () => ({
  ...actualPrefetch,
  prefetchCurrentOrganization: async (queryClient: QueryClient, section: OrganizationSection) => {
    prefetched.push(section);
    queryClient.setQueryData(["prefetched", section], { section });
  },
}));
mock.module("next-intl/server", () => ({
  ...actualServerIntl,
  getTranslations: async () => (key: string) => key,
}));

afterAll(() => {
  mock.module("./_lib/prefetch-current-organization", () => actualPrefetch);
  mock.module("next-intl/server", () => actualServerIntl);
});

const PAGES: Array<{ route: string; section: OrganizationSection; title: string }> = [
  { route: "humans", section: "humans", title: "humans" },
  { route: "groups", section: "groups", title: "groups" },
  { route: "legal-entities", section: "legalEntities", title: "legalEntities" },
  { route: "access", section: "access", title: "access" },
  { route: "invitations", section: "invitations", title: "invitations" },
  { route: "settings", section: "settings", title: "organizationSettings" },
];

type Dehydrated = { queries: Array<{ queryKey: unknown[] }> };

beforeEach(() => {
  prefetched = [];
});

describe("organization section pages", () => {
  for (const { route, section, title } of PAGES) {
    describe(route, () => {
      test("prefetches its own section and hydrates it around the current organization page", async () => {
        const { default: Page } = await import(`./${route}/page`);
        const element = (await Page()) as ReactElement<{
          state: Dehydrated;
          children: ReactElement<{ section: string }>;
        }>;

        expect(prefetched).toEqual([section]);
        expect(element.props.state.queries.map((query) => query.queryKey)).toEqual([
          ["prefetched", section],
        ]);
        expect(element.props.children.props.section).toBe(section);
      });

      test("is titled with its menu label", async () => {
        const { generateMetadata } = await import(`./${route}/page`);
        expect(await generateMetadata()).toEqual({ title });
      });
    });
  }
});
