package page

import (
	"net/http"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
)

func TestPageDeleteWithYes(t *testing.T) {
	api := newPageAPI(t, false)
	api.Respond("GET "+pagesPath, http.StatusOK, treeJSON)
	api.Respond("DELETE "+pagePath, http.StatusOK, `{"message":"deleted"}`)

	out, _, err := runCLI(t, "", "kalkan", "page", "delete", "engineering/plans/", "--yes")
	if err != nil {
		t.Fatal(err)
	}
	if out != "Deleted page engineering/plans and every page below it\n" {
		t.Errorf("out = %q", out)
	}
	if got := strings.Join(api.Calls(), ";"); got != "GET "+pagesPath+";DELETE "+pagePath {
		t.Errorf("calls = %v", got)
	}
}

func TestPageDeleteWithoutTerminalNeedsYes(t *testing.T) {
	api := newPageAPI(t, false)
	_, _, err := runCLI(t, "", "kalkan", "page", "delete", "engineering/plans")
	if err != terminal.ErrDeleteNeedsYes {
		t.Fatalf("err = %v", err)
	}
	noCalls(t, api)
}

func TestPageDeleteAsksAndConfirms(t *testing.T) {
	api := newPageAPI(t, true)
	api.Respond("GET "+pagesPath, http.StatusOK, treeJSON)
	api.Respond("DELETE "+pagePath, http.StatusOK, `{"message":"deleted"}`)

	out, _, err := runCLI(t, "y\n", "kalkan", "page", "delete", "engineering/plans")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(out, "Delete page engineering/plans and every page below it?") ||
		!strings.HasSuffix(out, "Deleted page engineering/plans and every page below it\n") {
		t.Errorf("out = %q", out)
	}
}

func TestPageDeleteAsksAndCancels(t *testing.T) {
	api := newPageAPI(t, true)
	api.Respond("GET "+pagesPath, http.StatusOK, treeJSON)

	out, _, err := runCLI(t, "n\n", "kalkan", "page", "delete", "engineering/plans")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(out, "Delete page engineering/plans and every page below it?") || !strings.HasSuffix(out, "Cancelled.\n") {
		t.Errorf("out = %q", out)
	}
	for _, call := range api.Calls() {
		if strings.HasPrefix(call, "DELETE") {
			t.Errorf("deleted: %v", call)
		}
	}
}

// With --json, a cancelled delete gives null, not the tree read to find the page.
func TestPageDeleteCancelledJSONIsNull(t *testing.T) {
	api := newPageAPI(t, true)
	api.Respond("GET "+pagesPath, http.StatusOK, treeJSON)
	buf := captureJSON(t)

	if _, _, err := runCLI(t, "n\n", "kalkan", "page", "delete", "engineering/plans"); err != nil {
		t.Fatal(err)
	}
	if got := flushJSON(t, buf); got != "null" {
		t.Errorf("result = %q, want null", got)
	}
}

func TestPageDeleteUnknownPathExits2(t *testing.T) {
	api := newPageAPI(t, false)
	api.Respond("GET "+pagesPath, http.StatusOK, treeJSON)
	_, _, err := runCLI(t, "", "kalkan", "page", "delete", "engineering/nope", "--yes")
	if client.ExitCode(err) != 2 || !strings.Contains(err.Error(), "No page at engineering/nope") {
		t.Fatalf("err = %v, exit %d", err, client.ExitCode(err))
	}
}
