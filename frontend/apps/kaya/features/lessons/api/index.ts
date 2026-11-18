import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { ApiResponse } from "@/types/api";

import type {
  LessonDetailResponse,
  LessonFiltersResponse,
  LessonListResponse,
  LessonQueryRequest,
  LessonRequest,
} from "../schemas";

/** One page of the organization's lessons, newest first. */
export async function listLessons(params: LessonQueryRequest) {
  const res = await api.get<ApiResponse<LessonListResponse[]>>(API_ROUTES.lessons.list, { params });
  return res.data;
}

/** The repositories and users the organization's lessons hold. */
export async function getLessonFilters() {
  const res = await api.get<ApiResponse<LessonFiltersResponse>>(API_ROUTES.lessons.filters);
  return res.data;
}

/** A lesson with the session that recorded it, and whether the caller may edit it. */
export async function getLesson(lessonId: string) {
  const res = await api.get<ApiResponse<LessonDetailResponse>>(API_ROUTES.lessons.detail(lessonId));
  return res.data;
}

export async function createLesson(data: LessonRequest) {
  const res = await api.post<ApiResponse<LessonDetailResponse>>(API_ROUTES.lessons.list, data);
  return res.data;
}

/** Replaces the title, text and repository; only the author, an ADMIN or an OWNER may. */
export async function updateLesson(lessonId: string, data: LessonRequest) {
  const res = await api.put<ApiResponse<LessonDetailResponse>>(
    API_ROUTES.lessons.detail(lessonId),
    data
  );
  return res.data;
}

/** Removes the lesson for good; only the author, an ADMIN or an OWNER may. */
export async function deleteLesson(lessonId: string): Promise<void> {
  await api.delete(API_ROUTES.lessons.detail(lessonId));
}
