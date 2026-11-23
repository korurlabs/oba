package delivery

import (
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/delivery"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/kayatest"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/machine"
)

func TestStatus_printsTheKindAndTheNamingRemote(t *testing.T) {
	kaya, root := kayatest.SetupDelivery(t)
	kaya.Publish(t, 1, map[string]string{"AGENTS.md": "rules v1\n", ".agents/skills/demo/SKILL.md": "skill v1\n"})
	if out, err := run(t, "", "sync"); err != nil {
		t.Fatalf("sync: %s, %v", out, err)
	}
	requests := kaya.Requests.Load()
	out, err := run(t, "", "status")
	if err != nil || !strings.Contains(out, "Delivered: recognised by remote origin ("+kayatest.Remote+").") || !strings.Contains(out, "config release 1") {
		t.Fatalf("status: %s, %v", out, err)
	}
	out, err = run(t, "", "status", "--json")
	for _, want := range []string{`"kind": "Delivered"`, `"remote": "` + kayatest.Remote + `"`, `"remoteName": "origin"`, `"state": "FRESH"`} {
		if err != nil || !strings.Contains(out, want) {
			t.Fatalf("status --json lacks %s: %s, %v", want, out, err)
		}
	}
	if got := kaya.PathsSince(requests); len(got) != 0 {
		t.Fatalf("status called %v, want no API call", got)
	}

	if err := os.MkdirAll(filepath.Join(root, ".agents"), 0o750); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(root, ".agents", ".managed.json"), []byte("{}\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	out, err = run(t, "", "status")
	if err != nil || !strings.HasPrefix(out, "NotCutOver: recognised by remote origin ("+kayatest.Remote+")") {
		t.Fatalf("status of a checkout not cut over: %s, %v", out, err)
	}

	kayatest.Git(t, root, "remote", "set-url", "origin", "git@github.com:someone/private.git")
	out, err = run(t, "", "status")
	if err != nil || !strings.HasPrefix(out, "NotKaya: ") || strings.Contains(out, "someone") {
		t.Fatalf("status of an unlisted checkout: %s, %v", out, err)
	}
	out, err = run(t, "", "status", "--json")
	if err != nil || !strings.Contains(out, `"kind": "NotKaya"`) || strings.Contains(out, "someone") || strings.Contains(out, `"remote"`) {
		t.Fatalf("status --json of an unlisted checkout: %s, %v", out, err)
	}
}

func TestSync_aRepositoryAddedToKayaIsSyncedByTheFirstSync(t *testing.T) {
	kaya, _ := kayatest.SetupDelivery(t)
	kaya.Publish(t, 1, map[string]string{"AGENTS.md": "rules v1\n", ".agents/skills/demo/SKILL.md": "skill v1\n"})
	kayatest.ListRepositories(t, kayatest.Organization)
	out, err := run(t, "", "sync")
	if err != nil || !strings.Contains(out, "config release 1 (FRESH)") {
		t.Fatalf("sync: %s, %v", out, err)
	}
	if list, err := delivery.LoadRepositoryList(); err != nil || !list.Has(kayatest.Remote) {
		t.Fatalf("list = %+v, %v", list, err)
	}
}

func TestSync_aRepositoryKayaNoLongerListsStaysDelivered(t *testing.T) {
	kaya, _ := kayatest.SetupDelivery(t)
	kaya.Publish(t, 1, map[string]string{"AGENTS.md": "rules v1\n", ".agents/skills/demo/SKILL.md": "skill v1\n"})
	kaya.Listed = nil
	out, err := run(t, "", "sync")
	if err != nil || !strings.Contains(out, "config release 1 (FRESH)") {
		t.Fatalf("sync: %s, %v", out, err)
	}
	if list, err := delivery.LoadRepositoryList(); err != nil || !list.Has(kayatest.Remote) {
		t.Fatalf("list = %+v, %v; a fetch removed a remote", list, err)
	}
}

func TestSync_aRefusedMachineTokenIsNamed(t *testing.T) {
	kayatest.SetupDelivery(t)
	kayatest.ListRepositories(t, kayatest.Organization)
	t.Setenv(machine.EnvToken, "kyt_revoked")
	out, err := run(t, "", "sync")
	if err == nil || !strings.Contains(err.Error(), "repository list") || !strings.Contains(err.Error(), machine.EnvToken) {
		t.Fatalf("sync: %s, %v; want the list and the machine token named", out, err)
	}
}
