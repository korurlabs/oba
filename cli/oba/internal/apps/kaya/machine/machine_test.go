package machine

import (
	"context"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
	"github.com/korurlabs/oba-cli/internal/dojo/config"
)

func TestAPIURL_isTheEnvironmentElseProductionAndNeverTheSession(t *testing.T) {
	clitest.SetupHome(t)
	clitest.LoginTo(t, "http://localhost:8080", clitest.OrgIDAcme)
	if got := APIURL(); got != config.ProdAPIURL {
		t.Fatalf("APIURL() = %q with a stored session, want %q", got, config.ProdAPIURL)
	}
	t.Setenv(config.EnvAPIURL, "http://localhost:8080/")
	if got := APIURL(); got != "http://localhost:8080" {
		t.Fatalf("APIURL() = %q, want the environment without the slash", got)
	}
}

func TestToken_takesTheEnvironmentFirst(t *testing.T) {
	t.Setenv(EnvToken, "  kyt_test  ")
	if token, err := Token(context.Background()); err != nil || token != "kyt_test" {
		t.Fatalf("Token() = %q, %v", token, err)
	}
}

func TestToken_namesTheVariableWithoutKagi(t *testing.T) {
	t.Setenv(EnvToken, "")
	t.Setenv("PATH", t.TempDir())
	if _, err := Token(context.Background()); err == nil || !strings.Contains(err.Error(), EnvToken) {
		t.Fatalf("err = %v, want one that names %s", err, EnvToken)
	}
}

func TestCheckTransport_allowsPlainHTTPOnlyToThisMachine(t *testing.T) {
	if CheckTransport("http://api.korur.com") == nil || CheckTransport("http://127.0.0.1:8080") != nil ||
		CheckTransport("http://localhost:8080") != nil || CheckTransport("https://api.korur.com") != nil {
		t.Fatal("plain http is allowed only to a loopback API")
	}
}
