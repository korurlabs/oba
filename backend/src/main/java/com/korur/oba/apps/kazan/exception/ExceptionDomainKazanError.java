package com.korur.oba.apps.kazan.exception;

import com.korur.oba.dojo.exception.enums.ExceptionCategory;
import com.korur.oba.dojo.exception.enums.ExceptionDomainError;

/**
 * Domain-specific error codes for the Kazan domain (KAI).
 *
 * <p>Covers Agile Project Management: boards, sprints, tickets, backlogs, and labels.</p>
 */
public enum ExceptionDomainKazanError implements ExceptionDomainError {

    // ── Security (KAZ_SEC) ───────────────────────────────────────────────────
    SEC_NOT_BOARD_MEMBER("KAZ_SEC_001", "User is not a member of this board.", ExceptionCategory.SEC),
    SEC_INSUFFICIENT_BOARD_PERMISSIONS("KAZ_SEC_002", "User does not have sufficient board permissions.", ExceptionCategory.SEC),
    SEC_CANNOT_MODIFY_OTHERS_UPDATE("KAZ_SEC_003", "Cannot edit or delete another user's update.", ExceptionCategory.SEC),
    SEC_CANNOT_MODIFY_OTHERS_MESSAGE("KAZ_SEC_004", "Cannot edit another user's message.", ExceptionCategory.SEC),
    SEC_CANNOT_MANAGE_THREAD("KAZ_SEC_005", "Only the thread's creator or an organization owner or admin can change the thread.", ExceptionCategory.SEC),
    SEC_CANNOT_PIN_MESSAGE("KAZ_SEC_006", "Only the message's author or an organization owner or admin can pin or unpin it.", ExceptionCategory.SEC),
    SEC_CANNOT_DELETE_OTHERS_MESSAGE("KAZ_SEC_007", "Only the message's author or an organization owner or admin can delete it.", ExceptionCategory.SEC),
    SEC_CANNOT_POST_OTHERS_UPDATE("KAZ_SEC_008", "Only the author can post their update.", ExceptionCategory.SEC),
    SEC_CANNOT_EDIT_OTHERS_SHARE("KAZ_SEC_009", "Only the author can edit or delete this pull request share.", ExceptionCategory.SEC),
    SEC_NOT_PULL_REQUEST_REVIEWER("KAZ_SEC_010", "Only a reviewer of this pull request share can approve it or withdraw the approval.", ExceptionCategory.SEC),
    SEC_CANNOT_REMOVE_CHANNEL_MEMBER("KAZ_SEC_011", "Only an organization owner or admin can remove another member from a channel.", ExceptionCategory.SEC),

    // ── Validation (KAZ_VAL) ─────────────────────────────────────────────────
    VAL_SPRINT_NOT_PLANNED("KAZ_VAL_003", "Sprint must be in PLANNED status to start.", ExceptionCategory.VAL),
    VAL_SPRINT_NOT_ACTIVE("KAZ_VAL_004", "Sprint must be in ACTIVE status to complete.", ExceptionCategory.VAL),
    VAL_TICKET_PLACEMENT("KAZ_VAL_005", "Either a sprint or backlog must be provided. Provide only sprint or backlog.", ExceptionCategory.VAL),
    VAL_CANNOT_DELETE_OWNER("KAZ_VAL_006", "Cannot remove the board owner.", ExceptionCategory.VAL),
    VAL_SPRINT_ALREADY_LINKED("KAZ_VAL_007", "Sprint is already linked to this board.", ExceptionCategory.VAL),
    VAL_SPRINT_NOT_LINKABLE("KAZ_VAL_008", "Sprint is not linkable to this board. Provided board should be parent, child or sibling to the sprint's current boards.", ExceptionCategory.VAL),
    VAL_CANNOT_EDIT_OTHERS_COMMENT("KAZ_VAL_009", "Cannot edit another user's comment.", ExceptionCategory.VAL),
    VAL_TICKET_ORDER("KAZ_VAL_010", "New ticket order must be greater or less than to the previous ticket order.", ExceptionCategory.VAL),
    VAL_SPRINT_TICKET_ACTION_REQUIRED("KAZ_VAL_011", "Sprint has tickets. A ticket action (MOVE_TO_BACKLOG, MOVE_TO_SPRINT, DELETE) is required.", ExceptionCategory.VAL),
    VAL_SPRINT_TARGET_SPRINT_REQUIRED("KAZ_VAL_012", "Target sprint ID is required when ticket action is MOVE_TO_SPRINT.", ExceptionCategory.VAL),
    VAL_SPRINT_TARGET_SPRINT_SAME_BOARD("KAZ_VAL_013", "Target sprint must belong to the same board.", ExceptionCategory.VAL),
    VAL_SPRINT_NOT_DELETED("KAZ_VAL_014", "Sprint is not deleted and cannot be restored.", ExceptionCategory.VAL),
    VAL_BOARD_DELETE_TICKET_ACTION_REQUIRED("KAZ_VAL_015", "Board has tickets. Specify ticketAction (MOVE or DELETE).", ExceptionCategory.VAL),
    VAL_BOARD_DELETE_TARGET_BOARD_REQUIRED("KAZ_VAL_016", "targetBoardId is required when ticketAction is MOVE.", ExceptionCategory.VAL),
    VAL_BOARD_DELETE_CHILD_ACTION_REQUIRED("KAZ_VAL_017", "Board has child boards. Specify childBoardAction (PROMOTE, MOVE, or DELETE).", ExceptionCategory.VAL),
    VAL_BOARD_DELETE_NEW_PARENT_REQUIRED("KAZ_VAL_018", "newParentBoardId is required when childBoardAction is MOVE.", ExceptionCategory.VAL),
    VAL_BOARD_DELETE_CIRCULAR_PARENT("KAZ_VAL_019", "New parent board cannot be a descendant of the board being deleted.", ExceptionCategory.VAL),
    VAL_BOARD_NOT_DELETED("KAZ_VAL_020", "Board is not deleted and cannot be restored.", ExceptionCategory.VAL),
    VAL_TICKET_NOT_DELETED("KAZ_VAL_021", "Ticket is not deleted and cannot be restored.", ExceptionCategory.VAL),
    VAL_TICKET_BOARD_DELETED("KAZ_VAL_022", "Cannot restore ticket because the parent board is deleted.", ExceptionCategory.VAL),
    VAL_TICKET_BOARD_MISMATCH("KAZ_VAL_023", "Ticket does not belong to the specified board.", ExceptionCategory.VAL),
    VAL_BOARD_CIRCULAR_PARENT("KAZ_VAL_024", "Cannot set parent: target parent is a descendant of this board.", ExceptionCategory.VAL),
    VAL_BULK_MOVE_DELETE_NOT_ALLOWED("KAZ_VAL_025", "DELETE action is not allowed for bulk move. Use MOVE_TO_BACKLOG or MOVE_TO_SPRINT.", ExceptionCategory.VAL),
    VAL_SPRINT_TARGET_SPRINT_SELF_REFERENCE("KAZ_VAL_026", "Target sprint cannot be the same as the sprint being completed.", ExceptionCategory.VAL),
    VAL_BOARD_DELETE_TARGET_SPRINT_BOARD_MISMATCH("KAZ_VAL_027", "Target sprint does not belong to the target board.", ExceptionCategory.VAL),
    VAL_BULK_MOVE_TICKETS_NOT_FOUND("KAZ_VAL_028", "Some tickets in the bulk move request were not found.", ExceptionCategory.VAL),
    VAL_MILESTONE_NOT_DELETED("KAZ_VAL_029", "Milestone is not deleted and cannot be restored.", ExceptionCategory.VAL),
    VAL_MILESTONE_START_AFTER_TARGET("KAZ_VAL_030", "Milestone start date must be on or before its target date.", ExceptionCategory.VAL),
    VAL_MILESTONE_NOT_LINKABLE("KAZ_VAL_031", "Milestone is not linkable to this ticket. The milestone's board must be the ticket's board or its parent, child or sibling.", ExceptionCategory.VAL),
    VAL_ROADMAP_FROM_AFTER_TO("KAZ_VAL_032", "Roadmap 'from' date must be on or before the 'to' date.", ExceptionCategory.VAL),
    VAL_MILESTONE_DEPENDENCY_SELF("KAZ_VAL_033", "A milestone cannot depend on itself.", ExceptionCategory.VAL),
    VAL_MILESTONE_DEPENDENCY_CYCLE("KAZ_VAL_034", "This dependency would create a cycle: the other milestone already depends on this one, directly or through others.", ExceptionCategory.VAL),
    VAL_MILESTONE_DEPENDENCY_NOT_RELATED("KAZ_VAL_035", "Milestones can only depend on milestones of the same board or its parent, child or sibling.", ExceptionCategory.VAL),
    VAL_DAILY_UPDATE_DUPLICATE_TICKET("KAZ_VAL_036", "A ticket can appear only once in an update.", ExceptionCategory.VAL),
    VAL_DAILY_UPDATE_FROM_AFTER_TO("KAZ_VAL_038", "Update 'from' date must be on or before the 'to' date.", ExceptionCategory.VAL),
    VAL_DAILY_UPDATE_REPORT_DATE_IN_FUTURE("KAZ_VAL_039", "The date cannot be in the future.", ExceptionCategory.VAL),
    VAL_SPRINT_RECURRENCE_DATES_INVALID("KAZ_VAL_040", "A repeating sprint needs a start and an end date at 00:00 UTC, 1 to 365 days apart.", ExceptionCategory.VAL),
    VAL_SPRINT_RECURRENCE_DAYS_BEFORE_INVALID("KAZ_VAL_041", "Days before must be set, from 1 to the sprint length minus 1, when the next sprint is created before the end date.", ExceptionCategory.VAL),
    VAL_SPRINT_RECURRENCE_SPRINT_COMPLETED("KAZ_VAL_042", "A completed sprint cannot repeat.", ExceptionCategory.VAL),
    VAL_CHAT_MESSAGE_INVALID("KAZ_VAL_043", "A message needs text or an attachment and allows at most 2000 characters, 20 mentions, 10 ticket references and 10 files.", ExceptionCategory.VAL),
    VAL_CHAT_MENTION_NOT_MEMBER("KAZ_VAL_044", "Only members who can read this conversation can be mentioned.", ExceptionCategory.VAL),
    VAL_CHAT_REPLY_OUTSIDE_CONVERSATION("KAZ_VAL_045", "A reply must target a message in the same channel or thread.", ExceptionCategory.VAL),
    VAL_CHAT_REACTION_INVALID("KAZ_VAL_046", "A message allows at most 20 different reactions of one emoji each.", ExceptionCategory.VAL),
    VAL_CHAT_DAILY_UPDATE_MESSAGE_READ_ONLY("KAZ_VAL_047", "Edit or delete a daily update from its own actions.", ExceptionCategory.VAL),
    VAL_CHAT_PIN_LIMIT_REACHED("KAZ_VAL_048", "This channel already has 50 pinned messages.", ExceptionCategory.VAL),
    VAL_CHAT_CURSOR_CONFLICT("KAZ_VAL_049", "Use only one of before, after or around.", ExceptionCategory.VAL),
    VAL_CHAT_THREAD_INVALID("KAZ_VAL_050", "A thread starts on a live channel message and archives after 60, 1440, 4320 or 10080 minutes.", ExceptionCategory.VAL),
    VAL_CHAT_CHANNEL_MEMBERS_NOT_EXPECTED("KAZ_VAL_051", "Members can be set only when a channel becomes private.", ExceptionCategory.VAL),
    VAL_CHAT_CHANNEL_NOT_PRIVATE("KAZ_VAL_052", "Members are managed only in a private channel.", ExceptionCategory.VAL),
    VAL_CHAT_CHANNEL_PURPOSE_PRIVATE("KAZ_VAL_053", "Set this channel to General before making it private.", ExceptionCategory.VAL),
    VAL_CHAT_DAILY_UPDATE_CHANNEL("KAZ_VAL_054", "A daily update can be posted only in the daily updates channel.", ExceptionCategory.VAL),
    VAL_PULL_REQUEST_SHARE_INVALID("KAZ_VAL_055", "A pull request share allows a note of at most 500 characters and up to 10 different reviewers, who are active members, admins or owners of the organization and not the author, and up to 10 different related tickets. A search allows at most 100 characters and pages start at 1.", ExceptionCategory.VAL),
    VAL_PULL_REQUEST_CHANNEL_ABSENT("KAZ_VAL_056", "This organization has no pull requests channel. Set a channel's purpose to Pull requests first.", ExceptionCategory.VAL),
    VAL_SUB_TICKET_IDS_WITHOUT_SELECTED("KAZ_VAL_057", "subTicketIds is only allowed with subTicketMode SELECTED.", ExceptionCategory.VAL),
    VAL_SUB_TICKET_NOT_DESCENDANT("KAZ_VAL_058", "Every listed sub-ticket must be a moved ticket or a sub-ticket, at any depth, of one.", ExceptionCategory.VAL),
    VAL_SUB_TICKET_BOARD_CHANGE("KAZ_VAL_059", "A sub-ticket cannot move to another board without its parent. Detach it first.", ExceptionCategory.VAL),
    VAL_SPRINT_SERIES_NAME_INVALID("KAZ_VAL_060", "A sprint in a series is named after its series. Change the series name, 1 to 240 characters, from an unfinished sprint of the series.", ExceptionCategory.VAL),
    VAL_SPRINT_NEXT_SPRINT_UNAVAILABLE("KAZ_VAL_061", "Only a sprint of a repeating series with a board can move its unfinished tickets to the next sprint.", ExceptionCategory.VAL),
    VAL_TICKET_BACKLOG_STATUS("KAZ_VAL_062", "A ticket in the backlog can only be To Do. Move it to a sprint first, then change its status.", ExceptionCategory.VAL),
    VAL_INCIDENT_CLOSE_REQUIRES_RCA("KAZ_VAL_063", "An incident can only be set to Done once its root cause analysis (RCA) and lessons learned are filled in.", ExceptionCategory.VAL),

    // ── Not Found (KAZ_NOT) ──────────────────────────────────────────────────
    NOT_BOARD_FOUND("KAZ_NOT_001", "Board not found.", ExceptionCategory.NOT),
    NOT_BOARD_MEMBER_FOUND("KAZ_NOT_002", "Board member not found.", ExceptionCategory.NOT),
    NOT_SPRINT_FOUND("KAZ_NOT_003", "Sprint not found.", ExceptionCategory.NOT),
    NOT_TICKET_FOUND("KAZ_NOT_004", "Ticket not found.", ExceptionCategory.NOT),
    NOT_BACKLOG_FOUND("KAZ_NOT_005", "Backlog not found.", ExceptionCategory.NOT),
    NOT_PROJECT_FOUND("KAZ_NOT_006", "Project not found.", ExceptionCategory.NOT),
    NOT_COMMENT_FOUND("KAZ_NOT_007", "Comment not found.", ExceptionCategory.NOT),
    NOT_LABEL_FOUND("KAZ_NOT_008", "Label not found.", ExceptionCategory.NOT),
    NOT_MILESTONE_FOUND("KAZ_NOT_009", "Milestone not found.", ExceptionCategory.NOT),
    NOT_MILESTONE_DEPENDENCY_FOUND("KAZ_NOT_010", "Milestone dependency not found.", ExceptionCategory.NOT),
    NOT_DAILY_UPDATE_FOUND("KAZ_NOT_011", "Update not found.", ExceptionCategory.NOT),
    NOT_ALERT_ROUTE_FOUND("KAZ_NOT_012", "Alert route not found.", ExceptionCategory.NOT),
    NOT_CHAT_MESSAGE_FOUND("KAZ_NOT_013", "Message not found.", ExceptionCategory.NOT),
    NOT_CHAT_THREAD_FOUND("KAZ_NOT_014", "Thread not found.", ExceptionCategory.NOT),
    NOT_CHAT_CHANNEL_FOUND("KAZ_NOT_015", "Channel not found.", ExceptionCategory.NOT),
    NOT_GITHUB_NOT_CONFIGURED("KAZ_NOT_016", "GitHub is not connected for this organization.", ExceptionCategory.NOT),
    NOT_GITHUB_REPOSITORY_FOUND("KAZ_NOT_017", "GitHub repository not found.", ExceptionCategory.NOT),
    NOT_GITHUB_PULL_REQUEST_FOUND("KAZ_NOT_018", "Pull request not found on GitHub.", ExceptionCategory.NOT),
    NOT_PULL_REQUEST_SHARE_FOUND("KAZ_NOT_019", "Pull request share not found.", ExceptionCategory.NOT),

    // ── State (KAZ_STA) ─────────────────────────────────────────────────────
    STA_SPRINT_ALREADY_COMPLETED("KAZ_STA_001", "Sprint is already completed.", ExceptionCategory.STA),
    STA_SPRINT_ALREADY_ACTIVE("KAZ_STA_002", "Sprint is already active.", ExceptionCategory.STA),
    STA_BOARD_MEMBER_ALREADY_EXISTS("KAZ_STA_003", "User is already a member of this board.", ExceptionCategory.STA),
    STA_ACTIVE_SPRINT_EXISTS("KAZ_STA_004", "Board already has an active sprint.", ExceptionCategory.STA),
    STA_TICKET_KEY_DUPLICATE("KAZ_STA_005", "A ticket with this key already exists.", ExceptionCategory.STA),
    STA_LABEL_NAME_DUPLICATE("KAZ_STA_006", "A label with this name already exists on this board.", ExceptionCategory.STA),
    STA_BOARD_PREFIX_DUPLICATE("KAZ_STA_007", "A board with this prefix already exists.", ExceptionCategory.STA),
    STA_BOARD_ALREADY_CHILD("KAZ_STA_008", "Board is already a child of the specified parent.", ExceptionCategory.STA),
    STA_MILESTONE_ALREADY_CLOSED("KAZ_STA_009", "Milestone is already closed.", ExceptionCategory.STA),
    STA_MILESTONE_ALREADY_OPEN("KAZ_STA_010", "Milestone is already open.", ExceptionCategory.STA),
    STA_SPRINT_RECURRENCE_SPRINT_COMPLETED_DURING_SAVE("KAZ_STA_011", "The sprint was completed while the repeat settings were being saved. Reload the board and change the settings of the current sprint.", ExceptionCategory.STA),
    STA_ALERT_ROUTE_SYSTEM_DUPLICATE("KAZ_STA_012", "This system already has a board.", ExceptionCategory.STA),
    STA_CHAT_THREAD_EXISTS("KAZ_STA_013", "This message already has a thread.", ExceptionCategory.STA),
    STA_CHAT_THREAD_CLOSED("KAZ_STA_014", "This thread is closed.", ExceptionCategory.STA),
    STA_CHAT_DAILY_UPDATE_POSTED("KAZ_STA_015", "This update is already in the chat.", ExceptionCategory.STA),
    STA_CHAT_PURPOSE_TAKEN("KAZ_STA_016", "Another channel already has this purpose. Set that channel to General first.", ExceptionCategory.STA),
    STA_SLUG_TAKEN("KAZ_STA_017", "Another item took this name a moment ago. Try again.", ExceptionCategory.STA),
    STA_BOARD_IN_OTHER_SPRINT_SERIES("KAZ_STA_018", "This board already belongs to another repeating sprint series. Turn that series off or unlink the board from its sprint first.", ExceptionCategory.STA),
    STA_BOARD_MEMBER_NOT_IN_ORGANIZATION("KAZ_STA_019", "This user is not a member of the board's organization.", ExceptionCategory.STA),

    // ── Upstream (KAZ_UPS) ───────────────────────────────────────────────────
    UPS_AI_UNAVAILABLE("KAZ_UPS_001", "AI service is currently unavailable.", ExceptionCategory.UPS),
    UPS_AI_ANALYSIS_FAILED("KAZ_UPS_002", "AI ticket analysis failed.", ExceptionCategory.UPS);

    private final String code;
    private final String message;
    private final ExceptionCategory category;

    ExceptionDomainKazanError(String code, String message, ExceptionCategory category) {
        this.code = code;
        this.message = message;
        this.category = category;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public ExceptionCategory getCategory() {
        return category;
    }
}
