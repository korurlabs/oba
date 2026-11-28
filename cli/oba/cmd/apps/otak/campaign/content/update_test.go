package content

import (
	"encoding/json"
	"net/http"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

const contentListJSON = `{"data":[{"id":"` + contentID + `","campaignId":"` + campaignID + `","legalEntityId":"` + legalEntityID +
	`","legalEntityShortName":"Korur B.V.","channel":"LINKEDIN","language":"NL","text":"Hallo","status":"SCHEDULED",` +
	`"plannedAt":"2027-03-09T09:00:00Z","publishedAt":null,"postUrl":null,` +
	`"createdAt":"2026-11-25T08:00:00Z","updatedAt":"2026-11-25T09:00:00Z"}]}`

func TestContentUpdateMergesTheCurrentValues(t *testing.T) {
	api := newContentAPI(t)
	api.Respond("GET "+contentsPath, http.StatusOK, contentListJSON)
	api.Respond("PUT "+contentPath, http.StatusOK, `{"data":{"id":"`+contentID+`","status":"PUBLISHED"}}`)
	in := `{"status": "PUBLISHED", "postUrl": "https://www.linkedin.com/feed/update/urn:li:activity:1/"}`
	out, _, err := runCLI(t, in, "otak", "campaign", "content", "update", campaignID, strings.ToUpper(contentID))
	if err != nil {
		t.Fatal(err)
	}
	if out != "Updated content "+strings.ToUpper(contentID)+" (PUBLISHED)\n" {
		t.Errorf("out = %q", out)
	}
	if got := strings.Join(api.Calls(), ","); got != "GET "+contentsPath+",PUT "+contentPath {
		t.Errorf("calls = %s", got)
	}
	var body map[string]any
	if err := json.Unmarshal([]byte(api.Request("PUT "+contentPath).Body), &body); err != nil {
		t.Fatal(err)
	}
	want := map[string]any{
		"legalEntityId": legalEntityID, "channel": "LINKEDIN", "language": "NL", "text": "Hallo", "status": "PUBLISHED",
		"plannedAt": "2027-03-09T09:00:00Z", "publishedAt": nil,
		"postUrl": "https://www.linkedin.com/feed/update/urn:li:activity:1/",
	}
	ja, _ := json.Marshal(body)
	jb, _ := json.Marshal(want)
	if string(ja) != string(jb) {
		t.Errorf("body = %s", ja)
	}
}

func TestContentUpdateUnknownContent(t *testing.T) {
	api := newContentAPI(t)
	api.Respond("GET "+contentsPath, http.StatusOK, `{"data":[]}`)
	_, _, err := runCLI(t, `{"text":"x"}`, "otak", "campaign", "content", "update", campaignID, contentID)
	if err == nil || !strings.Contains(err.Error(), "content "+contentID+" not found on campaign "+campaignID) || client.ExitCode(err) != 2 {
		t.Fatalf("err = %v, exit = %d", err, client.ExitCode(err))
	}
	if got := api.Calls(); len(got) != 1 {
		t.Errorf("calls = %v", got)
	}
}

func TestContentUpdateRefusals(t *testing.T) {
	for _, tt := range []struct {
		name, stdin, want string
	}{
		{"unknown key", `{"txt":"x"}`, `unknown field "txt". Did you mean "text"?`},
		{"empty object", `{}`, "Nothing to update."},
	} {
		t.Run(tt.name, func(t *testing.T) {
			api := newContentAPI(t)
			_, _, err := runCLI(t, tt.stdin, "otak", "campaign", "content", "update", campaignID, contentID)
			if err == nil || err.Error() != tt.want {
				t.Fatalf("err = %v, want %q", err, tt.want)
			}
			if got := api.Calls(); len(got) != 0 {
				t.Errorf("calls = %v", got)
			}
		})
	}
}

func TestContentUpdateStatusDates(t *testing.T) {
	api := newContentAPI(t)
	api.Respond("GET "+contentsPath, http.StatusOK, contentListJSON)
	api.Respond("PUT "+contentPath, http.StatusUnprocessableEntity,
		`{"error":{"code":"OTK_VAL_016","message":"A scheduled content needs a planned moment"}}`)
	_, _, err := runCLI(t, `{"plannedAt":null}`, "otak", "campaign", "content", "update", campaignID, contentID)
	if err == nil || err.Error() != "OTK_VAL_016: A scheduled content needs a planned moment" || client.ExitCode(err) != 1 {
		t.Fatalf("err = %v, exit = %d", err, client.ExitCode(err))
	}
}
