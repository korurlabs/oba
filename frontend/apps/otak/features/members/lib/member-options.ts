import type { OrganizationMember } from "@oba/shared/features/organizations/schemas";
import type { SelectOption } from "@oba/shared/features/form";

/** The name fields every member list carries; a profile may have no name yet. */
interface NamedMember {
  email: string;
  firstName: string | null;
  lastName: string | null;
}

/** A member's display name: first and last name, or the email while the profile has none. */
export function memberName(member: NamedMember): string {
  const name = [member.firstName, member.lastName]
    .filter((part) => typeof part === "string" && part.trim() !== "")
    .join(" ");
  return name === "" ? member.email : name;
}

/** Every member as a select option, active and disabled, sorted by name. */
export function memberOptions(members: readonly OrganizationMember[] | undefined): SelectOption[] {
  return (members ?? [])
    .map((member) => ({ value: member.userId, label: memberName(member) }))
    .sort((a, b) => a.label.localeCompare(b.label));
}
