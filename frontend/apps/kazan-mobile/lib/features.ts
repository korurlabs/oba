/*
 * Features the app carries but does not show yet. Each switch is a build-time
 * constant: turning a feature on is a code change and a new build, so every
 * user of one build sees the same app.
 */

/**
 * Ticket comments: the comment list, the composer, mentions and the comment
 * count in the delete question. The Kazan web app has no comments, so the
 * app keeps them dark until comments ship on web and mobile together; a
 * comment written here would otherwise be one that nobody on the web can see.
 * Off, a ticket reads no comments from the API and shows nothing of them.
 * Set it to true to turn every part back on.
 */
export const TICKET_COMMENTS_ENABLED: boolean = false;
