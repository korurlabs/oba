/**
 * The reason a sprint series records when it is switched off arrives as English
 * text from the server. The two reasons that switch a series off without the
 * user doing so are shown in the viewer's language; any other text is shown as
 * it came.
 *
 * The texts below match SprintRecurrenceConstant.RUN_MESSAGE_NO_LIVE_SPRINT and
 * the message the one-series-per-board migration wrote; a change there needs
 * the same change here.
 */

const NO_LIVE_SPRINT = "no running or planned sprint left";
const BOARD_IN_OTHER_SERIES = /^Switched off: board (.+) belongs to series (.+)$/;

/**
 * The translated reason for a series run message.
 *
 * @param message the series' last run message
 * @param t a translator scoped to `sprint.recurrence.reasons`
 */
export function seriesRunMessage(
  message: string,
  t: (key: string, values?: Record<string, string>) => string
): string {
  if (message === NO_LIVE_SPRINT) {
    return t("noLiveSprint");
  }
  const [, board, series] = BOARD_IN_OTHER_SERIES.exec(message) ?? [];
  if (board && series) {
    return t("boardInOtherSeries", { board, series });
  }
  return message;
}
