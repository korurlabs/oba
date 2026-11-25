package upload

import (
	"errors"
	"fmt"
	"net/http"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// Outcomes of one page of an upload.
const (
	OutcomeCreated   = "created"
	OutcomeUpdated   = "updated"
	OutcomeUnchanged = "unchanged"
	OutcomeConflict  = "conflict"
	OutcomeDenied    = "denied"
	OutcomeFailed    = "failed"
	OutcomeSkipped   = "skipped"
)

// allOutcomes keeps the counts of every run in one shape, zeros included, so scripts read each
// key without checking that it exists.
var allOutcomes = []string{OutcomeCreated, OutcomeUpdated, OutcomeUnchanged, OutcomeConflict, OutcomeDenied, OutcomeFailed, OutcomeSkipped}

const (
	// CodeParentNotWritten is the error code of a page skipped below a page that was not written.
	CodeParentNotWritten = "PARENT_NOT_WRITTEN"
	// codeCLIError is the code of a failure that is not an answer of the API.
	codeCLIError = "CLI_ERROR"
)

// ErrorDetail is why a page was not written: the API's error code, or HTTP_<status> when the
// answer carried none, or CLI_ERROR for a local failure, and the message.
type ErrorDetail struct {
	Code    string `json:"code"`
	Message string `json:"message"`
}

// PageResult is one page of the report. Error is set on every outcome but created, updated and
// unchanged.
type PageResult struct {
	Path    string       `json:"path"`
	Outcome string       `json:"outcome"`
	Version int64        `json:"version"`
	Error   *ErrorDetail `json:"error,omitempty"`
	// exit is the exit status this page's outcome asks for, 0 for a page written or skipped.
	exit int
	// cause is the error that kept the page from being written, nil when it was written or
	// skipped.
	cause error
}

// Report is the result of one upload over all its directories. RunErrors are failures of the run
// rather than of one page, such as a tree that could not be read; each is printed once.
type Report struct {
	Counts    map[string]int `json:"counts"`
	Pages     []PageResult   `json:"pages"`
	DryRun    bool           `json:"-"`
	RunErrors []error        `json:"-"`
}

// EmptyReport is the report of an upload that ended before it read a page, every count zero.
func EmptyReport() Report {
	return newReport(nil, false, nil)
}

// newReport counts the outcomes of pages, every outcome present.
func newReport(pages []PageResult, dryRun bool, runErrors []error) Report {
	report := Report{Counts: make(map[string]int, len(allOutcomes)), Pages: pages, DryRun: dryRun, RunErrors: runErrors}
	if report.Pages == nil {
		report.Pages = []PageResult{}
	}
	for _, outcome := range allOutcomes {
		report.Counts[outcome] = 0
	}
	for _, page := range pages {
		report.Counts[page.Outcome]++
	}
	return report
}

// classify maps an error to the outcome of its page and the exit status it asks for. It reads the
// error's type and API code, never its message: a page edited in Kalkan since the last upload, or
// a write that raced another, is a conflict (4); a 403 (3) and a 404, a page hidden from the
// caller (2), are denials; anything else is a failure (1).
func classify(err error) (string, int) {
	var apiErr *client.APIError
	if !errors.As(err, &apiErr) {
		return OutcomeFailed, exitFailure
	}
	switch {
	case apiErr.Code == client.CodeKalkanPageChanged, apiErr.Code == client.CodeConcurrentWrite:
		return OutcomeConflict, exitConflict
	case apiErr.Status == http.StatusForbidden:
		return OutcomeDenied, exitForbidden
	case apiErr.Status == http.StatusNotFound:
		return OutcomeDenied, exitNotFound
	default:
		return OutcomeFailed, exitFailure
	}
}

// stopsRun reports a refused sign-in: every later call would be refused the same way, so the run
// ends and the pages it has not reached are skipped.
func stopsRun(err error) bool {
	var apiErr *client.APIError
	return errors.As(err, &apiErr) && apiErr.Status == http.StatusUnauthorized
}

// pageError turns the error of one page into its result.
func pageError(path string, err error) PageResult {
	outcome, exit := classify(err)
	return PageResult{Path: path, Outcome: outcome, Error: describe(err), exit: exit, cause: err}
}

// skipped is the result of a page the run did not reach because of cause.
func skipped(path string, cause error) PageResult {
	detail := describe(cause)
	detail.Message = "not uploaded: " + detail.Message
	return PageResult{Path: path, Outcome: OutcomeSkipped, Error: detail}
}

// skippedBelow is the result of a page below origin, a page that was not written.
func skippedBelow(path, origin string) PageResult {
	return PageResult{
		Path:    path,
		Outcome: OutcomeSkipped,
		Error: &ErrorDetail{
			Code:    CodeParentNotWritten,
			Message: fmt.Sprintf("not uploaded: %s above it was not written", origin),
		},
	}
}

// describe returns the code and message an error is reported with. An API error keeps the text
// its callers wrapped it in, such as the file an upload was for and a hint how to fix it, since
// the API's own message cannot say which local file or step it refused.
func describe(err error) *ErrorDetail {
	var apiErr *client.APIError
	if !errors.As(err, &apiErr) {
		return &ErrorDetail{Code: codeCLIError, Message: err.Error()}
	}
	code := apiErr.Code
	if code == "" {
		code = fmt.Sprintf("HTTP_%d", apiErr.Status)
	}
	message := apiErr.Message
	switch {
	case apiErr.Status == http.StatusUnauthorized && !strings.Contains(message, "oba login"):
		message += "; run 'oba login'"
	case apiErr.Code == client.CodeKalkanPageChanged:
		message += conflictDetail(apiErr.Fields)
	}
	return &ErrorDetail{Code: code, Message: wrapperContext(err, apiErr) + message}
}

// wrapperContext returns the text that wraps apiErr inside err, for example "upload a.png: ".
// A wrapper that does not end with the API error, as errors.Join or a %w in mid-text would
// produce, is returned whole with a separator, so no context is lost.
func wrapperContext(err error, apiErr *client.APIError) string {
	full, inner := err.Error(), apiErr.Error()
	if full == inner {
		return ""
	}
	if prefix, ok := strings.CutSuffix(full, inner); ok {
		return prefix
	}
	return full + ": "
}

// conflictDetail names who last edited a page in Kalkan and when, so the person who uploads can
// talk to that editor before overwriting the edit with --force.
func conflictDetail(fields map[string]string) string {
	name, at := fields["updatedByName"], fields["updatedAt"]
	detail := ""
	switch {
	case name != "" && at != "":
		detail = fmt.Sprintf(" Last edited by %s at %s.", name, at)
	case at != "":
		detail = fmt.Sprintf(" Last edited at %s.", at)
	}
	return detail + " Pass --force to overwrite it."
}
