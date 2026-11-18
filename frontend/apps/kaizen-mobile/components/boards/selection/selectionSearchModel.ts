/** A command of the native header search bar. */
export type SearchBarStep = "focus" | "setText" | "blur";

/**
 * The commands, in order, that bring the header search back when selection
 * mode ends with `text` still filtering the list. Selection closes the native
 * search before it takes the bar out of the header, so the field returns
 * empty.
 *
 * Android's closed search is only an action in the bar, so a search in force
 * reopens it, gets its text back, and drops the keyboard that opening raises:
 * the field looks as it did before selection. An iOS field with text in it
 * stays in the header, so filling it is enough. A blank search leaves the
 * field closed on both.
 */
export function searchRestoreSteps(platform: string, text: string): SearchBarStep[] {
  if (text.trim() === "") return [];
  return platform === "android" ? ["focus", "setText", "blur"] : ["setText"];
}
