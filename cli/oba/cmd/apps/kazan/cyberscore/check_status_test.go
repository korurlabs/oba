package cyberscore

import (
	"net/http"
	"strings"
	"testing"
)

func TestCheckStatusBody(t *testing.T) {
	api := newAPI(t)
	api.Respond(checkRoute("PUT", idBackup, "/status"), http.StatusOK,
		`{"data":`+strings.Replace(backupJSON, `"NOT_STARTED"`, `"IN_PROGRESS"`, 1)+`}`)
	out, _, err := runCLI(t, "", "kazan", "cyberscore", "check", "status", "Backups", "tested", "in-progress")
	if err != nil || out != `Check "Backups tested" (`+idBackup+"): status IN_PROGRESS\n" {
		t.Fatalf("out %q err %v", out, err)
	}
	if body := api.Request(checkRoute("PUT", idBackup, "/status")).Body; body != `{"status":"IN_PROGRESS"}` {
		t.Errorf("body = %s", body)
	}
}

func TestCheckStatusInvalidBeforeAnyCall(t *testing.T) {
	api := newAPI(t)
	if _, _, err := runCLI(t, "", "kazan", "cyberscore", "check", "status", "Backups tested", "open"); err == nil ||
		len(api.Calls()) != 0 {
		t.Errorf("err %v calls %v", err, api.Calls())
	}
}
