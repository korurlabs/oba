package delivery

import (
	"context"
	"crypto/ed25519"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/client"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/seed"
)

const (
	testOrg    = "0d6f3c1e-7d1f-4a8e-9b2a-3c4d5e6f7a8b"
	testRepoID = "repo-1"
	testRemote = "github.com/example/demo"
	testKeyID  = "kaya-test"
)

// fakeSource serves signed releases from memory and counts what it is asked for.
type fakeSource struct {
	mu          sync.Mutex
	current     client.DeliveryCurrent
	releases    map[string]client.DeliveryRelease
	blobs       map[string][]byte
	failBlob    string
	currentErr  error
	blobCalls   int
	fetchCalls  int
	currentSeen int
}

func newFakeSource() *fakeSource {
	return &fakeSource{releases: map[string]client.DeliveryRelease{}, blobs: map[string][]byte{}}
}

func (f *fakeSource) etag() string {
	return fmt.Sprintf("%q", f.current.ManifestSHA256)
}

func (f *fakeSource) CurrentRelease(_ context.Context, remoteURL, etag string) (client.DeliveryCurrent, string, bool, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.currentSeen++
	if f.currentErr != nil {
		return client.DeliveryCurrent{}, "", false, f.currentErr
	}
	if seed.NormalizeRemote(remoteURL) != testRemote {
		return client.DeliveryCurrent{}, "", false, errors.New("unknown remote " + remoteURL)
	}
	if etag != "" && etag == f.etag() {
		return client.DeliveryCurrent{}, etag, true, nil
	}
	return f.current, f.etag(), false, nil
}

func (f *fakeSource) ReleaseByID(_ context.Context, id string) (client.DeliveryRelease, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.fetchCalls++
	release, ok := f.releases[id]
	if !ok {
		return client.DeliveryRelease{}, &client.APIError{Status: 404, Code: "KAY_NF_001"}
	}
	return release, nil
}

func (f *fakeSource) Blob(_ context.Context, sha string) ([]byte, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.blobCalls++
	if sha == f.failBlob {
		return nil, errors.New("connection reset")
	}
	data, ok := f.blobs[sha]
	if !ok {
		return nil, &client.APIError{Status: 404, Code: "KAY_NF_001"}
	}
	return data, nil
}

type spec struct {
	path, content, mode, target string
	alwaysOn                    bool
}

type fixture struct {
	t      *testing.T
	root   string
	priv   ed25519.PrivateKey
	keys   Keys
	source *fakeSource
	d      *Deliverer
}

func newFixture(t *testing.T) *fixture {
	t.Helper()
	isolateConfig(t)
	root := gitCheckout(t, "origin", "https://github.com/Example/demo.git")
	listRemotes(t, testOrg, testRemote)
	pub, priv, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	checkout, err := FindCheckout(filepath.Join(root))
	if err != nil {
		t.Fatal(err)
	}
	if checkout.Kind != Delivered {
		t.Fatalf("kind = %d, want Delivered", checkout.Kind)
	}
	f := &fixture{t: t, root: root, priv: priv, keys: Keys{testKeyID: pub}, source: newFakeSource()}
	f.d = &Deliverer{Checkout: checkout, Source: f.source, Keys: f.keys, Cache: Cache{Dir: filepath.Join(t.TempDir(), "blobs")}, OnlineTimeout: 5 * time.Second}
	return f
}

func sha(content string) string {
	sum := sha256.Sum256([]byte(content))
	return hex.EncodeToString(sum[:])
}

// release signs a release and makes it the server's current one.
func (f *fixture) release(number int, files []spec, mutate ...func(*Manifest)) client.DeliveryRelease {
	f.t.Helper()
	manifest := Manifest{Organization: testOrg, RemoteURL: testRemote, Number: number, CreatedAt: fmt.Sprintf("2026-10-%02dT10:00:00.000Z", number)}
	for _, s := range files {
		file := ManifestFile{Path: s.path, Mode: s.mode, AlwaysOn: s.alwaysOn}
		if file.Mode == "" {
			file.Mode = ModeFile
		}
		if file.Mode == ModeSymlink {
			file.Target = s.target
		} else {
			file.SHA256 = sha(s.content)
			f.source.blobs[file.SHA256] = []byte(s.content)
		}
		manifest.Files = append(manifest.Files, file)
	}
	for _, m := range mutate {
		m(&manifest)
	}
	text, err := json.Marshal(manifest)
	if err != nil {
		f.t.Fatal(err)
	}
	signed := client.DeliveryRelease{
		ReleaseID: fmt.Sprintf("rel-%d", number), RepositoryID: testRepoID, Number: number, Manifest: string(text),
		ManifestSHA256: sha(string(text)), Signature: base64.StdEncoding.EncodeToString(ed25519.Sign(f.priv, text)), KeyID: testKeyID,
	}
	f.source.releases[signed.ReleaseID] = signed
	f.source.current = client.DeliveryCurrent{RepositoryID: testRepoID, ReleaseID: signed.ReleaseID, Number: number, ManifestSHA256: signed.ManifestSHA256}
	return signed
}

func (f *fixture) sync(policy Policy) Outcome {
	f.t.Helper()
	out, err := f.d.Sync(context.Background(), policy)
	if err != nil {
		f.t.Fatalf("sync: %v", err)
	}
	return out
}

func (f *fixture) read(path string) string {
	f.t.Helper()
	data, err := os.ReadFile(filepath.Join(f.root, path))
	if err != nil {
		f.t.Fatalf("read %s: %v", path, err)
	}
	return string(data)
}

func baseFiles(agents string) []spec {
	return []spec{
		{path: "AGENTS.md", content: agents, alwaysOn: true},
		{path: ".agents/skills/demo/SKILL.md", content: "---\nname: demo\n---\nskill v1\n"},
		{path: ".agents/hooks/dispatch.sh", content: "#!/bin/sh\nexit 0\n", mode: ModeExecutable},
		{path: ".claude/settings.json", content: "{\n  \"permissions\": {\n    \"deny\": [\"Bash(rm:*)\"]\n  }\n}\n"},
		{path: ".claude/skills/demo", mode: ModeSymlink, target: "../../.agents/skills/demo"},
	}
}

func TestSync_placesTheFirstReleaseAndReportsFresh(t *testing.T) {
	f := newFixture(t)
	f.release(1, baseFiles("rules v1\n"))

	out := f.sync(Pin)

	if out.State != StateFresh || out.Active != 1 || !out.Switched {
		t.Fatalf("outcome = %+v", out)
	}
	if got := f.read("AGENTS.md"); got != "rules v1\n" {
		t.Fatalf("AGENTS.md = %q", got)
	}
	if target, err := os.Readlink(filepath.Join(f.root, "AGENTS.md")); err != nil || !strings.HasPrefix(target, ".kaya/current/") {
		t.Fatalf("AGENTS.md links to %q (%v)", target, err)
	}
	if got := f.read(".claude/skills/demo/SKILL.md"); !strings.Contains(got, "skill v1") {
		t.Fatalf("skill through the link = %q", got)
	}
	info, err := os.Stat(filepath.Join(f.root, ".kaya/versions/1/.agents/hooks/dispatch.sh"))
	if err != nil || info.Mode().Perm() != 0o755 {
		t.Fatalf("dispatch.sh mode = %v (%v)", info, err)
	}
	info, err = os.Stat(filepath.Join(f.root, ".kaya/versions/1/AGENTS.md"))
	if err != nil || info.Mode().Perm() != 0o644 {
		t.Fatalf("AGENTS.md mode = %v (%v)", info, err)
	}
	settings, err := os.Lstat(filepath.Join(f.root, SettingsPath))
	if err != nil || !settings.Mode().IsRegular() {
		t.Fatalf("settings is not a regular file: %v", err)
	}
	if !strings.Contains(f.read(ReleaseRecordPath), `"number": 1`) {
		t.Fatalf("release record = %s", f.read(ReleaseRecordPath))
	}
}

func TestSync_refusesWhatFailsVerificationAndKeepsTheCache(t *testing.T) {
	cases := map[string]func(f *fixture){
		"bad signature": func(f *fixture) {
			signed := f.release(1, baseFiles("v1\n"))
			signed.Signature = base64.StdEncoding.EncodeToString(make([]byte, ed25519.SignatureSize))
			f.source.releases[signed.ReleaseID] = signed
		},
		"unknown key": func(f *fixture) {
			signed := f.release(1, baseFiles("v1\n"))
			signed.KeyID = "other"
			f.source.releases[signed.ReleaseID] = signed
		},
		"wrong remote": func(f *fixture) {
			f.release(1, baseFiles("v1\n"), func(m *Manifest) { m.RemoteURL = "github.com/sensey/other" })
		},
		"wrong organization": func(f *fixture) {
			f.release(1, baseFiles("v1\n"), func(m *Manifest) { m.Organization = "another-org" })
		},
		"bad blob hash": func(f *fixture) {
			f.release(1, baseFiles("v1\n"))
			f.source.blobs[sha("v1\n")] = []byte("tampered\n")
		},
		"path outside the allow-list": func(f *fixture) {
			f.release(1, append(baseFiles("v1\n"), spec{path: ".github/workflows/x.yml", content: "on: push\n"}))
		},
		"hook as a link": func(f *fixture) {
			f.release(1, append(baseFiles("v1\n"), spec{path: ".agents/hooks/pre-bash-x.sh", mode: ModeSymlink, target: "dispatch.sh"}))
		},
	}
	for name, setup := range cases {
		t.Run(name, func(t *testing.T) {
			f := newFixture(t)
			setup(f)

			out := f.sync(Pin)

			if out.State != StateNone || out.Active != 0 || out.Offline == nil {
				t.Fatalf("outcome = %+v", out)
			}
			if _, err := os.Lstat(filepath.Join(f.root, ".kaya/versions/1")); err == nil {
				t.Fatal("a refused release was built")
			}
			if _, err := os.Lstat(filepath.Join(f.root, "AGENTS.md")); err == nil {
				t.Fatal("a refused release was placed")
			}
			if f.d.Cache.Has(sha("tampered\n")) {
				t.Fatal("content with the wrong hash reached the cache")
			}
		})
	}
}

func TestSync_refusesAnOlderReleaseNumber(t *testing.T) {
	f := newFixture(t)
	f.release(2, baseFiles("v2\n"))
	f.sync(Pin)
	f.release(1, baseFiles("v1\n"))
	f.source.current.ReleaseID = "rel-1"

	out := f.sync(Pin)

	if out.Active != 2 || out.State != StateCached || out.Offline == nil || !strings.Contains(out.Offline.Error(), "older") {
		t.Fatalf("outcome = %+v (offline %v)", out, out.Offline)
	}
	if got := f.read("AGENTS.md"); got != "v2\n" {
		t.Fatalf("AGENTS.md = %q", got)
	}
}

func TestSync_notModifiedDownloadsNothing(t *testing.T) {
	f := newFixture(t)
	f.release(1, baseFiles("v1\n"))
	f.sync(Pin)
	blobs, fetches := f.source.blobCalls, f.source.fetchCalls

	out := f.sync(Pin)

	if out.State != StateFresh || f.source.blobCalls != blobs || f.source.fetchCalls != fetches {
		t.Fatalf("outcome %+v, blob calls %d -> %d, fetches %d -> %d", out, blobs, f.source.blobCalls, fetches, f.source.fetchCalls)
	}
}

func TestSync_downloadsOnlyChangedBlobsAndRemovesStalePaths(t *testing.T) {
	f := newFixture(t)
	files := append(baseFiles("v1\n"), spec{path: ".agents/rules/old.md", content: "old\n"})
	f.release(1, files)
	f.sync(Pin)
	blobs := f.source.blobCalls

	f.release(2, baseFiles("v2\n"))
	out := f.sync(Pin)

	if !out.Switched || out.Active != 2 || f.source.blobCalls != blobs+1 {
		t.Fatalf("outcome %+v, blob calls %d -> %d", out, blobs, f.source.blobCalls)
	}
	if _, err := os.Lstat(filepath.Join(f.root, ".agents/rules/old.md")); err == nil {
		t.Fatal("a path release 2 no longer delivers is still there")
	}
	if got := f.read("AGENTS.md"); got != "v2\n" {
		t.Fatalf("AGENTS.md = %q", got)
	}
	if strings.Join(out.Changed, ",") != "AGENTS.md,.agents/rules/old.md (removed)" {
		t.Fatalf("changed = %v", out.Changed)
	}
}

func TestSync_failureMidDownloadKeepsTheActiveRelease(t *testing.T) {
	f := newFixture(t)
	f.release(1, baseFiles("v1\n"))
	f.sync(Pin)
	files := baseFiles("v2\n")
	for i := range 6 {
		files = append(files, spec{path: fmt.Sprintf(".agents/rules/r%d.md", i), content: fmt.Sprintf("rule %d\n", i)})
	}
	f.release(2, files)
	f.source.failBlob = sha("rule 3\n")

	out := f.sync(Pin)

	if out.Active != 1 || out.State != StateCached || out.Switched {
		t.Fatalf("outcome = %+v", out)
	}
	if got := f.read("AGENTS.md"); got != "v1\n" {
		t.Fatalf("AGENTS.md = %q", got)
	}
	if _, err := os.Lstat(filepath.Join(f.root, ".kaya/versions/2")); err == nil {
		t.Fatal("a partial version was built")
	}
	f.source.failBlob = ""
	if out := f.sync(Pin); out.Active != 2 || out.State != StateFresh {
		t.Fatalf("retry outcome = %+v", out)
	}
}

func TestSync_liveSessionPinsTheReleaseUntilRefresh(t *testing.T) {
	f := newFixture(t)
	f.release(1, baseFiles("v1\n"))
	f.sync(Pin)
	if err := f.d.Checkout.RegisterSession(Session{PID: os.Getpid(), Release: 1}); err != nil {
		t.Fatal(err)
	}
	f.release(2, baseFiles("v2\n"))

	out := f.sync(Pin)

	if out.State != StateStale || out.Active != 1 || out.Staged != 2 || len(out.LiveSessions) != 1 {
		t.Fatalf("pinned outcome = %+v", out)
	}
	if got := f.read("AGENTS.md"); got != "v1\n" {
		t.Fatalf("AGENTS.md = %q", got)
	}

	out = f.sync(Force)

	if out.State != StateFresh || out.Active != 2 || !out.Switched {
		t.Fatalf("forced outcome = %+v", out)
	}
}

func TestSync_restoresTheReleaseSettingsOverAnOldRevocationRule(t *testing.T) {
	f := newFixture(t)
	f.release(1, baseFiles("v1\n"))
	f.sync(Pin)
	want := f.read(SettingsPath)
	// An earlier CLI merged a revocation rule into the delivered settings file.
	old := "{\n  \"permissions\": {\n    \"deny\": [\"Bash(rm:*)\", \"Bash(curl:*)\"]\n  }\n}\n"
	if err := os.WriteFile(filepath.Join(f.root, SettingsPath), []byte(old), 0o600); err != nil {
		t.Fatal(err)
	}

	out := f.sync(Pin)

	if len(out.Problems) != 0 || len(out.Restored) != 1 {
		t.Fatalf("outcome = %+v", out)
	}
	if got := f.read(SettingsPath); got != want || strings.Contains(got, "curl") {
		t.Fatalf("settings = %q, want the release file %q", got, want)
	}
}

func TestPoll_buildsANewerReleaseWithoutSwitching(t *testing.T) {
	f := newFixture(t)
	f.release(1, baseFiles("v1\n"))
	f.sync(Pin)
	f.release(2, baseFiles("v2\n"))

	if err := f.d.Poll(context.Background()); err != nil {
		t.Fatalf("poll: %v", err)
	}

	if !f.d.Checkout.hasVersion(2) {
		t.Fatal("release 2 was not built")
	}
	if got := f.read("AGENTS.md"); got != "v1\n" {
		t.Fatalf("AGENTS.md = %q, the poll switched the checkout", got)
	}
	state, err := f.d.Checkout.LoadState()
	if err != nil {
		t.Fatal(err)
	}
	if state.Latest != 2 || state.Active != 1 {
		t.Fatalf("state = %+v, want latest 2 and active 1", state)
	}
}

func TestSync_hookSwitchesASkillChangeAndStagesAnAlwaysOnChange(t *testing.T) {
	f := newFixture(t)
	f.release(1, baseFiles("v1\n"))
	f.sync(Pin)

	skill := baseFiles("v1\n")
	skill[1].content = "---\nname: demo\n---\nskill v2\n"
	f.release(2, skill)
	out := f.sync(KeepAlwaysOn)
	if !out.Switched || out.Active != 2 || out.State != StateFresh {
		t.Fatalf("skill change outcome = %+v", out)
	}

	f.release(3, baseFiles("v3\n"))
	out = f.sync(KeepAlwaysOn)
	if out.Switched || out.Active != 2 || out.Staged != 3 || !out.AlwaysOnStaged || out.State != StateStale {
		t.Fatalf("always-on change outcome = %+v", out)
	}
}

func TestActivateStaged_onlyWithoutAnotherLiveSession(t *testing.T) {
	f := newFixture(t)
	f.release(1, baseFiles("v1\n"))
	f.sync(Pin)
	if err := f.d.Checkout.RegisterSession(Session{PID: os.Getpid(), SessionID: "s-1"}); err != nil {
		t.Fatal(err)
	}
	f.release(2, baseFiles("v2\n"))
	f.sync(Pin)
	f.d.Source = nil

	if n, err := f.d.ActivateStaged(context.Background()); err != nil || n != 0 {
		t.Fatalf("with a live session: %d, %v", n, err)
	}
	f.d.ExcludePID = os.Getpid()
	n, err := f.d.ActivateStaged(context.Background())
	if err != nil || n != 2 {
		t.Fatalf("without another session: %d, %v", n, err)
	}
	if got := f.read("AGENTS.md"); got != "v2\n" {
		t.Fatalf("AGENTS.md = %q", got)
	}
}

func TestSync_offlineUsesTheCacheAndSaysSo(t *testing.T) {
	f := newFixture(t)
	f.release(1, baseFiles("v1\n"))
	f.sync(Pin)
	f.source.currentErr = errors.New("dial tcp: connection refused")

	out := f.sync(Pin)

	if out.State != StateCached || out.Active != 1 || out.Offline == nil {
		t.Fatalf("outcome = %+v", out)
	}
}

func TestSync_restoresATamperedFileWithoutTheNetwork(t *testing.T) {
	f := newFixture(t)
	f.release(1, baseFiles("v1\n"))
	f.sync(Pin)
	agents := filepath.Join(f.root, "AGENTS.md")
	if err := os.Remove(agents); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(agents, []byte("edited by hand\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	cached := filepath.Join(f.root, ".kaya/versions/1/.agents/skills/demo/SKILL.md")
	if err := os.WriteFile(cached, []byte("tampered\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(f.root, SettingsPath), []byte("{}\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	f.d.Source = nil

	out := f.sync(Pin)

	if len(out.Restored) != 3 || len(out.Problems) != 0 || out.State != StateCached {
		t.Fatalf("outcome = %+v", out)
	}
	if got := f.read("AGENTS.md"); got != "v1\n" {
		t.Fatalf("AGENTS.md = %q", got)
	}
	if got := f.read(".claude/skills/demo/SKILL.md"); !strings.Contains(got, "skill v1") {
		t.Fatalf("skill = %q", got)
	}
}

func TestSync_unrestorableDamageIsUnverified(t *testing.T) {
	f := newFixture(t)
	f.release(1, baseFiles("v1\n"))
	f.sync(Pin)
	if err := os.WriteFile(filepath.Join(f.root, ".kaya/versions/1/AGENTS.md"), []byte("x\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	if err := os.RemoveAll(f.d.Cache.Dir); err != nil {
		t.Fatal(err)
	}
	f.d.Source = nil

	if out := f.sync(Pin); out.State != StateUnverified || len(out.Problems) == 0 {
		t.Fatalf("outcome = %+v", out)
	}
}

func TestSync_editedStoredReleaseIsNotTrusted(t *testing.T) {
	f := newFixture(t)
	f.release(1, baseFiles("v1\n"))
	f.sync(Pin)
	path := filepath.Join(f.root, ".kaya/releases/1.json")
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(path, []byte(strings.Replace(string(data), "AGENTS.md", "AGENTX.md", 1)), 0o600); err != nil {
		t.Fatal(err)
	}
	f.d.Source = nil

	if out := f.sync(Pin); out.State != StateUnverified {
		t.Fatalf("outcome = %+v", out)
	}
}

func mustActive(t *testing.T, f *fixture) *Release {
	t.Helper()
	active, _, err := f.d.ActiveRelease()
	if err != nil || active == nil {
		t.Fatalf("active release: %v", err)
	}
	return active
}

func TestValidateManifest_refusesLinkAndCaseProblems(t *testing.T) {
	file := func(path string) ManifestFile { return ManifestFile{Path: path, Mode: ModeFile, SHA256: sha(path)} }
	link := func(path, target string) ManifestFile {
		return ManifestFile{Path: path, Mode: ModeSymlink, Target: target}
	}
	cases := map[string][]ManifestFile{
		"SKILL.md as a link":   {file(".agents/skills/a/x.md"), link(".agents/skills/a/SKILL.md", "x.md")},
		"settings as a link":   {file(".agents/rules/s.json"), link(".claude/settings.json", "../.agents/rules/s.json")},
		"case conflict":        {file("AGENTS.md"), file("agents.md")},
		"path below a link":    {file(".agents/skills/a/SKILL.md"), link(".claude/skills/a", "../../.agents/skills/a"), file(".claude/skills/a/extra.md")},
		"link out of the repo": {link(".claude/skills/a", "../../../elsewhere")},
		"absolute target":      {link(".claude/skills/a", "/etc")},
		"undelivered target":   {link(".claude/skills/a", "../../.agents/skills/missing")},
		"link loop":            {link(".claude/skills/a", "b"), link(".claude/skills/b", "a")},
		"traversal":            {file(".agents/../etc/passwd")},
		"listed twice":         {file("AGENTS.md"), file("AGENTS.md")},
	}
	for name, files := range cases {
		t.Run(name, func(t *testing.T) {
			if err := ValidateManifest(Manifest{Files: files}); err == nil {
				t.Fatal("accepted")
			}
		})
	}
	ok := Manifest{Files: []ManifestFile{file(".agents/skills/a/SKILL.md"), link(".claude/skills/a", "../../.agents/skills/a")}}
	if err := ValidateManifest(ok); err != nil {
		t.Fatalf("refused an adapter link: %v", err)
	}
}

func TestManifestProblems_toolLinkToADeliveredDirectory(t *testing.T) {
	file := func(path string) ManifestFile { return ManifestFile{Path: path, Mode: ModeFile, SHA256: sha(path)} }
	link := func(path, target string) ManifestFile {
		return ManifestFile{Path: path, Mode: ModeSymlink, Target: target}
	}
	ok := Manifest{Files: []ManifestFile{
		file(".agents/AGENTS.md"), file(".agents/rules/core.md"), file(".agents/templates/handoff.md"),
		{Path: "AGENTS.md", Mode: ModeSymlink, Target: ".agents/AGENTS.md", AlwaysOn: true},
		link(".claude/rules", "../.agents/rules"), link(".claude/templates", "../.agents/templates"),
	}}
	if problems := ManifestProblems(ok); len(problems) != 0 {
		t.Fatalf("refused the tool links: %v", problems)
	}
	cases := map[string][]ManifestFile{
		"link to .git":               {link(".claude/rules", "../.git")},
		"link to an empty dir":       {file(".agents/AGENTS.md"), link(".claude/rules", "../.agents/rules")},
		"link to an undelivered dir": {link(".claude/rules", "../.agents/memory")},
		"path below a tool link":     {file(".agents/rules/core.md"), link(".claude/rules", "../.agents/rules"), file(".claude/rules/x.md")},
		"always-on link to a dir": {file(".agents/rules/core.md"),
			{Path: "AGENTS.md", Mode: ModeSymlink, Target: ".agents/rules", AlwaysOn: true}},
	}
	for name, files := range cases {
		t.Run(name, func(t *testing.T) {
			if problems := ManifestProblems(Manifest{Files: files}); len(problems) == 0 {
				t.Fatal("accepted")
			}
		})
	}
}

func TestSync_writesTheToolLinksAndRemovesOneALaterReleaseDrops(t *testing.T) {
	f := newFixture(t)
	links := []spec{
		{path: ".agents/AGENTS.md", content: "agents v1\n"},
		{path: ".agents/rules/core.md", content: "core v1\n"},
		{path: ".agents/templates/handoff.md", content: "handoff v1\n"},
		{path: "AGENTS.md", mode: ModeSymlink, target: ".agents/AGENTS.md", alwaysOn: true},
		{path: ".claude/rules", mode: ModeSymlink, target: "../.agents/rules"},
		{path: ".claude/templates", mode: ModeSymlink, target: "../.agents/templates"},
	}
	f.release(1, links)

	out := f.sync(Pin)
	if !out.Switched || out.Active != 1 {
		t.Fatalf("outcome = %+v", out)
	}
	for path, target := range map[string]string{
		"AGENTS.md": ".agents/AGENTS.md", ".claude/rules": "../.agents/rules", ".claude/templates": "../.agents/templates",
	} {
		if got, err := os.Readlink(filepath.Join(f.root, path)); err != nil || got != target {
			t.Fatalf("%s links to %q (%v), want %q", path, got, err, target)
		}
	}
	if got := f.read(".claude/rules/core.md"); got != "core v1\n" {
		t.Fatalf(".claude/rules/core.md = %q", got)
	}
	if got := f.read("AGENTS.md"); got != "agents v1\n" {
		t.Fatalf("AGENTS.md = %q", got)
	}
	if problems := f.d.Checkout.Inspect(mustActive(t, f).Manifest, f.d.Cache); len(problems) != 0 {
		t.Fatalf("the placed tool links do not match the release: %v", problems)
	}

	f.release(2, links[:5])
	if out = f.sync(Pin); !out.Switched || out.Active != 2 {
		t.Fatalf("outcome = %+v", out)
	}
	if _, err := os.Lstat(filepath.Join(f.root, ".claude/templates")); !errors.Is(err, os.ErrNotExist) {
		t.Fatalf(".claude/templates outlived the release that dropped it: %v", err)
	}
	if got := f.read(".agents/templates/handoff.md"); got != "handoff v1\n" {
		t.Fatalf("the link's removal touched its target: %q", got)
	}
}

func TestPlace_refusesAPathBelowALinkInTheCheckout(t *testing.T) {
	f := newFixture(t)
	outside := t.TempDir()
	if err := os.Symlink(outside, filepath.Join(f.root, ".agents")); err != nil {
		t.Fatal(err)
	}
	f.release(1, baseFiles("v1\n"))

	out := f.sync(Pin)

	if out.Switched {
		t.Fatalf("switched through a link: %+v", out)
	}
	entries, err := os.ReadDir(outside)
	if err != nil || len(entries) != 0 {
		t.Fatalf("wrote through the link: %v %v", entries, err)
	}
}

func TestMergeDeny_addsRulesOnceAndKeepsTheRest(t *testing.T) {
	content := []byte("{\n  \"model\": \"opus\",\n  \"permissions\": {\n    \"allow\": [\"Read\"],\n    \"deny\": [\"Bash(rm:*)\"]\n  }\n}\n")
	if got, err := MergeDeny(content); err != nil || string(got) != string(content) {
		t.Fatalf("without rules: %q, %v", got, err)
	}
	got, err := MergeDeny(content, "Bash(curl:*)", "Bash(rm:*)")
	if err != nil {
		t.Fatal(err)
	}
	var parsed struct {
		Model       string `json:"model"`
		Permissions struct {
			Allow []string `json:"allow"`
			Deny  []string `json:"deny"`
		} `json:"permissions"`
	}
	if err := json.Unmarshal(got, &parsed); err != nil {
		t.Fatal(err)
	}
	if parsed.Model != "opus" || strings.Join(parsed.Permissions.Allow, ",") != "Read" || strings.Join(parsed.Permissions.Deny, ",") != "Bash(rm:*),Bash(curl:*)" {
		t.Fatalf("merged = %s", got)
	}
	if strings.Index(string(got), "model") > strings.Index(string(got), "permissions") {
		t.Fatalf("key order changed: %s", got)
	}
}

func TestTrustedKeys_devKeysOnlyForALoopbackAPI(t *testing.T) {
	pub, _, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	t.Setenv(EnvDevTrustedKeys, "dev="+base64.StdEncoding.EncodeToString(pub))
	local, err := TrustedKeys("http://localhost:8080")
	if err != nil || local["dev"] == nil {
		t.Fatalf("local: %v, %v", local, err)
	}
	remote, err := TrustedKeys("https://api.korur.com")
	if err != nil || remote["dev"] != nil {
		t.Fatalf("remote trusts the dev key: %v, %v", remote, err)
	}
}

func TestWriteReport_writesTheRecorderRecord(t *testing.T) {
	t.Setenv("XDG_STATE_HOME", t.TempDir())
	if err := WriteReport("sess-1", NewReport(4, "ABC", StateStale, DeliveredByHook)); err != nil {
		t.Fatal(err)
	}
	base, err := RecorderStateDir()
	if err != nil {
		t.Fatal(err)
	}
	data, err := os.ReadFile(filepath.Join(base, "queue", "sessions", "sess-1", "config-release.json"))
	if err != nil {
		t.Fatal(err)
	}
	if string(data) != `{"number":4,"manifestSha256":"abc","state":"STALE","deliveredBy":"HOOK"}`+"\n" {
		t.Fatalf("report = %s", data)
	}
	if err := WriteReport("sess-2", NewReport(0, "", StateNone, DeliveredByLauncher)); err != nil {
		t.Fatal(err)
	}
	data, err = os.ReadFile(filepath.Join(base, "queue", "sessions", "sess-2", "config-release.json"))
	if err != nil || string(data) != `{"number":null,"manifestSha256":null,"state":"NONE","deliveredBy":"LAUNCHER"}`+"\n" {
		t.Fatalf("none report = %s (%v)", data, err)
	}
	if WriteReport("../escape", NewReport(1, "a", StateFresh, DeliveredByHook)) == nil {
		t.Fatal("a session id with a path was accepted")
	}
}

func TestCheckout_notCutOverIsLeftAlone(t *testing.T) {
	isolateConfig(t)
	root := gitCheckout(t, "origin", "git@github.com:example/app.git")
	listRemotes(t, testOrg, "github.com/example/app")
	if err := os.Mkdir(filepath.Join(root, ".agents"), 0o750); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(root, ".agents", ".managed.json"), []byte("{}\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	checkout, err := FindCheckout(filepath.Join(root, ".agents"))
	if err != nil || checkout.Kind != NotCutOver || checkout.Root != root || checkout.Remote != "github.com/example/app" {
		t.Fatalf("checkout = %+v, %v", checkout, err)
	}
}

func TestFileProblem_refusesPipelineFilesAndMcpConfig(t *testing.T) {
	sha := strings.Repeat("a", 64)
	for _, file := range []ManifestFile{
		{Path: ".agents/pipelines/x.yaml", SHA256: sha, Mode: ModeFile},
	} {
		if problem := fileProblem(file); problem == "" {
			t.Errorf("fileProblem(%s) = none, want the pipeline file refused", file.Path)
		}
	}
	for _, link := range []ManifestFile{
		{Path: ".claude/rules", Mode: ModeSymlink, Target: "../.agents/rules"},
		{Path: ".claude/templates", Mode: ModeSymlink, Target: "../.agents/templates"},
	} {
		if problem := fileProblem(link); problem != "" {
			t.Errorf("fileProblem(%s) = %q, want none", link.Path, problem)
		}
	}
	for _, mcp := range []ManifestFile{
		{Path: ".mcp.json", SHA256: sha, Mode: ModeFile},
		{Path: "projects/kagi/.mcp.json", SHA256: sha, Mode: ModeFile},
	} {
		if problem := fileProblem(mcp); problem == "" {
			t.Errorf("fileProblem(%s) = none, want the MCP config refused", mcp.Path)
		}
	}
}
