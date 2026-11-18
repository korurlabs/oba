package sprint

import (
	"net/http"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

const boardID = "0a000000-0000-4000-8000-000000000001"

func newClient(t *testing.T, body string) *client.Client {
	t.Helper()
	clitest.SetupHome(t)
	api := clitest.NewMockAPI(t)
	api.Respond("GET /kaizen/boards/"+boardID+"/sprints", http.StatusOK, body)
	return client.NewWithToken(api.URL(), clitest.OrgIDAcme, "tok")
}

func TestFind(t *testing.T) {
	const sprints = `{"data":[` +
		`{"id":"s1","name":"Sprint 1","status":"COMPLETED","createdAt":"2026-10-01T09:00:00Z"},` +
		`{"id":"s2","name":"Sprint 2","status":"PLANNED","createdAt":"2026-11-01T09:00:00Z"},` +
		`{"id":"s3","name":"sprint 2","status":"ACTIVE","createdAt":"2026-10-15T09:00:00Z"}]}`
	tests := []struct{ name, given, wantID, wantErr string }{
		{name: "keyword is the running sprint", given: "Sprint", wantID: "s3"},
		{name: "name prefers the running sprint", given: "SPRINT 2", wantID: "s3"},
		{name: "name", given: "sprint 1", wantID: "s1"},
		{name: "unknown", given: "Sprint 9", wantErr: `no sprint of the board is named "Sprint 9"`},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := Find(newClient(t, sprints), boardID, tt.given)
			if tt.wantErr != "" {
				if err == nil || !strings.Contains(err.Error(), tt.wantErr) {
					t.Fatalf("err = %v", err)
				}
				return
			}
			if err != nil || got.ID != tt.wantID {
				t.Errorf("got %+v, %v", got, err)
			}
		})
	}
}

func TestFindKeywordWithoutRunningSprintIsTheNewest(t *testing.T) {
	c := newClient(t, `{"data":[{"id":"s1","name":"A","status":"COMPLETED","createdAt":"2026-10-01T09:00:00Z"},`+
		`{"id":"s2","name":"B","status":"PLANNED","createdAt":"2026-11-01T09:00:00.5Z"}]}`)
	got, err := Find(c, boardID, "sprint")
	if err != nil || got.ID != "s2" {
		t.Errorf("got %+v, %v", got, err)
	}
}

func TestFindWithoutSprints(t *testing.T) {
	if _, err := Find(newClient(t, `{"data":[]}`), boardID, "x"); err == nil || !strings.Contains(err.Error(), "has no sprints") {
		t.Errorf("err = %v", err)
	}
}
