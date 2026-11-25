package propose

import (
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/kayatest"
)

// syncedRelease syncs a checkout to release 1 and makes the fake Kaya accept proposals.
func syncedRelease(t *testing.T) (*kayatest.FakeKaya, string) {
	t.Helper()
	kaya, root := kayatest.SetupDelivery(t)
	kaya.Publish(t, 1, map[string]string{"AGENTS.md": "rules v1\n", ".agents/skills/demo/SKILL.md": "skill v1\n"}, "AGENTS.md")
	if out, err := run(t, "", "sync"); err != nil {
		t.Fatalf("sync: %s, %v", out, err)
	}
	kaya.Propose = func(map[string]any) (int, map[string]any) {
		return http.StatusCreated, map[string]any{"success": true, "data": map[string]any{
			"proposalId": "p-1", "number": 3, "status": "IN_REVIEW", "name": "demo", "projectId": "s-1",
			"releaseNumber": 1, "executableChange": true, "reviewUrl": "https://kaya.korur.com/changes/p-1",
		}}
	}
	return kaya, root
}

func TestPropose_removeSendsADeletedEntryAndNamesTheOwnerForExecutableChanges(t *testing.T) {
	kaya, _ := syncedRelease(t)

	out, err := run(t, "", "propose", "--reason", "Drop the demo skill", "--remove", ".agents/skills/demo/SKILL.md")

	if err != nil || !strings.Contains(out, "D .agents/skills/demo/SKILL.md") || !strings.Contains(out, "an OWNER other than you approves it") {
		t.Fatalf("out = %s, err = %v", out, err)
	}
	files, _ := kaya.Proposals[0]["files"].([]any)
	entry, _ := files[0].(map[string]any)
	if len(files) != 1 || len(entry) != 2 || entry["path"] != ".agents/skills/demo/SKILL.md" || entry["deleted"] != true {
		t.Fatalf("files = %v", files)
	}
}

func TestPropose_bodyMatchesTheBackendContractForEveryKindOfFile(t *testing.T) {
	kaya, root := syncedRelease(t)
	if err := os.MkdirAll(filepath.Join(root, ".agents/hooks"), 0o750); err != nil {
		t.Fatal(err)
	}
	if err := os.MkdirAll(filepath.Join(root, ".agents/rules"), 0o750); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(root, ".agents/hooks/run.sh"), []byte("#!/bin/sh\n"), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(root, ".agents/rules/empty.md"), nil, 0o644); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(root, ".agents/rules/bin.md"), []byte{0xff, 0xfe}, 0o644); err != nil {
		t.Fatal(err)
	}
	if err := os.Symlink("../skills/demo/SKILL.md", filepath.Join(root, ".agents/rules/link.md")); err != nil {
		t.Fatal(err)
	}

	out, err := run(t, "", "propose", "--reason", "Add hooks, rules and a link")
	if err != nil {
		t.Fatalf("out = %s, err = %v", out, err)
	}

	body := kaya.Proposals[0]
	if body["remoteUrl"] != "git@github.com:example/demo.git" || body["releaseNumber"] != float64(1) || len(body) != 4 {
		t.Fatalf("body = %v", body)
	}
	byPath := map[string]map[string]any{}
	files, _ := body["files"].([]any)
	for _, f := range files {
		entry, _ := f.(map[string]any)
		path, _ := entry["path"].(string)
		byPath[path] = entry
	}
	want := map[string]map[string]any{
		".agents/hooks/run.sh":   {"path": ".agents/hooks/run.sh", "mode": "EXECUTABLE", "encoding": "UTF_8", "content": "#!/bin/sh\n"},
		".agents/rules/empty.md": {"path": ".agents/rules/empty.md", "mode": "FILE", "encoding": "UTF_8", "content": ""},
		".agents/rules/bin.md":   {"path": ".agents/rules/bin.md", "mode": "FILE", "encoding": "BASE64", "content": "//4="},
		".agents/rules/link.md":  {"path": ".agents/rules/link.md", "mode": "SYMLINK", "symlinkTarget": "../skills/demo/SKILL.md"},
	}
	if len(byPath) != len(want) {
		t.Fatalf("files = %v", files)
	}
	for path, expected := range want {
		got := byPath[path]
		if len(got) != len(expected) {
			t.Errorf("%s = %v, want %v", path, got, expected)
			continue
		}
		for key, value := range expected {
			if got[key] != value {
				t.Errorf("%s.%s = %v, want %v", path, key, got[key], value)
			}
		}
	}
}

func TestPropose_dryRunSendsNothingAndAReasonIsNotNeeded(t *testing.T) {
	kaya, root := syncedRelease(t)
	if err := os.Remove(filepath.Join(root, ".agents/skills/demo/SKILL.md")); err != nil {
		t.Fatal(err)
	}
	requests := kaya.Requests.Load()

	out, err := run(t, "", "propose", "--dry-run")

	if err != nil || !strings.Contains(out, "D .agents/skills/demo/SKILL.md") || !strings.Contains(out, "nothing was sent") {
		t.Fatalf("out = %s, err = %v", out, err)
	}
	if len(kaya.Proposals) != 0 || kaya.Requests.Load() != requests {
		t.Fatal("a dry run called Kaya")
	}
}

func TestPropose_askingForTheReasonReadsALineAndRefusesAShortOne(t *testing.T) {
	kaya, root := syncedRelease(t)
	if err := os.Remove(filepath.Join(root, "AGENTS.md")); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(root, "AGENTS.md"), []byte("rules v2\n"), 0o644); err != nil {
		t.Fatal(err)
	}

	out, err := run(t, "short\n", "propose")
	if err == nil || !strings.Contains(err.Error(), "the reason has 5 characters") || len(kaya.Proposals) != 0 {
		t.Fatalf("short reason: %s, %v", out, err)
	}
	out, err = run(t, "Tighten the core rules\n", "propose")
	if err != nil || !strings.Contains(out, "Why is this change needed?") || kaya.Proposals[0]["reason"] != "Tighten the core rules" {
		t.Fatalf("asked reason: %s, %v", out, err)
	}
	out, err = run(t, "", "propose", "--reason", strings.Repeat("r", 1001))
	if err == nil || !strings.Contains(err.Error(), "the reason has 1001 characters") {
		t.Fatalf("long reason: %s, %v", out, err)
	}
}

func TestPropose_everyRefusalCodeIsExplainedAndKeepsTheCheckout(t *testing.T) {
	kaya, root := syncedRelease(t)
	if err := os.Remove(filepath.Join(root, "AGENTS.md")); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(root, "AGENTS.md"), []byte("rules v2\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	cases := []struct {
		status int
		code   string
		values map[string]any
		want   string
	}{
		{http.StatusUnprocessableEntity, "KAY_CFG_001", map[string]any{"violations": []any{map[string]any{"path": "AGENTS.md", "rule": "SYMLINK_CONTENT_REQUIRED", "message": "must be a regular file"}}}, "AGENTS.md: SYMLINK_CONTENT_REQUIRED: must be a regular file"},
		{http.StatusConflict, "KAY_CFG_015", map[string]any{"inReviewVersionId": "v-7"}, "already in review (version v-7)"},
		{http.StatusConflict, "KAY_CFG_016", nil, "nothing to propose"},
		{http.StatusUnprocessableEntity, "KAY_CFG_019", map[string]any{"owners": map[string]any{"o1": []any{"AGENTS.md"}}}, "owner o1: oba kaya propose --reason <why> AGENTS.md"},
		{http.StatusNotFound, "KAY_NF_001", map[string]any{"repositoryId": "repo-1", "release": 1}, "knows no release 1"},
		{http.StatusForbidden, "ORG_SEC_001", map[string]any{"OrganizationId": "org-1"}, "not a member of this organization"},
		{http.StatusForbidden, "GEN_SEC_002", map[string]any{"status": "DISABLED"}, "is not active (DISABLED)"},
		{http.StatusForbidden, "ORG_SEC_003", map[string]any{"OrganizationId": "org-1"}, "is a GUEST of this organization"},
		{http.StatusForbidden, "ORG_SEC_005", map[string]any{"OrganizationId": "org-1"}, "organization is disabled"},
		{http.StatusForbidden, "GEN_SEC_005", map[string]any{"OrganizationId": "org-2"}, "named another organization (org-2)"},
		{http.StatusForbidden, "APP_SEC_001", map[string]any{"OrganizationId": "org-1", "App": "KAYA"}, "grant you Kaya in the Ulak web app"},
		{http.StatusUnauthorized, "KAY_SEC_003", nil, "refused the machine token"},
	}
	for _, c := range cases {
		kaya.Propose = func(map[string]any) (int, map[string]any) {
			return c.status, map[string]any{"success": false, "error": map[string]any{"code": c.code, "message": "refused", "errorValues": c.values}}
		}
		out, err := run(t, "", "propose", "--reason", "Tighten the core rules")
		if err == nil || !strings.Contains(err.Error(), c.want) {
			t.Errorf("%s: %s, %v", c.code, out, err)
		}
	}
	if got, _ := os.ReadFile(filepath.Join(root, "AGENTS.md")); string(got) != "rules v2\n" {
		t.Fatalf("a refusal changed the checkout: %q", got)
	}
}
