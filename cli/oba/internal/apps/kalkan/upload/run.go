package upload

import (
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// RunInput is one upload: the bundles ReadBundles read, and whether to only report (DryRun) or
// to overwrite pages edited in Kalkan since the last upload (Force).
type RunInput struct {
	Bundles []*Bundle
	DryRun  bool
	Force   bool
}

// run is the state of one upload over all its bundles.
type run struct {
	api       *client.Client
	dryRun    bool
	force     bool
	pages     []PageResult
	runErrors []error
	// stopped ends the run at a refused sign-in, since every later call would be refused the
	// same way.
	stopped error
}

// Run writes every page of every bundle, parents first, and reports each page's outcome. A page
// that is not written keeps the pages below it from being written; the run goes on with the
// others. A refused sign-in (401) ends the run and skips every page it has not reached.
func Run(c *client.Client, in RunInput) Report {
	r := &run{api: c, dryRun: in.DryRun, force: in.Force}
	for _, b := range in.Bundles {
		r.uploadBundle(b)
	}
	return newReport(r.pages, r.dryRun, r.runErrors)
}

// NotRun is the report of an upload that could make no call, such as one without a session:
// cause is its one run error and every page is skipped.
func NotRun(in RunInput, cause error) Report {
	var pages []PageResult
	for _, b := range in.Bundles {
		for _, page := range b.Pages {
			pages = append(pages, skipped(page.Path, cause))
		}
	}
	return newReport(pages, in.DryRun, []error{cause})
}

func (r *run) runError(err error) {
	r.runErrors = append(r.runErrors, err)
	if stopsRun(err) && r.stopped == nil {
		r.stopped = err
	}
}

// skipAll gives every page an outcome when the run cannot reach them.
func (r *run) skipAll(pages []Page, cause error) {
	for _, page := range pages {
		r.pages = append(r.pages, skipped(page.Path, cause))
	}
}

func (r *run) uploadBundle(b *Bundle) {
	if r.stopped != nil {
		r.skipAll(b.Pages, r.stopped)
		return
	}
	tree, err := r.api.ListKalkanPages()
	if err != nil {
		r.runError(err)
		r.skipAll(b.Pages, err)
		return
	}
	existing := make(map[string]client.KalkanPageListItem, len(tree))
	for _, page := range tree {
		existing[page.Path] = page
	}

	// blockedBy names, per page that was not written, the page whose outcome kept it from being
	// written, so its subtree is skipped with that path as the reason.
	blockedBy := make(map[string]string)
	dryCreated := make(map[string]bool)
	for i := range b.Pages {
		page := &b.Pages[i]
		parent := parentPath(page.Path)
		if r.stopped != nil {
			r.pages = append(r.pages, skipped(page.Path, r.stopped))
			blockedBy[page.Path] = page.Path
			continue
		}
		if origin, blocked := blockedBy[parent]; blocked && page.Path != b.Target {
			r.pages = append(r.pages, skippedBelow(page.Path, origin))
			blockedBy[page.Path] = origin
			continue
		}
		result := r.uploadPage(page, existing, dryCreated[parent] && page.Path != b.Target)
		r.pages = append(r.pages, result)
		switch result.Outcome {
		case OutcomeDenied, OutcomeFailed:
			blockedBy[page.Path] = page.Path
		case OutcomeCreated:
			if r.dryRun {
				dryCreated[page.Path] = true
			}
		}
		if r.stopped == nil && result.cause != nil && stopsRun(result.cause) {
			r.stopped = result.cause
		}
	}
}

func parentPath(path string) string {
	if i := strings.LastIndex(path, "/"); i >= 0 {
		return path[:i]
	}
	return ""
}
