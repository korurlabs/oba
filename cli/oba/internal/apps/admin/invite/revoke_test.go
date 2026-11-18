package invite

import (
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

func TestFind(t *testing.T) {
	invs := []client.Invitation{{ID: "aaaa-1", Email: "a@x"}, {ID: "bbbb-2", Email: "b@x"}}
	if inv, ok := Find(invs, "BBBB-2"); !ok || inv.Email != "b@x" {
		t.Errorf("Find = %v, %v", inv, ok)
	}
	if _, ok := Find(invs, "cccc-3"); ok {
		t.Error("Find found an absent id")
	}
}
