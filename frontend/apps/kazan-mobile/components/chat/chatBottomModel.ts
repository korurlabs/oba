import { CHAT_AT_BOTTOM_PX } from "@/components/chat/chatUnreadModel";

/*
 * Whether the bottom of an inverted message list, its newest message, is on
 * screen. The list reports its scroll offset (the distance from the bottom)
 * and its content height in separate events, in no fixed order. When a newer
 * page is added below the reader, the list keeps the reader's place, so the
 * distance from the bottom grows by the page's height, but no scroll event
 * has to report that. The track below works the distance out from the
 * content height instead, so a reader who is still near the bottom after a
 * short page counts as there without scrolling again, and a reader a long
 * page pushed up does not.
 */

export interface ChatBottomTrack {
  /** The view's distance from the bottom of the list, as last reported or worked out. */
  offset: number;
  /** The content height the list had when `offset` held. */
  offsetContentHeight: number;
  /**
   * The content height when a newer page was added in place. Scroll events at
   * this height were measured before the page was laid out. Null when no such
   * page is pending.
   */
  newerAddedFrom: number | null;
  /** The content height has changed since the pending newer page was added. */
  newerLaidOut: boolean;
}

export const CHAT_BOTTOM_START: ChatBottomTrack = {
  offset: 0,
  offsetContentHeight: 0,
  newerAddedFrom: null,
  newerLaidOut: false,
};

/**
 * A scroll event, or the end of a drag or a fling. While a newer page is
 * pending, an event at the content height from before the page was measured
 * before the page was laid out: its offset still lacks the page, so the page
 * stays pending and the offset is moved by whatever height the page has
 * added so far. That holds for the end of a drag or a fling as well, which
 * the list can report before the page is mounted. An event at another height
 * already includes the page and is the reader's real place.
 */
export function chatBottomAfterScroll(
  track: ChatBottomTrack,
  offset: number,
  contentHeight: number
): ChatBottomTrack {
  if (track.newerAddedFrom !== null && contentHeight === track.newerAddedFrom) {
    return {
      ...track,
      offset: Math.max(0, offset + track.offsetContentHeight - track.newerAddedFrom),
    };
  }
  return {
    offset,
    offsetContentHeight: contentHeight,
    newerAddedFrom: null,
    newerLaidOut: false,
  };
}

/**
 * A change of the content height. The first change after a newer page was
 * added in place is that page (and the loading row it replaced) below the
 * reader, so the reader's distance from the bottom changes by as much. Any
 * other change is above the reader (older pages) or comes with its own
 * scroll event, and leaves the distance as it was.
 */
export function chatBottomAfterContentSize(
  track: ChatBottomTrack,
  contentHeight: number
): ChatBottomTrack {
  if (track.newerAddedFrom === null || track.newerLaidOut) {
    return { ...track, offsetContentHeight: contentHeight };
  }
  return {
    ...track,
    offset: Math.max(0, track.offset + contentHeight - track.offsetContentHeight),
    offsetContentHeight: contentHeight,
    newerLaidOut: true,
  };
}

/** A newer page was added below the reader without scrolling to it. */
export function chatBottomAfterNewerAdded(track: ChatBottomTrack): ChatBottomTrack {
  return { ...track, newerAddedFrom: track.offsetContentHeight, newerLaidOut: false };
}

/**
 * A frame has passed since the newer page was mounted and laid out, so every
 * event the list sends from now on includes it. A page exactly as tall as the
 * loading row it replaced changes no height and sends no event of its own:
 * the reader's distance from the bottom is then the one last reported.
 */
export function chatBottomAfterNewerSettled(track: ChatBottomTrack): ChatBottomTrack {
  if (track.newerAddedFrom === null) return track;
  return { ...track, newerAddedFrom: null, newerLaidOut: false };
}

/**
 * True when the newest message is on screen. A newer page the list has not
 * laid out yet is not: what it adds is not on screen until it is.
 */
export function isChatBottomInView(track: ChatBottomTrack): boolean {
  const newerPending: boolean = track.newerAddedFrom !== null && !track.newerLaidOut;
  return !newerPending && track.offset <= CHAT_AT_BOTTOM_PX;
}
