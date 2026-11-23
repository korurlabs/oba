package delivery

import (
	"context"
	"errors"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

const otherOrg = "7a8b9c0d-1e2f-4a3b-8c4d-5e6f7a8b9c0d"

func floorOf(t *testing.T, organization, remote string) int {
	t.Helper()
	floors, err := LoadFloors()
	if err != nil {
		t.Fatal(err)
	}
	return floors.Floor(organization, remote)
}

func TestSync_aVerifiedReleaseRaisesTheFloor(t *testing.T) {
	f := newFixture(t)
	f.release(1, baseFiles("v1\n"))
	f.sync(Pin)
	if got := floorOf(t, testOrg, testRemote); got != 1 {
		t.Fatalf("floor = %d, want 1", got)
	}
	f.release(2, baseFiles("v2\n"))

	f.sync(Pin)

	if got := floorOf(t, testOrg, testRemote); got != 2 {
		t.Fatalf("floor = %d, want 2", got)
	}
	path, err := FloorsPath()
	if err != nil {
		t.Fatal(err)
	}
	info, err := os.Stat(path)
	if err != nil || info.Mode().Perm() != 0o600 {
		t.Fatalf("floors.json: %v, mode %v", err, info.Mode())
	}
	entries, err := os.ReadDir(filepath.Dir(path))
	if err != nil {
		t.Fatal(err)
	}
	for _, entry := range entries {
		if strings.Contains(entry.Name(), ".tmp-") {
			t.Fatalf("a temporary file was left: %s", entry.Name())
		}
	}
}

func TestSync_aStagedReleaseRaisesTheFloorToo(t *testing.T) {
	f := newFixture(t)
	f.release(1, baseFiles("v1\n"))
	f.sync(Pin)
	if err := f.d.Checkout.RegisterSession(Session{PID: os.Getpid(), Release: 1}); err != nil {
		t.Fatal(err)
	}
	f.release(2, baseFiles("v2\n"))

	out := f.sync(Pin)

	if out.Active != 1 || out.Staged != 2 {
		t.Fatalf("outcome = %+v, want release 2 staged", out)
	}
	if got := floorOf(t, testOrg, testRemote); got != 2 {
		t.Fatalf("floor = %d, want 2", got)
	}
}

func TestSync_aNewWorktreeRefusesAReleaseBelowTheFloor(t *testing.T) {
	f := newFixture(t)
	f.commit(map[string]string{"README.md": "demo\n"})
	f.release(3, baseFiles("v3\n"))
	f.sync(Pin)
	dir := f.worktree()
	f.release(2, baseFiles("v2\n"))

	out, err := f.deliverer(dir).Sync(context.Background(), Pin)

	if err != nil {
		t.Fatal(err)
	}
	if out.Active != 0 || out.State != StateNone || out.Offline == nil || !strings.Contains(out.Offline.Error(), "older than release 3") {
		t.Fatalf("outcome = %+v (offline %v)", out, out.Offline)
	}
	if _, err := os.Lstat(filepath.Join(dir, "AGENTS.md")); !errors.Is(err, os.ErrNotExist) {
		t.Fatalf("AGENTS.md was placed in the worktree (err %v)", err)
	}
	if got := floorOf(t, testOrg, testRemote); got != 3 {
		t.Fatalf("floor = %d, want 3", got)
	}

	f.release(3, baseFiles("v3\n"))
	out, err = f.deliverer(dir).Sync(context.Background(), Pin)

	if err != nil || out.Active != 3 {
		t.Fatalf("release 3 in the worktree: outcome = %+v, err = %v", out, err)
	}
}

func TestSync_aCheckoutAfterGitCleanRefusesAReleaseBelowTheFloor(t *testing.T) {
	f := newFixture(t)
	f.release(3, baseFiles("v3\n"))
	f.sync(Pin)
	gitIn(t, f.root, "clean", "-fdxq")
	f.release(2, baseFiles("v2\n"))

	out := f.sync(Pin)

	if out.Active != 0 || out.Offline == nil || !strings.Contains(out.Offline.Error(), "older than release 3") {
		t.Fatalf("outcome = %+v (offline %v)", out, out.Offline)
	}
	if _, err := os.Lstat(filepath.Join(f.root, "AGENTS.md")); !errors.Is(err, os.ErrNotExist) {
		t.Fatalf("AGENTS.md was placed (err %v)", err)
	}
}

func TestSync_aDamagedFloorsFileIsReportedAndKept(t *testing.T) {
	f := newFixture(t)
	path, err := FloorsPath()
	if err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(path, []byte("{"), 0o600); err != nil {
		t.Fatal(err)
	}
	f.release(1, baseFiles("v1\n"))

	out := f.sync(Pin)

	if out.Active != 1 || len(out.Warnings) == 0 || !strings.Contains(strings.Join(out.Warnings, "\n"), "damaged") {
		t.Fatalf("outcome = %+v", out)
	}
	if data, err := os.ReadFile(path); err != nil || string(data) != "{" {
		t.Fatalf("floors.json = %q, %v", data, err)
	}
}

func TestRaiseFloor_onlyRisesPerOrganizationAndRemote(t *testing.T) {
	isolateConfig(t)
	ctx := context.Background()
	const a, b = "github.com/example/a", "github.com/example/b"
	for _, raise := range []struct {
		organization, remote string
		number               int
	}{{testOrg, a, 5}, {testOrg, a, 3}, {strings.ToUpper(otherOrg), b, 2}} {
		if err := RaiseFloor(ctx, raise.organization, raise.remote, raise.number); err != nil {
			t.Fatal(err)
		}
	}

	for _, want := range []struct {
		organization, remote string
		floor                int
	}{{testOrg, a, 5}, {testOrg, b, 0}, {otherOrg, b, 2}, {otherOrg, a, 0}, {"", a, 5}, {"", b, 2}} {
		if got := floorOf(t, want.organization, want.remote); got != want.floor {
			t.Fatalf("floor of %s in %q = %d, want %d", want.remote, want.organization, got, want.floor)
		}
	}
	if err := RaiseFloor(ctx, "", a, 7); err == nil {
		t.Fatal("a floor without an organization was written")
	}
}
