package milestone

import (
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

const (
	boardKagi   = "11111111-1111-4111-8111-111111111111"
	boardMobile = "22222222-2222-4222-8222-222222222222"
	idBetaKagi  = "aaaaaaaa-0000-4000-8000-000000000001"
	idBetaKagi2 = "aaaaaaaa-0000-4000-8000-000000000002"
	idBetaMob   = "aaaaaaaa-0000-4000-8000-000000000003"
	idGA        = "aaaaaaaa-0000-4000-8000-000000000004"
	idDeleted   = "aaaaaaaa-0000-4000-8000-000000000009"
)

// sampleMilestones has a name shared by two milestones of one board and by a
// milestone of a related board, which the server allows.
func sampleMilestones() []client.Milestone {
	return []client.Milestone{
		{ID: idBetaKagi, Name: "Beta", TargetDate: "2026-11-01", BoardID: boardKagi, BoardName: "Kagi", BoardPrefix: "KAGI"},
		{ID: idBetaKagi2, Name: "beta", TargetDate: "2027-01-15", BoardID: boardKagi, BoardName: "Kagi", BoardPrefix: "KAGI"},
		{ID: idBetaMob, Name: "Beta", TargetDate: "2026-12-01", BoardID: boardMobile, BoardName: "Kagi Mobile", BoardPrefix: "KAGIM"},
		{ID: idGA, Name: "GA", TargetDate: "2027-03-01", BoardID: boardKagi, BoardName: "Kagi", BoardPrefix: "KAGI"},
	}
}

func TestMatchByID(t *testing.T) {
	m, err := match(sampleMilestones(), boardKagi, strings.ToUpper(idBetaKagi2))
	if err != nil || m.ID != idBetaKagi2 {
		t.Fatalf("got %+v, %v; want the milestone with id %s even though its name is shared", m, err, idBetaKagi2)
	}
}

func TestMatchUnknownIDIsPassedThrough(t *testing.T) {
	m, err := match(sampleMilestones(), boardKagi, idDeleted)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if m.ID != idDeleted || m.BoardID != boardKagi {
		t.Errorf("got %+v, want the id on the named board so that restore can address it", m)
	}
}

func TestMatchByUniqueName(t *testing.T) {
	m, err := match(sampleMilestones(), boardKagi, "  ga ")
	if err != nil || m.ID != idGA {
		t.Fatalf("got %+v, %v; want GA matched case-insensitively", m, err)
	}
}

func TestMatchAmbiguousNameListsEveryMatch(t *testing.T) {
	_, err := match(sampleMilestones(), boardKagi, "BETA")
	if err == nil {
		t.Fatal("want an error for a shared name")
	}

	msg := err.Error()
	for _, want := range []string{
		"3 milestones are named \"BETA\"",
		"pass the milestone id",
		idBetaKagi + "  Beta  target 2026-11-01  board Kagi (KAGI)",
		idBetaKagi2 + "  beta  target 2027-01-15  board Kagi (KAGI)",
		idBetaMob + "  Beta  target 2026-12-01  board Kagi Mobile (KAGIM)",
	} {
		if !strings.Contains(msg, want) {
			t.Errorf("error message misses %q:\n%s", want, msg)
		}
	}
	if strings.Contains(msg, idGA) {
		t.Errorf("error message lists a milestone of another name:\n%s", msg)
	}
}

func TestMatchNotFound(t *testing.T) {
	_, err := match(sampleMilestones(), boardKagi, "Launch")
	if err == nil || !strings.Contains(err.Error(), `milestone "Launch" not found`) || !strings.Contains(err.Error(), "GA") {
		t.Errorf("got %v, want a not-found error that lists the available names", err)
	}

	_, err = match(nil, boardKagi, "Launch")
	if err == nil || !strings.Contains(err.Error(), "no milestones on this board") {
		t.Errorf("got %v, want the no-milestones error", err)
	}
}

func TestMatchEmptyArg(t *testing.T) {
	if _, err := match(sampleMilestones(), boardKagi, "   "); err == nil {
		t.Error("want an error for an empty milestone argument")
	}
}

func TestBoardLabel(t *testing.T) {
	cases := []struct {
		m    client.Milestone
		want string
	}{
		{client.Milestone{BoardID: boardKagi, BoardName: "Kagi", BoardPrefix: "KAGI"}, "Kagi (KAGI)"},
		{client.Milestone{BoardID: boardKagi, BoardName: "Kagi"}, "Kagi"},
		{client.Milestone{BoardID: boardKagi}, boardKagi},
	}
	for _, tc := range cases {
		if got := BoardLabel(tc.m); got != tc.want {
			t.Errorf("BoardLabel(%+v) = %q, want %q", tc.m, got, tc.want)
		}
	}
}
