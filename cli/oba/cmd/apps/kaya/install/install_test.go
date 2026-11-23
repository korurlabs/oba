package install

import (
	"encoding/json"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"testing"

	"github.com/spf13/cobra"

	installer "github.com/korurlabs/oba-cli/internal/apps/kaya/install"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/kayatest"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// installEnv gives the command an empty home and temp folder and runs it as the user uid. It
// returns the home and the temp folder.
func installEnv(t *testing.T, uid int) (string, string) {
	t.Helper()
	home, temp := t.TempDir(), t.TempDir()
	t.Setenv("HOME", home)
	t.Setenv("XDG_CONFIG_HOME", "")
	t.Setenv("CLAUDE_CONFIG_DIR", "")
	t.Setenv("TMPDIR", temp)
	previous := effectiveUID
	effectiveUID = func() int { return uid }
	t.Cleanup(func() { effectiveUID = previous })
	return home, temp
}

func run(t *testing.T, args ...string) (string, error) {
	t.Helper()
	return kayatest.Run(t, []*cobra.Command{NewCmd()}, "", append([]string{"install"}, args...)...)
}

func entries(t *testing.T, dir string) []string {
	t.Helper()
	list, err := os.ReadDir(dir)
	if err != nil {
		t.Fatal(err)
	}
	names := make([]string, 0, len(list))
	for _, entry := range list {
		names = append(names, entry.Name())
	}
	return names
}

func TestInstall_refusesToRunAsRootInEveryForm(t *testing.T) {
	for _, args := range [][]string{
		nil,
		{"--dry-run"},
		{"--managed"},
		{"--managed", "--dry-run"},
		{"--remove-managed"},
	} {
		t.Run(strings.Join(append([]string{"install"}, args...), " "), func(t *testing.T) {
			home, temp := installEnv(t, 0)

			out, err := run(t, args...)

			if err == nil || !strings.Contains(err.Error(), "run oba kaya install as your own user, without sudo") {
				t.Fatalf("install as root: %s, %v", out, err)
			}
			if code := client.ExitCode(err); code != 1 {
				t.Fatalf("exit status = %d, want 1", code)
			}
			if got := append(entries(t, home), entries(t, temp)...); len(got) != 0 {
				t.Fatalf("install as root wrote %v", got)
			}
		})
	}
}

func TestInstall_managedStagesTheDropInAndPrintsTheRootStep(t *testing.T) {
	_, temp := installEnv(t, 501)
	dir, err := installer.ManagedDir(runtime.GOOS)
	if err != nil {
		t.Skip(err)
	}

	out, err := run(t, "--managed")

	if err != nil {
		t.Fatalf("install --managed: %s, %v", out, err)
	}
	staged := entries(t, temp)
	if len(staged) != 1 {
		t.Fatalf("temp folder holds %v, want the staged drop-in", staged)
	}
	file := filepath.Join(temp, staged[0])
	group := map[string]string{"darwin": "wheel", "linux": "root"}[runtime.GOOS]
	step := installer.ManagedPlan{Dir: dir, Path: installer.DropInPath(dir), Group: group}.InstallStep(file)
	if !strings.Contains(out, "\n"+step+"\n") || !strings.HasPrefix(step, "sudo /bin/mkdir -p ") {
		t.Fatalf("output = %s, want the line %s", out, step)
	}
	data, err := os.ReadFile(file)
	if err != nil {
		t.Fatal(err)
	}
	var dropIn map[string]json.RawMessage
	if err := json.Unmarshal(data, &dropIn); err != nil || len(dropIn) != 2 || dropIn["hooks"] == nil || dropIn["statusLine"] == nil {
		t.Fatalf("staged drop-in = %s, %v", data, err)
	}
}

func TestInstall_managedDryRunPrintsTheDropInAndWritesNothing(t *testing.T) {
	home, temp := installEnv(t, 501)
	if _, err := installer.ManagedDir(runtime.GOOS); err != nil {
		t.Skip(err)
	}

	out, err := run(t, "--managed", "--dry-run")

	if err != nil || !strings.Contains(out, "50-kaya.json:\n{\n") || !strings.Contains(out, "kaya hook record") || !strings.Contains(out, "kaya statusline") {
		t.Fatalf("install --managed --dry-run: %s, %v", out, err)
	}
	if got := append(entries(t, home), entries(t, temp)...); len(got) != 0 {
		t.Fatalf("a dry run wrote %v", got)
	}
}

func TestInstall_removeManagedPrintsOnlyTheRmLine(t *testing.T) {
	home, temp := installEnv(t, 501)
	dir, err := installer.ManagedDir(runtime.GOOS)
	if err != nil {
		t.Skip(err)
	}

	out, err := run(t, "--remove-managed")

	if err != nil || !strings.Contains(out, "\n"+installer.RemoveManagedStep(dir)+"\n") || !strings.Contains(out, "sudo /bin/rm -f ") {
		t.Fatalf("install --remove-managed: %s, %v", out, err)
	}
	if got := append(entries(t, home), entries(t, temp)...); len(got) != 0 {
		t.Fatalf("--remove-managed wrote %v", got)
	}
}

func TestInstall_refusesManagedTogetherWithRemoveManaged(t *testing.T) {
	installEnv(t, 501)
	if out, err := run(t, "--managed", "--remove-managed"); err == nil {
		t.Fatalf("accepted both flags: %s", out)
	}
}
