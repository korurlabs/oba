package page

import (
	"fmt"
	"io"
	"sort"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// PrintTree writes the pages depth first, each child under its parent ordered by position and
// slug, indented by its depth below the first printed level. A page whose parent is not among
// pages, such as the top of a subtree, is printed at the first level.
func PrintTree(out io.Writer, pages []client.KalkanPageListItem) {
	if len(pages) == 0 {
		_, _ = fmt.Fprintln(out, "No pages.")
		return
	}
	present := make(map[string]bool, len(pages))
	for _, page := range pages {
		present[page.ID] = true
	}
	children := make(map[string][]client.KalkanPageListItem)
	var tops []client.KalkanPageListItem
	for _, page := range pages {
		if page.ParentID == "" || !present[page.ParentID] {
			tops = append(tops, page)
			continue
		}
		children[page.ParentID] = append(children[page.ParentID], page)
	}
	var walk func(list []client.KalkanPageListItem, depth int)
	walk = func(list []client.KalkanPageListItem, depth int) {
		sortSiblings(list)
		for _, page := range list {
			_, _ = fmt.Fprintf(out, "%s%s%s\n", strings.Repeat("  ", depth), page.Path, pageMarks(page))
			walk(children[page.ID], depth+1)
		}
	}
	walk(tops, 0)
}

func sortSiblings(list []client.KalkanPageListItem) {
	sort.SliceStable(list, func(i, j int) bool {
		if list[i].Position != list[j].Position {
			return list[i].Position < list[j].Position
		}
		return list[i].Slug < list[j].Slug
	})
}

// pageMarks is the title, the compliance mark and, on a root, the caller's level.
func pageMarks(page client.KalkanPageListItem) string {
	var b strings.Builder
	if title := pageTitle(page.Titles); title != "" {
		b.WriteString("  " + title)
	}
	if page.Compliance {
		b.WriteString("  [compliance]")
	}
	if level := guardrail.OneLine(page.Level); level != "" {
		b.WriteString("  (" + level + ")")
	}
	return b.String()
}

// pageTitle prefers the English title and falls back to the Turkish one.
func pageTitle(titles map[string]string) string {
	if title := guardrail.OneLine(titles[client.KalkanLocaleEN]); title != "" {
		return title
	}
	return guardrail.OneLine(titles[client.KalkanLocaleTR])
}
