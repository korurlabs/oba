import { describe, expect, it } from "bun:test";

import {
  askIncidentClose,
  forgetIncidentCloseDraft,
  getIncidentClose,
  incidentCloseDraft,
  NEW_INCIDENT_DRAFT,
  settleIncidentClose,
  type IncidentCloseRequest,
} from "@/components/ticket/incidentCloseStore";

const TICKET = { boardId: "b0000000-0000-0000-0000-000000000001", ticketId: "t-41" };
const TEXTS = { rca: "Disk full", lessonsLearned: "Alert at 80%" };

function openRequest(): IncidentCloseRequest {
  const request: IncidentCloseRequest | null = getIncidentClose();
  if (!request) throw new Error("no open question");
  return request;
}

describe("incident close store", () => {
  it("hands the sheet the incident and resolves with the confirmed texts", async () => {
    const answer = askIncidentClose(TICKET);
    const request: IncidentCloseRequest = openRequest();
    expect(request.ticket).toEqual(TICKET);
    expect(request.draftKey).toBe("t-41");

    settleIncidentClose(request, TEXTS);
    expect(await answer).toEqual(TEXTS);
    expect(getIncidentClose()).toBeNull();
    forgetIncidentCloseDraft("t-41");
  });

  it("resolves with null on cancel, so the caller sends nothing", async () => {
    const answer = askIncidentClose(TICKET);
    settleIncidentClose(openRequest(), null);
    expect(await answer).toBeNull();
    expect(incidentCloseDraft("t-41")).toBeUndefined();
  });

  it("answers a question once: the sheet's close after a confirm changes nothing", async () => {
    const answer = askIncidentClose(TICKET);
    const request: IncidentCloseRequest = openRequest();
    settleIncidentClose(request, TEXTS);
    settleIncidentClose(request, null);
    expect(await answer).toEqual(TEXTS);
    forgetIncidentCloseDraft("t-41");
  });

  it("ends an older question as a cancel when a newer one opens", async () => {
    const first = askIncidentClose(TICKET);
    const second = askIncidentClose({ ...TICKET, ticketId: "t-42" });
    expect(await first).toBeNull();
    expect(openRequest().draftKey).toBe("t-42");
    settleIncidentClose(openRequest(), null);
    expect(await second).toBeNull();
  });

  it("keeps confirmed texts as a draft until the close lands", async () => {
    const answer = askIncidentClose(TICKET);
    settleIncidentClose(openRequest(), TEXTS);
    await answer;
    expect(incidentCloseDraft("t-41")).toEqual(TEXTS);
    forgetIncidentCloseDraft("t-41");
    expect(incidentCloseDraft("t-41")).toBeUndefined();
  });

  it("keys the draft of an incident not created yet as new", async () => {
    const answer = askIncidentClose();
    const request: IncidentCloseRequest = openRequest();
    expect(request.ticket).toBeUndefined();
    expect(request.draftKey).toBe(NEW_INCIDENT_DRAFT);
    settleIncidentClose(request, TEXTS);
    expect(await answer).toEqual(TEXTS);
    expect(incidentCloseDraft(NEW_INCIDENT_DRAFT)).toEqual(TEXTS);
    forgetIncidentCloseDraft(NEW_INCIDENT_DRAFT);
  });
});
