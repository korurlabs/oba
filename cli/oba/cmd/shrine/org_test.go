package shrine

import (
	"net/http"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
	"github.com/korurlabs/oba-cli/internal/dojo/config"
)

func orgFixture(t *testing.T) *clitest.MockAPI {
	t.Helper()
	clitest.SetupHome(t)
	api := clitest.NewMockAPI(t)
	api.Respond("GET /users/me", http.StatusOK, clitest.UserMeJSON)
	clitest.LoginTo(t, api.URL(), clitest.OrgIDAcme)
	return api
}

func TestOrgUse(t *testing.T) {
	tests := []struct {
		name, arg, wantID, wantErr string
	}{
		{"by id", clitest.OrgIDBeta, clitest.OrgIDBeta, ""},
		{"by name, any case", "aCmE", clitest.OrgIDAcme, ""},
		{"ambiguous name", "BETA", "", "several organizations are named \"BETA\": " + clitest.OrgIDBeta + ", " + clitest.OrgIDBeta2},
		{"unknown", "nope", "", `no organization of yours matches "nope"`},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			api := orgFixture(t)
			out, _, err := runCLI(t, "", "org", "use", tt.arg)
			if tt.wantErr != "" {
				if err == nil || !strings.Contains(err.Error(), tt.wantErr) {
					t.Fatalf("err = %v, want %q", err, tt.wantErr)
				}
				if f, _ := config.Load(); f.OrgID != "" {
					t.Errorf("org stored: %q", f.OrgID)
				}
				return
			}
			if err != nil {
				t.Fatal(err)
			}
			if f, _ := config.Load(); f.OrgID != tt.wantID {
				t.Errorf("stored org = %q, want %q", f.OrgID, tt.wantID)
			}
			if !strings.Contains(out, "Current organization: ") {
				t.Errorf("output = %q", out)
			}
			if got := api.Request("GET /users/me").Org; got != "" {
				t.Errorf("/users/me sent org header %q", got)
			}
		})
	}
}

func TestOrgListMarksCurrent(t *testing.T) {
	orgFixture(t)
	marked := func(out string) string {
		var rows []string
		for _, line := range strings.Split(out, "\n") {
			if strings.HasPrefix(line, "*") {
				rows = append(rows, line)
			}
		}
		if len(rows) != 1 {
			t.Fatalf("want one marked row, got %v in\n%s", rows, out)
		}
		return rows[0]
	}
	out, _, err := runCLI(t, "", "org", "list")
	if err != nil {
		t.Fatal(err)
	}
	if row := marked(out); !strings.Contains(row, "Acme") || !strings.Contains(row, "ADMIN") {
		t.Errorf("default org row = %q", row)
	}
	if !strings.Contains(out, "NAME") || !strings.Contains(out, clitest.OrgIDBeta2) {
		t.Errorf("output:\n%s", out)
	}

	if _, _, err := runCLI(t, "", "org", "use", clitest.OrgIDBeta); err != nil {
		t.Fatal(err)
	}
	out, _, err = runCLI(t, "", "org", "list")
	if err != nil {
		t.Fatal(err)
	}
	if row := marked(out); !strings.Contains(row, clitest.OrgIDBeta) {
		t.Errorf("chosen org row = %q", row)
	}
}

func TestOrgListNoOrganizations(t *testing.T) {
	clitest.SetupHome(t)
	api := clitest.NewMockAPI(t)
	api.Respond("GET /users/me", http.StatusOK, `{"data":{"id":"u","organizations":[]}}`)
	clitest.LoginTo(t, api.URL(), "")
	out, _, err := runCLI(t, "", "org", "list")
	if err != nil || !strings.Contains(out, "You belong to no organization.") {
		t.Fatalf("out = %q err = %v", out, err)
	}
}

func TestOrgListNotLoggedIn(t *testing.T) {
	clitest.SetupHome(t)
	_, _, err := runCLI(t, "", "org", "list")
	if err == nil || !strings.Contains(err.Error(), "You are not logged in.") {
		t.Fatalf("err = %v", err)
	}
}
