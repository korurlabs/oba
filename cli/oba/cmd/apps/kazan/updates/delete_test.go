package updates

import (
	"errors"
	"net/http"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
)

func TestDeleteNeedsYesWithoutATerminal(t *testing.T) {
	api := newAPI(t)
	_, _, err := run(t, "", "delete", idUpdate)
	if !errors.Is(err, terminal.ErrDeleteNeedsYes) || len(api.Calls()) != 0 {
		t.Fatalf("err = %v calls %v", err, api.Calls())
	}
}

func TestDeleteWithYesDeletes(t *testing.T) {
	api := newAPI(t)
	api.Respond("DELETE "+updatePath, http.StatusOK, `{"data":null,"message":"Deleted"}`)
	out, _, err := run(t, "", "delete", idUpdate, "--yes")
	if err != nil {
		t.Fatal(err)
	}
	if out != "Deleted update "+idUpdate+"\n" || len(writes(api)) != 1 {
		t.Errorf("out %q writes %v", out, writes(api))
	}
}

func TestDeleteOnATerminalAnsweredNoDeletesNothing(t *testing.T) {
	api := newAPI(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, true)
	out, _, err := run(t, "n\n", "delete", idUpdate)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.HasSuffix(out, "Cancelled.\n") || len(api.Calls()) != 0 {
		t.Errorf("out %q calls %v", out, api.Calls())
	}
}
