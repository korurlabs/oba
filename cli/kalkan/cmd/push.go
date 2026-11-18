package cmd

import (
	"context"
	"fmt"
	"io"
	"os"
	"regexp"
	"strings"

	"github.com/spf13/cobra"

	"github.com/korurlabs/kalkan-cli/internal/bundle"
	"github.com/korurlabs/kalkan-cli/internal/client"
	"github.com/korurlabs/kalkan-cli/internal/project"
)

var (
	pushLang   string
	pushDryRun bool
	pushForce  bool
)

// Outcomes of one page of a push.
const (
	outcomeCreated   = "created"
	outcomeUpdated   = "updated"
	outcomeUnchanged = "unchanged"
	outcomeConflict  = "conflict"
	outcomeDenied    = "denied"
	outcomeFailed    = "failed"
	outcomeSkipped   = "skipped"
)

// allOutcomes keeps the counts of every run in one shape, zeros included, so scripts read each
// key without checking that it exists.
var allOutcomes = []string{outcomeCreated, outcomeUpdated, outcomeUnchanged, outcomeConflict, outcomeDenied, outcomeFailed, outcomeSkipped}

// codeParentNotWritten is the error code of a page skipped below a page that was not written.
const codeParentNotWritten = "PARENT_NOT_WRITTEN"

// assetNamePattern is the name a push gives an uploaded asset: 12 hex digits of its SHA-256, a
// dash, its base name.
var assetNamePattern = regexp.MustCompile(`^[0-9a-f]{12}-`)

var pushCmd = &cobra.Command{
	Use:   "push [<dir> <target>]",
	Short: "Create or update every page of a directory under a target path",
	Long: "Writes every Markdown page of <dir> under the page path <target>, parents first, and " +
		"uploads the files they link to. Without arguments, pushes every entry of the kalkan.yaml " +
		"in the current directory or at the git root. a/b.md is page a/b in --lang, a/b.tr.md and " +
		"a/b.en.md set the language, a/index.md or a/README.md is page a. A page edited in Kalkan " +
		"since its last push is reported as conflict and not written unless --force is given; the " +
		"other pages are written. Exit code: 0 all written or unchanged, 3 a conflict, 4 a denial, " +
		"2 not signed in, 1 a failure; the first of 1, 2, 4, 3 that occurred wins. --dry-run " +
		"reports the outcome of every page without writing or uploading anything.",
	Args: func(cmd *cobra.Command, args []string) error {
		if len(args) != 0 && len(args) != 2 {
			return fmt.Errorf("name both <dir> and <target>, or neither to use %s", project.FileName)
		}
		return nil
	},
	RunE: runPush,
}

func init() {
	flags := pushCmd.Flags()
	flags.StringVar(&pushLang, "lang", "en", "language of files without a .tr or .en suffix: tr or en")
	flags.BoolVar(&pushDryRun, "dry-run", false, "report the outcome of every page without writing")
	flags.BoolVar(&pushForce, "force", false, "overwrite pages edited in Kalkan since the last push")
	rootCmd.AddCommand(pushCmd)
}

// pushPage is one page of the report. Error is set on every outcome but created, updated and
// unchanged.
type pushPage struct {
	Path    string       `json:"path"`
	Outcome string       `json:"outcome"`
	Version int64        `json:"version"`
	Error   *errorDetail `json:"error,omitempty"`
}

type pushReport struct {
	Counts map[string]int `json:"counts"`
	Pages  []pushPage     `json:"pages"`
}

// pushRun is the state of one push over all its bundles.
type pushRun struct {
	api    *client.Client
	dryRun bool
	force  bool
	pages  []pushPage
	// runErrors are failures of the run rather than of one page; each is printed on stderr.
	runErrors []error
	exit      int
	// stopped ends the run at a lost sign-in, since every later call would be refused the same way.
	stopped error
}

func runPush(cmd *cobra.Command, args []string) error {
	run := &pushRun{dryRun: pushDryRun, force: pushForce}
	bundles, err := readBundles(args)
	if err != nil {
		run.runError(err)
		return run.finish(cmd)
	}
	api, _, err := signedInClient()
	if err != nil {
		run.runError(err)
		for _, b := range bundles {
			run.skipAll(b.Pages, err)
		}
		return run.finish(cmd)
	}
	run.api = api
	for _, b := range bundles {
		run.pushBundle(cmd.Context(), b)
	}
	return run.finish(cmd)
}

// readBundles reads every directory of the push before any call, so an invalid kalkan.yaml, a
// missing directory or a file name that is not a slug ends the run with nothing written.
func readBundles(args []string) ([]*bundle.Bundle, error) {
	defaultLocale, err := parseLocale(pushLang)
	if err != nil {
		return nil, err
	}
	cwd, err := os.Getwd()
	if err != nil {
		return nil, fmt.Errorf("read the current directory: %w", err)
	}
	proj, err := project.Find(cwd)
	if err != nil {
		return nil, err
	}
	if len(args) == 2 {
		target, err := normalizePath(args[1])
		if err != nil {
			return nil, err
		}
		b, err := bundle.Read(args[0], target, defaultLocale)
		if err != nil {
			return nil, err
		}
		return []*bundle.Bundle{b}, nil
	}
	if proj == nil {
		return nil, fmt.Errorf("no %s in this directory or at the git root; run kalkan push <dir> <target>", project.FileName)
	}
	if len(proj.Push) == 0 {
		return nil, fmt.Errorf("%s lists no push entries", proj.Path)
	}
	bundles := make([]*bundle.Bundle, 0, len(proj.Push))
	for _, mapping := range proj.Push {
		target, err := normalizePath(mapping.To)
		if err != nil {
			return nil, fmt.Errorf("%s: %w", proj.Path, err)
		}
		locale := defaultLocale
		if mapping.Lang != "" {
			if locale, err = parseLocale(mapping.Lang); err != nil {
				return nil, fmt.Errorf("%s: %w", proj.Path, err)
			}
		}
		b, err := bundle.Read(proj.DirOf(mapping), target, locale)
		if err != nil {
			return nil, err
		}
		bundles = append(bundles, b)
	}
	return bundles, nil
}

func (r *pushRun) runError(err error) {
	r.runErrors = append(r.runErrors, err)
	r.exit = worseExit(r.exit, exitCodeOf(err))
	if exitCodeOf(err) == ExitNotSignedIn && r.stopped == nil {
		r.stopped = err
	}
}

// skipAll gives every page an outcome when the run cannot reach them.
func (r *pushRun) skipAll(pages []bundle.Page, cause error) {
	code, message := describe(cause)
	for _, page := range pages {
		r.pages = append(r.pages, pushPage{
			Path:    page.Path,
			Outcome: outcomeSkipped,
			Error:   &errorDetail{Code: code, Message: "not pushed: " + message},
		})
	}
}

func (r *pushRun) pushBundle(ctx context.Context, b *bundle.Bundle) {
	if r.stopped != nil {
		r.skipAll(b.Pages, r.stopped)
		return
	}
	tree, err := r.api.ListPages(ctx)
	if err != nil {
		r.runError(err)
		r.skipAll(b.Pages, err)
		return
	}
	existing := make(map[string]client.PageListItem, len(tree))
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
			r.skipAll([]bundle.Page{*page}, r.stopped)
			blockedBy[page.Path] = page.Path
			continue
		}
		if origin, blocked := blockedBy[parent]; blocked && page.Path != b.Target {
			r.pages = append(r.pages, pushPage{
				Path:    page.Path,
				Outcome: outcomeSkipped,
				Error: &errorDetail{
					Code:    codeParentNotWritten,
					Message: fmt.Sprintf("not pushed: %s above it was not written", origin),
				},
			})
			blockedBy[page.Path] = origin
			continue
		}
		result := r.pushPage(ctx, page, existing, dryCreated[parent] && page.Path != b.Target)
		r.pages = append(r.pages, result)
		switch result.Outcome {
		case outcomeDenied, outcomeFailed:
			blockedBy[page.Path] = page.Path
		case outcomeCreated:
			if r.dryRun {
				dryCreated[page.Path] = true
			}
		}
	}
}

func parentPath(path string) string {
	if i := strings.LastIndex(path, "/"); i >= 0 {
		return path[:i]
	}
	return ""
}

// pushPage writes one page and returns its outcome. parentDryCreated marks a dry run below a
// page the run would create: the server cannot check a page whose parent does not exist yet, and
// a page below a new one is new itself.
func (r *pushRun) pushPage(ctx context.Context, page *bundle.Page, existing map[string]client.PageListItem, parentDryCreated bool) pushPage {
	if page.AssetErr != nil {
		return r.pageError(page.Path, page.AssetErr)
	}
	if parentDryCreated {
		return pushPage{Path: page.Path, Outcome: outcomeCreated}
	}
	current, exists := existing[page.Path]
	if page.DirOnly && exists {
		// A directory without an index file carries no content of its own, so it never blanks
		// or conflicts with a page that people write in Kalkan.
		detail, err := r.api.GetPage(ctx, current.ID)
		if err != nil {
			return r.pageError(page.Path, err)
		}
		return pushPage{Path: page.Path, Outcome: outcomeUnchanged, Version: detail.Version}
	}

	urls := map[string]string{}
	created := false
	pageID := current.ID
	if len(page.Assets) > 0 && !exists && !r.dryRun {
		// Attachments need the page's id, so a new page is created first and then written again
		// with the uploaded links.
		result, err := r.api.UpsertPage(ctx, r.upsertInput(page, urls), false)
		if err != nil {
			return r.pageError(page.Path, err)
		}
		created = true
		pageID = result.ID
	}
	if len(page.Assets) > 0 && pageID != "" {
		var err error
		if urls, err = r.resolveAssets(ctx, pageID, page); err != nil {
			return r.pageError(page.Path, err)
		}
	}
	result, err := r.api.UpsertPage(ctx, r.upsertInput(page, urls), r.dryRun)
	if err != nil {
		return r.pageError(page.Path, err)
	}
	outcome := strings.ToLower(result.Outcome)
	if created {
		outcome = outcomeCreated
	}
	return pushPage{Path: page.Path, Outcome: outcome, Version: result.Version}
}

func (r *pushRun) upsertInput(page *bundle.Page, urls map[string]string) client.UpsertInput {
	translations := make([]client.TranslationInput, 0, len(page.Translations))
	for _, t := range page.Translations {
		translations = append(translations, client.TranslationInput{
			Locale: t.Locale,
			Title:  t.Title,
			Body:   t.Body(page.Assets, urls),
		})
	}
	return client.UpsertInput{
		Path:         page.Path,
		Position:     page.Position,
		Compliance:   page.Compliance,
		Translations: translations,
		Force:        r.force,
	}
}

// resolveAssets returns the URL of every asset of the page, uploading the ones the page does not
// hold yet. Every page holds its own copy of a file, so deleting one page never breaks the links
// of another; a file the page links twice, or under two names with the same content, is uploaded
// once, since the server refuses content a page already holds. A dry run uploads nothing and
// leaves a missing asset's link as written.
func (r *pushRun) resolveAssets(ctx context.Context, pageID string, page *bundle.Page) (map[string]string, error) {
	attachments, err := r.api.ListAttachments(ctx, client.StorageEntityTypeKalkanPage, pageID)
	if err != nil {
		return nil, err
	}
	urls := make(map[string]string, len(page.Assets))
	byHash := make(map[string]string, len(attachments)+len(page.Assets))
	for _, attachment := range attachments {
		link := unsigned(attachment.URL)
		urls[attachment.FileName] = link
		rememberAsset(byHash, attachment.FileName, link)
	}
	for _, asset := range page.Assets {
		if _, ok := urls[asset.Name]; ok {
			continue
		}
		if link, ok := byHash[asset.Name[:12]]; ok {
			urls[asset.Name] = link
			continue
		}
		if r.dryRun {
			continue
		}
		content, err := os.ReadFile(asset.Source) // #nosec G304 -- an asset inside the directory the user pushes
		if err != nil {
			return nil, fmt.Errorf("read the asset %s: %w", asset.Source, err)
		}
		attachment, err := r.api.UploadAttachment(ctx, client.StorageEntityTypeKalkanPage, pageID, asset.Name, content)
		if err != nil {
			if client.IsCode(err, client.CodeDuplicateContent) {
				return nil, fmt.Errorf("upload %s: this Kalkan page already holds a file with the same content; link to the copy it holds instead: %w", asset.Source, err)
			}
			return nil, fmt.Errorf("upload %s: %w", asset.Source, err)
		}
		link := unsigned(attachment.URL)
		urls[asset.Name] = link
		rememberAsset(byHash, asset.Name, link)
	}
	return urls, nil
}

// rememberAsset records the URL of an attachment a push named, under its hash prefix.
func rememberAsset(byHash map[string]string, fileName, link string) {
	if assetNamePattern.MatchString(fileName) {
		byHash[fileName[:12]] = link
	}
}

// unsigned drops the query string of an attachment URL. The API signs the URL anew on every
// answer, so a body holding the signed URL would change on every push and never be unchanged;
// the signature is added again when the page is read.
func unsigned(link string) string {
	if i := strings.Index(link, "?"); i >= 0 {
		return link[:i]
	}
	return link
}

// pageError turns the error of one page into its outcome and adds its exit code to the run.
func (r *pushRun) pageError(path string, err error) pushPage {
	exit := exitCodeOf(err)
	r.exit = worseExit(r.exit, exit)
	if exit == ExitNotSignedIn && r.stopped == nil {
		r.stopped = err
	}
	outcome := outcomeFailed
	switch exit {
	case ExitConflict:
		outcome = outcomeConflict
	case ExitDenied:
		outcome = outcomeDenied
	}
	code, message := describe(err)
	return pushPage{Path: path, Outcome: outcome, Error: &errorDetail{Code: code, Message: message}}
}

// finish prints the report and every error once, then ends the process with the run's exit code.
func (r *pushRun) finish(cmd *cobra.Command) error {
	stderr := cmd.ErrOrStderr()
	for _, err := range r.runErrors {
		code, message := describe(err)
		writeError(stderr, code, message)
	}
	report := pushReport{Counts: make(map[string]int, len(allOutcomes)), Pages: r.pages}
	if report.Pages == nil {
		report.Pages = []pushPage{}
	}
	for _, outcome := range allOutcomes {
		report.Counts[outcome] = 0
	}
	for _, page := range r.pages {
		report.Counts[page.Outcome]++
	}

	if jsonOutput {
		r.printPageErrors(stderr, report)
		if err := printJSON(cmd, report); err != nil {
			writeError(stderr, codeCLIError, err.Error())
			r.exit = worseExit(r.exit, ExitFailure)
		}
	} else {
		r.printText(cmd, report)
	}
	if r.exit != ExitOK {
		return &ExitError{Code: r.exit}
	}
	return nil
}

// printPageErrors prints the error of every page that conflicted, was denied or failed once on
// stderr, so a script that reads only stderr on a non-zero exit still learns why. A skipped page
// is left out: its cause is a run-level error or the error of its parent, both printed already.
func (r *pushRun) printPageErrors(stderr io.Writer, report pushReport) {
	for _, page := range report.Pages {
		if page.Error == nil || page.Outcome == outcomeSkipped {
			continue
		}
		writeError(stderr, page.Error.Code, page.Path+": "+page.Error.Message)
	}
}

func (r *pushRun) printText(cmd *cobra.Command, report pushReport) {
	stdout, stderr := cmd.OutOrStdout(), cmd.ErrOrStderr()
	for _, page := range report.Pages {
		if page.Outcome == outcomeSkipped && page.Error != nil {
			// A skipped page's cause is a run-level error or its parent's error, both printed on
			// stderr already, so the report line names the cause instead of printing it again.
			_, _ = fmt.Fprintf(stdout, "%-9s %s: %s (%s)\n", page.Outcome, page.Path, page.Error.Message, page.Error.Code)
			continue
		}
		if page.Error != nil {
			_, _ = fmt.Fprintf(stdout, "%-9s %s\n", page.Outcome, page.Path)
			_, _ = fmt.Fprintf(stderr, "Error: %s: %s (%s)\n", page.Path, page.Error.Message, page.Error.Code)
			continue
		}
		_, _ = fmt.Fprintf(stdout, "%-9s %s (version %d)\n", page.Outcome, page.Path, page.Version)
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
	if r.dryRun {
		suffix = " (dry run, nothing written)"
	}
	_, _ = fmt.Fprintf(stdout, "%d pages: %s%s\n", len(report.Pages), strings.Join(counts, ", "), suffix)
}
