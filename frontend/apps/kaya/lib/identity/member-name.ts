/** Who did it, from the member list; a user who left the organization is no longer listed. */
export function memberName(userId: string | null | undefined, names: ReadonlyMap<string, string>) {
  if (!userId) return "Unknown";
  return names.get(userId) ?? "A former member";
}
