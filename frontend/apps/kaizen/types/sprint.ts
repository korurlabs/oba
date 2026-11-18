import type { SprintStatus } from "./enums";

export interface Sprint {
  id: string;
  name: string;
  goal?: string | null;
  startDate?: string;
  endDate?: string;
  status: SprintStatus;
  boardId?: string;
  createdAt?: string;
  updatedAt?: string;
}
