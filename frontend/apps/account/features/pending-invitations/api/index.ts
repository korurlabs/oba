import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { ApiResponse } from "@oba/shared/schema";
import type { InvitationResponse } from "@oba/shared/features/users/schema";
import type { InvitationDecision } from "../schemas";

export async function decideInvitation(
  invitationId: string,
  decision: InvitationDecision,
): Promise<ApiResponse<InvitationResponse>> {
  const res = await api.post<ApiResponse<InvitationResponse>>(
    API_ROUTES.userInvitations.decision,
    { invitationId, decision },
  );
  return res.data;
}
