package cyberscore

import (
	"net/http"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

func TestCheckCreateBody(t *testing.T) {
	api := newAPI(t)
	api.Respond("POST "+checksURL, http.StatusCreated, `{"data":`+mfaJSON+`}`)
	out, _, err := runCLI(t, "", "kazan", "cyberscore", "check", "create", "--name", "MFA enforced",
		"--expected", "Every account signs in with MFA", "--weight", "10", "--description", "Why",
		"--sort-order", "1", "--category", "identity", "--outcome", "pass")
	if err != nil || out != `Created check "MFA enforced" (`+idMFA+")\n" {
		t.Fatalf("out %q err %v", out, err)
	}
	want := `{"name":"MFA enforced","description":"Why","expected":"Every account signs in with MFA","weight":10,` +
		`"sortOrder":1,"category":"IDENTITY","outcome":"PASS"}`
	if body := api.Request("POST " + checksURL).Body; body != want {
		t.Errorf("body = %s", body)
	}
}

func TestCheckCreateSortOrderZeroIsSent(t *testing.T) {
	api := newAPI(t)
	api.Respond("POST "+checksURL, http.StatusCreated, `{"data":`+backupJSON+`}`)
	if _, _, err := runCLI(t, "", "kazan", "cyberscore", "check", "create", "--name", "Backups tested",
		"--expected", "A restore ran this quarter", "--weight", "5", "--sort-order", "0"); err != nil {
		t.Fatal(err)
	}
	if body := api.Request("POST " + checksURL).Body; !strings.Contains(body, `"sortOrder":0`) {
		t.Errorf("body = %s", body)
	}
}

func TestCheckCreateChecksBeforeAnyCall(t *testing.T) {
	api := newAPI(t)
	base := []string{"kazan", "cyberscore", "check", "create"}
	for _, args := range [][]string{
		{"--expected", "x", "--weight", "10"},
		{"--name", "x", "--weight", "10"},
		{"--name", "x", "--expected", "x"},
		{"--name", "x", "--expected", "x", "--weight", "0"},
		{"--name", "x", "--expected", "x", "--weight", "10", "--sort-order", "10000"},
		{"--name", "x", "--expected", "x", "--weight", "10", "--category", "gdpr"},
	} {
		if _, _, err := runCLI(t, "", append(base, args...)...); err == nil {
			t.Errorf("%v: want an error", args)
		}
	}
	if calls := api.Calls(); len(calls) != 0 {
		t.Errorf("calls = %v", calls)
	}
}

func TestCheckCreateServerErrors(t *testing.T) {
	for _, tc := range []struct {
		status int
		body   string
		want   string
		exit   int
	}{
		{http.StatusConflict, `{"error":{"code":"KAZ_STA_020","message":"A check with this name already exists"}}`,
			"KAZ_STA_020: A check with this name already exists", 1},
		{http.StatusForbidden, `{"error":{"code":"ORG_SEC_003","message":"Insufficient organization role"}}`,
			"ORG_SEC_003: Insufficient organization role", 3},
	} {
		api := newAPI(t)
		api.Respond("POST "+checksURL, tc.status, tc.body)
		_, _, err := runCLI(t, "", "kazan", "cyberscore", "check", "create", "--name", "MFA enforced",
			"--expected", "x", "--weight", "10")
		if err == nil || err.Error() != tc.want || client.ExitCode(err) != tc.exit {
			t.Errorf("err %v exit %d", err, client.ExitCode(err))
		}
	}
}
