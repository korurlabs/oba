// Package kayatest holds what the tests of the oba kaya commands share: a fake Kaya delivery API
// with a signing key, a scratch delivered checkout and a runner of the command tree.
package kayatest

import (
	"bytes"
	"context"
	"crypto/ed25519"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"slices"
	"sort"
	"strings"
	"sync"
	"sync/atomic"
	"testing"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/delivery"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/machine"
	"github.com/korurlabs/oba-cli/internal/dojo/config"
	"github.com/korurlabs/oba-cli/internal/dojo/jsonout"
)

// FakeKaya is the delivery API of one repository, in the CustomResponse envelope.
type FakeKaya struct {
	priv     ed25519.PrivateKey
	Requests atomic.Int64
	// paths holds the path of every request in order, so a test can name what a hook called.
	pathsMu  sync.Mutex
	paths    []string
	Current  map[string]any
	releases map[string]map[string]any
	blobs    map[string][]byte
	// proposals holds the bodies POST /delivery/proposals received; propose answers them.
	Proposals []map[string]any
	Propose   func(body map[string]any) (int, map[string]any)
}

// PathsSince returns the paths requested after the first n requests.
func (k *FakeKaya) PathsSince(n int64) []string {
	k.pathsMu.Lock()
	defer k.pathsMu.Unlock()
	return slices.Clone(k.paths[n:])
}

// ServeHTTP answers the delivery and proposal calls.
func (k *FakeKaya) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	k.Requests.Add(1)
	k.pathsMu.Lock()
	k.paths = append(k.paths, r.URL.Path)
	k.pathsMu.Unlock()
	if r.Header.Get("Authorization") != "Bearer kyt_test" {
		w.WriteHeader(http.StatusUnauthorized)
		return
	}
	data := func(value any) {
		_ = json.NewEncoder(w).Encode(map[string]any{"success": true, "data": value})
	}
	switch {
	case r.Method == http.MethodPost && r.URL.Path == "/kaya/delivery/proposals" && k.Propose != nil:
		var body map[string]any
		_ = json.NewDecoder(r.Body).Decode(&body)
		k.Proposals = append(k.Proposals, body)
		status, payload := k.Propose(body)
		w.WriteHeader(status)
		_ = json.NewEncoder(w).Encode(payload)
	case r.URL.Path == "/kaya/delivery/current":
		w.Header().Set("ETag", fmt.Sprintf("%q", k.Current["manifestSha256"]))
		data(k.Current)
	case strings.HasPrefix(r.URL.Path, "/kaya/delivery/releases/"):
		data(k.releases[strings.TrimPrefix(r.URL.Path, "/kaya/delivery/releases/")])
	case strings.HasPrefix(r.URL.Path, "/kaya/delivery/blobs/"):
		_, _ = w.Write(k.blobs[strings.TrimPrefix(r.URL.Path, "/kaya/delivery/blobs/")])
	default:
		w.WriteHeader(http.StatusNotFound)
	}
}

// HexSum is the hex SHA-256 of data, as manifests name blobs.
func HexSum(data []byte) string {
	sum := sha256.Sum256(data)
	return hex.EncodeToString(sum[:])
}

// Publish makes release number current, with the files AGENTS.md and the demo skill first and
// the paths in alwaysOn marked always-on.
func (k *FakeKaya) Publish(t *testing.T, number int, files map[string]string, alwaysOn ...string) {
	t.Helper()
	type entry struct {
		Path     string `json:"path"`
		SHA256   string `json:"sha256"`
		Mode     string `json:"mode"`
		AlwaysOn bool   `json:"alwaysOn"`
	}
	var entries []entry
	paths := []string{"AGENTS.md", ".agents/skills/demo/SKILL.md"}
	var extra []string
	for path := range files {
		if path != paths[0] && path != paths[1] {
			extra = append(extra, path)
		}
	}
	sort.Strings(extra)
	for _, path := range append(paths, extra...) {
		content := []byte(files[path])
		k.blobs[HexSum(content)] = content
		on := false
		for _, a := range alwaysOn {
			on = on || a == path
		}
		entries = append(entries, entry{Path: path, SHA256: HexSum(content), Mode: "FILE", AlwaysOn: on})
	}
	manifest, err := json.Marshal(map[string]any{
		"organization": "org-1", "remoteUrl": "github.com/sensey/demo", "number": number,
		"createdAt": "2026-10-24T10:00:00.000Z", "files": entries,
	})
	if err != nil {
		t.Fatal(err)
	}
	id := fmt.Sprintf("rel-%d", number)
	k.releases[id] = map[string]any{
		"releaseId": id, "repositoryId": "repo-1", "number": number, "manifest": string(manifest),
		"manifestSha256": HexSum(manifest), "signature": base64.StdEncoding.EncodeToString(ed25519.Sign(k.priv, manifest)), "keyId": "dev",
	}
	k.Current = map[string]any{"repositoryId": "repo-1", "releaseId": id, "number": number, "manifestSha256": HexSum(manifest)}
}

// SetupDelivery starts a FakeKaya, points the machine calls at it with the machine token kyt_test
// and its signing key trusted, isolates HOME and the XDG directories, and changes into a scratch
// checkout delivered by Kaya, whose root it returns.
func SetupDelivery(t *testing.T) (*FakeKaya, string) {
	t.Helper()
	pub, priv, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	kaya := &FakeKaya{priv: priv, releases: map[string]map[string]any{}, blobs: map[string][]byte{}}
	server := httptest.NewServer(kaya)
	t.Cleanup(server.Close)
	home := t.TempDir()
	for key, value := range map[string]string{
		"HOME": home, "XDG_CACHE_HOME": filepath.Join(home, "cache"), "XDG_STATE_HOME": filepath.Join(home, "state"),
		"XDG_CONFIG_HOME": filepath.Join(home, "config"), config.EnvAPIURL: server.URL, config.EnvToken: "", machine.EnvToken: "kyt_test",
		delivery.EnvDevTrustedKeys: "dev=" + base64.StdEncoding.EncodeToString(pub), "CLAUDE_CONFIG_DIR": filepath.Join(home, "claude"),
		delivery.EnvLaunched: "", delivery.EnvCheckout: "", delivery.EnvRelease: "", delivery.EnvReleaseState: "", delivery.EnvOffline: "", "CLAUDE_PROJECT_DIR": "",
	} {
		t.Setenv(key, value)
	}
	root := filepath.Join(t.TempDir(), "demo")
	if err := os.MkdirAll(filepath.Join(root, ".git"), 0o750); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(root, "kaya.yaml"), []byte("remoteUrl: git@github.com:sensey/demo.git\norganization: org-1\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	wd, err := os.Getwd()
	if err != nil {
		t.Fatal(err)
	}
	if err := os.Chdir(root); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = os.Chdir(wd) })
	return kaya, root
}

// Run runs `oba kaya <args>` on a fresh command tree of cmds and returns stdout and stderr
// together, as a terminal shows them. With --json among args it behaves as the root command
// does: the human text is dropped and the output is the JSON result only, also when the run
// failed but kept its result.
func Run(t *testing.T, cmds []*cobra.Command, stdin string, args ...string) (string, error) {
	t.Helper()
	group := &cobra.Command{Use: "kaya"}
	group.AddCommand(cmds...)
	root := &cobra.Command{Use: "oba", SilenceUsage: true, SilenceErrors: true}
	root.AddCommand(group)
	jsonOn := false
	kept := make([]string, 0, len(args)+1)
	kept = append(kept, "kaya")
	for _, arg := range args {
		if arg == "--json" {
			jsonOn = true
			continue
		}
		kept = append(kept, arg)
	}
	var out, human bytes.Buffer
	root.SetIn(strings.NewReader(stdin))
	root.SetArgs(kept)
	if jsonOn {
		jsonout.Start(&out)
		t.Cleanup(jsonout.Stop)
		root.SetOut(&human)
		root.SetErr(&human)
	} else {
		root.SetOut(&out)
		root.SetErr(&out)
	}
	_, err := root.ExecuteC()
	if jsonOn {
		if err == nil || jsonout.KeptOnFailure() {
			if flushErr := jsonout.Flush(); flushErr != nil {
				t.Fatal(flushErr)
			}
		}
		jsonout.Stop()
	}
	return out.String(), err
}

// HookEvent is the JSON of a Claude Code hook event with fields.
func HookEvent(t *testing.T, fields map[string]any) string {
	t.Helper()
	data, err := json.Marshal(fields)
	if err != nil {
		t.Fatal(err)
	}
	return string(data)
}

// Sync does what oba kaya sync does in the working directory's checkout and returns the outcome
// as it prints it, with an error when the checkout has no verified release.
func Sync(t *testing.T) (string, error) {
	t.Helper()
	ctx := context.Background()
	checkout, err := delivery.CurrentCheckout()
	if err != nil {
		return "", err
	}
	d, err := delivery.Open(ctx, checkout, true)
	if err != nil {
		return "", err
	}
	lockCtx, cancel := delivery.LockContext(ctx)
	defer cancel()
	out, err := d.Sync(lockCtx, delivery.Pin)
	if err != nil {
		return "", err
	}
	state, err := checkout.LoadState()
	if err != nil {
		return "", err
	}
	var text bytes.Buffer
	delivery.PrintOutcome(&text, "", checkout, out, state)
	if out.State == delivery.StateNone || out.State == delivery.StateUnverified {
		return text.String(), fmt.Errorf("no verified release: %s", out.State)
	}
	return text.String(), nil
}
