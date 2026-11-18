package label

import (
	"bytes"
	"errors"
	"net/http"
	"strings"
	"testing"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/cmd/apps/kazan/boardflag"
	"github.com/korurlabs/oba-cli/internal/apps/kazan/board"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
	"github.com/korurlabs/oba-cli/internal/dojo/jsonout"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
)

const (
	idOps      = "0a000000-0000-4000-8000-000000000001"
	labelsPath = "/kazan/boards/" + idOps + "/labels"
	treeJSON   = `{"data":[{"id":"` + idOps + `","name":"Operations","prefix":"OPS","childBoards":[]}],` +
		`"pagination":{"currentPage":1,"last":true}}`
	labelsJSON = `{"data":[{"id":"l1","name":"bug","color":"#EF4444"},{"id":"l2","name":"chore","color":null}]}`
)

func newRoot() *cobra.Command {
	group := &cobra.Command{Use: "kazan"}
	boardflag.Add(group)
	group.AddCommand(NewCmd())
	root := &cobra.Command{Use: "oba", SilenceUsage: true, SilenceErrors: true}
	root.AddCommand(group)
	return root
}

func run(t *testing.T, stdin string, args ...string) (string, string, error) {
	t.Helper()
	return clitest.RunCLILines(t, newRoot(), stdin, append([]string{"kazan", "label"}, args...)...)
}

func newAPI(t *testing.T) *clitest.MockAPI {
	t.Helper()
	clitest.SetupHome(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, false)
	api := clitest.NewMockAPI(t)
	api.Respond("GET /kazan/boards", http.StatusOK, treeJSON)
	api.Respond("GET "+labelsPath, http.StatusOK, labelsJSON)
	clitest.LoginTo(t, api.URL(), clitest.OrgIDAcme)
	return api
}

func TestLabelListPrintsTheTable(t *testing.T) {
	api := newAPI(t)
	out, _, err := run(t, "", "list", "--board", "ops")
	if err != nil {
		t.Fatal(err)
	}
	lines := strings.Split(strings.TrimSpace(out), "\n")
	if len(lines) != 3 || strings.Join(strings.Fields(lines[1]), " ") != "bug #EF4444 l1" ||
		strings.Join(strings.Fields(lines[2]), " ") != "chore - l2" {
		t.Errorf("out =\n%s", out)
	}
	if strings.Join(api.Calls(), ",") != "GET /kazan/boards,GET "+labelsPath {
		t.Errorf("calls = %v", api.Calls())
	}
}

func TestLabelListJSONIsTheLabels(t *testing.T) {
	newAPI(t)
	var buf bytes.Buffer
	jsonout.Start(&buf)
	t.Cleanup(jsonout.Stop)
	if _, _, err := run(t, "", "list", "--board", "OPS"); err != nil {
		t.Fatal(err)
	}
	if err := jsonout.Flush(); err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(buf.String(), `"name": "chore"`) || strings.Contains(buf.String(), "Operations") {
		t.Errorf("json =\n%s", buf.String())
	}
}

func TestLabelListWithoutBoard(t *testing.T) {
	newAPI(t)
	if _, _, err := run(t, "", "list"); !errors.Is(err, board.ErrNoBoard) {
		t.Fatalf("err = %v", err)
	}
}

func TestLabelCreateSendsTheColorOnlyWhenGiven(t *testing.T) {
	api := newAPI(t)
	api.Respond("POST "+labelsPath, http.StatusCreated, `{"data":{"id":"l3","name":"feat"}}`)
	out, _, err := run(t, "", "create", "--board", "OPS", "--name", "feat")
	if err != nil {
		t.Fatal(err)
	}
	if out != "Label created: feat (l3)\n" || api.Request("POST "+labelsPath).Body != `{"name":"feat"}` {
		t.Errorf("out %q body %s", out, api.Request("POST "+labelsPath).Body)
	}
}

func TestLabelCreateWithColor(t *testing.T) {
	api := newAPI(t)
	api.Respond("POST "+labelsPath, http.StatusCreated, `{"data":{"id":"l3","name":"feat"}}`)
	if _, _, err := run(t, "", "create", "--board", idOps, "--name", "feat", "--color", "#00FF00"); err != nil {
		t.Fatal(err)
	}
	if api.Request("POST "+labelsPath).Body != `{"name":"feat","color":"#00FF00"}` {
		t.Errorf("body %s", api.Request("POST "+labelsPath).Body)
	}
	// An id on --board is used without reading the board tree.
	if strings.Join(api.Calls(), ",") != "POST "+labelsPath {
		t.Errorf("calls = %v", api.Calls())
	}
}

func TestLabelUpdateSendsTheGivenFields(t *testing.T) {
	api := newAPI(t)
	api.Respond("PUT "+labelsPath+"/l1", http.StatusOK, `{"data":{"id":"l1"}}`)
	out, _, err := run(t, "", "update", "l1", "--board", "OPS", "--color", "#000000")
	if err != nil {
		t.Fatal(err)
	}
	if out != "Label l1 updated.\n" || api.Request("PUT "+labelsPath+"/l1").Body != `{"color":"#000000"}` {
		t.Errorf("out %q body %s", out, api.Request("PUT "+labelsPath+"/l1").Body)
	}
}

func TestLabelUpdateWithoutFieldsMakesNoCall(t *testing.T) {
	api := newAPI(t)
	_, _, err := run(t, "", "update", "l1", "--board", "OPS")
	if !errors.Is(err, terminal.ErrNothingToUpdate) || len(api.Calls()) != 0 {
		t.Fatalf("err = %v, calls = %v", err, api.Calls())
	}
}

func TestLabelDelete(t *testing.T) {
	api := newAPI(t)
	api.Respond("DELETE "+labelsPath+"/l1", http.StatusOK, `{"data":null}`)
	out, _, err := run(t, "", "delete", "l1", "--board", "OPS")
	if err != nil || out != "Label l1 deleted.\n" {
		t.Fatalf("out %q err %v", out, err)
	}
	clitest.FakeTerminal(t, &stdinIsTerminal, true)
	out, _, err = run(t, "no\n", "delete", "l2", "--board", "OPS")
	if err != nil || !strings.HasSuffix(out, "Cancelled.\n") {
		t.Fatalf("out %q err %v", out, err)
	}
}

func TestLabelDeleteForbiddenExitsThree(t *testing.T) {
	api := newAPI(t)
	api.Respond("DELETE "+labelsPath+"/l1", http.StatusForbidden, `{"error":{"code":"AUTH_403","message":"denied"}}`)
	_, _, err := run(t, "", "delete", "l1", "--board", "OPS")
	if client.ExitCode(err) != 3 {
		t.Fatalf("err = %v", err)
	}
}
