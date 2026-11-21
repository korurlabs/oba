package propose

import (
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/kayatest"
)

func TestPropose_sendsTheEditedFilesAndPrintsTheReviewURL(t *testing.T) {
	kaya, root := kayatest.SetupDelivery(t)
	kaya.Publish(t, 1, map[string]string{"AGENTS.md": "rules v1\n", ".agents/skills/demo/SKILL.md": "skill v1\n"}, "AGENTS.md")
	if out, err := run(t, "", "sync"); err != nil {
		t.Fatalf("sync: %s, %v", out, err)
	}
	kaya.Propose = func(map[string]any) (int, map[string]any) {
		return http.StatusCreated, map[string]any{"success": true, "data": map[string]any{
			"proposalId": "p-1", "number": 7, "status": "IN_REVIEW", "projectId": "s-1", "name": "demo",
			"releaseNumber": 1, "executableChange": false, "reviewUrl": "https://kaya.korur.com/changes/p-1",
		}}
	}
	if err := os.Remove(filepath.Join(root, "AGENTS.md")); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(root, "AGENTS.md"), []byte("rules v2\n"), 0o644); err != nil {
		t.Fatal(err)
	}

	out, err := run(t, "", "propose", "--reason", "Tighten the core rules", "AGENTS.md")

	if err != nil || !strings.Contains(out, "M AGENTS.md") || !strings.Contains(out, "Proposal 7 of demo is in review: https://kaya.korur.com/changes/p-1") {
		t.Fatalf("propose: %s, %v", out, err)
	}
	if len(kaya.Proposals) != 1 {
		t.Fatalf("proposals sent = %d", len(kaya.Proposals))
	}
	body := kaya.Proposals[0]
	files, _ := body["files"].([]any)
	if body["remoteUrl"] != "git@github.com:sensey/demo.git" || body["releaseNumber"] != float64(1) || body["reason"] != "Tighten the core rules" || len(files) != 1 {
		t.Fatalf("body = %v", body)
	}
	if f, _ := files[0].(map[string]any); f["path"] != "AGENTS.md" || f["content"] != "rules v2\n" || f["mode"] != "FILE" {
		t.Fatalf("file = %v", files[0])
	}
}

func TestPropose_explainsAConflictWithANewerChange(t *testing.T) {
	kaya, root := kayatest.SetupDelivery(t)
	kaya.Publish(t, 1, map[string]string{"AGENTS.md": "rules v1\n", ".agents/skills/demo/SKILL.md": "skill v1\n"}, "AGENTS.md")
	if out, err := run(t, "", "sync"); err != nil {
		t.Fatalf("sync: %s, %v", out, err)
	}
	kaya.Propose = func(map[string]any) (int, map[string]any) {
		return http.StatusConflict, map[string]any{"success": false, "error": map[string]any{
			"code": "KAY_CFG_002", "message": "The draft must be rebased",
			"errorValues": map[string]any{"currentVersionId": "v-2", "conflicts": []any{
				map[string]any{"path": ".agents/skills/demo/SKILL.md", "draftChange": "MODIFIED", "currentChange": "MODIFIED"},
			}},
		}}
	}
	scratch := t.TempDir()
	if err := os.MkdirAll(filepath.Join(scratch, ".agents/skills/demo"), 0o750); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(scratch, ".agents/skills/demo/SKILL.md"), []byte("skill v2\n"), 0o644); err != nil {
		t.Fatal(err)
	}

	out, err := run(t, "", "propose", "--reason", "Sharpen the demo skill", "--from", scratch, ".agents/skills/demo/SKILL.md")

	if err == nil || !strings.Contains(err.Error(), "Kaya changed these paths since release 1") ||
		!strings.Contains(err.Error(), ".agents/skills/demo/SKILL.md: you changed it, Kaya changed it") {
		t.Fatalf("conflict: %s, %v", out, err)
	}
	if got, _ := os.ReadFile(filepath.Join(root, ".agents/skills/demo/SKILL.md")); string(got) != "skill v1\n" {
		t.Fatalf("--from changed the checkout: %q", got)
	}
}

func TestPropose_refusesLocallyWithoutCallingKaya(t *testing.T) {
	kaya, root := kayatest.SetupDelivery(t)

	out, err := run(t, "", "propose", "--reason", "No release here yet", "AGENTS.md")
	if err == nil || !strings.Contains(err.Error(), "no config release is active") {
		t.Fatalf("without a release: %s, %v", out, err)
	}

	kaya.Publish(t, 1, map[string]string{"AGENTS.md": "rules v1\n", ".agents/skills/demo/SKILL.md": "skill v1\n"}, "AGENTS.md")
	if out, err := run(t, "", "sync"); err != nil {
		t.Fatalf("sync: %s, %v", out, err)
	}
	requests := kaya.Requests.Load()
	if out, err := run(t, "", "propose", "--reason", "Nothing was edited"); err == nil || !strings.Contains(err.Error(), "nothing changed against config release 1") {
		t.Fatalf("unchanged: %s, %v", out, err)
	}
	if err := os.Symlink("../../.agents/skills/demo/SKILL.md", filepath.Join(root, ".agents/skills/link.md")); err != nil {
		t.Fatal(err)
	}
	if err := os.MkdirAll(filepath.Join(root, ".agents/skills/copy"), 0o750); err != nil {
		t.Fatal(err)
	}
	if err := os.Symlink("../demo/SKILL.md", filepath.Join(root, ".agents/skills/copy/SKILL.md")); err != nil {
		t.Fatal(err)
	}
	if out, err := run(t, "", "propose", "--reason", "Link the skill twice", ".agents/skills/copy"); err == nil || !strings.Contains(err.Error(), "a SKILL.md is never a link") {
		t.Fatalf("skill link: %s, %v", out, err)
	}
	if out, err := run(t, "", "propose", ".agents/skills/link.md"); err == nil || !strings.Contains(err.Error(), "the reason has 0 characters") {
		t.Fatalf("no reason: %s, %v", out, err)
	}
	out, err = run(t, "", "propose", "--dry-run", ".agents/skills/link.md")
	if err != nil || !strings.Contains(out, "A .agents/skills/link.md -> ../../.agents/skills/demo/SKILL.md") || !strings.Contains(out, "nothing was sent") {
		t.Fatalf("dry run: %s, %v", out, err)
	}
	if kaya.Requests.Load() != requests {
		t.Fatal("a refused proposal called Kaya")
	}
}
