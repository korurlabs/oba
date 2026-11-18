package board

import "testing"

func TestFlattenIsDepthFirst(t *testing.T) {
	flat := Flatten(readTree(t))
	if len(flat) != 4 || flat[0].ID != idOps || flat[1].ID != idInfra || flat[1].Depth != 1 || flat[2].ID != idKagi {
		t.Fatalf("flat = %+v", flat)
	}
	if got := TreeLabel("Infra", 1); got != "  └─ Infra" {
		t.Errorf("label = %q", got)
	}
	if got := TreeLabel("Deep", 2); got != "     └─ Deep" {
		t.Errorf("label = %q", got)
	}
}

func TestLocateFindsParent(t *testing.T) {
	node, parent, found := Locate(readTree(t), idInfra)
	if !found || node.Name != "Infra" || parent == nil || parent.ID != idOps {
		t.Fatalf("node %+v parent %+v found %v", node, parent, found)
	}
	if _, parent, found := Locate(readTree(t), idKagi); !found || parent != nil {
		t.Error("a top-level board has no parent")
	}
	if _, _, found := Locate(readTree(t), "missing"); found {
		t.Error("found a missing board")
	}
}
