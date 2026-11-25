package cyberscore

import (
	"net/http"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

func TestTrendSendsDays(t *testing.T) {
	api := newAPI(t)
	var query string
	api.Handle("GET /kazan/cyberscore/trend", func(w http.ResponseWriter, r *http.Request) {
		query = r.URL.RawQuery
		clitest.WriteJSON(w, `{"data":[{"date":"2026-11-25","scorePercentage":72,"scoreLevel":"LOW"}]}`)
	})
	out, _, err := runCLI(t, "", "kazan", "cyberscore", "trend", "--days", "90")
	if err != nil || out != "DATE        SCORE  LEVEL\n2026-11-25  72%    LOW\n" {
		t.Fatalf("out %q err %v", out, err)
	}
	if query != "days=90" {
		t.Errorf("query = %q", query)
	}
}

func TestTrendDefaultSendsNoDaysAndEmpty(t *testing.T) {
	api := newAPI(t)
	var query string
	api.Handle("GET /kazan/cyberscore/trend", func(w http.ResponseWriter, r *http.Request) {
		query = r.URL.RawQuery
		clitest.WriteJSON(w, `{"data":[]}`)
	})
	out, _, err := runCLI(t, "", "kazan", "cyberscore", "trend")
	if err != nil || out != "No trend yet.\n" || query != "" {
		t.Errorf("out %q err %v query %q", out, err, query)
	}
}

func TestTrendRefusesDaysOutOfRange(t *testing.T) {
	api := newAPI(t)
	for _, days := range []string{"0", "366"} {
		if _, _, err := runCLI(t, "", "kazan", "cyberscore", "trend", "--days", days); err == nil {
			t.Errorf("--days %s: want an error", days)
		}
	}
	if calls := api.Calls(); len(calls) != 0 {
		t.Errorf("calls = %v", calls)
	}
}
