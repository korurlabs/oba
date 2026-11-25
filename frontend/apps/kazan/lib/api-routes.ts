export const API_ROUTES = {
  boards: {
    list: "/kazan/boards",
    detail: (id: string) => `/kazan/boards/${id}`,
    bySlug: (slug: string) => `/kazan/boards/by-slug/${encodeURIComponent(slug)}`,
    related: (boardId: string) => `/kazan/boards/${boardId}/related`,
    addChildren: (boardId: string) => `/kazan/boards/${boardId}/children`,
  },
  boardMembers: {
    list: (boardId: string) => `/kazan/boards/${boardId}/members`,
    candidates: (boardId: string) => `/kazan/boards/${boardId}/members/candidates`,
    detail: (boardId: string, userId: string) =>
      `/kazan/boards/${boardId}/members/${userId}`,
    specialties: (boardId: string, userId: string) =>
      `/kazan/boards/${boardId}/members/${userId}/specialties`,
  },
  tickets: {
    list: (boardId: string) => `/kazan/boards/${boardId}/tickets`,
    detail: (boardId: string, ticketId: string) =>
      `/kazan/boards/${boardId}/tickets/${ticketId}`,
    move: (boardId: string, ticketId: string) =>
      `/kazan/boards/${boardId}/tickets/${ticketId}/move`,
    order: (boardId: string, ticketId: string) =>
      `/kazan/boards/${boardId}/tickets/${ticketId}/order`,
    bulkMove: (boardId: string) =>
      `/kazan/boards/${boardId}/tickets/bulk-move`,
    restore: (boardId: string, ticketId: string) =>
      `/kazan/boards/${boardId}/tickets/${ticketId}/restore`,
  },
  sprints: {
    list: (boardId: string) => `/kazan/boards/${boardId}/sprints`,
    detail: (boardId: string, sprintId: string) =>
      `/kazan/boards/${boardId}/sprints/${sprintId}`,
    current: (boardId: string) => `/kazan/boards/${boardId}/sprints/current`,
    childBoards: (boardId: string) =>
      `/kazan/boards/${boardId}/sprints/child-boards`,
    start: (boardId: string, sprintId: string) =>
      `/kazan/boards/${boardId}/sprints/${sprintId}/start`,
    startPreview: (boardId: string, sprintId: string) =>
      `/kazan/boards/${boardId}/sprints/${sprintId}/start-preview`,
    complete: (boardId: string, sprintId: string) =>
      `/kazan/boards/${boardId}/sprints/${sprintId}/complete`,
    link: (boardId: string, sprintId: string) =>
      `/kazan/boards/${boardId}/sprints/${sprintId}/link`,
    bulkLink: (boardId: string, sprintId: string) =>
      `/kazan/boards/${boardId}/sprints/${sprintId}/bulk-link`,
    unlink: (boardId: string, sprintId: string) =>
      `/kazan/boards/${boardId}/sprints/${sprintId}/unlink`,
    series: (boardId: string, sprintId: string) =>
      `/kazan/boards/${boardId}/sprints/${sprintId}/series`,
  },
  milestones: {
    list: (boardId: string) => `/kazan/boards/${boardId}/milestones`,
    detail: (boardId: string, milestoneId: string) =>
      `/kazan/boards/${boardId}/milestones/${milestoneId}`,
    close: (boardId: string, milestoneId: string) =>
      `/kazan/boards/${boardId}/milestones/${milestoneId}/close`,
    reopen: (boardId: string, milestoneId: string) =>
      `/kazan/boards/${boardId}/milestones/${milestoneId}/reopen`,
    link: (boardId: string, milestoneId: string) =>
      `/kazan/boards/${boardId}/milestones/${milestoneId}/link`,
    unlink: (boardId: string, milestoneId: string) =>
      `/kazan/boards/${boardId}/milestones/${milestoneId}/unlink`,
    restore: (boardId: string, milestoneId: string) =>
      `/kazan/boards/${boardId}/milestones/${milestoneId}/restore`,
    dependency: (boardId: string, milestoneId: string, dependsOnId: string) =>
      `/kazan/boards/${boardId}/milestones/${milestoneId}/dependencies/${dependsOnId}`,
  },
  roadmap: (boardId: string) => `/kazan/boards/${boardId}/roadmap`,
  labels: {
    list: (boardId: string) => `/kazan/boards/${boardId}/labels`,
    detail: (boardId: string, labelId: string) =>
      `/kazan/boards/${boardId}/labels/${labelId}`,
  },
  backlog: {
    detail: (boardId: string) => `/kazan/boards/${boardId}/backlog`,
    addTicket: (boardId: string, ticketId: string) =>
      `/kazan/boards/${boardId}/backlog/tickets/${ticketId}`,
  },
  projects: {
    list: (boardId: string) => `/kazan/boards/${boardId}/projects`,
    detail: (boardId: string, projectId: string) =>
      `/kazan/boards/${boardId}/projects/${projectId}`,
  },
  ai: {
    available: (boardId: string) => `/kazan/boards/${boardId}/ai/available`,
    analyze: (boardId: string) => `/kazan/boards/${boardId}/ai/analyze`,
    createFromSuggestions: (boardId: string) =>
      `/kazan/boards/${boardId}/ai/create-from-suggestions`,
  },
  alertRoutes: {
    list: "/kazan/alert-routes",
    detail: (routeId: string) => `/kazan/alert-routes/${routeId}`,
  },
  cyberscore: {
    current: "/kazan/cyberscore",
    trend: "/kazan/cyberscore/trend",
    checks: "/kazan/cyberscore/checks",
    checkDetail: (checkId: string) => `/kazan/cyberscore/checks/${checkId}`,
    checkOutcome: (checkId: string) => `/kazan/cyberscore/checks/${checkId}/outcome`,
    checkStatus: (checkId: string) => `/kazan/cyberscore/checks/${checkId}/status`,
  },
  allTickets: {
    list: "/kazan/tickets",
    byKey: (key: string) => `/kazan/tickets/by-key/${encodeURIComponent(key)}`,
  },
  updates: {
    list: "/kazan/updates",
    preview: "/kazan/updates/preview",
    detail: (updateId: string) => `/kazan/updates/${updateId}`,
    previewEdit: (updateId: string) => `/kazan/updates/${updateId}/preview`,
    missing: "/kazan/updates/missing",
  },
  github: {
    repositories: "/kazan/github/repositories",
    pullRequests: (githubRepositoryId: number) =>
      `/kazan/github/repositories/${githubRepositoryId}/pull-requests`,
    pullRequest: (githubRepositoryId: number, number: number) =>
      `/kazan/github/repositories/${githubRepositoryId}/pull-requests/${number}`,
    contributors: (githubRepositoryId: number) =>
      `/kazan/github/repositories/${githubRepositoryId}/contributors`,
  },
  pullRequestShares: {
    list: "/kazan/pull-request-shares",
    detail: (shareId: string) => `/kazan/pull-request-shares/${shareId}`,
    approval: (shareId: string) => `/kazan/pull-request-shares/${shareId}/approval`,
  },
  chat: {
    channels: "/kazan/chat/channels",
    channel: (channelId: string) => `/kazan/chat/channels/${channelId}`,
    channelMembers: (channelId: string) => `/kazan/chat/channels/${channelId}/members`,
    channelMember: (channelId: string, userId: string) =>
      `/kazan/chat/channels/${channelId}/members/${userId}`,
    messages: "/kazan/chat/messages",
    message: (messageId: string) => `/kazan/chat/messages/${messageId}`,
    reactions: (messageId: string) => `/kazan/chat/messages/${messageId}/reactions`,
    pin: (messageId: string) => `/kazan/chat/messages/${messageId}/pin`,
    pins: "/kazan/chat/pins",
    search: "/kazan/chat/search",
    members: "/kazan/chat/members",
    unread: "/kazan/chat/unread",
    readState: "/kazan/chat/read-state",
    threads: "/kazan/chat/threads",
    thread: (threadId: string) => `/kazan/chat/threads/${threadId}`,
    threadMessages: (threadId: string) => `/kazan/chat/threads/${threadId}/messages`,
    threadMembership: (threadId: string) => `/kazan/chat/threads/${threadId}/membership`,
    threadStatus: (threadId: string) => `/kazan/chat/threads/${threadId}/status`,
  },
  users: {
    list: "/users",
  },
  realtime: {
    tickets: "/realtime/tickets",
  },
  home: {
    summary: "/kazan/home/summary",
    staleBacklog: "/kazan/home/stale-backlog",
    leaderboard: "/kazan/home/leaderboard",
    sprint: "/kazan/home/sprint",
    deadlines: "/kazan/home/deadlines",
  },
} as const;
