import { describe, expect, it } from "bun:test";

import {
  CHAT_BOTTOM_START,
  chatBottomAfterContentSize,
  chatBottomAfterNewerAdded,
  chatBottomAfterNewerSettled,
  chatBottomAfterScroll,
  isChatBottomInView,
  type ChatBottomTrack,
} from "@/components/chat/chatBottomModel";
import { CHAT_AT_BOTTOM_PX } from "@/components/chat/chatUnreadModel";

/** A reader 10 px from the bottom of a 2000 px list, as the last scroll event reported. */
const nearBottom: ChatBottomTrack = chatBottomAfterContentSize(
  chatBottomAfterScroll(CHAT_BOTTOM_START, 10, 2000),
  2000
);

describe("chat bottom state", () => {
  it("counts the reader within the bottom band as at the bottom", () => {
    expect(isChatBottomInView(CHAT_BOTTOM_START)).toBe(true);
    expect(isChatBottomInView(nearBottom)).toBe(true);
    expect(isChatBottomInView(chatBottomAfterScroll(nearBottom, CHAT_AT_BOTTOM_PX + 1, 2000))).toBe(
      false
    );
  });

  it("holds a newer page that is not laid out yet as off screen", () => {
    expect(isChatBottomInView(chatBottomAfterNewerAdded(nearBottom))).toBe(false);
  });

  it("finds the reader still at the bottom after a short newer page, without another scroll", () => {
    const added: ChatBottomTrack = chatBottomAfterNewerAdded(nearBottom);
    // The page adds 30 px below the reader: 40 px from the bottom, inside the band.
    const laidOut: ChatBottomTrack = chatBottomAfterContentSize(added, 2030);
    expect(laidOut.offset).toBe(40);
    expect(isChatBottomInView(laidOut)).toBe(true);
  });

  it("finds the reader pushed up by a long newer page, though no scroll event says so", () => {
    const added: ChatBottomTrack = chatBottomAfterNewerAdded(nearBottom);
    const laidOut: ChatBottomTrack = chatBottomAfterContentSize(added, 2600);
    expect(laidOut.offset).toBe(610);
    expect(isChatBottomInView(laidOut)).toBe(false);
  });

  it("counts the loading row the page replaced", () => {
    // A 44 px loading row was laid out below the reader, then the page of 30 px replaced it.
    const loading: ChatBottomTrack = chatBottomAfterContentSize(
      chatBottomAfterScroll(nearBottom, 54, 2044),
      2044
    );
    const laidOut: ChatBottomTrack = chatBottomAfterContentSize(
      chatBottomAfterNewerAdded(loading),
      2030
    );
    expect(laidOut.offset).toBe(40);
    expect(isChatBottomInView(laidOut)).toBe(true);
  });

  it("gives the same place whichever of the scroll and size events comes first", () => {
    const added: ChatBottomTrack = chatBottomAfterNewerAdded(nearBottom);
    // The list kept the reader's place: the scroll event reports 40 px at the new height.
    const scrollFirst: ChatBottomTrack = chatBottomAfterContentSize(
      chatBottomAfterScroll(added, 40, 2030),
      2030
    );
    const sizeFirst: ChatBottomTrack = chatBottomAfterScroll(
      chatBottomAfterContentSize(added, 2030),
      40,
      2030
    );
    expect(scrollFirst).toEqual(sizeFirst);
    expect(isChatBottomInView(scrollFirst)).toBe(true);
  });

  it("does not take a scroll event from before the page was laid out as the reader's place", () => {
    const added: ChatBottomTrack = chatBottomAfterNewerAdded(nearBottom);
    const early: ChatBottomTrack = chatBottomAfterScroll(added, 12, 2000);
    expect(isChatBottomInView(early)).toBe(false);
    const laidOut: ChatBottomTrack = chatBottomAfterContentSize(early, 2600);
    expect(laidOut.offset).toBe(612);
    expect(isChatBottomInView(laidOut)).toBe(false);
  });

  it("leaves the place alone when older pages grow the list above the reader", () => {
    const older: ChatBottomTrack = chatBottomAfterContentSize(nearBottom, 4000);
    expect(older.offset).toBe(10);
    expect(isChatBottomInView(older)).toBe(true);
  });

  it("keeps a pending page for a drag end measured before the page was laid out", () => {
    const added: ChatBottomTrack = chatBottomAfterNewerAdded(nearBottom);
    // The drag ends 5 px from the bottom at the old content height: the page is not in it yet.
    const ended: ChatBottomTrack = chatBottomAfterScroll(added, 5, 2000);
    expect(isChatBottomInView(ended)).toBe(false);
    // The long page is laid out and its own scroll event is lost: the reader is 605 px up.
    const laidOut: ChatBottomTrack = chatBottomAfterContentSize(ended, 2600);
    expect(laidOut.offset).toBe(605);
    expect(isChatBottomInView(laidOut)).toBe(false);
    expect(isChatBottomInView(chatBottomAfterNewerSettled(laidOut))).toBe(false);
  });

  it("moves a drag end measured before the page by the page once the page is laid out", () => {
    const laidOut: ChatBottomTrack = chatBottomAfterContentSize(
      chatBottomAfterNewerAdded(nearBottom),
      2600
    );
    const lateEnd: ChatBottomTrack = chatBottomAfterScroll(laidOut, 5, 2000);
    expect(lateEnd.offset).toBe(605);
    expect(isChatBottomInView(lateEnd)).toBe(false);
  });

  it("takes the end of a drag after the page was laid out as the reader's place", () => {
    const laidOut: ChatBottomTrack = chatBottomAfterContentSize(
      chatBottomAfterNewerAdded(nearBottom),
      2600
    );
    const settled: ChatBottomTrack = chatBottomAfterScroll(laidOut, 5, 2600);
    expect(settled.newerAddedFrom).toBe(null);
    expect(settled.offset).toBe(5);
    expect(isChatBottomInView(settled)).toBe(true);
  });

  it("finds the reader at the bottom a frame after a page as tall as the loading row it replaced", () => {
    // A 44 px loading row below the reader is replaced by a page of 44 px: no height changes.
    const loading: ChatBottomTrack = chatBottomAfterContentSize(
      chatBottomAfterScroll(nearBottom, 54, 2044),
      2044
    );
    const added: ChatBottomTrack = chatBottomAfterNewerAdded(loading);
    const sameHeight: ChatBottomTrack = chatBottomAfterScroll(added, 30, 2044);
    expect(isChatBottomInView(sameHeight)).toBe(false);
    const settled: ChatBottomTrack = chatBottomAfterNewerSettled(sameHeight);
    expect(settled.offset).toBe(30);
    expect(isChatBottomInView(settled)).toBe(true);
  });

  it("does not take a later height change as the page once the page is laid out", () => {
    const laidOut: ChatBottomTrack = chatBottomAfterContentSize(
      chatBottomAfterNewerAdded(nearBottom),
      2030
    );
    const older: ChatBottomTrack = chatBottomAfterContentSize(laidOut, 4000);
    expect(older.offset).toBe(40);
    expect(isChatBottomInView(chatBottomAfterNewerSettled(older))).toBe(true);
  });

  it("leaves the track alone when no page is pending as a frame passes", () => {
    expect(chatBottomAfterNewerSettled(nearBottom)).toBe(nearBottom);
  });
});
