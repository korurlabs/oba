package board

import (
	"encoding/json"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

func readTree(t *testing.T) []client.Board {
	t.Helper()
	var resp client.APIResponse[[]client.Board]
	if err := json.Unmarshal([]byte(treeJSON), &resp); err != nil {
		t.Fatal(err)
	}
	return resp.Data
}

func TestFilterKeepsParentsOfAMatch(t *testing.T) {
	got := Filter(readTree(t), " INF")
	if len(got) != 2 || got[0].ID != idOps || len(got[0].ChildBoards) != 1 || got[1].ID != idOther {
		t.Fatalf("got %+v", got)
	}
	if len(Filter(readTree(t), "")) != 3 {
		t.Error("an empty search must keep every board")
	}
	if len(Filter(readTree(t), "zzz")) != 0 {
		t.Error("no match must keep nothing")
	}
}

func TestEncodeTreeKeepsEveryFieldAndOnlyKeptChildren(t *testing.T) {
	tree := Filter(readTree(t), "operations")
	items, err := EncodeTree(tree)
	if err != nil {
		t.Fatal(err)
	}
	if len(items) != 1 {
		t.Fatalf("items = %s", items)
	}
	var b map[string]json.RawMessage
	if err := json.Unmarshal(items[0], &b); err != nil {
		t.Fatal(err)
	}
	if string(b["color"]) != `"#ff0000"` || string(b["slug"]) != `"operations"` || string(b["childBoards"]) != `[]` {
		t.Errorf("board = %s", items[0])
	}
}
