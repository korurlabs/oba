package propose

import (
	"context"
	"crypto/ed25519"
	"crypto/rand"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/korurlabs/kaya-cli/internal/client"
	"github.com/korurlabs/kaya-cli/internal/delivery"
)

const (
	testOrg    = "0d6f3c1e-7d1f-4a8e-9b2a-3c4d5e6f7a8b"
	testRemote = "github.com/sensey/demo"
	testKeyID  = "kaya-test"
)

// memorySource serves one signed release from memory.
type memorySource struct {
	current client.DeliveryCurrent
	release client.DeliveryRelease
	blobs   map[string][]byte
}

func (m *memorySource) CurrentRelease(context.Context, string, string) (client.DeliveryCurrent, string, bool, error) {
	return m.current, `"etag"`, false, nil
}

func (m *memorySource) ReleaseByID(context.Context, string) (client.DeliveryRelease, error) {
	return m.release, nil
}

func (m *memorySource) Blob(_ context.Context, sha string) ([]byte, error) {
	if data, ok := m.blobs[sha]; ok {
		return data, nil
	}
	return nil, errors.New("no blob " + sha)
}

type file struct {
	path, content, mode, target string
}

// checkout syncs a checkout to a signed release of files and returns the proposal source.
func checkout(t *testing.T, files []file) Source {
	t.Helper()
	root := t.TempDir()
	if err := os.Mkdir(filepath.Join(root, ".git"), 0o750); err != nil {
		t.Fatal(err)
	}
	config := "remoteUrl: https://github.com/sensey/demo.git\norganization: " + testOrg + "\n"
	if err := os.WriteFile(filepath.Join(root, delivery.ConfigFile), []byte(config), 0o600); err != nil {
		t.Fatal(err)
	}
	pub, priv, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	source := &memorySource{blobs: map[string][]byte{}}
	manifest := delivery.Manifest{Organization: testOrg, RemoteURL: testRemote, Number: 4, CreatedAt: "2026-10-24T10:00:00.000Z"}
	for _, f := range files {
		entry := delivery.ManifestFile{Path: f.path, Mode: f.mode}
		if entry.Mode == "" {
			entry.Mode = delivery.ModeFile
		}
		if entry.Mode == delivery.ModeSymlink {
			entry.Target = f.target
		} else {
			entry.SHA256 = hashOf([]byte(f.content))
			source.blobs[entry.SHA256] = []byte(f.content)
		}
		manifest.Files = append(manifest.Files, entry)
	}
	text, err := json.Marshal(manifest)
	if err != nil {
		t.Fatal(err)
	}
	source.release = client.DeliveryRelease{
		ReleaseID: "rel-4", RepositoryID: "repo-1", Number: 4, Manifest: string(text), ManifestSHA256: hashOf(text),
		Signature: base64.StdEncoding.EncodeToString(ed25519.Sign(priv, text)), KeyID: testKeyID,
	}
	source.current = client.DeliveryCurrent{RepositoryID: "repo-1", ReleaseID: "rel-4", Number: 4, ManifestSHA256: hashOf(text)}
	co, err := delivery.FindCheckout(root)
	if err != nil {
		t.Fatal(err)
	}
	d := &delivery.Deliverer{Checkout: co, Source: source, Keys: delivery.Keys{testKeyID: pub},
		Cache: delivery.Cache{Dir: filepath.Join(t.TempDir(), "blobs")}, OnlineTimeout: 5 * time.Second}
	out, err := d.Sync(context.Background(), delivery.Pin)
	if err != nil || out.Active != 4 {
		t.Fatalf("sync: %+v, %v", out, err)
	}
	active, _, err := d.ActiveRelease()
	if err != nil || active == nil {
		t.Fatalf("active release: %v", err)
	}
	return Source{Checkout: co, Release: active.Manifest, Cache: d.Cache}
}

func baseFiles() []file {
	return []file{
		{path: "AGENTS.md", content: "# Rules\n"},
		{path: ".agents/skills/demo/SKILL.md", content: "---\nname: demo\ndescription: A demo skill\n---\nbody\n"},
		{path: ".agents/hooks/dispatch.sh", content: "#!/bin/sh\nexit 0\n", mode: delivery.ModeExecutable},
		{path: ".claude/settings.json", content: "{\n  \"permissions\": {\n    \"deny\": [\"Bash(rm:*)\"]\n  }\n}\n"},
		{path: ".claude/skills/demo", mode: delivery.ModeSymlink, target: "../../.agents/skills/demo"},
	}
}

func write(t *testing.T, root, path, content string, perm os.FileMode) {
	t.Helper()
	abs := filepath.Join(root, filepath.FromSlash(path))
	if err := os.MkdirAll(filepath.Dir(abs), 0o750); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(abs, []byte(content), perm); err != nil {
		t.Fatal(err)
	}
}

// replace swaps a delivered link for a regular file, as an editor that writes by rename does.
func replace(t *testing.T, root, path, content string) {
	t.Helper()
	if err := os.Remove(filepath.Join(root, filepath.FromSlash(path))); err != nil {
		t.Fatal(err)
	}
	write(t, root, path, content, 0o644)
}

func describe(changes []Change) string {
	return strings.Join(Describe(changes), "\n")
}

func TestCollect_scanFindsEditsReplacementsRemovalsAndNewFiles(t *testing.T) {
	src := checkout(t, baseFiles())
	root := src.Checkout.Root
	// Written through the delivered link: the edit lands in .kaya/versions/4/.
	write(t, root, "AGENTS.md", "# Rules\nmore\n", 0o644)
	replace(t, root, ".agents/skills/demo/SKILL.md", "---\nname: demo\ndescription: Edited\n---\nbody\n")
	if err := os.Remove(filepath.Join(root, ".agents/hooks/dispatch.sh")); err != nil {
		t.Fatal(err)
	}
	write(t, root, ".agents/rules/new.md", "# New\n", 0o644)
	write(t, root, ".agents/memory/lessons.md", "stays in git\n", 0o644)

	changes, err := Collect(src, nil, nil)
	if err != nil {
		t.Fatal(err)
	}

	want := "D .agents/hooks/dispatch.sh\nA .agents/rules/new.md\nM .agents/skills/demo/SKILL.md\nM AGENTS.md"
	if got := describe(changes); got != want {
		t.Fatalf("changes:\n%s\nwant:\n%s", got, want)
	}
	if string(changes[3].Content) != "# Rules\nmore\n" || changes[3].Base == nil {
		t.Fatalf("AGENTS.md change = %+v", changes[3])
	}
	if err := Check(src, changes); err != nil {
		t.Fatalf("check: %v", err)
	}
}

func TestCollect_findsNewAndChangedConventionDocs(t *testing.T) {
	files := append(baseFiles(), file{path: "docs/private/conventions/backend/32-soft-delete.md", content: "# Soft delete\n"})
	src := checkout(t, files)
	root := src.Checkout.Root
	write(t, root, "docs/private/conventions/backend/32-soft-delete.md", "# Soft delete\nmore\n", 0o644)
	write(t, root, "docs/private/conventions/frontend/01-new.md", "# New\n", 0o644)
	write(t, root, "docs/private/conventions/frontend/diagram.png", "png", 0o644)
	write(t, root, "docs/private/projects/kaya/notes.md", "not config\n", 0o644)

	scanned, err := Collect(src, nil, nil)
	if err != nil {
		t.Fatal(err)
	}
	named, err := Collect(src, []string{"docs/private/conventions"}, nil)
	if err != nil {
		t.Fatal(err)
	}

	want := "M docs/private/conventions/backend/32-soft-delete.md\nA docs/private/conventions/frontend/01-new.md"
	if got := describe(scanned); got != want {
		t.Errorf("scan:\n%s\nwant:\n%s", got, want)
	}
	if got := describe(named); got != want {
		t.Errorf("named directory:\n%s\nwant:\n%s", got, want)
	}
	if err := Check(src, scanned); err != nil {
		t.Errorf("check: %v", err)
	}
}

func TestCollect_unchangedCheckoutProposesNothing(t *testing.T) {
	src := checkout(t, baseFiles())

	changes, err := Collect(src, nil, nil)

	if err != nil || len(changes) != 0 {
		t.Fatalf("changes = %s, err = %v", describe(changes), err)
	}
}

func TestCollect_namedDirectoryAndModeChange(t *testing.T) {
	src := checkout(t, baseFiles())
	root := src.Checkout.Root
	replace(t, root, ".agents/hooks/dispatch.sh", "#!/bin/sh\nexit 0\n")
	write(t, root, ".agents/hooks/second.sh", "#!/bin/sh\n", 0o755)
	write(t, root, "AGENTS.md", "# Edited, not named\n", 0o644)

	paths, err := ResolveArgs(src, filepath.Join(root, ".agents"), []string{"hooks"})
	if err != nil {
		t.Fatal(err)
	}
	changes, err := Collect(src, paths, nil)

	want := "M .agents/hooks/dispatch.sh (EXECUTABLE -> FILE)\nA .agents/hooks/second.sh (executable)"
	if err != nil || describe(changes) != want {
		t.Fatalf("changes:\n%s\nerr = %v", describe(changes), err)
	}
}

func TestCollect_fromDirectoryAndRemoveLeaveTheCheckoutAlone(t *testing.T) {
	src := checkout(t, baseFiles())
	scratch := t.TempDir()
	write(t, scratch, "AGENTS.md", "# Rules from the agent\n", 0o644)
	write(t, scratch, ".agents/skills/other/SKILL.md", "---\nname: other\n---\n", 0o644)
	src.From = scratch

	paths, err := ResolveArgs(src, "/elsewhere", []string{"AGENTS.md", filepath.Join(scratch, ".agents/skills/other")})
	if err != nil {
		t.Fatal(err)
	}
	changes, err := Collect(src, paths, []string{".agents/hooks/dispatch.sh"})

	want := "D .agents/hooks/dispatch.sh\nA .agents/skills/other/SKILL.md\nM AGENTS.md"
	if err != nil || describe(changes) != want {
		t.Fatalf("changes:\n%s\nerr = %v", describe(changes), err)
	}
	if _, err := Collect(src, []string{".agents/rules/missing.md"}, nil); err == nil || !strings.Contains(err.Error(), "--remove") {
		t.Fatalf("missing path in --from: %v", err)
	}
	if _, err := Collect(src, nil, []string{".agents/rules/never.md"}); err == nil || !strings.Contains(err.Error(), "nothing to remove") {
		t.Fatalf("removal of an undelivered path: %v", err)
	}
	if _, err := ResolveArgs(src, "/", []string{"/etc/passwd"}); err == nil {
		t.Fatal("a path outside --from was accepted")
	}
}

// outside is a directory outside the source, with a secret a proposal must never read.
func outside(t *testing.T) string {
	t.Helper()
	dir := t.TempDir()
	write(t, dir, "id_rsa", "secret key\n", 0o600)
	write(t, dir, "SKILL.md", "---\nname: x\n---\nsecret skill\n", 0o644)
	write(t, dir, "dispatch.sh", "#!/bin/sh\nsecret hook\n", 0o755)
	return dir
}

func link(t *testing.T, target, at string) {
	t.Helper()
	if err := os.MkdirAll(filepath.Dir(at), 0o750); err != nil {
		t.Fatal(err)
	}
	if err := os.Symlink(target, at); err != nil {
		t.Fatal(err)
	}
}

func refusedBelowLink(t *testing.T, changes []Change, err error, what string) {
	t.Helper()
	if err == nil || !strings.Contains(err.Error(), "lies below the link") {
		t.Fatalf("%s: changes %q, err = %v", what, describe(changes), err)
	}
	if strings.Contains(err.Error(), "secret") {
		t.Fatalf("%s: the refusal carries content from outside: %v", what, err)
	}
	for _, change := range changes {
		if strings.Contains(string(change.Content), "secret") {
			t.Fatalf("%s: read %s from outside the source", what, change.Path)
		}
	}
}

func TestCollect_checkoutNeverReadsThroughAParentLink(t *testing.T) {
	src := checkout(t, baseFiles())
	root := src.Checkout.Root
	dir := outside(t)
	link(t, dir, filepath.Join(root, ".agents/skills/x"))

	for _, arg := range []string{".agents/skills/x/SKILL.md", ".agents/skills/x/id_rsa"} {
		paths, err := ResolveArgs(src, root, []string{arg})
		if err != nil {
			t.Fatal(err)
		}
		changes, err := Collect(src, paths, nil)
		refusedBelowLink(t, changes, err, arg)
	}

	// A delivered directory replaced by a link: the scan reads its delivered path, and refuses.
	if err := os.RemoveAll(filepath.Join(root, ".agents/hooks")); err != nil {
		t.Fatal(err)
	}
	link(t, dir, filepath.Join(root, ".agents/hooks"))
	changes, err := Collect(src, nil, nil)
	refusedBelowLink(t, changes, err, "scan")
	changes, err = Collect(src, []string{".agents/hooks/dispatch.sh"}, nil)
	refusedBelowLink(t, changes, err, "named delivered path")
}

func TestCollect_fromNeverReadsThroughAParentLink(t *testing.T) {
	src := checkout(t, baseFiles())
	scratch := t.TempDir()
	dir := outside(t)
	link(t, dir, filepath.Join(scratch, ".agents/skills/x"))
	link(t, dir, filepath.Join(scratch, ".agents/hooks"))
	src.From = scratch

	for _, arg := range []string{".agents/skills/x/SKILL.md", filepath.Join(scratch, ".agents/skills/x/id_rsa"), ".agents/hooks/dispatch.sh"} {
		paths, err := ResolveArgs(src, "/elsewhere", []string{arg})
		if err != nil {
			t.Fatal(err)
		}
		changes, err := Collect(src, paths, nil)
		refusedBelowLink(t, changes, err, arg)
	}

	// A scan of --from lists the links themselves and reads nothing behind them.
	changes, err := Collect(src, nil, nil)
	if err != nil {
		t.Fatal(err)
	}
	for _, change := range changes {
		if strings.Contains(string(change.Content), "secret") || strings.HasPrefix(change.Path, ".agents/skills/x/") {
			t.Fatalf("scan read %s from outside --from", change.Path)
		}
	}
}

func TestCheck_refusesWhatKayaWouldRefuse(t *testing.T) {
	src := checkout(t, baseFiles())
	root := src.Checkout.Root
	write(t, root, ".agents/memory/note.md", "lessons stay in git\n", 0o644)
	write(t, root, ".agents/skills/DEMO/extra.md", "case\n", 0o644)
	for _, dir := range []string{".agents/skills/linked", ".agents/rules"} {
		if err := os.MkdirAll(filepath.Join(root, dir), 0o750); err != nil {
			t.Fatal(err)
		}
	}
	for path, target := range map[string]string{
		".agents/skills/linked/SKILL.md": "../demo/SKILL.md",
		".agents/hooks/run.sh":           "dispatch.sh",
		".agents/rules/outside.md":       "../../../etc/passwd",
		".agents/rules/dangling.md":      "missing.md",
	} {
		if err := os.Symlink(target, filepath.Join(root, filepath.FromSlash(path))); err != nil {
			t.Fatal(err)
		}
	}

	changes, err := Collect(src, []string{".agents/memory/note.md", ".agents/skills/DEMO", ".agents/skills/linked", ".agents/hooks/run.sh", ".agents/rules"}, nil)
	if err != nil {
		t.Fatal(err)
	}
	err = Check(src, changes)

	var refusal *Refusal
	if !errors.As(err, &refusal) {
		t.Fatalf("err = %v", err)
	}
	text := err.Error()
	for _, want := range []string{
		".agents/memory/note.md: not a path Kaya delivers",
		".agents/skills/DEMO/extra.md: differs from .agents/skills/demo only in letter case",
		".agents/skills/linked/SKILL.md: a SKILL.md is never a link",
		".agents/hooks/run.sh: a hook is never a link",
		".agents/rules/outside.md: the link leaves the repository",
		".agents/rules/dangling.md: points at .agents/rules/missing.md, which this release does not deliver",
	} {
		if !strings.Contains(text, want) {
			t.Errorf("missing %q in:\n%s", want, text)
		}
	}
}

func TestCheck_removalThatLeavesALinkDanglingIsRefused(t *testing.T) {
	src := checkout(t, baseFiles())

	changes, err := Collect(src, nil, []string{".agents/skills/demo/SKILL.md"})
	if err != nil {
		t.Fatal(err)
	}
	err = Check(src, changes)

	if err == nil || !strings.Contains(err.Error(), ".claude/skills/demo: points at .agents/skills/demo, which this release does not deliver") {
		t.Fatalf("err = %v", err)
	}
}

func TestCheck_sizeLimitsOnlyRefuseAddedOrGrownFiles(t *testing.T) {
	long := func(lines int) string {
		return "---\nname: big\n---\n" + strings.Repeat("line\n", lines-3)
	}
	files := append(baseFiles(), file{path: ".agents/skills/big/SKILL.md", content: long(1600)})
	src := checkout(t, files)
	root := src.Checkout.Root

	replace(t, root, ".agents/skills/big/SKILL.md", long(1550))
	changes, err := Collect(src, []string{".agents/skills/big"}, nil)
	if err != nil || Check(src, changes) != nil {
		t.Fatalf("a shrinking skill over the limit was refused: %v / %v", err, Check(src, changes))
	}

	replace(t, root, ".agents/skills/big/SKILL.md", long(1601))
	changes, _ = Collect(src, []string{".agents/skills/big"}, nil)
	if err := Check(src, changes); err == nil || !strings.Contains(err.Error(), "has 1601 lines; a SKILL.md must stay under 1500") {
		t.Fatalf("a growing skill: %v", err)
	}

	write(t, root, ".agents/skills/new/SKILL.md", long(1500), 0o644)
	description := "---\nname: wordy\ndescription: " + strings.Repeat("w", 1500) + "\nwhen_to_use: " + strings.Repeat("u", 40) + "\n---\n"
	write(t, root, ".agents/skills/wordy/SKILL.md", description, 0o644)
	write(t, root, ".agents/rules/huge.md", strings.Repeat("x", maxFileBytes+1), 0o644)
	changes, _ = Collect(src, []string{".agents/skills/new", ".agents/skills/wordy", ".agents/rules/huge.md"}, nil)
	err = Check(src, changes)
	for _, want := range []string{
		".agents/skills/new/SKILL.md: has 1500 lines",
		".agents/skills/wordy/SKILL.md: description plus when_to_use have 1540 characters; at most 1536",
		fmt.Sprintf(".agents/rules/huge.md: has %d bytes", maxFileBytes+1),
	} {
		if err == nil || !strings.Contains(err.Error(), want) {
			t.Errorf("missing %q in %v", want, err)
		}
	}
}

func TestCheck_settingsMustStayAJSONObject(t *testing.T) {
	src := checkout(t, baseFiles())
	write(t, src.Checkout.Root, ".claude/settings.json", "[1, 2]\n", 0o644)

	changes, err := Collect(src, []string{".claude/settings.json"}, nil)
	if err != nil {
		t.Fatal(err)
	}

	if err := Check(src, changes); err == nil || !strings.Contains(err.Error(), ".claude/settings.json: is not a JSON object") {
		t.Fatalf("err = %v", err)
	}
}

func TestWithoutMergedDeny_neverProposesAMergedRule(t *testing.T) {
	base := []byte("{\n  \"permissions\": {\n    \"deny\": [\"Bash(rm:*)\"]\n  }\n}\n")
	guardrails := []string{"Bash(curl:*)", "Bash(rm:*)"}
	merged, err := delivery.MergeDeny(base, guardrails...)
	if err != nil {
		t.Fatal(err)
	}

	same, err := withoutMergedDeny(merged, base, guardrails)
	if err != nil || string(same) != string(base) {
		t.Fatalf("unchanged settings = %q, %v", same, err)
	}

	edited := strings.Replace(string(merged), "\"permissions\"", "\"model\": \"opus\",\n  \"permissions\"", 1)
	got, err := withoutMergedDeny([]byte(edited), base, guardrails)
	if err != nil {
		t.Fatal(err)
	}
	if strings.Contains(string(got), "curl") || !strings.Contains(string(got), "Bash(rm:*)") || !strings.Contains(string(got), `"model": "opus"`) {
		t.Fatalf("edited settings = %s", got)
	}

	bare := []byte("{\n  \"model\": \"sonnet\"\n}\n")
	mergedBare, err := delivery.MergeDeny(bare, guardrails...)
	if err != nil {
		t.Fatal(err)
	}
	editedBare := strings.Replace(string(mergedBare), "sonnet", "opus", 1)
	got, err = withoutMergedDeny([]byte(editedBare), bare, guardrails)
	if err != nil || string(got) != "{\n  \"model\": \"opus\"\n}\n" {
		t.Fatalf("settings without permissions = %q, %v", got, err)
	}
}

func TestRequest_sendsTextBinaryLinksAndRemovals(t *testing.T) {
	changes := []Change{
		{Path: "AGENTS.md", Kind: Modified, Mode: client.ModeFile, Content: []byte("text\n")},
		{Path: ".agents/hooks/bin", Kind: Added, Mode: client.ModeExecutable, Content: []byte{0xff, 0x00}},
		{Path: ".agents/rules/empty.md", Kind: Added, Mode: client.ModeFile, Content: []byte{}},
		{Path: ".claude/skills/x", Kind: Added, Mode: client.ModeSymlink, Target: "../../.agents/skills/x"},
		{Path: ".agents/rules/old.md", Kind: Deleted},
	}

	request, err := Request("git@github.com:sensey/demo.git", 4, "Because the rules changed", changes)
	if err != nil {
		t.Fatal(err)
	}
	body, err := json.Marshal(request)
	if err != nil {
		t.Fatal(err)
	}

	want := `{"remoteUrl":"git@github.com:sensey/demo.git","releaseNumber":4,"reason":"Because the rules changed","files":[` +
		`{"path":"AGENTS.md","mode":"FILE","encoding":"UTF_8","content":"text\n"},` +
		`{"path":".agents/hooks/bin","mode":"EXECUTABLE","encoding":"BASE64","content":"/wA="},` +
		`{"path":".agents/rules/empty.md","mode":"FILE","encoding":"UTF_8","content":""},` +
		`{"path":".claude/skills/x","mode":"SYMLINK","symlinkTarget":"../../.agents/skills/x"},` +
		`{"path":".agents/rules/old.md","deleted":true}]}`
	if string(body) != want {
		t.Fatalf("body:\n%s\nwant:\n%s", body, want)
	}

	big := []Change{{Path: "AGENTS.md", Kind: Modified, Mode: client.ModeFile, Content: []byte(strings.Repeat("\"", MaxBodyBytes/2))}}
	if _, err := Request("r", 4, "Because the rules changed", big); err == nil || !strings.Contains(err.Error(), "smaller groups") {
		t.Fatalf("an oversized body: %v", err)
	}
}

func TestCheckReason_countsLikeKaya(t *testing.T) {
	if CheckReason("too short") == nil || CheckReason(strings.Repeat("r", 1001)) == nil {
		t.Fatal("a reason outside 10 to 1000 characters was accepted")
	}
	if err := CheckReason("Ten chars!"); err != nil {
		t.Fatal(err)
	}
}

func TestExplain_turnsKayaRefusalsIntoNextSteps(t *testing.T) {
	modified, deleted := "MODIFIED", "DELETED"
	stale := &client.APIError{Status: 409, Code: "KAY_CFG_002", Message: "rebase", ErrorValues: map[string]any{
		"currentVersionId": "v-9",
		"conflicts":        []any{map[string]any{"path": "AGENTS.md", "draftChange": modified, "currentChange": deleted}},
	}}
	err := Explain(stale, 4)
	if !strings.Contains(err.Error(), "Kaya changed these paths since release 4") ||
		!strings.Contains(err.Error(), "AGENTS.md: you changed it, Kaya removed it") || !strings.Contains(err.Error(), "kaya sync") {
		t.Fatalf("stale: %v", err)
	}
	if !client.IsCode(err, "KAY_CFG_002") {
		t.Fatal("the refusal hides Kaya's answer")
	}

	owners := &client.APIError{Status: 422, Code: "KAY_CFG_019", ErrorValues: map[string]any{
		"owners": map[string]any{"owner-b": []any{"b.md"}, "owner-a": []any{"a2.md", "a1.md"}},
	}}
	if got := Explain(owners, 4).Error(); !strings.Contains(got, "owner owner-a: kaya propose --reason <why> a1.md a2.md\n  owner owner-b:") {
		t.Fatalf("owners: %s", got)
	}

	rules := &client.APIError{Status: 422, Code: "KAY_CFG_001", ErrorValues: map[string]any{
		"violations": []any{map[string]any{"path": "AGENTS.md", "rule": "SYMLINK_CONTENT_REQUIRED", "message": "must be a regular file"}},
	}}
	if got := Explain(rules, 4).Error(); !strings.Contains(got, "AGENTS.md: SYMLINK_CONTENT_REQUIRED: must be a regular file\n") {
		t.Fatalf("violations: %s", got)
	}

	for code, want := range map[string]string{
		"KAY_CFG_015": "already in review (version v-1)",
		"KAY_CFG_016": "nothing to propose",
		"KAY_SEC_003": "Kaya refused the machine token",
	} {
		apiErr := &client.APIError{Status: 409, Code: code, ErrorValues: map[string]any{"inReviewVersionId": "v-1"}}
		if got := Explain(apiErr, 4).Error(); !strings.Contains(got, want) {
			t.Errorf("%s: %s", code, got)
		}
	}
	siblings := &client.APIError{Status: 422, Code: "KAY_CFG_019", ErrorValues: map[string]any{"candidates": []any{"pack-1", "pack-2"}}}
	if got := Explain(siblings, 4).Error(); !strings.Contains(got, "Several packs at the root") || !strings.Contains(got, "pack pack-2") {
		t.Fatalf("sibling root packs: %s", got)
	}
	noRoot := &client.APIError{Status: 422, Code: "KAY_CFG_019", ErrorValues: map[string]any{"candidates": []any{}}}
	if got := Explain(noRoot, 4).Error(); !strings.Contains(got, "uses no pack at its root") {
		t.Fatalf("no root pack: %s", got)
	}
	plain := errors.New("dial tcp: refused")
	if Explain(plain, 4) != plain {
		t.Fatal("a transport error was rewritten")
	}
}

func TestCollect_findsTheAgentsFileOfAProjectFolder(t *testing.T) {
	src := checkout(t, append(baseFiles(), file{path: "projects/kagi/AGENTS.md", content: "# Kagi\n"}))
	root := src.Checkout.Root
	write(t, root, "projects/kagi/AGENTS.md", "# Kagi\nmore\n", 0o644)
	write(t, root, "projects/kagi/operator/AGENTS.md", "stays in git\n", 0o644)

	changes, err := Collect(src, nil, nil)
	if err != nil || describe(changes) != "M projects/kagi/AGENTS.md" {
		t.Fatalf("scan:\n%s\nerr = %v", describe(changes), err)
	}
	if err := Check(src, changes); err != nil {
		t.Fatalf("check: %v", err)
	}

	write(t, root, "projects/kaizen/AGENTS.md", "# Kaizen\n", 0o644)
	write(t, root, "projects/kaizen/cli/AGENTS.md", "stays in git\n", 0o644)
	changes, err = Collect(src, []string{"projects/kaizen"}, nil)
	if err != nil || describe(changes) != "A projects/kaizen/AGENTS.md" {
		t.Fatalf("named folder:\n%s\nerr = %v", describe(changes), err)
	}

	scratch := t.TempDir()
	write(t, scratch, "projects/kagi/AGENTS.md", "# Kagi from the agent\n", 0o644)
	src.From = scratch
	changes, err = Collect(src, nil, nil)
	if err != nil || describe(changes) != "M projects/kagi/AGENTS.md" {
		t.Fatalf("from scan:\n%s\nerr = %v", describe(changes), err)
	}
	changes, err = Collect(src, []string{"projects/kagi"}, nil)
	if err != nil || describe(changes) != "M projects/kagi/AGENTS.md" {
		t.Fatalf("from folder:\n%s\nerr = %v", describe(changes), err)
	}
}
