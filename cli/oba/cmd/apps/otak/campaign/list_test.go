package campaign

import (
	"net/http"
	"net/url"
	"strings"
	"testing"

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
	api := newCampaignAPI(t)
	queries := recordList(api, `{"data":[`+
		`{"id":"c1","name":"De wacht","description":null,"startDate":"2027-03-01","endDate":"2027-03-31",`+
		`"owner":{"id":"u1","name":"Ayse Kaya"},"eventId":"e1","icpId":null,"contentCount":3},`+
		`{"id":"c2","name":"Teaser","description":null,"startDate":null,"endDate":null,`+
		`"owner":null,"eventId":"e1","icpId":null,"contentCount":0}]}`)
	out, _, err := runCLI(t, "", "otak", "campaign", "list", "--search", " wacht ", "--event", "e1")
	if err != nil {
		t.Fatal(err)
	}
	if len(*queries) != 1 {
		t.Fatalf("queries = %v", *queries)
	}
	want := url.Values{"search": {"wacht"}, "eventId": {"e1"}}
	if got := (*queries)[0]; got.Encode() != want.Encode() {
		t.Errorf("query = %v, want %v (no page or amount)", got, want)
	}
	wantOut := "NAME  START  END  CONTENTS  OWNER  ID\n" +
		"De wacht  2027-03-01  2027-03-31  3  Ayse Kaya  c1\n" +
		"Teaser  -  -  0  -  c2\n"
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

func TestCampaignListHasNoStatusOrChannelFilter(t *testing.T) {
	newCampaignAPI(t)
	for _, flag := range []string{"--status", "--channel"} {
		_, _, err := runCLI(t, "", "otak", "campaign", "list", flag, "x")
		if err == nil || !strings.Contains(err.Error(), "unknown flag: "+flag) {
			t.Errorf("%s: err = %v", flag, err)
		}
	}
}

func normalizeSpaces(s string) string {
	lines := strings.Split(s, "\n")
	for i, line := range lines {
		lines[i] = strings.Join(strings.Fields(line), " ")
	}
	return strings.Join(lines, "\n")
}
