import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { ApiResponse } from "@oba/shared/schema";
import type {
  BoardMemberAddRequest,
  BoardMemberUpdateRequest,
  BoardMemberSpecialtyUpdateRequest,
  BoardMemberResponse,
  BoardMemberCandidateResponse,
} from "../schemas";

export async function listBoardMembers(boardId: string) {
  const res = await api.get<ApiResponse<BoardMemberResponse[]>>(
    API_ROUTES.boardMembers.list(boardId),
  );
  return res.data;
}

/** The active members of the board's organization, for the "Add Member" picker (board ADMIN or OWNER). */
export async function listBoardMemberCandidates(boardId: string) {
  const res = await api.get<ApiResponse<BoardMemberCandidateResponse[]>>(
    API_ROUTES.boardMembers.candidates(boardId),
  );
  return res.data;
}

export async function addBoardMember(
  boardId: string,
  data: BoardMemberAddRequest,
) {
  const res = await api.post<ApiResponse<BoardMemberResponse>>(
    API_ROUTES.boardMembers.list(boardId),
    data,
  );
  return res.data;
}

export async function removeBoardMember(
  boardId: string,
  userId: string,
): Promise<void> {
  await api.delete(API_ROUTES.boardMembers.detail(boardId, userId));
}

export async function updateBoardMember(
  boardId: string,
  userId: string,
  data: BoardMemberUpdateRequest,
) {
  const res = await api.put<ApiResponse<BoardMemberResponse>>(
    API_ROUTES.boardMembers.detail(boardId, userId),
    data,
  );
  return res.data;
}

export async function updateBoardMemberSpecialties(
  boardId: string,
  userId: string,
  data: BoardMemberSpecialtyUpdateRequest,
): Promise<void> {
  await api.put(API_ROUTES.boardMembers.specialties(boardId, userId), data);
}
