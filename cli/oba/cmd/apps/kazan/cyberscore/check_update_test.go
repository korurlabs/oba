package cyberscore

import (
	"net/http"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
)

func TestCheckUpdateSendsMergedBody(t *testing.T) {
	api := newAPI(t)
	api.Respond(checkRoute("PUT", idMFA, ""), http.StatusOK, `{"data":`+mfaJSON+`}`)
	out, _, err := runCLI(t, "", "kazan", "cyberscore", "check", "update", "MFA", "enforced", "--weight", "20",
		"--description", "")
	if err != nil || out != `Updated check "MFA enforced" (`+idMFA+")\n" {
		t.Fatalf("out %q err %v", out, err)
	}
	want := `{"name":"MFA enforced","expected":"Every account signs in with MFA","weight":20,"sortOrder":1,` +
		`"category":"IDENTITY","outcome":"PASS"}`
	if body := api.Request(checkRoute("PUT", idMFA, "")).Body; body != want {
		t.Errorf("body = %s", body)
	}
}

func TestCheckUpdateWithNothing(t *testing.T) {
	api := newAPI(t)
	_, _, err := runCLI(t, "", "kazan", "cyberscore", "check", "update", "MFA enforced")
	if err != terminal.ErrNothingToUpdate || len(api.Calls()) != 0 {
		t.Errorf("err %v calls %v", err, api.Calls())
	}
}

func TestCheckUpdateUnlistedIDExitsTwo(t *testing.T) {
	api := newAPI(t)
	const other = "c0000000-0000-4000-8000-000000000099"
	_, _, err := runCLI(t, "", "kazan", "cyberscore", "check", "update", other, "--weight", "20")
	if client.ExitCode(err) != 2 {
		t.Errorf("err %v exit %d", err, client.ExitCode(err))
	}
	for _, call := range api.Calls() {
		if call != "GET "+checksURL {
			t.Errorf("unexpected call %s", call)
		}
	}
}
