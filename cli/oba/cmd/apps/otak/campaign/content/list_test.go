package content

import (
	"net/http"
	"strings"
	"testing"
	"time"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

func TestContentListPrintsTheAgenda(t *testing.T) {
	old := time.Local
	time.Local = time.UTC
	t.Cleanup(func() { time.Local = old })
	api := newContentAPI(t)
	api.Respond("GET "+contentsPath, http.StatusOK, `{"data":[`+
		`{"id":"k1","campaignId":"`+campaignID+`","legalEntityId":"`+legalEntityID+`","legalEntityShortName":"Korur B.V.",`+
		`"channel":"LINKEDIN","language":"NL","text":"Terwijl u slaapt,\nhouden wij de wacht. En nog veel meer tekst daarna.",`+
		`"status":"SCHEDULED","plannedAt":"2027-03-09T09:00:00Z","publishedAt":null,"postUrl":null},`+
		`{"id":"k2","campaignId":"`+campaignID+`","legalEntityId":"`+legalEntityID+`","legalEntityShortName":"Korur B.V.",`+
		`"channel":"LINKEDIN","language":"EN","text":"We keep watch.",`+
		`"status":"DRAFT","plannedAt":null,"publishedAt":null,"postUrl":null}]}`)
	out, _, err := runCLI(t, "", "otak", "campaign", "content", "list", campaignID)
	if err != nil {
		t.Fatal(err)
	}
	wantOut := "STATUS  CHANNEL  LANGUAGE  LEGAL ENTITY  PLANNED  PUBLISHED  TEXT  ID\n" +
		"SCHEDULED  LINKEDIN  NL  Korur B.V.  2027-03-09 09:00  -  Terwijl u slaapt, houden wij de wacht. E...  k1\n" +
		"DRAFT  LINKEDIN  EN  Korur B.V.  -  -  We keep watch.  k2\n"
	if normalizeSpaces(out) != normalizeSpaces(wantOut) {
		t.Errorf("out =\n%s", out)
	}
	if api.Request("GET "+contentsPath).Org != clitest.OrgIDAcme {
		t.Error("organization header missing")
	}
}

func TestContentListEmptyAndUnknownCampaign(t *testing.T) {
	api := newContentAPI(t)
	api.Respond("GET "+contentsPath, http.StatusOK, `{"data":[]}`)
	out, _, err := runCLI(t, "", "otak", "campaign", "content", "list", campaignID)
	if err != nil || out != "No contents.\n" {
		t.Fatalf("out = %q, err = %v", out, err)
	}

	api = newContentAPI(t)
	api.Respond("GET "+contentsPath, http.StatusNotFound, `{"error":{"code":"OTK_NOT_012","message":"Campaign not found"}}`)
	_, _, err = runCLI(t, "", "otak", "campaign", "content", "list", campaignID)
	if err == nil || err.Error() != "OTK_NOT_012: Campaign not found" || client.ExitCode(err) != 2 {
		t.Fatalf("err = %v, exit = %d", err, client.ExitCode(err))
	}
}

func normalizeSpaces(s string) string {
	lines := strings.Split(s, "\n")
	for i, line := range lines {
		lines[i] = strings.Join(strings.Fields(line), " ")
	}
	return strings.Join(lines, "\n")
}
