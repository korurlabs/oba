import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { ApiResponse } from "@oba/shared/schema";
import type {
  AiAnalyzeRequest,
  AiCreateFromSuggestionsRequest,
  AiAnalysisResponse,
} from "../schemas";
import type { TicketDetailResponse } from "@/features/tickets/schemas";

export async function getAiAvailability(boardId: string) {
  const res = await api.get<ApiResponse<Record<string, boolean>>>(
    API_ROUTES.ai.available(boardId),
  );
  return res.data;
}

export async function analyzeWithAi(
  boardId: string,
  data: AiAnalyzeRequest,
) {
  const res = await api.post<ApiResponse<AiAnalysisResponse>>(
    API_ROUTES.ai.analyze(boardId),
    data,
  );
  return res.data;
}

export async function createFromSuggestions(
  boardId: string,
  data: AiCreateFromSuggestionsRequest,
) {
  const res = await api.post<ApiResponse<TicketDetailResponse[]>>(
    API_ROUTES.ai.createFromSuggestions(boardId),
    data,
  );
  return res.data;
}
