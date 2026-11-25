package cyberscore

import (
	"net/http"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

func TestCheckDeleteAsksAndCancels(t *testing.T) {
	api := newAPI(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, true)
	out, _, err := runCLI(t, "n\n", "kazan", "cyberscore", "check", "delete", "MFA enforced")
	if err != nil || !strings.Contains(out, `Delete check "MFA enforced" (`+idMFA+")?") || !strings.HasSuffix(out, "Cancelled.\n") {
		t.Errorf("out %q err %v", out, err)
	}
	for _, call := range api.Calls() {
		if strings.HasPrefix(call, "DELETE") {
			t.Errorf("deleted: %v", call)
		}
	}
}

func TestCheckDeleteYesSkipsTheQuestion(t *testing.T) {
	api := newAPI(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, true)
	api.Respond(checkRoute("DELETE", idMFA, ""), http.StatusOK, `{"data":null}`)
	out, _, err := runCLI(t, "", "kazan", "cyberscore", "check", "delete", "MFA enforced", "--yes")
	if err != nil || out != `Deleted check "MFA enforced" (`+idMFA+")\n" {
		t.Errorf("out %q err %v", out, err)
	}
}

func TestCheckDeleteJSONDoesNotAsk(t *testing.T) {
	api := newAPI(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, true)
	api.Respond(checkRoute("DELETE", idMFA, ""), http.StatusOK, `{"data":null}`)
	out, err := runJSON(t, "kazan", "cyberscore", "check", "delete", idMFA)
	if err != nil || strings.TrimSpace(out) != "null" {
		t.Errorf("out %q err %v", out, err)
	}
	api.Request(checkRoute("DELETE", idMFA, ""))
}
