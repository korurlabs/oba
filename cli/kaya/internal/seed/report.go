package seed

import (
	"fmt"
	"io"
	"sort"
	"strings"

	"github.com/korurlabs/kaya-cli/internal/client"
)

// RepositoryCheck is the release comparison of one repository, named by its repository name.
type RepositoryCheck struct {
	Repository    string
	ReleaseNumber int
	// Skipped says why the repository was not compared; empty when it was.
	Skipped    string
	Mismatches []Mismatch
}

// PrintPlan writes what a dry run would send.
func PrintPlan(w io.Writer, plan Plan) {
	_, _ = fmt.Fprintf(w, "Source ref: %s\n\nSets:\n", plan.Request.SourceRef)
	for _, set := range plan.Request.Sets {
		line := fmt.Sprintf("  %-9s %-24s %4d files", set.Kind, set.Name, len(set.Files))
		if set.Parent != "" {
			line += "  parent " + set.Parent
		}
		_, _ = fmt.Fprintln(w, line)
	}
	if len(plan.Request.Repositories) > 0 {
		_, _ = fmt.Fprintln(w, "\nRepositories:")
		for _, repo := range plan.Request.Repositories {
			packs := make([]string, 0, len(repo.Packs))
			for _, pack := range repo.Packs {
				if pack.RootPath == "" {
					packs = append(packs, pack.Name)
				} else {
					packs = append(packs, pack.Name+" at "+pack.RootPath)
				}
			}
			_, _ = fmt.Fprintf(w, "  %s  remote %s  packs [%s]\n", repo.Name, repo.RemoteURL, strings.Join(packs, ", "))
		}
	}
	_, _ = fmt.Fprintf(w, "\nBlobs: %d\n", len(plan.Blobs))
	printNotImported(w, plan.Request.NotImported)
	printDrift(w, plan.Request.Drift)
	printWarnings(w, plan.Warnings)
}

// PrintRun writes the report of a seed run and the release comparison.
func PrintRun(w io.Writer, run client.SeedRun, warnings []string, checks []RepositoryCheck) {
	report := run.Report
	applied := "applied"
	if !report.Applied {
		applied = "not applied: every write was discarded"
	}
	_, _ = fmt.Fprintf(w, "Seed run %s: %s (%s)\nSource ref: %s\n", run.ID, run.Status, applied, run.SourceRef)

	_, _ = fmt.Fprintln(w, "\nEntities:")
	kinds := make([]string, 0, len(report.Entities))
	for kind := range report.Entities {
		kinds = append(kinds, kind)
	}
	sort.Strings(kinds)
	for _, kind := range kinds {
		if report.Entities[kind] > 0 {
			_, _ = fmt.Fprintf(w, "  %-11s %d\n", kind, report.Entities[kind])
		}
	}

	_, _ = fmt.Fprintln(w, "\nSets:")
	for _, set := range report.Sets {
		version := "no version"
		if set.VersionNumber != nil {
			version = fmt.Sprintf("version %d", *set.VersionNumber)
		}
		line := fmt.Sprintf("  %-9s %-24s %-9s %s, %d files", set.Kind, set.Name, set.Outcome, version, set.FileCount)
		if set.RefusedCount > 0 {
			line += fmt.Sprintf(", %d refused", set.RefusedCount)
		}
		if set.ExecutableChange {
			line += ", executable change"
		}
		_, _ = fmt.Fprintln(w, line)
	}

	if len(report.Releases) > 0 {
		_, _ = fmt.Fprintln(w, "\nReleases:")
		for _, release := range report.Releases {
			_, _ = fmt.Fprintf(w, "  repository %s: release %d, %d files, manifest %s\n",
				release.RepositoryID, release.Number, release.FileCount, short(release.ManifestSHA256))
		}
	}

	if len(report.Refused) > 0 {
		_, _ = fmt.Fprintln(w, "\nRefused files:")
		for _, refused := range report.Refused {
			_, _ = fmt.Fprintf(w, "  %s %s: %s\n", refused.SetName, refused.Path, refusalText(refused))
		}
	}
	if len(report.Violations) > 0 {
		_, _ = fmt.Fprintln(w, "\nComposition violations:")
		for _, violation := range report.Violations {
			_, _ = fmt.Fprintf(w, "  %s\n", violationText([]client.Violation{violation}))
		}
	}
	printSizeWarnings(w, report.Warnings)
	printNotImported(w, report.NotImported)
	printDrift(w, report.Drift)
	printWarnings(w, warnings)

	if len(checks) > 0 {
		_, _ = fmt.Fprintln(w, "\nRelease compared with the repository on disk:")
		for _, check := range checks {
			switch {
			case check.Skipped != "":
				_, _ = fmt.Fprintf(w, "  %s: not compared, %s\n", check.Repository, check.Skipped)
			case len(check.Mismatches) == 0:
				_, _ = fmt.Fprintf(w, "  %s: release %d equals the disk\n", check.Repository, check.ReleaseNumber)
			default:
				_, _ = fmt.Fprintf(w, "  %s: release %d differs in %d paths\n", check.Repository, check.ReleaseNumber, len(check.Mismatches))
				for _, mismatch := range check.Mismatches {
					_, _ = fmt.Fprintf(w, "    %s: %s\n", mismatch.Path, mismatch.Reason)
				}
			}
		}
	}
}

// refusalText is why a file was refused: the error code and reason of a file no kind delivers or
// that does not parse, else the file rules it breaks.
func refusalText(refused client.SeedRefusedFile) string {
	if refused.ErrorCode == "" {
		return violationText(refused.Violations)
	}
	text := refused.ErrorCode
	if refused.Reason != "" {
		text += " (" + refused.Reason + ")"
	}
	return text
}

func violationText(violations []client.Violation) string {
	parts := make([]string, 0, len(violations))
	for _, violation := range violations {
		text := violation.Rule
		if violation.Path != "" {
			text += " at " + violation.Path
		}
		if violation.Message != "" {
			text += " (" + violation.Message + ")"
		}
		parts = append(parts, text)
	}
	return strings.Join(parts, "; ")
}

// printSizeWarnings lists the size limits Kaya reported for files it imported anyway; a later
// change of such a file through Kaya is refused until it fits.
func printSizeWarnings(w io.Writer, warnings []client.SeedWarning) {
	if len(warnings) == 0 {
		return
	}
	_, _ = fmt.Fprintf(w, "\nSize limits broken, imported anyway (%d):\n", len(warnings))
	for _, warning := range warnings {
		text := violationText([]client.Violation{warning.Violation})
		if warning.SetName != "" {
			text = warning.SetName + ": " + text
		}
		_, _ = fmt.Fprintf(w, "  %s\n", text)
	}
}

func printNotImported(w io.Writer, items []client.NotImported) {
	if len(items) == 0 {
		return
	}
	_, _ = fmt.Fprintf(w, "\nNot imported (%d):\n", len(items))
	for _, item := range items {
		_, _ = fmt.Fprintf(w, "  %s: %s\n", item.Path, item.Reason)
	}
}

func printDrift(w io.Writer, drift []client.Drift) {
	if len(drift) == 0 {
		return
	}
	_, _ = fmt.Fprintf(w, "\nDrift from .agents/.managed.json (%d):\n", len(drift))
	for _, item := range drift {
		pack := item.Pack
		if pack == "" {
			pack = "unknown pack"
		}
		_, _ = fmt.Fprintf(w, "  %s (%s): expected %s, disk %s\n", item.Path, pack, short(item.ExpectedSHA256), short(item.ActualSHA256))
	}
}

func printWarnings(w io.Writer, warnings []string) {
	if len(warnings) == 0 {
		return
	}
	_, _ = fmt.Fprintln(w, "\nWarnings:")
	for _, warning := range warnings {
		_, _ = fmt.Fprintf(w, "  %s\n", warning)
	}
}
