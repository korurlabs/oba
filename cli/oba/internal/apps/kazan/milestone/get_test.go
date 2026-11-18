package milestone

import (
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

func TestFormatDependency(t *testing.T) {
	d := client.MilestoneDependency{MilestoneID: "id-1", Name: "Beta", TargetDate: "2026-11-20"}
	if got, want := FormatDependency(d), "Beta (target 2026-11-20, id-1)"; got != want {
		t.Errorf("FormatDependency = %q, want %q", got, want)
	}
	d.Conflict = true
	if got, want := FormatDependency(d), "Beta (target 2026-11-20, id-1) [CONFLICT]"; got != want {
		t.Errorf("FormatDependency (conflict) = %q, want %q", got, want)
	}
}
