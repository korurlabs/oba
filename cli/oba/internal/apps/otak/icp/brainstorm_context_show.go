package icp

import (
	"fmt"
	"io"
	"strings"
	"unicode/utf8"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// PrintBrainstormContext writes the first step of a brainstorm: what Oba knows about the company,
// as the user can check it. Each profile field shows its value, "-   (empty)" when it has none,
// and each existing ICP sits on one line with the fields the brief sends. A website is marked
// as the site Claude Code reads, and a profile without a description gets a note, as the
// description tells Claude Code most about what the company does and the brainstorm never
// writes to the profile. Every value passes through guardrail.Sanitize, since the profile and
// the ICPs may hold text copied from the web. When Oba knows nothing, the step says so in one
// line.
func PrintBrainstormContext(out io.Writer, x BrainstormContext) {
	org := guardrail.OneLine(x.Profile.Name)
	if !x.Describes() {
		_, _ = fmt.Fprintf(out, "Step 1 of 3 · Oba has no profile for %s yet.\n", org)
		return
	}
	fields := x.profileFields()
	width := 0
	for _, field := range fields {
		width = max(width, len(field.name))
	}
	_, _ = fmt.Fprintf(out, "Step 1 of 3 · What Oba knows about %s\n", org)
	for _, field := range fields {
		value := guardrail.Sanitize(text(field.value))
		lines := strings.Split(value, "\n")
		switch {
		case value == "":
			lines[0] = "-   (empty)"
		case field.name == "website":
			lines[0] += "   (Claude will read this site)"
		}
		_, _ = fmt.Fprintf(out, "  %-*s  %s\n", width+1, field.name+":", lines[0])
		for _, line := range lines[1:] {
			_, _ = fmt.Fprintf(out, "  %-*s  %s\n", width+1, "", line)
		}
	}
	printIcps(out, x.Icps, width)
	if text(x.Profile.Description) == "" {
		_, _ = fmt.Fprintln(out)
		_, _ = fmt.Fprintln(out, "  ! No description yet. You can add one in the next step.")
		_, _ = fmt.Fprintf(out, "    It is used for this brainstorm only; to keep it, add it to the %s profile in Oba.\n", org)
	}
}

// printIcps writes the existing ICPs as numbered rows (see IcpRows); no ICPs show as "none yet"
// after the label.
func printIcps(out io.Writer, icps []client.Icp, width int) {
	if len(icps) == 0 {
		_, _ = fmt.Fprintf(out, "  %-*s  none yet\n", width+1, "ICPs:")
		return
	}
	_, _ = fmt.Fprintf(out, "  ICPs (%d):\n", len(icps))
	for n, row := range IcpRows(icps) {
		_, _ = fmt.Fprintf(out, "    %d. %s\n", n+1, row)
	}
}

// IcpRows renders each ICP as one row of name, industry, country, size range and role, each
// column padded to its widest value and an open field as "any" like the ICP list does. Rows
// carry no number, so that each command lists them under its own numbering and indent. Every
// value is sanitized onto one line, since an ICP may hold text copied from the web.
func IcpRows(icps []client.Icp) []string {
	cells := make([][]string, 0, len(icps))
	widths := make([]int, 5)
	for _, i := range icps {
		row := []string{
			orDash(guardrail.OneLine(i.Name)), orDash(guardrail.OneLine(i.Industry)), valueOr(cleaned(i.Country), "any"),
			SizeRange(cleaned(i.CompanySizeMin), cleaned(i.CompanySizeMax)),
			valueOr(cleaned(i.TargetPersonRole), "any"),
		}
		for c, cell := range row {
			widths[c] = max(widths[c], utf8.RuneCountInString(cell))
		}
		cells = append(cells, row)
	}
	rows := make([]string, 0, len(cells))
	for _, row := range cells {
		var b strings.Builder
		for c, cell := range row {
			b.WriteString(cell)
			if c < len(row)-1 {
				b.WriteString(strings.Repeat(" ", widths[c]-utf8.RuneCountInString(cell)+2))
			}
		}
		rows = append(rows, b.String())
	}
	return rows
}

func orDash(value string) string {
	if value == "" {
		return "-"
	}
	return value
}
