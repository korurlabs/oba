package page

import (
	"encoding/json"
	"net/http"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

// servePage serves the tree and the detail of engineering/plans.
func servePage(api *clitest.MockAPI) {
	api.Respond("GET "+pagesPath, http.StatusOK, treeJSON)
	api.Respond("GET "+pagePath, http.StatusOK, detailJSON)
}

func TestPageGetPrintsEveryTranslation(t *testing.T) {
	api := newPageAPI(t, false)
	servePage(api)

	out, _, err := runCLI(t, "", "kalkan", "page", "get", "engineering/plans")
	if err != nil {
		t.Fatal(err)
	}
	want := "engineering/plans (version 3, your level EDIT)\n" +
		"\n== TR: Planlar (updated 2026-11-24T09:00:00Z by Ada)\n\n# Planlar\n" +
		"\n== EN: Plans (updated 2026-11-25T10:00:00Z by Bo)\n\n# Plans\n"
	if out != want {
		t.Errorf("out =\n%q\nwant\n%q", out, want)
	}
	if got := strings.Join(api.Calls(), ";"); got != "GET "+pagesPath+";GET "+pagePath {
		t.Errorf("calls = %v", got)
	}
}

func TestPageGetLangPrintsTheRawBody(t *testing.T) {
	api := newPageAPI(t, false)
	servePage(api)

	out, _, err := runCLI(t, "", "kalkan", "page", "get", "engineering/plans", "--lang", "tr")
	if err != nil {
		t.Fatal(err)
	}
	if out != "# Planlar\n" {
		t.Errorf("out = %q", out)
	}
}

func TestPageGetJSONIsTheDetail(t *testing.T) {
	api := newPageAPI(t, false)
	servePage(api)
	buf := captureJSON(t)

	if _, _, err := runCLI(t, "", "kalkan", "page", "get", "engineering/plans"); err != nil {
		t.Fatal(err)
	}
	var page client.KalkanPageDetail
	if err := json.Unmarshal([]byte(flushJSON(t, buf)), &page); err != nil {
		t.Fatal(err)
	}
	if page.ID != pageID || page.Version != 3 || len(page.Translations) != 2 {
		t.Errorf("json page = %+v", page)
	}
}

func TestPageGetUnknownLanguageFailsBeforeAnyCall(t *testing.T) {
	api := newPageAPI(t, false)
	_, _, err := runCLI(t, "", "kalkan", "page", "get", "engineering/plans", "--lang", "de")
	if err == nil || !strings.Contains(err.Error(), `unknown language "de"`) {
		t.Fatalf("err = %v", err)
	}
	noCalls(t, api)
}

func TestPageGetUnknownPathExits2(t *testing.T) {
	api := newPageAPI(t, false)
	api.Respond("GET "+pagesPath, http.StatusOK, treeJSON)
	_, _, err := runCLI(t, "", "kalkan", "page", "get", "engineering/nope")
	if client.ExitCode(err) != 2 || !strings.Contains(err.Error(), "KLK_NOT_001") {
		t.Errorf("err = %v, exit %d", err, client.ExitCode(err))
	}
}
