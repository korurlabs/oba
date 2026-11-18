import { describe, expect, test } from "bun:test";
import { QueryClient } from "@tanstack/react-query";
import { RELATION_KEYS } from "../hooks/query-keys";
import type { PipelineStage, RelationListItem } from "../schemas";
import {
  applyBattlefieldMove,
  BATTLEFIELD_COLUMNS,
  battlefieldCardLinkId,
  battlefieldDropRefusal,
  battlefieldStageOf,
  battlefieldStageSelectId,
  battlefieldTypeOf,
  LEAD_CARD_STAGES,
  columnValueTotal,
  groupBattlefieldByStage,
  rollbackBattlefieldMove,
  stageSelectFocusTarget,
  type BattlefieldResponse,
} from "./battlefield";

const ORG = "org-1";

function lead(id: string, name: string, stage: PipelineStage | null): RelationListItem {
  return {
    id,
    name,
    website: null,
    type: "LEAD",
    stage,
    owner: null,
    mainPersonContactName: null,
    mainPersonContactEmail: null,
    mainBusinessContactEmail: null,
    expectedValue: null,
    updatedAt: "2026-11-09T10:00:00Z",
  };
}

function customer(id: string, name: string, stage: PipelineStage): RelationListItem {
  return { ...lead(id, name, stage), type: "CUSTOMER" };
}

function battlefieldWith(...leads: RelationListItem[]): QueryClient {
  const queryClient = new QueryClient();
  const battlefield: BattlefieldResponse = { success: true, message: "", data: leads };
  queryClient.setQueryData(RELATION_KEYS.battlefield(ORG), battlefield);
  return queryClient;
}

describe("BATTLEFIELD_COLUMNS", () => {
  test("every stage, open ones first, then the customers, then the inactive leads", () => {
    expect([...BATTLEFIELD_COLUMNS]).toEqual([
      "NEW",
      "QUALIFIED",
      "CONTACTED",
      "WARM",
      "PROPOSAL",
      "CLOSED",
      "DIRECT",
      "LOST",
      "PARKED",
    ]);
  });
});

describe("LEAD_CARD_STAGES", () => {
  test("every lead stage and CLOSED, never DIRECT", () => {
    expect([...LEAD_CARD_STAGES]).toEqual([
      "NEW",
      "QUALIFIED",
      "CONTACTED",
      "WARM",
      "PROPOSAL",
      "CLOSED",
      "LOST",
      "PARKED",
    ]);
  });
});

describe("battlefieldDropRefusal", () => {
  test("a lead may go to any lead stage and to CLOSED", () => {
    for (const stage of LEAD_CARD_STAGES) {
      expect(battlefieldDropRefusal(lead("a", "Acme", "NEW"), stage)).toBeNull();
    }
  });

  test("a lead may not go to DIRECT", () => {
    expect(battlefieldDropRefusal(lead("a", "Acme", "WARM"), "DIRECT")).toBe("directNotForLead");
  });

  test("a customer goes nowhere", () => {
    expect(battlefieldDropRefusal(customer("a", "Acme", "CLOSED"), "NEW")).toBe("notLead");
    expect(battlefieldDropRefusal(customer("a", "Acme", "DIRECT"), "CLOSED")).toBe("notLead");
  });
});

describe("battlefieldTypeOf", () => {
  test("a customer stage gives CUSTOMER, a lead stage LEAD", () => {
    expect(battlefieldTypeOf("CLOSED")).toBe("CUSTOMER");
    expect(battlefieldTypeOf("DIRECT")).toBe("CUSTOMER");
    expect(battlefieldTypeOf("PROPOSAL")).toBe("LEAD");
    expect(battlefieldTypeOf("LOST")).toBe("LEAD");
  });
});

describe("groupBattlefieldByStage", () => {
  test("keeps the server's name order in each column and skips relations without a stage", () => {
    const columns = groupBattlefieldByStage([
      lead("a", "Acme", "NEW"),
      lead("b", "Beta", "WARM"),
      lead("c", "Core", "NEW"),
      lead("d", "Delta", null),
    ]);
    expect(columns.get("NEW")?.map((item) => item.id)).toEqual(["a", "c"]);
    expect(columns.get("WARM")?.map((item) => item.id)).toEqual(["b"]);
    expect([...columns.keys()]).toEqual(["NEW", "WARM"]);
  });

  test("puts customers in their stage column", () => {
    const columns = groupBattlefieldByStage([
      customer("a", "Acme", "CLOSED"),
      customer("b", "Beta", "DIRECT"),
    ]);
    expect(columns.get("CLOSED")?.map((item) => item.id)).toEqual(["a"]);
    expect(columns.get("DIRECT")?.map((item) => item.id)).toEqual(["b"]);
  });
});

describe("optimistic move", () => {
  test("moves the card at once and keeps its place in the list", () => {
    const queryClient = battlefieldWith(lead("a", "Acme", "NEW"), lead("b", "Beta", "NEW"));
    const context = applyBattlefieldMove(queryClient, ORG, "a", "WARM");

    expect(context).toEqual({ relationId: "a", previousStage: "NEW", stage: "WARM" });
    const battlefield = queryClient.getQueryData<BattlefieldResponse>(
      RELATION_KEYS.battlefield(ORG)
    );
    expect(battlefield?.data?.map((item) => [item.id, item.stage])).toEqual([
      ["a", "WARM"],
      ["b", "NEW"],
    ]);
  });

  test("does nothing for the same stage or a card not on the battlefield", () => {
    const queryClient = battlefieldWith(lead("a", "Acme", "NEW"));
    expect(applyBattlefieldMove(queryClient, ORG, "a", "NEW")).toBeNull();
    expect(applyBattlefieldMove(queryClient, ORG, "missing", "WARM")).toBeNull();
    expect(battlefieldStageOf(queryClient, ORG, "a")).toBe("NEW");
  });

  test("a move to CLOSED makes the card a customer at once", () => {
    const queryClient = battlefieldWith(lead("a", "Acme", "PROPOSAL"));
    applyBattlefieldMove(queryClient, ORG, "a", "CLOSED");
    const battlefield = queryClient.getQueryData<BattlefieldResponse>(
      RELATION_KEYS.battlefield(ORG)
    );
    expect(battlefield?.data?.map((item) => [item.stage, item.type])).toEqual([
      ["CLOSED", "CUSTOMER"],
    ]);
  });

  test("does nothing when the battlefield is not cached", () => {
    expect(applyBattlefieldMove(new QueryClient(), ORG, "a", "WARM")).toBeNull();
  });
});

describe("rollback", () => {
  test("a refused move puts the card back", () => {
    const queryClient = battlefieldWith(lead("a", "Acme", "NEW"));
    const context = applyBattlefieldMove(queryClient, ORG, "a", "PROPOSAL");
    if (context === null) throw new Error("the move was not applied");

    rollbackBattlefieldMove(queryClient, ORG, context);
    expect(battlefieldStageOf(queryClient, ORG, "a")).toBe("NEW");
  });

  test("a refused move to CLOSED puts the lead back as a lead", () => {
    const queryClient = battlefieldWith(lead("a", "Acme", "PROPOSAL"));
    const context = applyBattlefieldMove(queryClient, ORG, "a", "CLOSED");
    if (context === null) throw new Error("the move was not applied");

    rollbackBattlefieldMove(queryClient, ORG, context);
    const battlefield = queryClient.getQueryData<BattlefieldResponse>(
      RELATION_KEYS.battlefield(ORG)
    );
    expect(battlefield?.data?.map((item) => [item.stage, item.type])).toEqual([
      ["PROPOSAL", "LEAD"],
    ]);
  });

  test("keeps a move of another card made in the meantime", () => {
    const queryClient = battlefieldWith(lead("a", "Acme", "NEW"), lead("b", "Beta", "NEW"));
    const first = applyBattlefieldMove(queryClient, ORG, "a", "WARM");
    applyBattlefieldMove(queryClient, ORG, "b", "CONTACTED");
    if (first === null) throw new Error("the move was not applied");

    rollbackBattlefieldMove(queryClient, ORG, first);
    expect(battlefieldStageOf(queryClient, ORG, "a")).toBe("NEW");
    expect(battlefieldStageOf(queryClient, ORG, "b")).toBe("CONTACTED");
  });

  test("keeps a later move of the same card", () => {
    const queryClient = battlefieldWith(lead("a", "Acme", "NEW"));
    const first = applyBattlefieldMove(queryClient, ORG, "a", "WARM");
    applyBattlefieldMove(queryClient, ORG, "a", "PROPOSAL");
    if (first === null) throw new Error("the move was not applied");

    rollbackBattlefieldMove(queryClient, ORG, first);
    expect(battlefieldStageOf(queryClient, ORG, "a")).toBe("PROPOSAL");
  });
});

describe("stageSelectFocusTarget", () => {
  const move = { relationId: "a", stage: "WARM" as PipelineStage };

  test("waits while the battlefield still shows the card at its old stage", () => {
    expect(stageSelectFocusTarget([lead("a", "Acme", "NEW")], move)).toBe("wait");
  });

  test("the card's select once it stands in its new column", () => {
    expect(stageSelectFocusTarget([lead("a", "Acme", "WARM")], move)).toBe("select");
    const lost = { relationId: "a", stage: "LOST" as PipelineStage };
    expect(stageSelectFocusTarget([lead("a", "Acme", "LOST")], lost)).toBe("select");
  });

  test("the card's name link once a move to CLOSED made it a customer", () => {
    const closed = { relationId: "a", stage: "CLOSED" as PipelineStage };
    expect(stageSelectFocusTarget([customer("a", "Acme", "CLOSED")], closed)).toBe("link");
  });

  test("nothing when the card left the battlefield", () => {
    expect(stageSelectFocusTarget([lead("b", "Beta", "WARM")], move)).toBe("none");
  });
});

describe("battlefieldStageSelectId", () => {
  test("is unique per relation", () => {
    expect(battlefieldStageSelectId("a")).not.toBe(battlefieldStageSelectId("b"));
  });
});

describe("battlefieldCardLinkId", () => {
  test("is unique per relation and differs from the select's id", () => {
    expect(battlefieldCardLinkId("a")).not.toBe(battlefieldCardLinkId("b"));
    expect(battlefieldCardLinkId("a")).not.toBe(battlefieldStageSelectId("a"));
  });
});

describe("columnValueTotal", () => {
  test("null when no lead in the column has an expected value", () => {
    expect(columnValueTotal([])).toBeNull();
    expect(columnValueTotal([lead("a", "Acme", "NEW")])).toBeNull();
  });

  test("the sum of the values that are set, without float drift", () => {
    const priced = (id: string, value: number | null): RelationListItem => ({
      ...lead(id, id, "NEW"),
      expectedValue: value,
    });
    expect(columnValueTotal([priced("a", 0.1), priced("b", null), priced("c", 0.2)])).toBe(0.3);
    expect(columnValueTotal([priced("a", 1500), priced("b", 249.99)])).toBe(1749.99);
  });
});
