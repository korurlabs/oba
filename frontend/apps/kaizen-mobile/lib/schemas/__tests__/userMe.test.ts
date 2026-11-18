import { describe, expect, test } from "bun:test";
import { userMeSchema } from "@oba/shared/features/users/schema";

/**
 * The `data` of `GET /users/me` for the local `dev` user, as the backend sends
 * it. The organization id is a seeded one without an RFC 9562 version nibble.
 */
const SEEDED_ORGANIZATION_ID = "b0000000-0000-0000-0000-000000000001";

function realMe(): Record<string, unknown> {
  return {
    id: "0c79633d-70e3-487b-b84c-8ed3f69c6a54",
    createdAt: "2026-11-02T15:15:47.715809Z",
    updatedAt: "2026-11-05T12:22:24.882246Z",
    email: "dev@example.com",
    userStatus: "ACTIVE",
    profile: {
      firstName: "Dev",
      lastName: "User",
      phoneNumber: null,
      userPosition: null,
      userPreferredLanguage: null,
      userGender: null,
    },
    defaultOrganizationId: SEEDED_ORGANIZATION_ID,
    userPreferences: {
      theme: "system",
      sidebar: { boardOrder: [], pinnedBoardIds: [], childBoardOrder: {} },
    },
    organizations: [
      { id: SEEDED_ORGANIZATION_ID, name: "Sensey", role: "OWNER", organizationStatus: "ACTIVE" },
    ],
    pendingInvitations: [],
  };
}

describe("userMeSchema", () => {
  test("accepts the backend response with a seeded organization id", () => {
    const me = userMeSchema.parse(realMe());
    expect(me.organizations?.[0]?.id).toBe(SEEDED_ORGANIZATION_ID);
    expect(me.defaultOrganizationId).toBe(SEEDED_ORGANIZATION_ID);
  });

  test("accepts null organization and invitation lists", () => {
    const me = userMeSchema.parse({ ...realMe(), organizations: null, pendingInvitations: null });
    expect(me.organizations).toBeNull();
    expect(me.pendingInvitations).toBeNull();
  });

  test("accepts a pending invitation with seeded ids and null timestamps", () => {
    const me = userMeSchema.parse({
      ...realMe(),
      pendingInvitations: [
        {
          id: "c0000000-0000-0000-0000-000000000001",
          organizationId: SEEDED_ORGANIZATION_ID,
          organizationName: "Sensey",
          email: "dev@example.com",
          role: "MEMBER",
          status: "PENDING",
          invitedByEmail: "owner@example.com",
          acceptedAt: null,
          expiresAt: null,
          declinedAt: null,
          createdAt: "2026-11-02T15:15:47.715809Z",
        },
      ],
    });
    expect(me.pendingInvitations).toHaveLength(1);
  });

  test("accepts the status the backend sends for a closed account", () => {
    const me = userMeSchema.parse({ ...realMe(), userStatus: "DISABLED" });
    expect(me.userStatus).toBe("DISABLED");
  });

  test("still rejects an organization id that is not an id", () => {
    const result = userMeSchema.safeParse({
      ...realMe(),
      organizations: [
        { id: "sensey", name: "Sensey", role: "OWNER", organizationStatus: "ACTIVE" },
      ],
    });
    expect(result.success).toBe(false);
  });
});
