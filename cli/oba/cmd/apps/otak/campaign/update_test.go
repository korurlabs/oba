package campaign

import (
	"encoding/json"
	"net/http"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

const campaignDetailJSON = `{"data":{"id":"` + campaignID + `","name":"De wacht","description":"Nachtwacht",` +
	`"startDate":"2027-03-01","endDate":"2027-03-31",` +
	`"owner":{"id":"11111111-1111-4111-8111-111111111111","name":"Ayse Kaya"},"eventId":null,` +
	`"icpId":"22222222-2222-4222-8222-222222222222","contentCount":2,` +
	`"createdAt":"2026-11-25T08:00:00Z","updatedAt":"2026-11-25T09:00:00Z"}}`

func TestCampaignUpdateMergesOwner(t *testing.T) {
	api := newCampaignAPI(t)
	api.Respond("GET "+campaignPath, http.StatusOK, campaignDetailJSON)
	api.Respond("PUT "+campaignPath, http.StatusOK, campaignDetailJSON)
	in := `{"endDate": "2027-04-15", "icpId": null}`
	out, _, err := runCLI(t, in, "otak", "campaign", "update", campaignID)
	if err != nil {
		t.Fatal(err)
	}
	if out != "Updated campaign "+campaignID+" (De wacht)\n" {
		t.Errorf("out = %q", out)
	}
	if got := strings.Join(api.Calls(), ","); got != "GET "+campaignPath+",PUT "+campaignPath {
		t.Errorf("calls = %s", got)
	}
	var body map[string]any
	if err := json.Unmarshal([]byte(api.Request("PUT "+campaignPath).Body), &body); err != nil {
		t.Fatal(err)
	}
	want := map[string]any{
		"name": "De wacht", "description": "Nachtwacht", "startDate": "2027-03-01", "endDate": "2027-04-15",
		"ownerUserId": "11111111-1111-4111-8111-111111111111", "eventId": nil, "icpId": nil,
	}
	if !jsonEqual(body, want) {
		t.Errorf("body = %v", body)
	}
}

func TestCampaignUpdateWithoutOwnerSendsNull(t *testing.T) {
	api := newCampaignAPI(t)
	api.Respond("GET "+campaignPath, http.StatusOK, strings.Replace(campaignDetailJSON,
		`"owner":{"id":"11111111-1111-4111-8111-111111111111","name":"Ayse Kaya"}`, `"owner":null`, 1))
	api.Respond("PUT "+campaignPath, http.StatusOK, campaignDetailJSON)
	if _, _, err := runCLI(t, `{"name":"De wacht 2"}`, "otak", "campaign", "update", campaignID); err != nil {
		t.Fatal(err)
	}
	var body map[string]json.RawMessage
	if err := json.Unmarshal([]byte(api.Request("PUT "+campaignPath).Body), &body); err != nil {
		t.Fatal(err)
	}
	if string(body["ownerUserId"]) != "null" || string(body["name"]) != `"De wacht 2"` {
		t.Errorf("body = %s", api.Request("PUT "+campaignPath).Body)
	}
}

func TestCampaignUpdateRefusals(t *testing.T) {
	for _, tt := range []struct {
		name, stdin, want string
	}{
		{"unknown key", `{"nme":"x"}`, `unknown field "nme". Did you mean "name"?`},
		{"empty object", `{}`, "Nothing to update."},
	} {
		t.Run(tt.name, func(t *testing.T) {
			api := newCampaignAPI(t)
			_, _, err := runCLI(t, tt.stdin, "otak", "campaign", "update", campaignID)
			if err == nil || err.Error() != tt.want {
				t.Fatalf("err = %v, want %q", err, tt.want)
			}
			if got := api.Calls(); len(got) != 0 {
				t.Errorf("calls = %v", got)
			}
		})
	}
}

func TestCampaignUpdateNotFound(t *testing.T) {
	api := newCampaignAPI(t)
	api.Respond("GET "+campaignPath, http.StatusNotFound, `{"error":{"code":"OTK_NOT_012","message":"Campaign not found"}}`)
	_, _, err := runCLI(t, `{"name":"x"}`, "otak", "campaign", "update", campaignID)
	if err == nil || err.Error() != "OTK_NOT_012: Campaign not found" || client.ExitCode(err) != 2 {
		t.Fatalf("err = %v, exit = %d", err, client.ExitCode(err))
	}
}

func jsonEqual(a, b map[string]any) bool {
	ja, errA := json.Marshal(a)
	jb, errB := json.Marshal(b)
	return errA == nil && errB == nil && string(ja) == string(jb)
}
