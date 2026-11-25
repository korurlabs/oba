package campaign

import (
	"net/http"
	"net/url"
	"strings"
	"testing"
	"time"

	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

// recordList serves GET /otak/campaigns with body and returns the queries it received.
func recordList(api *clitest.MockAPI, body string) *[]url.Values {
	var queries []url.Values
	api.Handle("GET /otak/campaigns", func(w http.ResponseWriter, r *http.Request) {
		queries = append(queries, r.URL.Query())
		clitest.WriteJSON(w, body)
	})
	return &queries
}

func TestCampaignListOneUnpagedCallWithFilters(t *testing.T) {
	old := time.Local
	time.Local = time.UTC
	t.Cleanup(func() { time.Local = old })
	api := newCampaignAPI(t)
	queries := recordList(api, `{"data":[`+
		`{"id":"c1","name":"Launch","channel":"LINKEDIN","status":"SCHEDULED","plannedAt":"2027-03-09T09:00:00Z","publishedAt":null,`+
		`"languages":["NL","EN"],"owner":{"id":"u1","name":"Ayse Kaya"},"eventId":null,"icpId":null},`+
		`{"id":"c2","name":"Teaser","channel":"LINKEDIN","status":"SCHEDULED","plannedAt":"2027-03-10T09:30:00Z","publishedAt":null,`+
		`"languages":[],"owner":null,"eventId":null,"icpId":null}]}`)
	out, _, err := runCLI(t, "", "otak", "campaign", "list", "--status", "Scheduled", "--channel", "linkedin", "--search", " launch ")
	if err != nil {
		t.Fatal(err)
	}
	if len(*queries) != 1 {
		t.Fatalf("queries = %v", *queries)
	}
	want := url.Values{"status": {"SCHEDULED"}, "channel": {"LINKEDIN"}, "search": {"launch"}}
	if got := (*queries)[0]; got.Encode() != want.Encode() {
		t.Errorf("query = %v, want %v (no page or amount)", got, want)
	}
	wantOut := "NAME    CHANNEL   STATUS     PLANNED           PUBLISHED  LANGUAGES  OWNER      ID\n" +
		"Launch  LINKEDIN  SCHEDULED  2027-03-09 09:00  -          NL EN      Ayse Kaya  c1\n" +
		"Teaser  LINKEDIN  SCHEDULED  2027-03-10 09:30  -          -          -          c2\n"
	if normalizeSpaces(out) != normalizeSpaces(wantOut) {
		t.Errorf("out =\n%s", out)
	}
	if api.Request("GET /otak/campaigns").Org != clitest.OrgIDAcme {
		t.Error("organization header missing")
	}
}

func TestCampaignListWithoutFiltersSendsNoQuery(t *testing.T) {
	api := newCampaignAPI(t)
	queries := recordList(api, `{"data":[]}`)
	out, _, err := runCLI(t, "", "otak", "campaign", "list")
	if err != nil {
		t.Fatal(err)
	}
	if len((*queries)[0]) != 0 {
		t.Errorf("query = %v", (*queries)[0])
	}
	if out != "No campaigns.\n" {
		t.Errorf("out = %q", out)
	}
}

func TestCampaignListUnknownValueBeforeAnyCall(t *testing.T) {
	for _, tt := range []struct {
		flag, value, want string
	}{
		{"--status", "live", `unknown status "live"; allowed: draft, scheduled, published, archived`},
		{"--channel", "mastodon", `unknown channel "mastodon"; allowed: linkedin, x, newsletter`},
	} {
		t.Run(tt.flag, func(t *testing.T) {
			api := newCampaignAPI(t)
			_, _, err := runCLI(t, "", "otak", "campaign", "list", tt.flag, tt.value)
			if err == nil || err.Error() != tt.want {
				t.Fatalf("err = %v, want %q", err, tt.want)
			}
			if got := api.Calls(); len(got) != 0 {
				t.Errorf("calls = %v", got)
			}
		})
	}
}

func normalizeSpaces(s string) string {
	lines := strings.Split(s, "\n")
	for i, line := range lines {
		lines[i] = strings.Join(strings.Fields(line), " ")
	}
	return strings.Join(lines, "\n")
}
