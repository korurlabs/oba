// End-to-end test of Kaya's pack tree and config chain on the local stack: the real backend, the
// real Keycloak, the real oba binary, a scratch copy of this repository's agent config and a
// scratch git checkout. oba kaya launch runs a stub script in place of claude. Only one step starts
// the real Claude Code, and only when `claude` is on the PATH (see below).
//
// The chain it proves, in order:
//
//  1. oba kaya seed imports a copy of this repository's agent config into Kaya as a pack tree: the
//     packs of a korurlabs/agents clone under the root pack org (mdf reported as a deprecated
//     pack, not imported), the repository's own files in its family pack village, and a pack
//     kagi mounted in the folder projects/kagi whose folder AGENTS.md is sent as AGENTS.md.
//  2. The stored tree, the repository's pack list with the mount folder and the tool links of the
//     family pack read as the seed planned them.
//  3. oba kaya launch puts the signed release into a scratch checkout: the delivered files, the three
//     tool links (AGENTS.md, .claude/rules, .claude/templates) and the mounted pack's rule and
//     folder AGENTS.md appear, and the launched command sees KAYA_RELEASE.
//  4. Real Claude Code loads the kagi rule (seen by an InstructionsLoaded hook) only when it
//     reads a file under projects/kagi. AGENTS.md files come from Claude Code's built-in
//     agents_md feature, which fires no InstructionsLoaded hook, so codewords prove them: the
//     seed carries one in the root AGENTS.md and one in the mounted pack's folder AGENTS.md,
//     and the model must report the root codeword without reading anything, the folder
//     codeword only after reading a file under projects/kagi, and NONE for the folder after
//     reading a file under projects/kazan.
//     A second real Claude Code session, with the built binary first on its PATH, the e2e
//     machine token and the test backend in its environment, is recorded by the oba kaya hook record
//     rows of the delivered settings and sent by the detached oba kaya sender: GET /sessions/{id}
//     answers it with its user, model, branch and repository, and GET /sessions/{id}/events
//     holds SessionStart, UserPromptSubmit, PostToolUse of Bash and Stop with gapless seq.
//     The machine is registered under the hostname, which the capture reports as machine name.
//  5. A MEMBER drafts and submits a change to the family pack through the API, the proposer
//     cannot approve it, and an OWNER (a second member) approves it.
//  6. A new session (oba kaya launch) and oba kaya sync see the new release and the new file.
//
// The test is skipped unless KAYA_E2E=1, so `make test` and `go test ./...` stay green without
// a stack. It adds packs, one repository with its releases, one machine
// to the Korur organization of the database it runs against, and the release it approves adds
// the file .agents/templates/e2e-marker.md to the pack village; run it against a throwaway
// database. Step 4 sends two short prompts to the Anthropic API through the developer's own
// Claude Code sign-in; KAYA_E2E_CLAUDE=0 skips it. The capture session keeps its transcript in
// ~/.claude/projects until the test ends, because Claude Code reports the model only there.
//
// To run it:
//
//	cd projects/kaya/backend
//	podman compose up -d postgres
//	podman compose --profile auth up -d keycloak     # realm kaya on :8087, users dev and dev2
//	java -jar target/kaya-api-0.0.1-SNAPSHOT.jar     # :8083, after mvn clean package, with the
//	                                                 # environment of projects/kaya/README.md
//	cd cli/oba
//	KAYA_E2E=1 KAYA_E2E_AGENTS=<clone of korurlabs/agents> go test -count=1 -v -timeout 10m ./e2e/kaya/
//
// It needs: the backend on :8083 with a release signing key (CONFIG_SIGNING_KEY and
// CONFIG_SIGNING_KEY_ID; no Kagi value is otherwise required), Keycloak on :8087 with the realm
// file projects/kaya/backend/src/main/resources/keycloak/kaya-realm.json (user dev@example.com is
// the OWNER of Korur, dev2@example.com is a MEMBER, password 1234 for both, and the confidential
// client web with its dev secret, whose password grant mints the tokens: the oba-cli client
// has only the device grant, which needs a browser), a clone of korurlabs/agents in
// KAYA_E2E_AGENTS (a current clone: before the seed the test compares every managed file of
// .agents/.managed.json with the clone and fails fast when one differs, because the release
// would then differ from the disk and the seed would not exit 0), a Go toolchain (the test builds the binary), git, a POSIX shell and cp. It
// uses a scratch HOME for oba, so the developer's own Oba sign-in is not touched. Running it
// takes about a minute.
//
// Variables: KAYA_E2E_API (default http://localhost:8083), KAYA_E2E_KEYCLOAK (default
// http://localhost:8087), KAYA_E2E_WEB_SECRET (default the dev secret of the realm file),
// KAYA_E2E_AGENTS (required: the current clone whose packs the seed imports), KAYA_E2E_REPO_ROOT (the
// repository whose agent config is copied; default the repository this file is in) and
// KAYA_E2E_CLAUDE (0 skips the real Claude Code step).
//
// A run killed between submit and approve leaves a draft or a version in review on the pack
// village; the next run then fails at submit with KAY_CFG_015. Delete the draft or reject the
// version in the Kaya web app (or through DELETE /drafts/{id}) and run again.
package e2e

import (
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"sort"
	"strings"
	"testing"
	"time"
)

const (
	// repositoryName is the repository the seed creates from the remote of kaya.yaml, and
	// familyPack the pack that takes its own files: the last pack of .agents/.managed.json
	// other than org.
	repositoryName = "village"
	familyPack     = "village"
	rootPack       = "org"
	// mountedPack is mounted in mountFolder of the repository and is no pack of the agents clone.
	mountedPack = "kagi"
	mountFolder = "projects/kagi"
	markerPath  = ".agents/templates/e2e-marker.md"
	// ownFile is a file only this repository has: no pack of the clone delivers it, so the seed
	// folds it into the family pack.
	ownFile = ".agents/templates/e2e-own.md"
)

// stack is the running local services and the identities the test acts as.
type stack struct {
	t        *testing.T
	api      string
	keycloak string
	secret   string
	orgID    string
}

// envelope is the CustomResponse every Kaya endpoint answers with.
type envelope struct {
	Success bool            `json:"success"`
	Data    json.RawMessage `json:"data"`
	Error   *struct {
		Code        string         `json:"code"`
		Message     string         `json:"message"`
		ErrorValues map[string]any `json:"errorValues"`
	} `json:"error"`
}

func getenv(name, fallback string) string {
	if value := strings.TrimSpace(os.Getenv(name)); value != "" {
		return value
	}
	return fallback
}

// token mints an access token for a realm user through the web client's password grant.
func (s *stack) token(user string) string {
	s.t.Helper()
	form := url.Values{
		"grant_type": {"password"}, "client_id": {"web"}, "client_secret": {s.secret},
		"username": {user}, "password": {"1234"}, "scope": {"openid"},
	}
	resp, err := http.PostForm(s.keycloak+"/realms/kaya/protocol/openid-connect/token", form)
	if err != nil {
		s.t.Fatalf("Keycloak at %s is not reachable: %v", s.keycloak, err)
	}
	defer func() { _ = resp.Body.Close() }()
	body, _ := io.ReadAll(resp.Body)
	var parsed struct {
		AccessToken string `json:"access_token"`
	}
	if err := json.Unmarshal(body, &parsed); err != nil || parsed.AccessToken == "" {
		s.t.Fatalf("no token for %s (status %d): %s", user, resp.StatusCode, body)
	}
	return parsed.AccessToken
}

// call sends a request as user ("" for none) and returns the status and the decoded envelope.
func (s *stack) call(user, method, path string, body any) (int, envelope) {
	s.t.Helper()
	var reader io.Reader
	if body != nil {
		encoded, err := json.Marshal(body)
		if err != nil {
			s.t.Fatalf("encode the body of %s %s: %v", method, path, err)
		}
		reader = bytes.NewReader(encoded)
	}
	req, err := http.NewRequest(method, s.api+path, reader)
	if err != nil {
		s.t.Fatalf("build %s %s: %v", method, path, err)
	}
	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	if user != "" {
		req.Header.Set("Authorization", "Bearer "+s.token(user))
	}
	if s.orgID != "" {
		req.Header.Set("X-Organization-ID", s.orgID)
	}
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		s.t.Fatalf("%s %s: %v", method, path, err)
	}
	defer func() { _ = resp.Body.Close() }()
	raw, _ := io.ReadAll(resp.Body)
	var env envelope
	if err := json.Unmarshal(raw, &env); err != nil {
		s.t.Fatalf("%s %s answered %d, not an envelope: %s", method, path, resp.StatusCode, raw)
	}
	return resp.StatusCode, env
}

// must calls and requires the wanted status, decoding data into out when it is not nil.
func (s *stack) must(user, method, path string, body any, want int, out any) {
	s.t.Helper()
	status, env := s.call(user, method, path, body)
	if status != want {
		code := ""
		if env.Error != nil {
			code = env.Error.Code + " " + env.Error.Message
		}
		s.t.Fatalf("%s %s as %s: status %d, want %d (%s)", method, path, user, status, want, code)
	}
	if out != nil {
		if err := json.Unmarshal(env.Data, out); err != nil {
			s.t.Fatalf("%s %s: decode data: %v: %s", method, path, err, env.Data)
		}
	}
}

// kaya runs the binary with the scratch home and the local stack's addresses.
type kaya struct {
	t      *testing.T
	binary string
	home   string
	env    []string
}

func newKaya(t *testing.T, binary, home string, extra ...string) *kaya {
	var env []string
	for _, entry := range os.Environ() {
		// A session of the developer's own Kaya must not leak into the scratch one.
		if strings.HasPrefix(entry, "KAYA_") || strings.HasPrefix(entry, "OBA_") || strings.HasPrefix(entry, "XDG_") ||
			strings.HasPrefix(entry, "HOME=") || strings.HasPrefix(entry, "CLAUDE_") {
			continue
		}
		env = append(env, entry)
	}
	env = append(env,
		"HOME="+home,
		"XDG_CONFIG_HOME="+filepath.Join(home, "config"),
		"XDG_CACHE_HOME="+filepath.Join(home, "cache"),
		"XDG_STATE_HOME="+filepath.Join(home, "state"),
	)
	return &kaya{t: t, binary: binary, home: home, env: append(env, extra...)}
}

func (k *kaya) with(extra ...string) *kaya {
	return &kaya{t: k.t, binary: k.binary, home: k.home, env: append(append([]string{}, k.env...), extra...)}
}

func (k *kaya) command(dir string, args ...string) *exec.Cmd {
	cmd := exec.Command(k.binary, append([]string{"kaya"}, args...)...) // #nosec G204 -- the binary this test built
	cmd.Dir = dir
	cmd.Env = k.env
	return cmd
}

// run runs the binary to its end and returns its output; a failure is fatal unless wantFail.
func (k *kaya) run(dir string, args ...string) string {
	k.t.Helper()
	out, err := k.command(dir, args...).CombinedOutput()
	if err != nil {
		k.t.Fatalf("oba kaya %s: %v\n%s", strings.Join(args, " "), err, out)
	}
	return string(out)
}

// status is oba kaya status --json or oba kaya sync --json.
type status struct {
	State    string   `json:"state"`
	Active   int      `json:"active"`
	Staged   int      `json:"staged"`
	Switched bool     `json:"switched"`
	Problems []string `json:"problems"`
}

func (k *kaya) status(dir string, args ...string) status {
	k.t.Helper()
	var parsed status
	out := k.run(dir, args...)
	if err := json.Unmarshal([]byte(out), &parsed); err != nil {
		k.t.Fatalf("kaya %s did not print JSON: %v\n%s", strings.Join(args, " "), err, out)
	}
	if len(parsed.Problems) > 0 {
		k.t.Fatalf("kaya %s reports problems: %v", strings.Join(args, " "), parsed.Problems)
	}
	return parsed
}

// signIn returns the environment of user's calls in the scratch home: OBA_TOKEN with a token of
// the password grant (the oba-cli client has only the device grant, which needs a browser) and
// the test API. It also chooses the organization, as `oba org use` would.
func (s *stack) signIn(home, user string) []string {
	s.t.Helper()
	dir := filepath.Join(home, ".config", "oba")
	if err := os.MkdirAll(dir, 0o700); err != nil {
		s.t.Fatalf("create %s: %v", dir, err)
	}
	encoded, err := json.Marshal(map[string]string{"org-id": s.orgID})
	if err != nil {
		s.t.Fatalf("encode the settings: %v", err)
	}
	if err := os.WriteFile(filepath.Join(dir, "config.json"), encoded, 0o600); err != nil {
		s.t.Fatalf("write the settings: %v", err)
	}
	return []string{"OBA_TOKEN=" + s.token(user), "OBA_API_URL=" + s.api}
}

// claudeEnv is the environment of every real Claude Code session of this test: the developer's
// own CLAUDE_, KAYA_ and XDG_STATE_HOME values are dropped, the built binary comes first on the
// PATH, and the capture hooks of the delivered settings send to the test backend with the e2e
// machine token and queue in a scratch state directory, so no hook reaches the developer's Kaya.
func claudeEnv(binary, machineToken, api, state string) []string {
	var env []string
	for _, entry := range os.Environ() {
		if strings.HasPrefix(entry, "CLAUDE_") || strings.HasPrefix(entry, "CLAUDECODE") || strings.HasPrefix(entry, "KAYA_") || strings.HasPrefix(entry, "OBA_") ||
			strings.HasPrefix(entry, "XDG_STATE_HOME=") || strings.HasPrefix(entry, "PATH=") {
			continue
		}
		env = append(env, entry)
	}
	return append(env,
		"PATH="+filepath.Dir(binary)+string(os.PathListSeparator)+os.Getenv("PATH"),
		"KAYA_RECORDER_TOKEN="+machineToken,
		"OBA_API_URL="+api,
		"XDG_STATE_HOME="+state,
	)
}

func mustRun(t *testing.T, dir, name string, args ...string) string {
	t.Helper()
	cmd := exec.Command(name, args...) // #nosec G204 -- fixed tools of the test
	cmd.Dir = dir
	out, err := cmd.CombinedOutput()
	if err != nil {
		t.Fatalf("%s %s: %v\n%s", name, strings.Join(args, " "), err, out)
	}
	return string(out)
}

func readFile(t *testing.T, path string) string {
	t.Helper()
	data, err := os.ReadFile(path) // #nosec G304 -- a path inside the scratch directories
	if err != nil {
		t.Fatalf("read %s: %v", path, err)
	}
	return string(data)
}

func contains(list []string, want string) bool {
	for _, item := range list {
		if item == want {
			return true
		}
	}
	return false
}

// kagiRule is a rule of the mounted pack, loaded by Claude Code only for files under its folder.
const kagiRule = "---\npaths:\n  - \"" + mountFolder + "/**\"\n---\n# Kagi folder rule\n\nKAGI-RULE-MARK\n"

// rootCodeword and folderCodeword are placed in the root AGENTS.md and in the mounted pack's
// folder AGENTS.md before the seed. Claude Code loads AGENTS.md through its built-in agents_md
// feature, which fires no InstructionsLoaded hook, so the test observes the load by asking the
// model to report the codewords it was given.
const (
	rootCodeword   = "ROOT-CODEWORD-PELICAN-4172"
	folderCodeword = "FOLDER-CODEWORD-MARMOT-9306"
)

// kagiAgentsFile is the AGENTS.md of the mounted folder.
const kagiAgentsFile = "# Kagi folder agents file\n\nThe codeword of this folder is " + folderCodeword + ".\n"

// rootAgentsAddition is appended to the root AGENTS.md of the scratch copy.
const rootAgentsAddition = "\n\nThe codeword of the repository root is " + rootCodeword + ".\n"

// agentsReplyFormat is the strict reply the codeword prompt asks for.
const agentsReplyFormat = "Then end your answer with exactly two lines and nothing after them:\n" +
	"ROOT=<value>\nFOLDER=<value>\n" +
	"ROOT is the codeword of the repository root that your AGENTS.md instructions give you, and " +
	"FOLDER is the codeword that the AGENTS.md instructions of the folder of the file you read give you. " +
	"Write NONE for a codeword that no instruction in your context gives you. Never guess a codeword."

// reportedCodeword returns the value of a KEY=value line of a reply, ignoring Markdown
// emphasis and quotes around the value; found is false when no such line exists.
func reportedCodeword(reply, key string) (value string, found bool) {
	for _, line := range strings.Split(reply, "\n") {
		line = strings.Trim(strings.TrimSpace(line), "*`'\" ")
		if rest, ok := strings.CutPrefix(line, key+"="); ok {
			value, found = strings.Trim(strings.TrimSpace(rest), "*`'\" ."), true
		}
	}
	return value, found
}

// requireCurrentAgentsClone fails when a file the copied repository got from the agents clone
// differs from the clone's pack file: the clone is behind the commit the repository was synced
// from, and the seed would report that the release differs from the disk.
func requireCurrentAgentsClone(t *testing.T, repoRoot, agents string) {
	t.Helper()
	var managed struct {
		Files map[string]string `json:"files"`
		Packs []string          `json:"packs"`
	}
	if err := json.Unmarshal([]byte(readFile(t, filepath.Join(repoRoot, ".agents", ".managed.json"))), &managed); err != nil {
		t.Fatalf("read .agents/.managed.json: %v", err)
	}
	var stale []string
	for path, wantSum := range managed.Files {
		for _, packName := range append([]string{rootPack}, managed.Packs...) {
			raw, err := os.ReadFile(filepath.Join(agents, "packs", packName, strings.TrimPrefix(path, ".agents/"))) // #nosec G304 -- a file of the clone named by the test
			if err != nil {
				continue
			}
			if sum := sha256.Sum256(raw); hex.EncodeToString(sum[:]) != wantSum {
				stale = append(stale, path)
			}
			break
		}
	}
	if len(stale) > 0 {
		sort.Strings(stale)
		t.Fatalf("KAYA_E2E_AGENTS=%s is behind the commit of korurlabs/agents this repository was synced from: "+
			"%d managed files differ from the clone (for example %s). Update the clone (git pull) and run again.",
			agents, len(stale), stale[0])
	}
}

// writeTree writes the files of a path to content map below dir.
func writeTree(t *testing.T, dir string, files map[string]string) {
	t.Helper()
	for rel, content := range files {
		path := filepath.Join(dir, rel)
		if err := os.MkdirAll(filepath.Dir(path), 0o750); err != nil {
			t.Fatalf("create the parent of %s: %v", rel, err)
		}
		if err := os.WriteFile(path, []byte(content), 0o600); err != nil {
			t.Fatalf("write %s: %v", rel, err)
		}
	}
}

// copyConfig copies the agent config of repoRoot into dst, keeping links as links.
func copyConfig(t *testing.T, repoRoot, dst string) {
	t.Helper()
	paths := []string{
		"kaya.yaml", "AGENTS.md", ".agents/AGENTS.md", ".agents/.managed.json",
		".agents/rules", ".agents/skills", ".agents/roles", ".agents/workflows", ".agents/templates", ".agents/hooks",
		".claude/agents", ".claude/skills", ".claude/settings.json",
		// The tool links, which a seed keeps and a release delivers as links.
		".claude/rules", ".claude/templates",
	}
	for _, rel := range paths {
		src := filepath.Join(repoRoot, rel)
		if _, err := os.Lstat(src); err != nil {
			continue
		}
		if err := os.MkdirAll(filepath.Dir(filepath.Join(dst, rel)), 0o750); err != nil {
			t.Fatalf("create the parent of %s: %v", rel, err)
		}
		mustRun(t, repoRoot, "cp", "-a", src, filepath.Join(dst, rel))
	}
}

type membership struct {
	OrganizationID string `json:"organizationId"`
	Name           string `json:"name"`
	Role           string `json:"role"`
}

type me struct {
	Memberships []membership `json:"memberships"`
}

type version struct {
	ID     string `json:"id"`
	Number int    `json:"number"`
	Status string `json:"status"`
}

type repository struct {
	ID   string `json:"id"`
	Name string `json:"name"`
}

// loadedInstruction is the input of an InstructionsLoaded hook, the fields the test reads.
type loadedInstruction struct {
	FilePath   string `json:"file_path"`
	MemoryType string `json:"memory_type"`
	LoadReason string `json:"load_reason"`
}

type pack struct {
	ID               string   `json:"id"`
	Name             string   `json:"name"`
	ParentPackID     *string  `json:"parentPackId"`
	CurrentVersionID *string  `json:"currentVersionId"`
	MountPaths       []string `json:"mountPaths"`
}

// packs reads every pack of the organization by name.
func (s *stack) packs(user string) map[string]pack {
	s.t.Helper()
	var list []pack
	s.must(user, http.MethodGet, "/packs", nil, http.StatusOK, &list)
	byName := map[string]pack{}
	for _, candidate := range list {
		byName[candidate.Name] = candidate
	}
	return byName
}

// versionFiles reads the paths of a stored pack version, with the symlink target of each link.
func (s *stack) versionFiles(user, versionID string) map[string]string {
	s.t.Helper()
	var detail struct {
		Files []struct {
			Path          string `json:"path"`
			SymlinkTarget string `json:"symlinkTarget"`
		} `json:"files"`
	}
	s.must(user, http.MethodGet, "/versions/"+versionID, nil, http.StatusOK, &detail)
	files := map[string]string{}
	for _, file := range detail.Files {
		files[file.Path] = file.SymlinkTarget
	}
	return files
}

// seedPlan is the part of oba kaya seed --dry-run --json the test reads.
type seedPlan struct {
	Sets []struct {
		Name   string `json:"name"`
		Parent string `json:"parent"`
		Files  []struct {
			Path string `json:"path"`
		} `json:"files"`
	} `json:"sets"`
	Repositories []struct {
		RemoteURL string `json:"remoteUrl"`
		Packs     []struct {
			Name     string `json:"name"`
			RootPath string `json:"rootPath"`
		} `json:"packs"`
	} `json:"repositories"`
	NotImported []struct {
		Path   string `json:"path"`
		Reason string `json:"reason"`
	} `json:"notImported"`
}

// toolMapping is one entry of GET /path-mappings/tools, the parts the test reads.
type toolMapping struct {
	Tool  string `json:"tool"`
	Reads []struct {
		ReadPath string `json:"readPath"`
		State    string `json:"state"`
		Count    int    `json:"count"`
	} `json:"reads"`
}

// toolLinks are the links a tool reads through, with the target a release gives each.
var toolLinks = map[string]string{
	"AGENTS.md":         ".agents/AGENTS.md",
	".claude/rules":     "../.agents/rules",
	".claude/templates": "../.agents/templates",
}

// requireToolLinks fails unless every tool link of dir is a real link with its target and the
// rules read through .claude/rules.
func requireToolLinks(t *testing.T, dir string) {
	t.Helper()
	for link, want := range toolLinks {
		got, err := os.Readlink(filepath.Join(dir, link))
		if err != nil {
			t.Fatalf("%s is not a link in the checkout: %v", link, err)
		}
		if got != want {
			t.Fatalf("%s links to %q, want %q", link, got, want)
		}
	}
	if text := readFile(t, filepath.Join(dir, ".claude", "rules", "core.md")); !strings.Contains(text, "Core rules") {
		t.Fatalf(".claude/rules/core.md does not read through the link to the delivered rule")
	}
}

func TestConfigChainOnTheLocalStack(t *testing.T) {
	if os.Getenv("KAYA_E2E") != "1" {
		t.Skip("set KAYA_E2E=1 to run the end-to-end test against the local Kaya stack (see the header of e2e_test.go)")
	}
	s := &stack{
		t:        t,
		api:      strings.TrimRight(getenv("KAYA_E2E_API", "http://localhost:8083"), "/"),
		keycloak: strings.TrimRight(getenv("KAYA_E2E_KEYCLOAK", "http://localhost:8087"), "/"),
		secret:   getenv("KAYA_E2E_WEB_SECRET", "web-dev-secret-12345"),
	}
	nonce := fmt.Sprintf("%d", time.Now().UnixNano())

	scratch, err := os.MkdirTemp("", "kaya-e2e-")
	if err != nil {
		t.Fatalf("create the scratch directory: %v", err)
	}
	t.Cleanup(func() {
		// A watcher of the last session may still hold the directory for up to a minute.
		if err := os.RemoveAll(scratch); err != nil {
			t.Logf("scratch directory %s was not fully removed: %v", scratch, err)
		}
	})
	home := filepath.Join(scratch, "home")
	source := filepath.Join(scratch, "source")
	checkout := filepath.Join(scratch, "checkout")
	for _, dir := range []string{home, source, checkout} {
		if err := os.MkdirAll(dir, 0o750); err != nil {
			t.Fatalf("create %s: %v", dir, err)
		}
	}

	root := t
	var binary string
	var repoID, familyPackID, markerVersionID string
	var machineID, machineToken string
	var firstRelease, secondRelease int
	var cli *kaya

	step := func(name string, body func(t *testing.T)) {
		t.Helper()
		if !t.Run(name, body) {
			t.FailNow()
		}
	}

	step("build the oba binary", func(t *testing.T) {
		binary = filepath.Join(scratch, "oba")
		mustRun(t, filepath.Join("..", ".."), "go", "build", "-o", binary, ".")
	})

	step("sign in as two members", func(t *testing.T) {
		s.t = t
		for user, wantRole := range map[string]string{"dev": "OWNER", "dev2": "MEMBER"} {
			var profile me
			s.must(user, http.MethodPost, "/users/me", nil, http.StatusCreated, &profile)
			found := false
			for _, m := range profile.Memberships {
				if m.Name == "Korur" {
					found = true
					s.orgID = m.OrganizationID
					if m.Role != wantRole {
						t.Fatalf("%s is %s in Korur, the realm file makes the user %s", user, m.Role, wantRole)
					}
				}
			}
			if !found {
				t.Fatalf("%s has no membership in Korur: %+v", user, profile.Memberships)
			}
		}
	})

	step("seed a scratch copy of this repository", func(t *testing.T) {
		s.t = t
		root := getenv("KAYA_E2E_REPO_ROOT", "")
		if root == "" {
			if root, err = filepath.Abs(filepath.Join("..", "..", "..", "..")); err != nil {
				t.Fatalf("find the repository root: %v", err)
			}
		}
		copyConfig(t, root, source)
		// The root codeword goes into the file the root AGENTS.md link points at.
		rootAgents := filepath.Join(source, ".agents", "AGENTS.md")
		if err := os.WriteFile(rootAgents, []byte(readFile(t, rootAgents)+rootAgentsAddition), 0o600); err != nil {
			t.Fatalf("add the root codeword to %s: %v", rootAgents, err)
		}
		// The mounted pack kagi: a rule scoped to its folder, in its namespace, and the folder
		// AGENTS.md. The README files are what the Claude Code step reads.
		writeTree(t, source, map[string]string{
			".agents/rules/" + mountedPack + "/kagi-api.md": kagiRule,
			mountFolder + "/AGENTS.md":                      kagiAgentsFile,
			mountFolder + "/README.md":                      "kagi folder\n",
			"projects/kazan/README.md":                      "kazan folder\n",
			ownFile:                                         "a file only this repository has\n",
		})
		mustRun(t, source, "git", "init", "-q")
		remote := ""
		for _, line := range strings.Split(readFile(t, filepath.Join(source, "kaya.yaml")), "\n") {
			if value, ok := strings.CutPrefix(line, "remoteUrl:"); ok {
				remote = strings.TrimSpace(value)
			}
		}
		if remote == "" {
			t.Fatal("kaya.yaml names no remoteUrl")
		}
		mustRun(t, source, "git", "remote", "add", "origin", remote)
		mustRun(t, source, "git", "add", "-A")
		mustRun(t, source, "git", "-c", "user.email=e2e@example.com", "-c", "user.name=e2e", "commit", "-q", "-m", "seed")

		cli = newKaya(t, binary, home, s.signIn(home, "dev")...)
		agents := getenv("KAYA_E2E_AGENTS", "")
		if agents == "" {
			t.Fatal("set KAYA_E2E_AGENTS to a clone of korurlabs/agents: the seed imports its packs")
		}
		requireCurrentAgentsClone(t, root, agents)
		seedArgs := []string{"seed", "--repo", source, "--agents", agents, "--pack", mountedPack + "=" + mountFolder}

		// The plan first: parents, the mount, the deprecated pack and the folder AGENTS.md.
		var plan seedPlan
		if err := json.Unmarshal([]byte(cli.run(source, append(seedArgs, "--dry-run", "--json")...)), &plan); err != nil {
			t.Fatalf("oba kaya seed --dry-run --json did not print JSON: %v", err)
		}
		parents := map[string]string{}
		sentPaths := map[string][]string{}
		for _, set := range plan.Sets {
			parents[set.Name] = set.Parent
			for _, file := range set.Files {
				sentPaths[set.Name] = append(sentPaths[set.Name], file.Path)
			}
		}
		for name, want := range map[string]string{rootPack: "", familyPack: rootPack, mountedPack: familyPack} {
			if got, ok := parents[name]; !ok || got != want {
				t.Fatalf("the seed plan gives pack %q the parent %q (listed %v), want %q", name, got, ok, want)
			}
		}
		if !contains(sentPaths[mountedPack], "AGENTS.md") || contains(sentPaths[mountedPack], mountFolder+"/AGENTS.md") {
			t.Fatalf("the mounted pack must send the folder AGENTS.md as AGENTS.md: %v", sentPaths[mountedPack])
		}
		if !contains(sentPaths[mountedPack], ".agents/rules/"+mountedPack+"/kagi-api.md") {
			t.Fatalf("the mounted pack must take the rule of its namespace: %v", sentPaths[mountedPack])
		}
		if !contains(sentPaths[familyPack], ownFile) || contains(sentPaths[rootPack], ownFile) {
			t.Fatalf("the repository's own file %s must go into the family pack %s only", ownFile, familyPack)
		}
		deprecated := false
		for _, skipped := range plan.NotImported {
			if skipped.Path == "agents:packs/mdf/" && skipped.Reason == "Deprecated pack" {
				deprecated = true
			}
		}
		if !deprecated {
			t.Fatalf("packs/mdf is not reported as a Deprecated pack: %+v", plan.NotImported)
		}
		if len(plan.Repositories) != 1 {
			t.Fatalf("the seed plan holds %d repositories, want 1", len(plan.Repositories))
		}
		mountSeen := false
		for _, mounted := range plan.Repositories[0].Packs {
			if mounted.Name == mountedPack && mounted.RootPath == mountFolder {
				mountSeen = true
			}
		}
		if !mountSeen {
			t.Fatalf("the repository's pack list lacks %s at %s: %+v", mountedPack, mountFolder, plan.Repositories[0].Packs)
		}

		// With a current agents clone the seed exits 0; exit 2 would mean a release differs from the disk.
		seedOut, seedErr := cli.command(source, seedArgs...).CombinedOutput()
		out := string(seedOut)
		if seedErr != nil {
			t.Fatalf("oba kaya seed: %v\n%s", seedErr, out)
		}
		if !strings.Contains(out, "PASSED") {
			t.Fatalf("the seed did not pass:\n%s", out)
		}

		var repos []repository
		s.must("dev", http.MethodGet, "/repositories", nil, http.StatusOK, &repos)
		for _, repo := range repos {
			if repo.Name == repositoryName {
				repoID = repo.ID
			}
		}
		if repoID == "" {
			t.Fatalf("no repository %q after the seed: %+v", repositoryName, repos)
		}
	})

	step("the stored pack tree, the repository's packs and the family pack's files read as planned", func(t *testing.T) {
		s.t = t
		stored := s.packs("dev")
		for name, wantParent := range map[string]string{rootPack: "", familyPack: rootPack, mountedPack: familyPack} {
			got, ok := stored[name]
			if !ok {
				t.Fatalf("pack %q is not stored: %v", name, stored)
			}
			gotParent := ""
			if got.ParentPackID != nil {
				gotParent = *got.ParentPackID
			}
			wantID := ""
			if wantParent != "" {
				wantID = stored[wantParent].ID
			}
			if gotParent != wantID {
				t.Fatalf("pack %q has parent %q, want %q (%q)", name, gotParent, wantParent, wantID)
			}
			if got.CurrentVersionID == nil {
				t.Fatalf("pack %q has no current version after the seed", name)
			}
		}
		if _, ok := stored["mdf"]; ok {
			t.Fatal("the deprecated pack mdf was imported")
		}
		familyPackID = stored[familyPack].ID
		if !contains(stored[mountedPack].MountPaths, mountFolder) {
			t.Fatalf("pack %q reports mount paths %v, want %s", mountedPack, stored[mountedPack].MountPaths, mountFolder)
		}

		var repoPacks struct {
			Packs []struct {
				Name     string  `json:"name"`
				RootPath *string `json:"rootPath"`
			} `json:"packs"`
		}
		s.must("dev", http.MethodGet, "/repositories/"+repoID+"/packs", nil, http.StatusOK, &repoPacks)
		rootPaths := map[string]string{}
		for _, entry := range repoPacks.Packs {
			rootPaths[entry.Name] = ""
			if entry.RootPath != nil {
				rootPaths[entry.Name] = *entry.RootPath
			}
		}
		for name, wantFolder := range map[string]string{rootPack: "", familyPack: "", mountedPack: mountFolder} {
			if folder, ok := rootPaths[name]; !ok || folder != wantFolder {
				t.Fatalf("the repository composes %v, want %s at %q", rootPaths, name, wantFolder)
			}
		}

		mounted := s.versionFiles("dev", *stored[mountedPack].CurrentVersionID)
		if _, ok := mounted["AGENTS.md"]; !ok {
			t.Fatalf("the mounted pack stores no AGENTS.md: %v", mounted)
		}
		if _, ok := mounted[mountFolder+"/AGENTS.md"]; ok {
			t.Fatalf("the mounted pack stores the folder path of its AGENTS.md: %v", mounted)
		}
		family := s.versionFiles("dev", *stored[familyPack].CurrentVersionID)
		if _, ok := family[ownFile]; !ok {
			t.Fatalf("the family pack does not hold the repository's own file %s", ownFile)
		}
		if _, ok := s.versionFiles("dev", *stored[rootPack].CurrentVersionID)[ownFile]; ok {
			t.Fatalf("the root pack holds the repository's own file %s", ownFile)
		}

		// Claude Code reads the root AGENTS.md and the rules through the family pack's tool links.
		// The templates come from packs, which this version does not hold, so its link leads to
		// no file here and only the delivered checkout shows it.
		var tools []toolMapping
		s.must("dev", http.MethodGet, "/path-mappings/tools?versionId="+*stored[familyPack].CurrentVersionID, nil, http.StatusOK, &tools)
		wantLinked := map[string]bool{"AGENTS.md": false, ".claude/rules/<path>.md": false}
		for _, tool := range tools {
			if tool.Tool != "CLAUDE_CODE" {
				continue
			}
			for _, read := range tool.Reads {
				if _, ok := wantLinked[read.ReadPath]; ok {
					wantLinked[read.ReadPath] = read.State == "LINKED" && read.Count > 0
				}
			}
		}
		for readPath, linked := range wantLinked {
			if !linked {
				t.Fatalf("Claude Code does not read %s as LINKED in the family pack: %+v", readPath, tools)
			}
		}
	})

	step("register a machine and trust the local signing key", func(t *testing.T) {
		s.t = t
		var created struct {
			Machine struct {
				ID string `json:"id"`
			} `json:"machine"`
			Token string `json:"token"`
		}
		// The machine is registered under the hostname, the name the capture reports, so its label
		// matches its batches; a later run of this test on the same host finds it by that name.
		machine, err := os.Hostname()
		if err != nil {
			t.Fatalf("read the hostname: %v", err)
		}
		switch status, env := s.call("dev", http.MethodPost, "/machines", map[string]string{"machineName": machine}); {
		case status == http.StatusCreated:
			if err := json.Unmarshal(env.Data, &created); err != nil {
				t.Fatalf("decode the created machine: %v", err)
			}
		case status == http.StatusConflict && env.Error != nil && env.Error.ErrorValues["machineId"] != nil:
			// An earlier run of this test left the machine of this host: take a new token for it.
			created.Machine.ID = fmt.Sprint(env.Error.ErrorValues["machineId"])
			s.must("dev", http.MethodPost, "/machines/"+created.Machine.ID+"/token", nil, http.StatusCreated, &created)
		default:
			t.Fatalf("POST /machines answered %d: %+v", status, env.Error)
		}
		machineID, machineToken = created.Machine.ID, created.Token
		if machineToken == "" {
			t.Fatal("the machine was created without a token")
		}
		root.Cleanup(func() {
			t := root
			_ = t
			s.t = t
			if status, _ := s.call("dev", http.MethodDelete, "/machines/"+machineID+"/token", nil); status >= 300 {
				t.Logf("the e2e machine token %s was not revoked (status %d)", machineID, status)
			}
		})

		// A fresh token: the one of the seed may have expired by now.
		signIn := s.signIn(home, "dev")
		cli = cli.with(signIn...)
		line := strings.TrimSpace(cli.run(source, "signing-keys"))
		keys := strings.TrimPrefix(line, "export KAYA_DEV_TRUSTED_KEYS=")
		if keys == line || keys == "" {
			t.Fatalf("oba kaya signing-keys printed no key line: %q", line)
		}
		cli = newKaya(t, binary, home, append(signIn, "KAYA_DEV_TRUSTED_KEYS="+keys, "KAYA_RECORDER_TOKEN="+machineToken)...)
	})

	// The checkout is a cut-over repository: a bootstrap AGENTS.md, kaya.yaml and no
	// .agents/.managed.json, with every delivered path ignored by git.
	mustWrite := func(t *testing.T, path, content string) {
		t.Helper()
		if err := os.WriteFile(path, []byte(content), 0o600); err != nil {
			t.Fatalf("write %s: %v", path, err)
		}
	}
	stub := filepath.Join(scratch, "claude-stub.sh")
	stubOut := func(name string) string { return filepath.Join(scratch, name+".env") }
	hold := func(name string) string { return filepath.Join(scratch, name+".hold") }
	// launch runs oba kaya launch with the stub, which records the KAYA_ variables the launcher gave
	// it and, when KAYA_E2E_HOLD names a file, stays alive until that file exists, like a session.
	launch := func(t *testing.T, name string, holdSession bool) *exec.Cmd {
		t.Helper()
		extra := []string{"KAYA_E2E_OUT=" + stubOut(name)}
		if holdSession {
			extra = append(extra, "KAYA_E2E_HOLD="+hold(name))
		}
		cmd := cli.with(extra...).command(checkout, "launch", stub)
		var output bytes.Buffer
		cmd.Stdout, cmd.Stderr = &output, &output
		if err := cmd.Start(); err != nil {
			t.Fatalf("start oba kaya launch: %v", err)
		}
		root.Cleanup(func() {
			t := root
			_ = t
			if cmd.ProcessState == nil && cmd.Process != nil {
				_ = cmd.Process.Kill()
			}
		})
		return cmd
	}
	stubVars := func(t *testing.T, name string) map[string]string {
		t.Helper()
		vars := map[string]string{}
		for _, line := range strings.Split(readFile(t, stubOut(name)), "\n") {
			if key, value, ok := strings.Cut(line, "="); ok {
				vars[key] = value
			}
		}
		return vars
	}

	step("launch delivers the seeded files", func(t *testing.T) {
		s.t = t
		mustWrite(t, stub, "#!/bin/sh\nenv | grep '^KAYA_' | sort > \"$KAYA_E2E_OUT\"\n"+
			"i=0\nwhile [ -n \"$KAYA_E2E_HOLD\" ] && [ ! -e \"$KAYA_E2E_HOLD\" ] && [ $i -lt 1800 ]; do sleep 0.1; i=$((i+1)); done\n")
		if err := os.Chmod(stub, 0o700); err != nil { // #nosec G302 -- a script the test runs
			t.Fatalf("make the stub executable: %v", err)
		}
		mustRun(t, checkout, "git", "init", "-q")
		// The registered email of the OWNER and the repository's remote, so every session the
		// real Claude Code steps record here can be sent to Kaya.
		mustRun(t, checkout, "git", "config", "user.email", "dev@example.com")
		mustRun(t, checkout, "git", "config", "user.name", "Dev")
		mustRun(t, checkout, "git", "remote", "add", "origin", strings.TrimSpace(strings.TrimPrefix(
			regexp.MustCompile(`(?m)^remoteUrl:.*$`).FindString(readFile(t, filepath.Join(source, "kaya.yaml"))), "remoteUrl:")))
		mustWrite(t, filepath.Join(checkout, "kaya.yaml"), readFile(t, filepath.Join(source, "kaya.yaml")))
		mustWrite(t, filepath.Join(checkout, "AGENTS.md"),
			"Agent config comes from Kaya; read `.agents/AGENTS.md`; if it is missing, stop and ask the user to run `oba kaya sync`.\n")
		mustWrite(t, filepath.Join(checkout, ".gitignore"), ".kaya/\n.agents/\n.claude/\n")

		cmd := launch(t, "first", false)
		if err := cmd.Wait(); err != nil {
			t.Fatalf("oba kaya launch: %v\n%s", err, cmd.Stdout.(*bytes.Buffer).String())
		}
		vars := stubVars(t, "first")
		if vars["KAYA_LAUNCHED"] != "1" || vars["KAYA_RELEASE"] == "" || vars["KAYA_RELEASE"] == "0" {
			t.Fatalf("the launched command did not get a release: %v", vars)
		}
		if vars["KAYA_RELEASE_STATE"] != "FRESH" {
			t.Fatalf("KAYA_RELEASE_STATE = %q, want FRESH: %v", vars["KAYA_RELEASE_STATE"], vars)
		}
		if _, err := fmt.Sscanf(vars["KAYA_RELEASE"], "%d", &firstRelease); err != nil {
			t.Fatalf("KAYA_RELEASE %q is not a number", vars["KAYA_RELEASE"])
		}

		if got, want := readFile(t, filepath.Join(checkout, ".agents", "AGENTS.md")), readFile(t, filepath.Join(source, ".agents", "AGENTS.md")); got != want {
			t.Fatalf(".agents/AGENTS.md was delivered with other content than the seed read")
		}
		for _, rel := range []string{".claude/settings.json", ".agents/.kaya-release.json"} {
			info, err := os.Lstat(filepath.Join(checkout, rel))
			if err != nil {
				t.Fatalf("%s was not delivered: %v", rel, err)
			}
			if rel == ".claude/settings.json" && !info.Mode().IsRegular() {
				t.Fatalf("%s must be a regular file, is %s", rel, info.Mode())
			}
		}
		if _, err := os.Lstat(filepath.Join(checkout, markerPath)); err == nil {
			// An earlier run's change is part of the org root pack now; the later steps use a new nonce.
			t.Logf("%s is already part of release %d from an earlier run", markerPath, firstRelease)
		}
		requireToolLinks(t, checkout)
		// The orchestrator, plan, implement and review skills are delivered as skills, and a
		// release carries no command files.
		for _, skill := range []string{"orchestrator", "plan", "implement", "review"} {
			rel := filepath.Join(".claude", "skills", skill, "SKILL.md")
			if info, err := os.Stat(filepath.Join(checkout, rel)); err != nil || !info.Mode().IsRegular() {
				t.Fatalf("%s was not delivered as a file: %v", rel, err)
			}
		}
		if _, err := os.Lstat(filepath.Join(checkout, ".claude", "commands")); !os.IsNotExist(err) {
			t.Fatalf(".claude/commands exists in the checkout (error %v), a release must deliver no commands", err)
		}
		if got := readFile(t, filepath.Join(checkout, "projects", "kagi", "AGENTS.md")); got != kagiAgentsFile {
			t.Fatalf("projects/kagi/AGENTS.md = %q, want the folder AGENTS.md the seed read", got)
		}
		if got := readFile(t, filepath.Join(checkout, ".agents", "rules", mountedPack, "kagi-api.md")); got != kagiRule {
			t.Fatalf("the mounted pack's rule was delivered with other content than the seed read: %q", got)
		}
		if got := readFile(t, filepath.Join(checkout, ownFile)); got != "a file only this repository has\n" {
			t.Fatalf("%s = %q, want the repository's own file", ownFile, got)
		}
		got := cli.status(checkout, "status", "--json")
		if got.Active != firstRelease {
			t.Fatalf("oba kaya status says release %d, the session got %d", got.Active, firstRelease)
		}
	})

	// Not a step: a failed check here must not hide the steps after it.
	t.Run("real Claude Code loads the mounted pack's instructions only under its folder", func(t *testing.T) {
		if getenv("KAYA_E2E_CLAUDE", "") == "0" {
			t.Skip("KAYA_E2E_CLAUDE=0")
		}
		claude, err := exec.LookPath("claude")
		if err != nil {
			t.Skip("claude is not on the PATH")
		}
		writeTree(t, checkout, map[string]string{
			mountFolder + "/README.md": "kagi folder\n",
			"projects/kazan/README.md": "kazan folder\n",
		})
		settingsLocal := filepath.Join(checkout, ".claude", "settings.local.json")
		t.Cleanup(func() {
			if err := os.Remove(settingsLocal); err != nil && !errors.Is(err, os.ErrNotExist) {
				t.Errorf("remove the temporary hook %s: %v", settingsLocal, err)
			}
		})

		// ask runs one fresh session that reads one file and returns the instruction files the
		// InstructionsLoaded hook saw, in order, and the model's reply with the codewords.
		ask := func(name, target string) ([]loadedInstruction, string) {
			t.Helper()
			logPath := filepath.Join(scratch, name+".instructions.log")
			hookSettings, err := json.Marshal(map[string]any{"hooks": map[string]any{"InstructionsLoaded": []any{
				map[string]any{"hooks": []any{map[string]any{
					"type": "command", "command": "cat >> '" + logPath + "'; echo >> '" + logPath + "'",
				}}},
			}}})
			if err != nil {
				t.Fatalf("encode the hook settings: %v", err)
			}
			if err := os.WriteFile(settingsLocal, hookSettings, 0o600); err != nil {
				t.Fatalf("write %s: %v", settingsLocal, err)
			}
			ctx, cancel := context.WithTimeout(context.Background(), 4*time.Minute)
			defer cancel()
			cmd := exec.CommandContext(ctx, claude, "-p", "Read "+target+" and stop reading. "+agentsReplyFormat, // #nosec G204 -- the claude on the PATH
				"--model", "haiku", "--allowedTools", "Read", "--max-turns", "4",
				"--setting-sources", "project,local", "--no-session-persistence")
			cmd.Dir = checkout
			cmd.Env = claudeEnv(binary, machineToken, s.api, filepath.Join(scratch, "claude-state"))
			out, err := cmd.CombinedOutput()
			if err != nil {
				t.Fatalf("claude -p for %s: %v\n%s", target, err, out)
			}
			reply := string(out)
			t.Logf("claude read %s: %s", target, strings.TrimSpace(reply))
			raw, err := os.ReadFile(logPath) // #nosec G304 -- a log the hook wrote in the scratch directory
			if errors.Is(err, os.ErrNotExist) {
				return nil, reply
			}
			if err != nil {
				t.Fatalf("read the hook log: %v", err)
			}
			var loaded []loadedInstruction
			decoder := json.NewDecoder(bytes.NewReader(raw))
			for decoder.More() {
				var event loadedInstruction
				if err := decoder.Decode(&event); err != nil {
					t.Fatalf("the hook log is not JSON: %v\n%s", err, raw)
				}
				loaded = append(loaded, event)
			}
			return loaded, reply
		}
		// matches tells whether a loaded file is the one named by suffix, ignoring the link a
		// tool followed to reach it.
		matches := func(event loadedInstruction, suffix string) bool { return strings.HasSuffix(event.FilePath, suffix) }

		kagiLoaded, kagiReply := ask("claude-kagi", mountFolder+"/README.md")
		if len(kagiLoaded) == 0 {
			t.Fatal("the InstructionsLoaded hook saw nothing: the hook did not run")
		}
		kazanLoaded, kazanReply := ask("claude-kazan", "projects/kazan/README.md")

		// loadedAfterTheRead requires that suffix was loaded, and not at session start.
		loadedAfterTheRead := func(t *testing.T, suffix string) {
			t.Helper()
			found := false
			for _, event := range kagiLoaded {
				if matches(event, suffix) {
					found = true
					if event.LoadReason == "session_start" {
						t.Errorf("%s was loaded at session start, not by the read of a file under %s: %+v", suffix, mountFolder, event)
					}
				}
			}
			if !found {
				t.Errorf("reading %s/README.md did not load %s; the hook saw %+v", mountFolder, suffix, kagiLoaded)
			}
		}
		t.Run("the mounted pack's rule loads after the read of a file under its folder", func(t *testing.T) {
			loadedAfterTheRead(t, "/rules/"+mountedPack+"/kagi-api.md")
		})
		// Claude Code loads AGENTS.md through its built-in agents_md feature: the root file at
		// session start, a folder's file after a file under that folder is read. That feature
		// fires no InstructionsLoaded hook, so the codewords prove the load.
		wantCodeword := func(t *testing.T, reply, key, want string) {
			t.Helper()
			if got, found := reportedCodeword(reply, key); !found || got != want {
				t.Errorf("%s = %q (line found: %v), want %q; reply:\n%s", key, got, found, want, reply)
			}
		}
		t.Run("the root AGENTS.md is in context without a read", func(t *testing.T) {
			wantCodeword(t, kagiReply, "ROOT", rootCodeword)
			wantCodeword(t, kazanReply, "ROOT", rootCodeword)
		})
		t.Run("the mounted pack's folder AGENTS.md is in context after the read of a file under its folder", func(t *testing.T) {
			wantCodeword(t, kagiReply, "FOLDER", folderCodeword)
		})
		t.Run("the mounted pack's folder AGENTS.md is not in context for a file under another folder", func(t *testing.T) {
			wantCodeword(t, kazanReply, "FOLDER", "NONE")
		})
		t.Run("nothing of the mounted pack loads for a file under another folder", func(t *testing.T) {
			for _, event := range kazanLoaded {
				if strings.Contains(event.FilePath, "/rules/"+mountedPack+"/") || strings.Contains(event.FilePath, mountFolder) {
					t.Errorf("reading a file under projects/kazan loaded %s: %+v", event.FilePath, event)
				}
			}
		})
	})

	// Not a step, like the run above: a failed check here must not hide the steps after it.
	t.Run("a real Claude Code session is captured by oba kaya hook record and reaches the session API", func(t *testing.T) {
		s.t = t
		if getenv("KAYA_E2E_CLAUDE", "") == "0" {
			t.Skip("KAYA_E2E_CLAUDE=0")
		}
		claude, err := exec.LookPath("claude")
		if err != nil {
			t.Skip("claude is not on the PATH")
		}
		// The delivered settings carry the capture rows, and no Python recorder is in the checkout.
		settings := readFile(t, filepath.Join(checkout, ".claude", "settings.json"))
		if !strings.Contains(settings, "oba kaya hook record") || strings.Contains(settings, "record.py") {
			t.Fatalf("the delivered .claude/settings.json does not run oba kaya hook record, or still runs record.py")
		}
		if _, err := os.Stat(filepath.Join(checkout, "projects", "kaya", "recorder")); err == nil {
			t.Fatal("the scratch checkout holds a Python recorder; it must not")
		}
		// A branch with a commit, so the session reports a branch.
		mustRun(t, checkout, "git", "switch", "-q", "-c", "e2e/capture-"+nonce)
		mustRun(t, checkout, "git", "add", "kaya.yaml", "AGENTS.md", ".gitignore")
		mustRun(t, checkout, "git", "commit", "-q", "-m", "e2e checkout")

		sessionID := fmt.Sprintf("%08x-e2e0-4000-8000-%012x", time.Now().Unix(), time.Now().UnixNano()%(1<<47))
		ctx, cancel := context.WithTimeout(context.Background(), 4*time.Minute)
		defer cancel()
		// The session keeps its transcript, because Claude Code reports the model only there; the
		// transcript is removed with the test.
		t.Cleanup(func() {
			if home, err := os.UserHomeDir(); err == nil {
				matches, _ := filepath.Glob(filepath.Join(home, ".claude", "projects", "*", sessionID+".jsonl"))
				for _, match := range matches {
					if err := os.Remove(match); err != nil {
						t.Logf("transcript %s was not removed: %v", match, err)
					}
					_ = os.Remove(filepath.Dir(match)) // only when empty
				}
			}
		})
		// Bash, because the capture rows of the delivered settings match Agent, Skill, Bash and
		// the file-changing tools, not Read.
		cmd := exec.CommandContext(ctx, claude, "-p", "Run the shell command `echo kaya-e2e-capture` with the Bash tool, then answer with the word done.", // #nosec G204 -- the claude on the PATH
			"--model", "haiku", "--allowedTools", "Bash", "--max-turns", "4",
			"--setting-sources", "project,local", "--session-id", sessionID)
		cmd.Dir = checkout
		cmd.Env = claudeEnv(binary, machineToken, s.api, filepath.Join(scratch, "claude-state"))
		out, err := cmd.CombinedOutput()
		if err != nil {
			t.Fatalf("claude -p: %v\n%s", err, out)
		}
		t.Logf("claude answered: %s", strings.TrimSpace(string(out)))

		// The detached sender sends the queue within seconds.
		type detail struct {
			SessionID       string  `json:"sessionId"`
			RepositoryName  string  `json:"repositoryName"`
			UserID          string  `json:"userId"`
			UserDisplayName string  `json:"userDisplayName"`
			Branch          *string `json:"branch"`
			Model           *string `json:"model"`
		}
		var session detail
		deadline := time.Now().Add(30 * time.Second)
		for {
			status, env := s.call("dev", http.MethodGet, "/sessions/"+sessionID, nil)
			if status == http.StatusOK {
				if err := json.Unmarshal(env.Data, &session); err != nil {
					t.Fatalf("decode the session: %v: %s", err, env.Data)
				}
				break
			}
			if time.Now().After(deadline) {
				logs, _ := os.ReadFile(filepath.Join(scratch, "claude-state", "kaya", "recorder.log"))
				t.Fatalf("GET /sessions/%s answered %d after 30 s; recorder.log:\n%s", sessionID, status, logs)
			}
			time.Sleep(time.Second)
		}
		if session.SessionID != sessionID || session.UserID == "" || session.UserDisplayName == "" {
			t.Errorf("the session has no user: %+v", session)
		}
		if session.Model == nil || !strings.Contains(*session.Model, "haiku") {
			t.Errorf("the session model is %v, want a haiku model", session.Model)
		}
		if session.Branch == nil || *session.Branch != "e2e/capture-"+nonce {
			t.Errorf("the session branch is %v, want e2e/capture-%s", session.Branch, nonce)
		}
		if session.RepositoryName != repositoryName {
			t.Errorf("the session repository is %q, want %q", session.RepositoryName, repositoryName)
		}

		// Stop is the last event of the turn: wait for it, then check the whole stream.
		var events []map[string]any
		for {
			var batch []map[string]any
			s.must("dev", http.MethodGet, "/sessions/"+sessionID+"/events", nil, http.StatusOK, &batch)
			events = batch
			names := map[string]bool{}
			for _, event := range events {
				name, _ := event["event"].(string)
				names[name] = true
			}
			if names["Stop"] {
				break
			}
			if time.Now().After(deadline.Add(30 * time.Second)) {
				t.Fatalf("no Stop event after 60 s: %v", events)
			}
			time.Sleep(time.Second)
		}
		seen := map[string]bool{}
		for i, event := range events {
			name, _ := event["event"].(string)
			seen[name] = true
			if tool, _ := event["tool_name"].(string); tool == "Bash" && (name == "PreToolUse" || name == "PostToolUse") {
				seen["Bash:"+name] = true
			}
			if seq, _ := event["seq"].(float64); int(seq) != i+1 {
				t.Errorf("event %d has seq %v, want %d (gapless from 1)", i, event["seq"], i+1)
			}
		}
		for _, want := range []string{"SessionStart", "UserPromptSubmit", "Stop"} {
			if !seen[want] {
				t.Errorf("no %s event among %d events", want, len(events))
			}
		}
		if !seen["Bash:PreToolUse"] && !seen["Bash:PostToolUse"] {
			t.Errorf("no PreToolUse or PostToolUse of the Bash tool among %d events", len(events))
		}
	})

	marker := "e2e marker " + nonce + "\n"
	step("change the config: draft, submit, approve by a second member", func(t *testing.T) {
		s.t = t

		var draft version
		s.must("dev2", http.MethodPost, "/drafts", map[string]any{"packId": familyPackID}, http.StatusCreated, &draft)
		markerVersionID = draft.ID
		root.Cleanup(func() {
			t := root
			_ = t
			s.t = t
			var latest version
			status, env := s.call("dev2", http.MethodGet, "/versions/"+markerVersionID, nil)
			if status != http.StatusOK || json.Unmarshal(env.Data, &latest) != nil {
				t.Logf("could not read version %s for cleanup (status %d)", markerVersionID, status)
				return
			}
			if latest.Status == "IN_REVIEW" {
				if status, _ := s.call("dev", http.MethodPost, "/changes/"+markerVersionID+"/reviews",
					map[string]string{"decision": "REJECT", "comment": "e2e cleanup"}); status >= 300 {
					t.Logf("version %s was not rejected (status %d); reject it by hand", markerVersionID, status)
					return
				}
				latest.Status = "DRAFT"
			}
			if latest.Status == "DRAFT" {
				if status, _ := s.call("dev2", http.MethodDelete, "/drafts/"+markerVersionID, nil); status >= 300 {
					t.Logf("draft %s was not deleted (status %d); delete it by hand", markerVersionID, status)
				}
			}
		})

		s.must("dev2", http.MethodPut, "/drafts/"+draft.ID+"/files",
			map[string]any{"path": markerPath, "mode": "FILE", "encoding": "UTF_8", "content": marker}, http.StatusOK, nil)
		s.must("dev2", http.MethodPost, "/changes/"+draft.ID+"/submit",
			map[string]string{"reason": "end-to-end test " + nonce}, http.StatusOK, nil)

		// The proposer, and a MEMBER, cannot approve.
		if status, _ := s.call("dev2", http.MethodPost, "/changes/"+draft.ID+"/reviews", map[string]string{"decision": "APPROVE"}); status != http.StatusForbidden {
			t.Fatalf("the proposer's own approval answered %d, want 403", status)
		}
		var result struct {
			Version  version `json:"version"`
			Releases []struct {
				Number int `json:"number"`
			} `json:"releases"`
		}
		s.must("dev", http.MethodPost, "/changes/"+draft.ID+"/reviews", map[string]string{"decision": "APPROVE"}, http.StatusOK, &result)
		if result.Version.Status != "APPROVED" {
			t.Fatalf("version status after approve = %q, want APPROVED", result.Version.Status)
		}
		if len(result.Releases) == 0 {
			t.Fatal("the approval created no repository release")
		}
	})

	step("a new session and a sync see the new release", func(t *testing.T) {
		s.t = t
		cmd := launch(t, "second", false)
		if err := cmd.Wait(); err != nil {
			t.Fatalf("oba kaya launch: %v\n%s", err, cmd.Stdout.(*bytes.Buffer).String())
		}
		vars := stubVars(t, "second")
		if _, err := fmt.Sscanf(vars["KAYA_RELEASE"], "%d", &secondRelease); err != nil {
			t.Fatalf("KAYA_RELEASE %q is not a number: %v", vars["KAYA_RELEASE"], vars)
		}
		if secondRelease <= firstRelease {
			t.Fatalf("the new session runs release %d, not newer than %d", secondRelease, firstRelease)
		}
		if got := readFile(t, filepath.Join(checkout, markerPath)); got != marker {
			t.Fatalf("%s = %q, want %q", markerPath, got, marker)
		}
		synced := cli.status(checkout, "sync", "--json")
		if synced.Active != secondRelease || synced.Switched {
			t.Fatalf("oba kaya sync after the launch: active %d switched %v, want %d and false", synced.Active, synced.Switched, secondRelease)
		}
	})
}
