import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { ApiResponse } from "@oba/shared/schema";
import type {
  OrganizationDetail,
  OrganizationUpdateRequest,
  OrganizationMember,
  OrganizationMemberStatus,
  Invitation,
  CreateInvitationForm,
  UpdateMemberRoleRequest,
  OrganizationGroup,
  OrganizationGroupRequest,
  OrganizationGroupMember,
  OrganizationGroupMemberAddRequest,
  AppGrant,
  AppGrantCreateRequest,
  MemberAppAccess,
} from "@oba/shared/features/organizations/schemas";

export async function getOrganization(organizationId: string) {
  const res = await api.get<ApiResponse<OrganizationDetail>>(
    API_ROUTES.organizations.detail(organizationId),
  );
  return res.data;
}

export async function updateOrganization(
  organizationId: string,
  data: OrganizationUpdateRequest,
) {
  const res = await api.put<ApiResponse<OrganizationDetail>>(
    API_ROUTES.organizations.detail(organizationId),
    data,
  );
  return res.data;
}

// -- Organization Members -----------------------------------------------------

export async function listOrganizationMembers(organizationId: string) {
  const res = await api.get<ApiResponse<OrganizationMember[]>>(
    API_ROUTES.organizationMembers.list(organizationId),
  );
  return res.data;
}

export async function updateMemberRole(
  organizationId: string,
  userId: string,
  data: UpdateMemberRoleRequest,
) {
  const res = await api.patch<ApiResponse<OrganizationMember>>(
    API_ROUTES.organizationMembers.role(organizationId, userId),
    data,
  );
  return res.data;
}

export async function updateMemberStatus(
  organizationId: string,
  userId: string,
  status: OrganizationMemberStatus,
) {
  await api.patch(
    API_ROUTES.organizationMembers.status(organizationId, userId),
    { status },
  );
}

export async function removeMember(organizationId: string, userId: string) {
  await api.delete(API_ROUTES.organizationMembers.detail(organizationId, userId));
}

// -- Organization Groups ------------------------------------------------------

export async function listOrganizationGroups(organizationId: string) {
  const res = await api.get<ApiResponse<OrganizationGroup[]>>(
    API_ROUTES.organizationGroups.list(organizationId),
  );
  return res.data;
}

export async function createOrganizationGroup(
  organizationId: string,
  data: OrganizationGroupRequest,
) {
  const res = await api.post<ApiResponse<OrganizationGroup>>(
    API_ROUTES.organizationGroups.list(organizationId),
    data,
  );
  return res.data;
}

export async function updateOrganizationGroup(
  organizationId: string,
  groupId: string,
  data: OrganizationGroupRequest,
) {
  const res = await api.put<ApiResponse<OrganizationGroup>>(
    API_ROUTES.organizationGroups.detail(organizationId, groupId),
    data,
  );
  return res.data;
}

export async function deleteOrganizationGroup(
  organizationId: string,
  groupId: string,
): Promise<void> {
  await api.delete(API_ROUTES.organizationGroups.detail(organizationId, groupId));
}

export async function listOrganizationGroupMembers(
  organizationId: string,
  groupId: string,
) {
  const res = await api.get<ApiResponse<OrganizationGroupMember[]>>(
    API_ROUTES.organizationGroups.members(organizationId, groupId),
  );
  return res.data;
}

export async function addOrganizationGroupMembers(
  organizationId: string,
  groupId: string,
  data: OrganizationGroupMemberAddRequest,
) {
  const res = await api.post<ApiResponse<OrganizationGroupMember[]>>(
    API_ROUTES.organizationGroups.members(organizationId, groupId),
    data,
  );
  return res.data;
}

export async function removeOrganizationGroupMember(
  organizationId: string,
  groupId: string,
  userId: string,
): Promise<void> {
  await api.delete(
    API_ROUTES.organizationGroups.member(organizationId, groupId, userId),
  );
}

// -- App Grants and App Access ------------------------------------------------

export async function listAppGrants(organizationId: string) {
  const res = await api.get<ApiResponse<AppGrant[]>>(
    API_ROUTES.appGrants.list(organizationId),
  );
  return res.data;
}

export async function createAppGrant(
  organizationId: string,
  data: AppGrantCreateRequest,
) {
  const res = await api.post<ApiResponse<AppGrant>>(
    API_ROUTES.appGrants.list(organizationId),
    data,
  );
  return res.data;
}

export async function revokeAppGrant(
  organizationId: string,
  grantId: string,
): Promise<void> {
  await api.delete(API_ROUTES.appGrants.detail(organizationId, grantId));
}

export async function listMemberAppAccess(organizationId: string) {
  const res = await api.get<ApiResponse<MemberAppAccess[]>>(
    API_ROUTES.appGrants.appAccess(organizationId),
  );
  return res.data;
}

// -- Invitations --------------------------------------------------------------

export async function listInvitations(organizationId: string) {
  const res = await api.get<ApiResponse<Invitation[]>>(
    API_ROUTES.invitations.list,
    { headers: { "X-Organization-ID": organizationId } },
  );
  return res.data;
}

export async function createInvitation(
  organizationId: string,
  data: CreateInvitationForm,
) {
  const res = await api.post<ApiResponse<Invitation>>(
    API_ROUTES.invitations.list,
    data,
    { headers: { "X-Organization-ID": organizationId } },
  );
  return res.data;
}

export async function revokeInvitation(
  organizationId: string,
  invitationId: string,
) {
  await api.delete(API_ROUTES.invitations.detail(invitationId), {
    headers: { "X-Organization-ID": organizationId },
  });
}
