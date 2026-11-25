package cyberscore

import (
	"net/http"
	"strings"
	"testing"
)

func TestCheckOutcomeValueLast(t *testing.T) {
	api := newAPI(t)
	api.Respond(checkRoute("PUT", idBackup, "/outcome"), http.StatusOK,
		`{"data":`+strings.Replace(strings.Replace(backupJSON, `"TODO"`, `"FAIL"`, 1), `"NOT_STARTED"`, `"DONE"`, 1)+`}`)
	out, _, err := runCLI(t, "", "kazan", "cyberscore", "check", "outcome", "backups", "tested", "fail")
	if err != nil || out != `Check "Backups tested" (`+idBackup+"): outcome FAIL, status DONE\n" {
		t.Fatalf("out %q err %v", out, err)
	}
	if body := api.Request(checkRoute("PUT", idBackup, "/outcome")).Body; body != `{"outcome":"FAIL"}` {
		t.Errorf("body = %s", body)
	}
}

func TestCheckOutcomeRefusals(t *testing.T) {
	api := newAPI(t)
	_, _, err := runCLI(t, "", "kazan", "cyberscore", "check", "outcome", "MFA", "enforced", "passed")
	if err == nil || !strings.HasPrefix(err.Error(), `invalid outcome "passed": use one of TODO, PASS,`) {
		t.Errorf("err = %v", err)
	}
	if _, _, err := runCLI(t, "", "kazan", "cyberscore", "check", "outcome", "PASS"); err == nil {
		t.Error("one argument: want a usage error")
	}
	if calls := api.Calls(); len(calls) != 0 {
		t.Errorf("calls = %v", calls)
	}
}
