package page

import (
	"encoding/json"
	"net/http"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

func TestPageListPrintsTheTree(t *testing.T) {
	api := newPageAPI(t, false)
	api.Respond("GET "+pagesPath, http.StatusOK, treeJSON)

	out, _, err := runCLI(t, "", "kalkan", "page", "list")
	if err != nil {
		t.Fatal(err)
	}
	want := "engineering  Engineering  (EDIT)\n" +
		"  engineering/plans  Planlar  [compliance]\n" +
		"    engineering/plans/q4\n"
	if out != want {
		t.Errorf("out =\n%s\nwant\n%s", out, want)
	}
	if req := api.Request("GET " + pagesPath); req.Org != clitest.OrgIDAcme {
		t.Errorf("org = %q", req.Org)
	}
}

func TestPageListOfPathPrintsTheSubtree(t *testing.T) {
	api := newPageAPI(t, false)
	api.Respond("GET "+pagesPath, http.StatusOK, treeJSON)
	buf := captureJSON(t)

	out, _, err := runCLI(t, "", "kalkan", "page", "list", "/engineering/plans/")
	if err != nil {
		t.Fatal(err)
	}
	if out != "engineering/plans  Planlar  [compliance]\n  engineering/plans/q4\n" {
		t.Errorf("out = %q", out)
	}
	var pages []client.KalkanPageListItem
	if err := json.Unmarshal([]byte(flushJSON(t, buf)), &pages); err != nil {
		t.Fatal(err)
	}
	if len(pages) != 2 || pages[0].Path != "engineering/plans" || pages[1].Path != "engineering/plans/q4" {
		t.Errorf("json pages = %+v", pages)
	}
}

func TestPageListUnknownPathExits2(t *testing.T) {
	api := newPageAPI(t, false)
	api.Respond("GET "+pagesPath, http.StatusOK, treeJSON)
	_, _, err := runCLI(t, "", "kalkan", "page", "list", "engineering/nope")
	if client.ExitCode(err) != 2 || !strings.Contains(err.Error(), "No page at engineering/nope") {
		t.Errorf("err = %v, exit %d", err, client.ExitCode(err))
	}
}

func TestPageListInvalidPathFailsBeforeAnyCall(t *testing.T) {
	api := newPageAPI(t, false)
	_, _, err := runCLI(t, "", "kalkan", "page", "list", "a/-b")
	if err == nil || client.ExitCode(err) != 1 || !strings.Contains(err.Error(), "is not a valid slug") {
		t.Fatalf("err = %v", err)
	}
	noCalls(t, api)
}
