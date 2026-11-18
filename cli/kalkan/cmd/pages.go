package cmd

import (
	"context"
	"errors"
	"fmt"
	"io"
	"net/http"
	"sort"
	"strings"

	"github.com/spf13/cobra"

	"github.com/korurlabs/kalkan-cli/internal/bundle"
	"github.com/korurlabs/kalkan-cli/internal/client"
)

// pathNotFound is a path the tree does not hold: missing, or below a root the caller cannot see.
// It is reported as the API reports a hidden page, so it exits with the code of a denial.
func pathNotFound(path string) error {
	return &client.APIError{
		Status:  http.StatusNotFound,
		Code:    client.CodePageNotFound,
		Message: fmt.Sprintf("No page at %s, or you have no access to it.", path),
	}
}

// normalizePath trims surrounding slashes and checks every segment against the server's slug
// rule, before any call, so that a typo fails at once with exit code 1 instead of as a 422 or a
// 404 that reads like a denial.
func normalizePath(raw string) (string, error) {
	path := strings.Trim(strings.TrimSpace(raw), "/")
	if path == "" {
		return "", errors.New("the path is empty; name a page as root/slug/slug")
	}
	segments := strings.Split(path, "/")
	if len(segments) > bundle.MaxDepth {
		return "", fmt.Errorf("the path %q is deeper than %d pages", path, bundle.MaxDepth)
	}
	for _, segment := range segments {
		if !bundle.ValidSlug(segment) {
			return "", fmt.Errorf("%q in %q is not a valid slug: start with a letter or digit, then up to 99 letters, digits, '.', '_' or '-'", segment, path)
		}
	}
	return path, nil
}

// findPage looks a path up in the tree; a path the tree does not hold is a not-found APIError.
func findPage(ctx context.Context, api *client.Client, path string) (client.PageListItem, error) {
	pages, err := api.ListPages(ctx)
	if err != nil {
		return client.PageListItem{}, err
	}
	for _, page := range pages {
		if page.Path == path {
			return page, nil
		}
	}
	return client.PageListItem{}, pathNotFound(path)
}

var pagesCmd = &cobra.Command{
	Use:   "pages [<path>]",
	Short: "List the pages you can see, or the subtree of one page",
	Long: "Lists every page under the root pages you can see, as a tree. With a path, lists that " +
		"page and the pages below it. With --json, prints the pages as a flat array, parents first.",
	Args: cobra.MaximumNArgs(1),
	RunE: runPages,
}

func init() {
	rootCmd.AddCommand(pagesCmd)
}

func runPages(cmd *cobra.Command, args []string) error {
	prefix := ""
	if len(args) == 1 {
		path, err := normalizePath(args[0])
		if err != nil {
			return fail(cmd, err)
		}
		prefix = path
	}
	api, _, err := signedInClient()
	if err != nil {
		return fail(cmd, err)
	}
	pages, err := api.ListPages(cmd.Context())
	if err != nil {
		return fail(cmd, err)
	}
	if prefix != "" {
		pages = subtree(pages, prefix)
		if len(pages) == 0 {
			return fail(cmd, pathNotFound(prefix))
		}
	}
	if jsonOutput {
		if pages == nil {
			pages = []client.PageListItem{}
		}
		if err := printJSON(cmd, pages); err != nil {
			return fail(cmd, err)
		}
		return nil
	}
	printTree(cmd.OutOrStdout(), pages)
	return nil
}

// subtree keeps the page at prefix and every page below it.
func subtree(pages []client.PageListItem, prefix string) []client.PageListItem {
	var kept []client.PageListItem
	for _, page := range pages {
		if page.Path == prefix || strings.HasPrefix(page.Path, prefix+"/") {
			kept = append(kept, page)
		}
	}
	return kept
}

// printTree prints the pages depth first, each child under its parent ordered by position and
// slug, indented by its depth below the first printed level.
func printTree(w io.Writer, pages []client.PageListItem) {
	if len(pages) == 0 {
		_, _ = fmt.Fprintln(w, "No pages.")
		return
	}
	present := make(map[string]bool, len(pages))
	for _, page := range pages {
		present[page.ID] = true
	}
	children := make(map[string][]client.PageListItem)
	var tops []client.PageListItem
	for _, page := range pages {
		if page.ParentID == "" || !present[page.ParentID] {
			tops = append(tops, page)
			continue
		}
		children[page.ParentID] = append(children[page.ParentID], page)
	}
	var walk func(list []client.PageListItem, depth int)
	walk = func(list []client.PageListItem, depth int) {
		sortSiblings(list)
		for _, page := range list {
			_, _ = fmt.Fprintf(w, "%s%s%s\n", strings.Repeat("  ", depth), page.Path, pageMarks(page))
			walk(children[page.ID], depth+1)
		}
	}
	walk(tops, 0)
}

func sortSiblings(list []client.PageListItem) {
	sort.SliceStable(list, func(i, j int) bool {
		if list[i].Position != list[j].Position {
			return list[i].Position < list[j].Position
		}
		return list[i].Slug < list[j].Slug
	})
}

// pageMarks is the title, the compliance mark and, on a root, the caller's level.
func pageMarks(page client.PageListItem) string {
	var b strings.Builder
	if title := pageTitle(page.Titles); title != "" {
		b.WriteString("  " + title)
	}
	if page.Compliance {
		b.WriteString("  [compliance]")
	}
	if page.Level != "" {
		b.WriteString("  (" + page.Level + ")")
	}
	return b.String()
}

// pageTitle prefers the English title and falls back to the Turkish one.
func pageTitle(titles map[string]string) string {
	if title := titles[client.LocaleEN]; title != "" {
		return title
	}
	return titles[client.LocaleTR]
}
