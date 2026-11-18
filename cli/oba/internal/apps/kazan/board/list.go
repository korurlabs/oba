package board

import (
	"fmt"
	"io"
	"strings"
	"text/tabwriter"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// descriptionWidth is how many characters of a description the board table shows.
const descriptionWidth = 50

// List reads the board tree of the organization and keeps the branches that match search; an
// empty search keeps every board. With a search the read is kept out of the --json output, since
// the API's whole tree is not the result; the caller records the filtered tree.
func List(c *client.Client, search string) ([]client.Board, error) {
	reader := c
	if strings.TrimSpace(search) != "" {
		reader = c.Unrecorded()
	}
	tree, err := reader.ListBoards(true)
	if err != nil {
		return nil, err
	}
	return Filter(tree, search), nil
}

// PrintList writes the board tree as a numbered table: #, NAME (indented under its parent), KEY,
// DESCRIPTION (top-level boards only, cut at 50 characters). Every cell is put on one line with
// terminal controls removed, since names and descriptions are typed by users.
func PrintList(out io.Writer, tree []client.Board) error {
	if len(tree) == 0 {
		_, _ = fmt.Fprintln(out, "No boards found.")
		return nil
	}
	w := tabwriter.NewWriter(out, 0, 0, 2, ' ', 0)
	_, _ = fmt.Fprintln(w, "#\tNAME\tKEY\tDESCRIPTION")
	for i, b := range Flatten(tree) {
		description := ""
		if b.Depth == 0 {
			description = shorten(guardrail.OneLine(b.Description), descriptionWidth)
		}
		_, _ = fmt.Fprintf(w, "%d\t%s\t%s\t%s\n", i+1, TreeLabel(cell(b.Name), b.Depth), cell(b.Prefix), description)
	}
	return w.Flush()
}

// shorten cuts s to width characters and marks the cut with "...".
func shorten(s string, width int) string {
	runes := []rune(s)
	if len(runes) <= width {
		return s
	}
	return string(runes[:width]) + "..."
}

// cell is value on one line with every terminal control removed, or "-" when nothing is left.
func cell(value string) string {
	clean := guardrail.OneLine(value)
	if clean == "" {
		return emptyCell
	}
	return clean
}
