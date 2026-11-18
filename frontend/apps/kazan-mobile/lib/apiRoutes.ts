/**
 * Every Oba API path the app calls, relative to `ENV.apiBaseUrl`. Hooks and
 * screens take paths only from here. A path with an id is a function that
 * encodes its parameters; query strings go in Axios `params`, not in the path.
 *
 * Organization-scoped paths need `X-Organization-ID`, which the request
 * interceptor in lib/api.ts adds from the active organization.
 */

const encode = (value: string): string => encodeURIComponent(value);

export const API_ROUTES = {
  users: {
    /** GET the profile with `defaultOrganizationId`, `organizations` and `pendingInvitations`; PUT updates it; DELETE disables the account. */
    me: "/users/me",
    /** POST accept or decline an invitation. */
    invitationDecision: "/users/me/invitations/decision",
  },
  organizations: {
    /** GET the caller's organizations with role and member count. */
    list: "/organizations",
    byId: (organizationId: string): string => `/organizations/${encode(organizationId)}`,
  },
  push: {
    /** PUT registers this device's Expo token; returns the device with its `id`. */
    devices: "/push/devices",
    /** DELETE unregisters a device at sign-out. */
    device: (deviceId: string): string => `/push/devices/${encode(deviceId)}`,
    /** GET and PUT the five notification switches. */
    preferences: "/push/preferences",
  },
  realtime: {
    /** POST a one-time ticket before each STOMP connect. */
    tickets: "/realtime/tickets",
  },
  storage: {
    /** POST multipart upload; GET by `entityType` and `entityId`. */
    attachments: "/storage/attachments",
    /** POST one file to learn whether its content is already stored for the entity type. */
    attachmentsExists: "/storage/attachments/exists",
    /** DELETE one attachment. */
    attachment: (attachmentId: string): string => `/storage/attachments/${encode(attachmentId)}`,
  },
  home: {
    summary: "/kazan/home/summary",
    sprint: "/kazan/home/sprint",
    leaderboard: "/kazan/home/leaderboard",
    deadlines: "/kazan/home/deadlines",
    staleBacklog: "/kazan/home/stale-backlog",
  },
  boards: {
    list: "/kazan/boards",
    byId: (boardId: string): string => `/kazan/boards/${encode(boardId)}`,
    bySlug: (slug: string): string => `/kazan/boards/by-slug/${encode(slug)}`,
    related: (boardId: string): string => `/kazan/boards/${encode(boardId)}/related`,
    /** GET the board's members with their board role; POST adds one. */
    members: (boardId: string): string => `/kazan/boards/${encode(boardId)}/members`,
    /** GET the active members of the board's organization, with `kazanAccess`, the people Add member offers. */
    memberCandidates: (boardId: string): string =>
      `/kazan/boards/${encode(boardId)}/members/candidates`,
    /** PUT changes a member's role; DELETE removes the member. */
    member: (boardId: string, userId: string): string =>
      `/kazan/boards/${encode(boardId)}/members/${encode(userId)}`,
    /** PUT makes existing boards children of the board. */
    children: (boardId: string): string => `/kazan/boards/${encode(boardId)}/children`,
    /** GET the board's backlog; its `id` scopes the backlog ticket list. */
    backlog: (boardId: string): string => `/kazan/boards/${encode(boardId)}/backlog`,
    /** GET milestones by board lane between `from` and `to`. */
    roadmap: (boardId: string): string => `/kazan/boards/${encode(boardId)}/roadmap`,
    /** GET the board's and its related boards' labels; POST creates one on the board. */
    labels: (boardId: string): string => `/kazan/boards/${encode(boardId)}/labels`,
    /** PUT and DELETE a label under the board that owns it. */
    label: (boardId: string, labelId: string): string =>
      `/kazan/boards/${encode(boardId)}/labels/${encode(labelId)}`,
    /** GET the board's and its related boards' projects; POST creates one on the board. */
    projects: (boardId: string): string => `/kazan/boards/${encode(boardId)}/projects`,
    /** PUT and DELETE a project under the board that owns it. */
    project: (boardId: string, projectId: string): string =>
      `/kazan/boards/${encode(boardId)}/projects/${encode(projectId)}`,
    /** GET the board's milestones; POST creates one. */
    milestones: (boardId: string): string => `/kazan/boards/${encode(boardId)}/milestones`,
    /** GET, PUT and DELETE one milestone. */
    milestone: (boardId: string, milestoneId: string): string =>
      `/kazan/boards/${encode(boardId)}/milestones/${encode(milestoneId)}`,
    milestoneClose: (boardId: string, milestoneId: string): string =>
      `/kazan/boards/${encode(boardId)}/milestones/${encode(milestoneId)}/close`,
    milestoneReopen: (boardId: string, milestoneId: string): string =>
      `/kazan/boards/${encode(boardId)}/milestones/${encode(milestoneId)}/reopen`,
  },
  tickets: {
    /** GET across boards (`assigneeIds`, `createdByIds`, `dueDateStart`, `dueDateEnd`). */
    all: "/kazan/tickets",
    byKey: (ticketKey: string): string => `/kazan/tickets/by-key/${encode(ticketKey)}`,
    /** GET a board's tickets; POST creates one. */
    list: (boardId: string): string => `/kazan/boards/${encode(boardId)}/tickets`,
    byId: (boardId: string, ticketId: string): string =>
      `/kazan/boards/${encode(boardId)}/tickets/${encode(ticketId)}`,
    move: (boardId: string, ticketId: string): string =>
      `/kazan/boards/${encode(boardId)}/tickets/${encode(ticketId)}/move`,
    order: (boardId: string, ticketId: string): string =>
      `/kazan/boards/${encode(boardId)}/tickets/${encode(ticketId)}/order`,
    restore: (boardId: string, ticketId: string): string =>
      `/kazan/boards/${encode(boardId)}/tickets/${encode(ticketId)}/restore`,
    bulkMove: (boardId: string): string => `/kazan/boards/${encode(boardId)}/tickets/bulk-move`,
  },
  comments: {
    list: (boardId: string, ticketId: string): string =>
      `/kazan/boards/${encode(boardId)}/tickets/${encode(ticketId)}/comments`,
    byId: (boardId: string, ticketId: string, commentId: string): string =>
      `/kazan/boards/${encode(boardId)}/tickets/${encode(ticketId)}/comments/${encode(commentId)}`,
  },
  sprints: {
    /** GET a board's sprints; POST creates one. */
    list: (boardId: string): string => `/kazan/boards/${encode(boardId)}/sprints`,
    childBoards: (boardId: string): string =>
      `/kazan/boards/${encode(boardId)}/sprints/child-boards`,
    byId: (boardId: string, sprintId: string): string =>
      `/kazan/boards/${encode(boardId)}/sprints/${encode(sprintId)}`,
    series: (boardId: string, sprintId: string): string =>
      `/kazan/boards/${encode(boardId)}/sprints/${encode(sprintId)}/series`,
    startPreview: (boardId: string, sprintId: string): string =>
      `/kazan/boards/${encode(boardId)}/sprints/${encode(sprintId)}/start-preview`,
    start: (boardId: string, sprintId: string): string =>
      `/kazan/boards/${encode(boardId)}/sprints/${encode(sprintId)}/start`,
    complete: (boardId: string, sprintId: string): string =>
      `/kazan/boards/${encode(boardId)}/sprints/${encode(sprintId)}/complete`,
    link: (boardId: string, sprintId: string): string =>
      `/kazan/boards/${encode(boardId)}/sprints/${encode(sprintId)}/link`,
    bulkLink: (boardId: string, sprintId: string): string =>
      `/kazan/boards/${encode(boardId)}/sprints/${encode(sprintId)}/bulk-link`,
    unlink: (boardId: string, sprintId: string): string =>
      `/kazan/boards/${encode(boardId)}/sprints/${encode(sprintId)}/unlink`,
  },
  chat: {
    /** GET a page of a channel or thread; POST sends one as multipart form data. */
    messages: "/kazan/chat/messages",
    message: (messageId: string): string => `/kazan/chat/messages/${encode(messageId)}`,
    /** POST adds and DELETE removes a reaction. */
    reactions: (messageId: string): string =>
      `/kazan/chat/messages/${encode(messageId)}/reactions`,
    /** PUT pins and DELETE unpins a message. */
    pin: (messageId: string): string => `/kazan/chat/messages/${encode(messageId)}/pin`,
    pins: "/kazan/chat/pins",
    search: "/kazan/chat/search",
    members: "/kazan/chat/members",
    unread: "/kazan/chat/unread",
    readState: "/kazan/chat/read-state",
    channels: "/kazan/chat/channels",
    channel: (channelId: string): string => `/kazan/chat/channels/${encode(channelId)}`,
    channelMembers: (channelId: string): string =>
      `/kazan/chat/channels/${encode(channelId)}/members`,
    channelMember: (channelId: string, userId: string): string =>
      `/kazan/chat/channels/${encode(channelId)}/members/${encode(userId)}`,
    threads: "/kazan/chat/threads",
    thread: (threadId: string): string => `/kazan/chat/threads/${encode(threadId)}`,
    threadStatus: (threadId: string): string => `/kazan/chat/threads/${encode(threadId)}/status`,
    threadMessages: (threadId: string): string =>
      `/kazan/chat/threads/${encode(threadId)}/messages`,
    threadMembership: (threadId: string): string =>
      `/kazan/chat/threads/${encode(threadId)}/membership`,
  },
  pullRequests: {
    shares: "/kazan/pull-request-shares",
    sharePreview: "/kazan/pull-request-shares/preview",
    share: (shareId: string): string => `/kazan/pull-request-shares/${encode(shareId)}`,
    /** PUT approves and DELETE withdraws the approval. */
    approval: (shareId: string): string =>
      `/kazan/pull-request-shares/${encode(shareId)}/approval`,
    githubRepositories: "/kazan/github/repositories",
    githubContributors: (repositoryId: string): string =>
      `/kazan/github/repositories/${encode(repositoryId)}/contributors`,
    githubPullRequests: (repositoryId: string): string =>
      `/kazan/github/repositories/${encode(repositoryId)}/pull-requests`,
    githubPullRequest: (repositoryId: string, number: number): string =>
      `/kazan/github/repositories/${encode(repositoryId)}/pull-requests/${encode(String(number))}`,
  },
  dailyUpdates: {
    /** GET the list; POST creates an update. */
    list: "/kazan/updates",
    byId: (updateId: string): string => `/kazan/updates/${encode(updateId)}`,
    preview: "/kazan/updates/preview",
    suggestedTickets: "/kazan/updates/suggested-tickets",
    /** GET who has not posted today (organization OWNER and ADMIN). */
    missing: "/kazan/updates/missing",
    /** POST reminds a member to post today's update (organization OWNER and ADMIN). */
    nudge: (userId: string): string => `/kazan/updates/missing/${encode(userId)}/nudge`,
  },
} as const;
