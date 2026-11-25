package upload

import (
	"fmt"
	"io"
	"strings"
)

// PrintErrors prints every run error and the error of every page that conflicted, was denied or
// failed once on errOut, so a script that reads only stderr on a non-zero exit still learns why.
// A skipped page is left out: its cause is a run error or the error of its parent, both printed
// already.
func PrintErrors(errOut io.Writer, report Report) {
	for _, err := range report.RunErrors {
		detail := describe(err)
		_, _ = fmt.Fprintf(errOut, "Error: %s (%s)\n", detail.Message, detail.Code)
	}
	for _, page := range report.Pages {
		if page.Error == nil || page.Outcome == OutcomeSkipped {
			continue
		}
		_, _ = fmt.Fprintf(errOut, "Error: %s: %s (%s)\n", page.Path, page.Error.Message, page.Error.Code)
	}
}

// PrintReport prints one line per page and a count line on out. A skipped page's line names its
// cause, which PrintErrors does not repeat.
func PrintReport(out io.Writer, report Report) {
	for _, page := range report.Pages {
		switch {
		case page.Outcome == OutcomeSkipped && page.Error != nil:
			_, _ = fmt.Fprintf(out, "%-9s %s: %s (%s)\n", page.Outcome, page.Path, page.Error.Message, page.Error.Code)
		case page.Error != nil:
			_, _ = fmt.Fprintf(out, "%-9s %s\n", page.Outcome, page.Path)
		default:
			_, _ = fmt.Fprintf(out, "%-9s %s (version %d)\n", page.Outcome, page.Path, page.Version)
		}
	}
	if len(report.Pages) == 0 {
		return
	}
	var counts []string
	for _, outcome := range allOutcomes {
		if n := report.Counts[outcome]; n > 0 {
			counts = append(counts, fmt.Sprintf("%d %s", n, outcome))
		}
	}
	suffix := ""
	if report.DryRun {
		suffix = " (dry run, nothing written)"
	}
	_, _ = fmt.Fprintf(out, "%d pages: %s%s\n", len(report.Pages), strings.Join(counts, ", "), suffix)
}
