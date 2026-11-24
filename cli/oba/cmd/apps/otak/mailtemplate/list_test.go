package mailtemplate

import (
	"net/http"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

func TestMailTemplateListFiltersAndTable(t *testing.T) {
	api := clitest.NewSignedInAPI(t)
	var query string
	api.Handle("GET "+mailTemplatesPath, func(w http.ResponseWriter, r *http.Request) {
		query = r.URL.RawQuery
		clitest.WriteJSON(w, `{"data":[`+mailTemplateJSON("Intro", "EN", "Hello there", "")+`],"pagination":{"currentPage":1,"last":true}}`)
	})
	out, _, err := runCLI(t, "otak", "mail-template", "list", "--lang", "us", "--search", " intro ")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(query, "language=EN") || !strings.Contains(query, "search=intro") {
		t.Errorf("query = %q", query)
	}
	lines := strings.Split(strings.TrimSpace(out), "\n")
	if len(lines) != 2 || !strings.HasPrefix(lines[0], "NAME") || !strings.Contains(lines[1], "Hello there") ||
		!strings.Contains(lines[1], mailTemplateID) {
		t.Errorf("out =\n%s", out)
	}
}

func TestMailTemplateListEmpty(t *testing.T) {
	api := clitest.NewSignedInAPI(t)
	api.Respond("GET "+mailTemplatesPath, http.StatusOK, `{"data":[],"pagination":{"currentPage":1,"last":true}}`)
	out, _, err := runCLI(t, "otak", "mail-template", "list")
	if err != nil || out != "No mail templates.\n" {
		t.Fatalf("out %q, err %v", out, err)
	}
}
