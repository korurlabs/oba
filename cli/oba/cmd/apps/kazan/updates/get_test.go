package updates

import (
	"net/http"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

func TestGetPrintsTheSummaryText(t *testing.T) {
	api := newAPI(t)
	api.Respond("GET "+updatePath, http.StatusOK, `{"data":{"id":"`+idUpdate+`","summaryText":"Line 1\nLine 2\n"}}`)
	out, _, err := run(t, "", "get", idUpdate)
	if err != nil {
		t.Fatal(err)
	}
	if out != "Line 1\nLine 2\n" {
		t.Errorf("out = %q", out)
	}
	if org := api.Request("GET " + updatePath).Org; org != "" {
		t.Errorf("organization header %q, want none: updates span organizations", org)
	}
}

func TestGetRejectsANonIDBeforeAnyRequest(t *testing.T) {
	api := newAPI(t)
	_, _, err := run(t, "", "get", "OPS-7")
	if err == nil || !strings.Contains(err.Error(), "invalid update ID") || len(api.Calls()) != 0 {
		t.Fatalf("err = %v calls %v", err, api.Calls())
	}
}

func TestGetNotFoundExitsTwo(t *testing.T) {
	api := newAPI(t)
	api.Respond("GET "+updatePath, http.StatusNotFound, `{"error":{"code":"KAI_NF","message":"Daily update not found"}}`)
	_, _, err := run(t, "", "get", idUpdate)
	if client.ExitCode(err) != 2 {
		t.Fatalf("err = %v", err)
	}
}

func TestGetJSONIsTheUpdate(t *testing.T) {
	api := newAPI(t)
	api.Respond("GET "+updatePath, http.StatusOK, `{"data":{"id":"`+idUpdate+`","items":[{"ticketKey":"OPS-7","canOpen":false}]}}`)
	flush := captureJSON(t)
	if _, _, err := run(t, "", "get", idUpdate); err != nil {
		t.Fatal(err)
	}
	if got := flush(); !strings.Contains(got, `"canOpen": false`) {
		t.Errorf("json =\n%s", got)
	}
}
