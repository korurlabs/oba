package cyberscore

import (
	"net/http"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

func TestCheckGetByMultiWordName(t *testing.T) {
	newAPI(t)
	out, _, err := runCLI(t, "", "kazan", "cyberscore", "check", "get", "mfa", "ENFORCED")
	if err != nil {
		t.Fatal(err)
	}
	for _, want := range []string{"Name:        MFA enforced\n", "ID:          " + idMFA + "\n", "Category:    IDENTITY\n",
		"Weight:      10\n", "Outcome:     PASS\n", "Expected:    Every account signs in with MFA\n", "Description: Why\n"} {
		if !strings.Contains(out, want) {
			t.Errorf("out misses %q:\n%s", want, out)
		}
	}
}

func TestCheckGetJSONIsTheRawItem(t *testing.T) {
	newAPI(t)
	out, err := runJSON(t, "kazan", "cyberscore", "check", "get", idBackup)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.HasPrefix(strings.TrimSpace(out), "{") || !strings.Contains(out, `"description": null`) ||
		strings.Contains(out, "MFA enforced") {
		t.Errorf("out = %s", out)
	}
}

func TestCheckGetUnknown(t *testing.T) {
	newAPI(t)
	_, _, err := runCLI(t, "", "kazan", "cyberscore", "check", "get", "Disk", "encryption")
	if err == nil || err.Error() != `check "Disk encryption" not found. Available checks: MFA enforced, Backups tested` ||
		client.ExitCode(err) != 1 {
		t.Errorf("err = %v", err)
	}
}

func TestCheckGetForbiddenExitsThree(t *testing.T) {
	api := newAPI(t)
	api.Respond("GET "+checksURL, http.StatusForbidden, `{"error":{"code":"ORG_SEC_001","message":"Not a member"}}`)
	_, _, err := runCLI(t, "", "kazan", "cyberscore", "check", "get", "MFA enforced")
	if client.ExitCode(err) != 3 {
		t.Errorf("err %v exit %d", err, client.ExitCode(err))
	}
}
