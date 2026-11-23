import { describe, expect, it, mock } from "bun:test";

import type { UploadedImage } from "@/components/ticket/descriptionImagesModel";
import { dueDateInstant } from "@/components/ticket/dueDateModel";
import {
  BACKLOG_PLACEMENT,
  createTicketBody,
  rankOrDefault,
} from "@/components/ticket/newTicketModel";
import type { TicketDraftOptions, TicketDraftValues } from "@/components/ticket/ticketDraftModel";
import type { ChatLocalFile } from "@/lib/schemas/chat";
import type { PendingImage } from "@/lib/schemas/tickets";

// The model maps upload refusals with ticketAttachmentsModel.ts, which loads
// lib/api.ts; these are the stubs lib/__tests__/api.test.ts uses for it.
mock.module("react-native", () => ({
  Platform: { OS: "ios" },
  AppState: { addEventListener: () => ({ remove: () => undefined }) },
}));

mock.module("expo-secure-store", () => ({
  getItemAsync: async () => null,
  setItemAsync: async () => undefined,
  deleteItemAsync: async () => undefined,
}));

mock.module("@/lib/env", () => ({
  ENV: {
    apiBaseUrl: "https://api.test",
    keycloakIssuer: "https://auth.test/realms/sensey",
    keycloakClientId: "kazan-mobile",
    accountUrl: "https://account.test",
    ulakUrl: "https://ulak.test",
    kazanWebUrl: "https://kazan.test",
  },
}));

mock.module("expo-auth-session", () => ({
  fetchDiscoveryAsync: async (issuer: string) => ({
    authorizationEndpoint: `${issuer}/protocol/openid-connect/auth`,
    tokenEndpoint: `${issuer}/protocol/openid-connect/token`,
    endSessionEndpoint: `${issuer}/protocol/openid-connect/logout`,
  }),
  makeRedirectUri: ({ scheme, path }: { scheme: string; path: string }) => `${scheme}://${path}`,
}));

const {
  commitCreatedDescription,
  createFieldErrors,
  DescriptionTooLongError,
  draftProblems,
  draftTicketView,
  duplicateAttachmentNames,
  firstProblemField,
  imageFailureMessage,
  newTicketResultKey,
  onBoard,
  partialCreatedDescription,
  withoutPendingImages,
} = await import("@/components/ticket/ticketDraftModel");
const { MissingPendingImageError } = await import("@/components/ticket/descriptionImagesModel");
const { ApiError } = await import("@/lib/api");

const values = (overrides: Partial<TicketDraftValues> = {}): TicketDraftValues => ({
  title: "Fix login",
  type: "BUG",
  priority: "HIGH",
  status: "IN_PROGRESS",
  missionRank: "L",
  placement: BACKLOG_PLACEMENT,
  assigneeIds: ["u1"],
  labelIds: [],
  milestoneId: null,
  projectId: null,
  dueDay: null,
  isSubTicket: false,
  ...overrides,
});

const options: TicketDraftOptions = {
  members: [
    { id: "m1", userId: "u1", firstName: "Ada", lastName: "Lovelace", email: null, role: "MEMBER" },
  ],
  labels: [{ id: "l1", name: "api", color: "#f00" }],
  sprints: [
    { id: "s1", name: "Sprint 1", slug: null, status: "ACTIVE", startDate: null, endDate: null },
  ],
  milestones: [{ id: "ms1", name: "Beta", status: "OPEN", targetDate: "2026-12-01" }],
  projects: [{ id: "p1", name: "Mobile", color: null }],
};

const EMPTY_OPTIONS: TicketDraftOptions = {
  members: [],
  labels: [],
  sprints: [],
  milestones: [],
  projects: [],
};

describe("onBoard", () => {
  it("keeps a choice only on the board it was made on", () => {
    const choice = { boardId: "b1", value: "s1" };
    expect(onBoard(choice, "b1", BACKLOG_PLACEMENT)).toBe("s1");
    expect(onBoard(choice, "b2", BACKLOG_PLACEMENT)).toBe(BACKLOG_PLACEMENT);
    expect(onBoard(null, "b1", BACKLOG_PLACEMENT)).toBe(BACKLOG_PLACEMENT);
  });
});

describe("draftTicketView", () => {
  it("shows a backlog draft as Todo without a sprint", () => {
    const view = draftTicketView(values(), options);
    expect(view.header).toEqual({
      type: "BUG",
      title: "Fix login",
      status: "TODO",
      priority: "HIGH",
      missionRank: "L",
    });
    expect(view.fields.status).toBe("TODO");
    expect(view.fields.sprint).toBeNull();
  });

  it("keeps the picked status and shows the sprint of a sprint draft", () => {
    const view = draftTicketView(values({ placement: "s1" }), options);
    expect(view.header.status).toBe("IN_PROGRESS");
    expect(view.fields.sprint).toEqual({ id: "s1", name: "Sprint 1", status: "ACTIVE" });
  });

  it("keeps the picked status of a sub-ticket, whose parent decides its place", () => {
    expect(draftTicketView(values({ isSubTicket: true }), options).header.status).toBe(
      "IN_PROGRESS"
    );
  });

  it("names the picked people, labels, milestone, project and due day", () => {
    const view = draftTicketView(
      values({
        labelIds: ["l1"],
        milestoneId: "ms1",
        projectId: "p1",
        dueDay: "2026-11-20",
      }),
      options
    );
    expect(view.fields.assignees.map((person) => person.firstName)).toEqual(["Ada"]);
    expect(view.fields.labels).toEqual([{ id: "l1", name: "api", color: "#f00" }]);
    expect(view.fields.milestone?.name).toBe("Beta");
    expect(view.fields.project).toEqual({ id: "p1", name: "Mobile", color: null });
    expect(view.fields.dueDate).toBe(dueDateInstant("2026-11-20"));
  });

  it("leaves out ids whose board lists have not loaded yet", () => {
    const view = draftTicketView(
      values({ labelIds: ["l1"], milestoneId: "ms1", projectId: "p1", placement: "s1" }),
      EMPTY_OPTIONS
    );
    expect(view.fields.assignees).toEqual([]);
    expect(view.fields.labels).toEqual([]);
    expect(view.fields.milestone).toBeNull();
    expect(view.fields.project).toBeNull();
    expect(view.fields.sprint).toBeNull();
  });

  it("shows the rank once, as the header's chip, and not in the field list", () => {
    const view = draftTicketView(values(), options);
    expect(view.header.missionRank).toBe("L");
    expect("missionRank" in view.fields).toBe(false);
  });

  it("shows no rank for a value that is not a mission rank, so the chip reads as missing", () => {
    const view = draftTicketView(values({ missionRank: "M2" }), options);
    expect(view.header.missionRank).toBeNull();
  });
});

describe("createFieldErrors", () => {
  it("puts each refused field under its field, the header chips, a row or a toast", () => {
    expect(
      createFieldErrors({
        title: "Title is required",
        description: "Too long",
        assigneeIds: "Not a member",
        backlogId: "Unknown backlog",
        sprintId: "Sprint closed",
        dueDate: "In the past",
        type: "Unknown type",
        missionRank: "Unknown rank",
      })
    ).toEqual({
      title: "Title is required",
      description: "Too long",
      missionRank: "Unknown rank",
      rows: {
        assignees: "Not a member",
        sprint: "Unknown backlog\nSprint closed",
        dueDate: "In the past",
      },
      others: ["Unknown type"],
    });
  });

  it("returns no errors for no fields", () => {
    expect(createFieldErrors({})).toEqual({
      title: null,
      description: null,
      missionRank: null,
      rows: {},
      others: [],
    });
  });
});

describe("the mission rank default", () => {
  it("starts a new ticket at M and sends M with the create request", () => {
    const rank = rankOrDefault(null);
    expect(rank).toBe("M");
    expect(draftTicketView(values({ missionRank: rank }), options).header.missionRank).toBe("M");
    const body = createTicketBody(
      {
        title: "Fix login",
        description: "",
        type: "BUG",
        priority: "HIGH",
        missionRank: rank,
        placement: BACKLOG_PLACEMENT,
        assigneeIds: [],
        labelIds: [],
      },
      { backlogId: "b1", parentTicketId: null }
    );
    expect(body?.missionRank).toBe("M");
  });
});

describe("draftProblems", () => {
  const draft = { title: "Fix login", description: "", missionRank: "M" };

  it("passes a draft with a title and a rank", () => {
    expect(draftProblems(draft)).toBeNull();
  });

  it("requires a title that is more than spaces", () => {
    expect(draftProblems({ ...draft, title: "   " })).toEqual({
      title: "required",
      missionRank: null,
      description: null,
    });
  });

  it("requires a mission rank", () => {
    expect(draftProblems({ ...draft, missionRank: "" })?.missionRank).toBe("required");
    expect(draftProblems({ ...draft, missionRank: "M2" })?.missionRank).toBe("required");
  });

  it("flags every missing field at once", () => {
    expect(draftProblems({ ...draft, title: "", missionRank: "" })).toEqual({
      title: "required",
      missionRank: "required",
      description: null,
    });
  });

  it("refuses a title or description over the API's limit", () => {
    const problems = draftProblems({
      ...draft,
      title: "t".repeat(256),
      description: "d".repeat(10001),
    });
    expect(problems?.title).toBe("too-long");
    expect(problems?.description).toBe("too-long");
  });
});

describe("firstProblemField", () => {
  it("picks the topmost field with a problem", () => {
    expect(
      firstProblemField({ title: "required", missionRank: "required", description: null })
    ).toBe("title");
    expect(
      firstProblemField({ title: null, missionRank: "required", description: "too-long" })
    ).toBe("missionRank");
    expect(firstProblemField({ title: null, missionRank: null, description: "too-long" })).toBe(
      "description"
    );
    expect(firstProblemField({ title: null, missionRank: null, description: null })).toBeNull();
  });
});

const file = (name: string): ChatLocalFile => ({
  key: name,
  uri: `file:///cache/${name}`,
  name,
  mimeType: "image/png",
  size: 10,
  width: 40,
  height: 30,
});

const pending = (key: string, uploaded?: UploadedImage): PendingImage => ({
  key,
  file: file(`${key}.png`),
  src: `kazan-pending:${key}`,
  alt: `${key}.png`,
  width: 40,
  height: 30,
  uploaded,
});

const tag = (key: string): string =>
  `<img src="kazan-pending:${key}" alt="${key}.png" width="40" height="30" loading="lazy" decoding="async">`;

const savedTag = (key: string): string =>
  `<img src="https://blob.test/${key}" alt="${key}.png" width="40" height="30" loading="lazy" decoding="async" data-attachment-id="id-${key}">`;

const stored = (key: string): UploadedImage => ({
  id: `id-${key}`,
  url: `https://blob.test/${key}`,
});

describe("duplicateAttachmentNames", () => {
  it("names the files whose content is already attached, in order", async () => {
    const names: string[] = await duplicateAttachmentNames(
      [file("a.png"), file("b.pdf"), file("c.png")],
      async (candidate) => candidate.name !== "b.pdf",
      () => undefined
    );
    expect(names).toEqual(["a.png", "c.png"]);
  });

  it("reports a failed check and goes on with the next file", async () => {
    const failed: string[] = [];
    const names: string[] = await duplicateAttachmentNames(
      [file("a.png"), file("b.png")],
      async (candidate) => {
        if (candidate.name === "a.png") throw new Error("offline");
        return true;
      },
      (candidate) => failed.push(candidate.name)
    );
    expect(failed).toEqual(["a.png"]);
    expect(names).toEqual(["b.png"]);
  });

  it("sends nothing without staged files", async () => {
    let calls = 0;
    const names: string[] = await duplicateAttachmentNames(
      [],
      async () => {
        calls += 1;
        return true;
      },
      () => undefined
    );
    expect(names).toEqual([]);
    expect(calls).toBe(0);
  });
});

describe("withoutPendingImages", () => {
  it("removes an image paragraph with its blank line", () => {
    expect(withoutPendingImages(`Before\n\n${tag("a")}\n\nAfter`)).toBe("Before\n\nAfter");
  });

  it("removes images at the start, at the end and next to each other", () => {
    expect(withoutPendingImages(`${tag("a")}\n\nText`)).toBe("Text");
    expect(withoutPendingImages(`Text\n\n${tag("a")}`)).toBe("Text");
    expect(withoutPendingImages(`A\n\n${tag("a")}\n\n${tag("b")}\n\nB`)).toBe("A\n\nB");
    expect(withoutPendingImages(tag("a"))).toBe("");
  });

  it("removes an image inside a line and keeps the text", () => {
    expect(withoutPendingImages(`See ${tag("a")} here`)).toBe("See  here");
  });

  it("keeps saved images and other text", () => {
    const text = `Intro\n\n${savedTag("x")}\n\n![old](https://blob.test/old.png)`;
    expect(withoutPendingImages(text)).toBe(text);
  });
});

describe("partialCreatedDescription", () => {
  it("keeps the uploaded images and leaves out the others", () => {
    const markdown = `Intro\n\n${tag("a")}\n\n${tag("b")}\n\nEnd`;
    const result: string = partialCreatedDescription(
      markdown,
      [pending("a"), pending("b")],
      new Map([["a", stored("a")]])
    );
    expect(result).toBe(
      `Intro\n\n<img src="https://blob.test/a" alt="a.png" width="40" height="30" loading="lazy" decoding="async" data-attachment-id="id-a">\n\nEnd`
    );
    expect(result).not.toContain("kazan-pending:");
  });

  it("leaves every image out when their links would pass the limit", () => {
    const filler: string = "x".repeat(9_900);
    const markdown = `${filler}\n\n${tag("a")}`;
    const result: string = partialCreatedDescription(
      markdown,
      [pending("a")],
      new Map([["a", { id: "id-a", url: `https://blob.test/${"u".repeat(200)}` }]])
    );
    expect(result).toBe(filler);
  });
});

describe("commitCreatedDescription", () => {
  it("sends nothing when the description holds no pending image", async () => {
    let saves = 0;
    const outcome = await commitCreatedDescription({
      markdown: `Text ${savedTag("x")}`,
      images: [pending("a")],
      upload: async () => stored("a"),
      save: async () => {
        saves += 1;
      },
    });
    expect(outcome).toEqual({ imageError: null, saveError: null });
    expect(saves).toBe(0);
  });

  it("uploads the images in order and saves the text with their links", async () => {
    const uploads: string[] = [];
    const saved: string[] = [];
    const outcome = await commitCreatedDescription({
      markdown: `${tag("a")}\n\nText\n\n${tag("b")}`,
      images: [pending("a"), pending("b"), pending("removed")],
      upload: async (image) => {
        uploads.push(image.key);
        return stored(image.key);
      },
      save: async (description) => {
        saved.push(description);
      },
    });
    expect(outcome).toEqual({ imageError: null, saveError: null });
    expect(uploads).toEqual(["a", "b"]);
    expect(saved).toHaveLength(1);
    expect(saved[0]).toContain('src="https://blob.test/a"');
    expect(saved[0]).toContain('data-attachment-id="id-b"');
    expect(saved[0]).not.toContain("kazan-pending:");
  });

  it("saves the uploaded images without the failed ones when an upload fails", async () => {
    const failure = new ApiError("offline", { kind: "network" });
    const saved: string[] = [];
    const outcome = await commitCreatedDescription({
      markdown: `${tag("a")}\n\n${tag("b")}\n\nText`,
      images: [pending("a"), pending("b")],
      upload: async (image) => {
        if (image.key === "b") throw failure;
        return stored(image.key);
      },
      save: async (description) => {
        saved.push(description);
      },
    });
    expect(outcome).toEqual({ imageError: failure, saveError: null });
    expect(saved).toEqual([
      `<img src="https://blob.test/a" alt="a.png" width="40" height="30" loading="lazy" decoding="async" data-attachment-id="id-a">\n\nText`,
    ]);
  });

  it("saves the text without images when a pending tag has no file", async () => {
    const saved: string[] = [];
    const outcome = await commitCreatedDescription({
      markdown: `Text\n\n${tag("lost")}`,
      images: [],
      upload: async (image) => stored(image.key),
      save: async (description) => {
        saved.push(description);
      },
    });
    expect(outcome.imageError).toBeInstanceOf(MissingPendingImageError);
    expect(outcome.saveError).toBeNull();
    expect(saved).toEqual(["Text"]);
  });

  it("refuses links that pass the limit and saves the text without them", async () => {
    const filler: string = "x".repeat(9_900);
    const saved: string[] = [];
    const outcome = await commitCreatedDescription({
      markdown: `${filler}\n\n${tag("a")}`,
      images: [pending("a")],
      upload: async () => ({ id: "id-a", url: `https://blob.test/${"u".repeat(200)}` }),
      save: async (description) => {
        saved.push(description);
      },
    });
    expect(outcome.imageError).toBeInstanceOf(DescriptionTooLongError);
    expect(saved).toEqual([filler]);
  });

  it("reports a failed save beside the image result", async () => {
    const failure = new ApiError("refused", { status: 403, kind: "forbidden" });
    const outcome = await commitCreatedDescription({
      markdown: tag("a"),
      images: [pending("a")],
      upload: async () => stored("a"),
      save: async () => {
        throw failure;
      },
    });
    expect(outcome).toEqual({ imageError: null, saveError: failure });
  });
});

describe("newTicketResultKey", () => {
  it("names what the new ticket lacks", () => {
    expect(newTicketResultKey(false, false)).toBe("newTicketCreated");
    expect(newTicketResultKey(true, false)).toBe("newTicketCreatedImagesFailed");
    expect(newTicketResultKey(false, true)).toBe("newTicketCreatedAttachmentsFailed");
    expect(newTicketResultKey(true, true)).toBe("newTicketCreatedImagesAndAttachmentsFailed");
  });
});

describe("imageFailureMessage", () => {
  it("explains a lost file, a text over the limit and an upload refusal", () => {
    expect(imageFailureMessage(new MissingPendingImageError(["kazan-pending:a"]))).toEqual({
      key: "descriptionImageMissing",
      params: {},
    });
    expect(imageFailureMessage(new DescriptionTooLongError(10_200))).toEqual({
      key: "ticketDescriptionTooLongAfterImages",
      params: { max: 10_000 },
    });
    expect(imageFailureMessage(new ApiError("offline", { kind: "network" }))).toEqual({
      key: "ticketAttachmentsNetwork",
      params: {},
    });
    expect(imageFailureMessage(new Error("boom"))).toEqual({
      key: "ticketAttachmentsUploadFailed",
      params: {},
    });
  });
});
