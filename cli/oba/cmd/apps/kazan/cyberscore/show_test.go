package cyberscore

import (
	"net/http"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

func TestShowPrintsScoreWithoutBoard(t *testing.T) {
	api := newAPI(t)
	api.Respond("GET /kazan/cyberscore", http.StatusOK, `{"data":{"scorePercentage":72,"scoreLevel":"LOW",`+
		`"breakdownByCategory":{"DATA":50,"IDENTITY":80},"scorableCheckCount":5,"lastCalculatedAt":"2026-11-25T10:00:00Z"}}`)
	out, _, err := runCLI(t, "", "kazan", "cyberscore", "show")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.HasPrefix(out, "Score:       72% (LOW)\n") || !strings.HasSuffix(out, "Breakdown:\n  IDENTITY  80%\n  DATA      50%\n") {
		t.Errorf("out =\n%s", out)
	}
	if org := api.Request("GET /kazan/cyberscore").Org; org != clitest.OrgIDAcme {
		t.Errorf("X-Organization-ID = %q", org)
	}
}

func TestShowJSONIsTheData(t *testing.T) {
	api := newAPI(t)
	api.Respond("GET /kazan/cyberscore", http.StatusOK, `{"data":{"scorePercentage":0,"scoreLevel":"UNKNOWN",`+
		`"breakdownByCategory":{},"scorableCheckCount":0,"lastCalculatedAt":null}}`)
	out, err := runJSON(t, "kazan", "cyberscore", "show")
	if err != nil || !strings.Contains(out, `"scoreLevel": "UNKNOWN"`) {
		t.Errorf("out %q err %v", out, err)
	}
}
