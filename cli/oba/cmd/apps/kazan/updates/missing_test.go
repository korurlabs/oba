package updates

import (
	"net/http"
	"net/url"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

const missingJSON = `{"data":{"organizationId":"` + clitest.OrgIDAcme + `","date":"2026-10-16","deadline":"2026-10-16T07:30:00Z",` +
	`"workday":true,"deadlinePassed":true,"expectedCount":4,"members":[` +
	`{"userId":"u2","email":"bob@x.io","firstName":"Bob","lastName":"B"},` +
	`{"userId":"u3","email":"eve@x.io","postedAt":"2026-10-16T08:05:00Z"}]}}`

func TestMissingPrintsTheReportOfTheCurrentOrganization(t *testing.T) {
	api := newAPI(t)
	var query string
	queryOf(api, "GET "+updatesPath+"/missing", missingJSON, &query)
	out, _, err := run(t, "", "missing", "--date", "2026-10-16")
	if err != nil {
		t.Fatal(err)
	}
	q, _ := url.ParseQuery(query)
	if q.Get("organizationId") != clitest.OrgIDAcme || q.Get("date") != "2026-10-16" {
		t.Errorf("query = %s", query)
	}
	for _, part := range []string{"Friday 2026-10-16, deadline 10:30 Europe/Istanbul: 2 of 4 have not posted",
		"NAME", "Bob B", "missing", "eve@x.io", "late 11:05"} {
		if !strings.Contains(out, part) {
			t.Errorf("out missing %q:\n%s", part, out)
		}
	}
}

func TestMissingWithoutDateSendsNoDate(t *testing.T) {
	api := newAPI(t)
	var query string
	queryOf(api, "GET "+updatesPath+"/missing", `{"data":{"date":"2026-10-17","deadline":"2026-10-17T07:30:00Z",`+
		`"workday":true,"deadlinePassed":false,"expectedCount":2,"members":[]}}`, &query)
	out, _, err := run(t, "", "missing")
	if err != nil {
		t.Fatal(err)
	}
	if query != "organizationId="+clitest.OrgIDAcme {
		t.Errorf("query = %s", query)
	}
	if !strings.Contains(out, "0 of 2 have not posted yet") || !strings.Contains(out, "Everyone has posted.") {
		t.Errorf("out = %q", out)
	}
}

func TestMissingOnAWeekend(t *testing.T) {
	api := newAPI(t)
	api.Respond("GET "+updatesPath+"/missing", http.StatusOK, `{"data":{"date":"2026-10-18","workday":false,"members":[]}}`)
	out, _, err := run(t, "", "missing")
	if err != nil || out != "No update is expected on weekends.\n" {
		t.Fatalf("err %v out %q", err, out)
	}
}

func TestMissingBadDateFailsBeforeAnyRequest(t *testing.T) {
	api := newAPI(t)
	_, _, err := run(t, "", "missing", "--date", "16-10-2026")
	if err == nil || !strings.Contains(err.Error(), "--date") || len(api.Calls()) != 0 {
		t.Fatalf("err = %v calls %v", err, api.Calls())
	}
}

func TestMissingForbiddenExitsThree(t *testing.T) {
	api := newAPI(t)
	api.Respond("GET "+updatesPath+"/missing", http.StatusForbidden, `{"error":{"code":"KAI_FORBIDDEN","message":"Forbidden"}}`)
	_, _, err := run(t, "", "missing")
	if client.ExitCode(err) != 3 {
		t.Fatalf("err = %v", err)
	}
}

func TestMissingJSONIsTheReport(t *testing.T) {
	api := newAPI(t)
	api.Respond("GET "+updatesPath+"/missing", http.StatusOK, missingJSON)
	flush := captureJSON(t)
	if _, _, err := run(t, "", "missing"); err != nil {
		t.Fatal(err)
	}
	if got := flush(); !strings.Contains(got, `"expectedCount": 4`) {
		t.Errorf("json =\n%s", got)
	}
}
