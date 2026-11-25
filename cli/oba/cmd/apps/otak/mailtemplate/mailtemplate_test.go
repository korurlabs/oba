package mailtemplate

import (
	"slices"
	"testing"
)

// Every oba command moves files with upload and download, so no alias or hidden command answers
// to push or pull; the oba root reports either as an unknown command.
func TestMailTemplateHasOnlyListUploadAndDownload(t *testing.T) {
	var names []string
	for _, sub := range NewCmd().Commands() {
		names = append(names, sub.Name())
		if len(sub.Aliases) != 0 || sub.Hidden {
			t.Errorf("%s: aliases %v, hidden %v", sub.Name(), sub.Aliases, sub.Hidden)
		}
	}
	slices.Sort(names)
	if !slices.Equal(names, []string{"download", "list", "upload"}) {
		t.Errorf("subcommands = %v", names)
	}
	for _, old := range []string{"push", "pull"} {
		if found, _, err := newRoot().Find([]string{"otak", "mail-template", old}); err == nil && found.Name() != "mail-template" {
			t.Errorf("%s resolves to %q", old, found.CommandPath())
		}
	}
}
