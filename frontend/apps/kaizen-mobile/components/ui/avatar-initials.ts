/**
 * Up to two initials of a name, upper-cased: "Mert Yilmaz" is "MY", "kaizen"
 * is "K". An empty name is "?".
 */
export function initials(name: string | null | undefined): string {
  const letters = String(name ?? "")
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((word) => (Array.from(word)[0] ?? "").toUpperCase())
    .join("");
  return letters || "?";
}

/**
 * The tint pairs of initials avatars, as ground and text classes. They reuse
 * the ticket-type pairs, which hold their contrast in both schemes, in the
 * order blue, green, purple, amber, red, grey.
 */
export const AVATAR_TINTS = [
  { ground: "bg-type-incident-bg", text: "text-type-incident-fg" },
  { ground: "bg-type-feature-bg", text: "text-type-feature-fg" },
  { ground: "bg-type-problem-bg", text: "text-type-problem-fg" },
  { ground: "bg-type-tech-debt-bg", text: "text-type-tech-debt-fg" },
  { ground: "bg-type-bug-bg", text: "text-type-bug-fg" },
  { ground: "bg-type-refactor-bg", text: "text-type-refactor-fg" },
] as const;

/** The tint of a name: the same name always gets the same colour. */
export function avatarTint(name: string | null | undefined): (typeof AVATAR_TINTS)[number] {
  const value = String(name ?? "");
  let sum = 0;
  for (let index = 0; index < value.length; index += 1) {
    sum = (sum + value.charCodeAt(index)) % AVATAR_TINTS.length;
  }
  return AVATAR_TINTS[sum];
}

/** The design avatar sizes: 20 on cards, 32 in rows, 64 on the profile. */
export const AVATAR_SIZE = { sm: 20, md: 32, lg: 64 } as const;

export type AvatarSize = keyof typeof AVATAR_SIZE | number;

export function avatarPixels(size: AvatarSize): number {
  return typeof size === "number" ? size : AVATAR_SIZE[size];
}
