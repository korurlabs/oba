package updates

import (
	"net/http"
	"net/url"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

const listJSON = `{"data":[{"id":"` + idUpdate + `","createdAt":"2026-11-18T07:00:00Z","editedAt":"2026-11-18T08:00:00Z",` +
	`"author":{"email":"ada@x.io","firstName":"Ada","lastName":"Lovelace"},"items":[{"ticketKey":"OPS-7"},{"ticketKey":"KAGI-2"}]}],` +
	`"pagination":{"currentPage":1,"totalPages":3,"totalElements":51,"last":false}}`

func TestListPrintsTheTableAndThePage(t *testing.T) {
	api := newAPI(t)
	var query string
	queryOf(api, "GET "+updatesPath, listJSON, &query)
	out, _, err := run(t, "", "list")
	if err != nil {
		t.Fatal(err)
	}
	for _, part := range []string{"DATE", "AUTHOR", "TICKETS", "(edited)", "Ada Lovelace", "OPS-7, KAGI-2", idUpdate,
		"Page 1 of 3 (51 updates). Use --page for more."} {
		if !strings.Contains(out, part) {
			t.Errorf("out missing %q:\n%s", part, out)
		}
	}
	if query != "amount=25&page=1" {
		t.Errorf("query = %s", query)
	}
}

func TestListSendsTheFilters(t *testing.T) {
	api := newAPI(t)
	api.Respond("GET /users/me", http.StatusOK, clitest.UserMeJSON)
	api.Respond("GET /organizations/"+clitest.OrgIDAcme+"/members", http.StatusOK,
		`{"data":[{"userId":"u2","email":"bob@x.io"}],"pagination":{"currentPage":1,"last":true}}`)
	var query string
	queryOf(api, "GET "+updatesPath, `{"data":[]}`, &query)
	out, _, err := run(t, "", "list", "--author", "me,BOB@x.io", "--boards", "kagi", "--from", "2026-10-01",
		"--to", "2026-10-31", "--page", "2", "--amount", "50")
	if err != nil {
		t.Fatal(err)
	}
	q, _ := url.ParseQuery(query)
	if strings.Join(q["authorIds"], ",") != "u1,u2" || q.Get("boardIds") != idKagi || q.Get("from") != "2026-10-01" ||
		q.Get("to") != "2026-10-31" || q.Get("page") != "2" || q.Get("amount") != "50" {
		t.Errorf("query = %s", query)
	}
	if out != "No updates found.\n" {
		t.Errorf("out = %q", out)
	}
}

func TestListFlagErrorsComeBeforeAnyRequest(t *testing.T) {
	cases := []struct {
		name, want string
		args       []string
	}{
		{"page zero", "--page must be 1 or more", []string{"--page", "0"}},
		{"amount too big", "--amount must be from 1 to 50", []string{"--amount", "51"}},
		{"bad from", "--from: invalid date", []string{"--from", "18.11.2026"}},
		{"from after to", "is after --to", []string{"--from", "2026-11-02", "--to", "2026-11-01"}},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			api := newAPI(t)
			_, _, err := run(t, "", append([]string{"list"}, tc.args...)...)
			if err == nil || !strings.Contains(err.Error(), tc.want) || len(api.Calls()) != 0 {
				t.Fatalf("err = %v calls %v", err, api.Calls())
			}
		})
	}
}

func TestListJSONIsTheUpdates(t *testing.T) {
	api := newAPI(t)
	api.Respond("GET "+updatesPath, http.StatusOK, listJSON)
	flush := captureJSON(t)
	if _, _, err := run(t, "", "list"); err != nil {
		t.Fatal(err)
	}
	if got := flush(); !strings.HasPrefix(got, "[") || !strings.Contains(got, `"ticketKey": "KAGI-2"`) {
		t.Errorf("json =\n%s", got)
	}
}
