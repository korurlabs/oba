package cmd

import (
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"strings"

	"github.com/spf13/cobra"

	"github.com/korurlabs/kalkan-cli/internal/auth"
	"github.com/korurlabs/kalkan-cli/internal/client"
)

// Exit codes. Scripts and agents branch on them, so they never change meaning.
const (
	ExitOK          = 0
	ExitFailure     = 1
	ExitNotSignedIn = 2
	ExitConflict    = 3
	ExitDenied      = 4
)

// Error codes of failures that are not an answer of the API.
const (
	codeNotSignedIn = "NOT_SIGNED_IN"
	codeCLIError    = "CLI_ERROR"
)

// exitCodeOf maps an error to its exit code. It reads the error's type and API code with
// errors.As and errors.Is, never its message: no usable sign-in or a 401 is 2, a page edited in
// Kalkan since the last push or a write that raced another is 3, a 403, a 404 or a missing
// app grant is 4, anything else 1.
func exitCodeOf(err error) int {
	if err == nil {
		return ExitOK
	}
	if errors.Is(err, auth.ErrNotSignedIn) {
		return ExitNotSignedIn
	}
	var apiErr *client.APIError
	if !errors.As(err, &apiErr) {
		return ExitFailure
	}
	switch {
	case apiErr.Status == http.StatusUnauthorized:
		return ExitNotSignedIn
	case apiErr.Code == client.CodeChangedSincePush, apiErr.Code == client.CodeConcurrentWrite:
		return ExitConflict
	case apiErr.Code == client.CodeNoAppAccess,
		apiErr.Status == http.StatusForbidden, apiErr.Status == http.StatusNotFound:
		return ExitDenied
	default:
		return ExitFailure
	}
}

// exitRank orders the exit codes of a run that ends several ways at once: a failure outranks a
// lost sign-in, which outranks a denial, which outranks a conflict. A conflict ranks lowest
// because only it is cleared by a deliberate --force; the others need a fix first.
var exitRank = map[int]int{
	ExitOK:          0,
	ExitConflict:    1,
	ExitDenied:      2,
	ExitNotSignedIn: 3,
	ExitFailure:     4,
}

// worseExit returns the exit code of higher precedence; an unknown code counts as a failure.
func worseExit(current, next int) int {
	currentRank, ok := exitRank[current]
	if !ok {
		currentRank = exitRank[ExitFailure]
	}
	nextRank, ok := exitRank[next]
	if !ok {
		nextRank = exitRank[ExitFailure]
	}
	if nextRank > currentRank {
		return next
	}
	return current
}

// describe returns the code and message an error is printed with. An API error keeps the text
// its callers wrapped it in, such as the file an upload was for and a hint how to fix it, since
// the API's own message cannot say which local file or step it refused.
func describe(err error) (string, string) {
	var apiErr *client.APIError
	if errors.As(err, &apiErr) {
		code := apiErr.Code
		if code == "" {
			code = fmt.Sprintf("HTTP_%d", apiErr.Status)
		}
		message := apiErr.Message
		switch {
		case apiErr.Status == http.StatusUnauthorized:
			message += "; run kalkan login"
		case apiErr.Code == client.CodeChangedSincePush:
			message += conflictDetail(apiErr.ErrorValues)
		}
		return code, wrapperContext(err, apiErr) + message
	}
	if errors.Is(err, auth.ErrNotSignedIn) {
		return codeNotSignedIn, err.Error()
	}
	return codeCLIError, err.Error()
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

// conflictDetail names who last edited a page in Kalkan and when, so the person who pushes can
// talk to that editor before overwriting the edit with --force.
func conflictDetail(values map[string]any) string {
	name, _ := values["updatedByName"].(string)
	at, _ := values["updatedAt"].(string)
	detail := ""
	switch {
	case name != "" && at != "":
		detail = fmt.Sprintf(" Last edited by %s at %s.", name, at)
	case at != "":
		detail = fmt.Sprintf(" Last edited at %s.", at)
	}
	return detail + " Pass --force to overwrite it."
}

// errorBody is the JSON shape of a run-level error on stderr.
type errorBody struct {
	Error errorDetail `json:"error"`
}

type errorDetail struct {
	Code    string `json:"code"`
	Message string `json:"message"`
}

// fail prints err once on stderr, as JSON with --json, and returns the ExitError that ends the
// process with the error's exit code, so Execute prints nothing more.
func fail(cmd *cobra.Command, err error) error {
	code, message := describe(err)
	writeError(cmd.ErrOrStderr(), code, message)
	return &ExitError{Code: exitCodeOf(err)}
}

func writeError(w io.Writer, code, message string) {
	if jsonOutput {
		encoded, err := json.Marshal(errorBody{Error: errorDetail{Code: code, Message: message}})
		if err == nil {
			_, _ = fmt.Fprintln(w, string(encoded))
			return
		}
	}
	_, _ = fmt.Fprintf(w, "Error: %s (%s)\n", message, code)
}

// printJSON writes v as indented JSON on stdout.
func printJSON(cmd *cobra.Command, v any) error {
	encoder := json.NewEncoder(cmd.OutOrStdout())
	encoder.SetIndent("", "  ")
	if err := encoder.Encode(v); err != nil {
		return fmt.Errorf("print the result: %w", err)
	}
	return nil
}
