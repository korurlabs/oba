import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import { filterEmptyValues } from "@/lib/filter-utils";
import type { ApiResponse } from "@oba/shared/schema";
import type {
  BoardCreateRequest,
  BoardDeleteRequest,
  BoardUpdateRequest,
  BoardParentUpdateRequest,
  BoardDetailResponse,
  BoardListResponse,
  BoardListWithChildrenResponse,
} from "../schemas";

export async function createBoard(data: BoardCreateRequest) {
  const res = await api.post<ApiResponse<BoardDetailResponse>>(
    API_ROUTES.boards.list,
    data,
  );
  return res.data;
}

export async function listBoards(params?: Record<string, unknown>) {
  const res = await api.get<ApiResponse<BoardListWithChildrenResponse[]>>(
    API_ROUTES.boards.list,
    { params: filterEmptyValues(params) },
  );
  return res.data;
}

/**
 * Resolves a board by its URL slug among the caller's member boards. Answers
 * 404 both for an unknown slug and for a board the caller is not a member of,
 * so a link does not reveal whether a board exists.
 */
export async function getBoardBySlug(slug: string) {
  const res = await api.get<ApiResponse<BoardListResponse>>(
    API_ROUTES.boards.bySlug(slug),
  );
  return res.data;
}

/** Reads one member board by id; 404 when it is gone or the caller is not a member. */
export async function getBoard(id: string) {
  const res = await api.get<ApiResponse<BoardListResponse>>(
    API_ROUTES.boards.detail(id),
  );
  return res.data;
}

export async function updateBoard(id: string, data: BoardUpdateRequest) {
  const res = await api.put<ApiResponse<BoardDetailResponse>>(
    API_ROUTES.boards.detail(id),
    data,
  );
  return res.data;
}

/** Moves a board under another board, or to the top level with `clearParent: true`. */
export async function updateBoardParent(id: string, data: BoardParentUpdateRequest) {
  const res = await api.put<ApiResponse<BoardDetailResponse>>(
    API_ROUTES.boards.detail(id),
    data,
  );
  return res.data;
}

export async function listRelatedBoards(boardId: string) {
  const res = await api.get<ApiResponse<BoardListResponse[]>>(
    API_ROUTES.boards.related(boardId),
  );
  return res.data;
}

export async function deleteBoard(id: string, params?: BoardDeleteRequest): Promise<void> {
  await api.delete(API_ROUTES.boards.detail(id), {
    params: filterEmptyValues(params as Record<string, unknown>),
  });
}

export async function addChildBoards(
  boardId: string,
  childBoardIds: string[],
) {
  const res = await api.put<ApiResponse<BoardDetailResponse[]>>(
    API_ROUTES.boards.addChildren(boardId),
    { childBoardIds },
  );
  return res.data;
}

