package pack

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"testing"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/kayatest"
	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

// fakePacks is the pack list and the required endpoint of one organization, signed in as the user
// of 'oba login'.
type fakePacks struct {
	mu    sync.Mutex
	packs []map[string]any
	// answer is the status and body of PUT /kaya/packs/{id}/required, given the flag sent.
	answer func(id string, required bool) (int, map[string]any)
	puts   []put
}

// put is one PUT /kaya/packs/{id}/required the fake received.
type put struct {
	id   string
	body map[string]any
}

func (f *fakePacks) sent() []put {
	f.mu.Lock()
	defer f.mu.Unlock()
	return append([]put(nil), f.puts...)
}

// set changes what the fake answers, under its lock, since the server reads it in another goroutine.
func (f *fakePacks) set(change func(f *fakePacks)) {
	f.mu.Lock()
	defer f.mu.Unlock()
	change(f)
}

func (f *fakePacks) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	if r.Header.Get("Authorization") != "Bearer access-token" || r.Header.Get("X-Organization-ID") != clitest.OrgIDAcme {
		w.WriteHeader(http.StatusUnauthorized)
		return
	}
	id, isRequired := strings.CutSuffix(strings.TrimPrefix(r.URL.Path, "/kaya/packs/"), "/required")
	switch {
	case r.Method == http.MethodGet && r.URL.Path == "/kaya/packs":
		f.mu.Lock()
		packs := f.packs
		f.mu.Unlock()
		_ = json.NewEncoder(w).Encode(map[string]any{"success": true, "data": packs})
	case r.Method == http.MethodPut && isRequired:
		var body map[string]any
		_ = json.NewDecoder(r.Body).Decode(&body)
		f.mu.Lock()
		f.puts = append(f.puts, put{id: id, body: body})
		answer := f.answer
		f.mu.Unlock()
		required, _ := body["required"].(bool)
		status, payload := answer(id, required)
		w.WriteHeader(status)
		_ = json.NewEncoder(w).Encode(payload)
	default:
		w.WriteHeader(http.StatusNotFound)
	}
}

// setup signs in to a fakePacks that holds the packs base (required) and web, and answers every
// change with releases created.
func setup(t *testing.T, releases int) *fakePacks {
	t.Helper()
	clitest.SetupHome(t)
	fake := &fakePacks{
		packs: []map[string]any{
			{"id": "p-web", "name": "web", "required": false, "mountPaths": []string{}},
			{"id": "p-base", "name": "base", "required": true, "mountPaths": []string{}},
		},
		answer: func(id string, required bool) (int, map[string]any) {
			return http.StatusOK, map[string]any{"success": true,
				"data": map[string]any{"packId": id, "required": required, "releaseCount": releases}}
		},
	}
	server := httptest.NewServer(fake)
	t.Cleanup(server.Close)
	clitest.LoginTo(t, server.URL, clitest.OrgIDAcme)
	return fake
}

// run runs oba kaya pack.
func run(t *testing.T, args ...string) (string, error) {
	t.Helper()
	return kayatest.Run(t, []*cobra.Command{NewCmd()}, "", args...)
}
