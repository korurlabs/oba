export const API_ROUTES = {
  boards: {
    list: "/kaizen/boards",
    detail: (id: string) => `/kaizen/boards/${id}`,
    bySlug: (slug: string) => `/kaizen/boards/by-slug/${encodeURIComponent(slug)}`,
    related: (boardId: string) => `/kaizen/boards/${boardId}/related`,
    addChildren: (boardId: string) => `/kaizen/boards/${boardId}/children`,
  },
  boardMembers: {
    list: (boardId: string) => `/kaizen/boards/${boardId}/members`,
    candidates: (boardId: string) => `/kaizen/boards/${boardId}/members/candidates`,
    detail: (boardId: string, userId: string) =>
      `/kaizen/boards/${boardId}/members/${userId}`,
    specialties: (boardId: string, userId: string) =>
      `/kaizen/boards/${boardId}/members/${userId}/specialties`,
  },
  tickets: {
    list: (boardId: string) => `/kaizen/boards/${boardId}/tickets`,
    detail: (boardId: string, ticketId: string) =>
      `/kaizen/boards/${boardId}/tickets/${ticketId}`,
    move: (boardId: string, ticketId: string) =>
      `/kaizen/boards/${boardId}/tickets/${ticketId}/move`,
    order: (boardId: string, ticketId: string) =>
      `/kaizen/boards/${boardId}/tickets/${ticketId}/order`,
    bulkMove: (boardId: string) =>
      `/kaizen/boards/${boardId}/tickets/bulk-move`,
    restore: (boardId: string, ticketId: string) =>
      `/kaizen/boards/${boardId}/tickets/${ticketId}/restore`,
  },
  sprints: {
    list: (boardId: string) => `/kaizen/boards/${boardId}/sprints`,
    detail: (boardId: string, sprintId: string) =>
      `/kaizen/boards/${boardId}/sprints/${sprintId}`,
    current: (boardId: string) => `/kaizen/boards/${boardId}/sprints/current`,
    childBoards: (boardId: string) =>
      `/kaizen/boards/${boardId}/sprints/child-boards`,
    start: (boardId: string, sprintId: string) =>
      `/kaizen/boards/${boardId}/sprints/${sprintId}/start`,
    startPreview: (boardId: string, sprintId: string) =>
      `/kaizen/boards/${boardId}/sprints/${sprintId}/start-preview`,
    complete: (boardId: string, sprintId: string) =>
      `/kaizen/boards/${boardId}/sprints/${sprintId}/complete`,
    link: (boardId: string, sprintId: string) =>
      `/kaizen/boards/${boardId}/sprints/${sprintId}/link`,
    bulkLink: (boardId: string, sprintId: string) =>
      `/kaizen/boards/${boardId}/sprints/${sprintId}/bulk-link`,
    unlink: (boardId: string, sprintId: string) =>
      `/kaizen/boards/${boardId}/sprints/${sprintId}/unlink`,
    series: (boardId: string, sprintId: string) =>
      `/kaizen/boards/${boardId}/sprints/${sprintId}/series`,
  },
  milestones: {
    list: (boardId: string) => `/kaizen/boards/${boardId}/milestones`,
    detail: (boardId: string, milestoneId: string) =>
      `/kaizen/boards/${boardId}/milestones/${milestoneId}`,
    close: (boardId: string, milestoneId: string) =>
      `/kaizen/boards/${boardId}/milestones/${milestoneId}/close`,
    reopen: (boardId: string, milestoneId: string) =>
      `/kaizen/boards/${boardId}/milestones/${milestoneId}/reopen`,
    link: (boardId: string, milestoneId: string) =>
      `/kaizen/boards/${boardId}/milestones/${milestoneId}/link`,
    unlink: (boardId: string, milestoneId: string) =>
      `/kaizen/boards/${boardId}/milestones/${milestoneId}/unlink`,
    restore: (boardId: string, milestoneId: string) =>
      `/kaizen/boards/${boardId}/milestones/${milestoneId}/restore`,
    dependency: (boardId: string, milestoneId: string, dependsOnId: string) =>
      `/kaizen/boards/${boardId}/milestones/${milestoneId}/dependencies/${dependsOnId}`,
  },
  roadmap: (boardId: string) => `/kaizen/boards/${boardId}/roadmap`,
  labels: {
    list: (boardId: string) => `/kaizen/boards/${boardId}/labels`,
    detail: (boardId: string, labelId: string) =>
      `/kaizen/boards/${boardId}/labels/${labelId}`,
  },
  backlog: {
    detail: (boardId: string) => `/kaizen/boards/${boardId}/backlog`,
    addTicket: (boardId: string, ticketId: string) =>
      `/kaizen/boards/${boardId}/backlog/tickets/${ticketId}`,
  },
  projects: {
    list: (boardId: string) => `/kaizen/boards/${boardId}/projects`,
    detail: (boardId: string, projectId: string) =>
      `/kaizen/boards/${boardId}/projects/${projectId}`,
  },
  ai: {
    available: (boardId: string) => `/kaizen/boards/${boardId}/ai/available`,
    analyze: (boardId: string) => `/kaizen/boards/${boardId}/ai/analyze`,
    createFromSuggestions: (boardId: string) =>
      `/kaizen/boards/${boardId}/ai/create-from-suggestions`,
  },
  alertRoutes: {
    list: "/kaizen/alert-routes",
    detail: (routeId: string) => `/kaizen/alert-routes/${routeId}`,
  },
  allTickets: {
    list: "/kaizen/tickets",
    byKey: (key: string) => `/kaizen/tickets/by-key/${encodeURIComponent(key)}`,
  },
  updates: {
    list: "/kaizen/updates",
    preview: "/kaizen/updates/preview",
    detail: (updateId: string) => `/kaizen/updates/${updateId}`,
    previewEdit: (updateId: string) => `/kaizen/updates/${updateId}/preview`,
    missing: "/kaizen/updates/missing",
  },
  github: {
    repositories: "/kaizen/github/repositories",
    pullRequests: (githubRepositoryId: number) =>
      `/kaizen/github/repositories/${githubRepositoryId}/pull-requests`,
    pullRequest: (githubRepositoryId: number, number: number) =>
      `/kaizen/github/repositories/${githubRepositoryId}/pull-requests/${number}`,
    contributors: (githubRepositoryId: number) =>
      `/kaizen/github/repositories/${githubRepositoryId}/contributors`,
  },
  pullRequestShares: {
    list: "/kaizen/pull-request-shares",
    detail: (shareId: string) => `/kaizen/pull-request-shares/${shareId}`,
    approval: (shareId: string) => `/kaizen/pull-request-shares/${shareId}/approval`,
  },
  chat: {
    channels: "/kaizen/chat/channels",
    channel: (channelId: string) => `/kaizen/chat/channels/${channelId}`,
    channelMembers: (channelId: string) => `/kaizen/chat/channels/${channelId}/members`,
    channelMember: (channelId: string, userId: string) =>
      `/kaizen/chat/channels/${channelId}/members/${userId}`,
    messages: "/kaizen/chat/messages",
    message: (messageId: string) => `/kaizen/chat/messages/${messageId}`,
    reactions: (messageId: string) => `/kaizen/chat/messages/${messageId}/reactions`,
    pin: (messageId: string) => `/kaizen/chat/messages/${messageId}/pin`,
    pins: "/kaizen/chat/pins",
    search: "/kaizen/chat/search",
    members: "/kaizen/chat/members",
    unread: "/kaizen/chat/unread",
    readState: "/kaizen/chat/read-state",
    threads: "/kaizen/chat/threads",
    thread: (threadId: string) => `/kaizen/chat/threads/${threadId}`,
    threadMessages: (threadId: string) => `/kaizen/chat/threads/${threadId}/messages`,
    threadMembership: (threadId: string) => `/kaizen/chat/threads/${threadId}/membership`,
    threadStatus: (threadId: string) => `/kaizen/chat/threads/${threadId}/status`,
  },
  users: {
    list: "/users",
  },
  realtime: {
    tickets: "/realtime/tickets",
  },
  home: {
    summary: "/kaizen/home/summary",
    staleBacklog: "/kaizen/home/stale-backlog",
    leaderboard: "/kaizen/home/leaderboard",
    sprint: "/kaizen/home/sprint",
    deadlines: "/kaizen/home/deadlines",
  },
} as const;
