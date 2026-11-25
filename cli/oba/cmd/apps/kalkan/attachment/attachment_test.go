package attachment

import (
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

func TestAttachmentNeedsPage(t *testing.T) {
	api := newAttachmentAPI(t, false)
	file := writeFile(t, t.TempDir(), "a.pdf", "%PDF-1.7")
	for _, args := range [][]string{
		{"kalkan", "attachment", "list"},
		{"kalkan", "attachment", "add", "--file", file},
		{"kalkan", "attachment", "delete", "--id", attachmentID, "--yes"},
	} {
		_, _, err := runCLI(t, "", args...)
		if err == nil || !strings.Contains(err.Error(), `"page" not set`) {
			t.Errorf("%v: err = %v", args, err)
		}
	}
	if got := api.Calls(); len(got) != 0 {
		t.Errorf("calls = %v", got)
	}
}

func TestAttachmentRefusesAnInvalidPathBeforeAnyCall(t *testing.T) {
	api := newAttachmentAPI(t, false)
	file := writeFile(t, t.TempDir(), "a.pdf", "%PDF-1.7")
	for _, args := range [][]string{
		{"kalkan", "attachment", "list", "--page", "/"},
		{"kalkan", "attachment", "add", "--page", "engineering/-bad", "--file", file},
		{"kalkan", "attachment", "delete", "--page", "a/b/c/d/e/f/g/h/i/j/k", "--id", attachmentID, "--yes"},
	} {
		_, _, err := runCLI(t, "", args...)
		if err == nil || client.ExitCode(err) != 1 {
			t.Errorf("%v: err = %v", args, err)
		}
	}
	if got := api.Calls(); len(got) != 0 {
		t.Errorf("calls = %v", got)
	}
}

func TestAttachmentOfAnUnknownPageExits2(t *testing.T) {
	api := newAttachmentAPI(t, false)
	_, _, err := runCLI(t, "", "kalkan", "attachment", "list", "--page", "engineering/nope")
	if err == nil || client.ExitCode(err) != 2 || !strings.Contains(err.Error(), "engineering/nope") {
		t.Fatalf("err = %v", err)
	}
	if got := api.Calls(); len(got) != 1 || got[0] != "GET "+pagesPath {
		t.Errorf("calls = %v", got)
	}
	for _, r := range api.Requests() {
		if r.Path != "/v3/api-docs" && r.Org != clitest.OrgIDAcme {
			t.Errorf("%s %s sent X-Organization-ID %q", r.Method, r.Path, r.Org)
		}
	}
}
