/**
 * The query keys of versioned config, shared by the scope, versions,
 * releases, links, changes and kind features. Every key starts with the
 * organization under one root, so an approval, which moves a current version
 * and publishes releases, reloads all of them at once. A version and its
 * files never change once stored, except a draft's: its writes reach the
 * draft's entries through these shapes.
 */
export const VERSIONED_KEYS = {
  all: (organizationId: string) => ["versioned", organizationId] as const,
  /** Packs and each repository's pack list: what the scope tree offers. */
  owners: (organizationId: string) => [...VERSIONED_KEYS.all(organizationId), "owners"] as const,
  packs: (organizationId: string) => [...VERSIONED_KEYS.owners(organizationId), "packs"] as const,
  repositoryPacks: (organizationId: string, repositoryId: string) =>
    [...VERSIONED_KEYS.owners(organizationId), "repository-packs", repositoryId] as const,
  releases: (organizationId: string, repositoryId: string) =>
    [...VERSIONED_KEYS.all(organizationId), "releases", repositoryId] as const,
  version: (organizationId: string, versionId: string) =>
    [...VERSIONED_KEYS.all(organizationId), "version", versionId] as const,
  files: (organizationId: string, versionId: string) =>
    [...VERSIONED_KEYS.all(organizationId), "file", versionId] as const,
  diffs: (organizationId: string, versionId: string) =>
    [...VERSIONED_KEYS.all(organizationId), "diff", versionId] as const,
  /** The kind lists (rules and the other agent files, conventions, skills, settings) of several versions; a draft write reloads them all. */
  kinds: (organizationId: string) => [...VERSIONED_KEYS.all(organizationId), "kinds"] as const,
  changes: (organizationId: string) => [...VERSIONED_KEYS.all(organizationId), "changes"] as const,
  /** Every pack's effective items; a draft write or an approval reloads them. */
  effective: (organizationId: string) =>
    [...VERSIONED_KEYS.all(organizationId), "effective"] as const,
  /** One pack's items at one of its versions (`null` for the current one), own only or with the inherited ones. */
  packEffective: (
    organizationId: string,
    packId: string,
    versionId: string | null,
    inherited: boolean
  ) => [...VERSIONED_KEYS.effective(organizationId), packId, versionId, inherited] as const,
};
