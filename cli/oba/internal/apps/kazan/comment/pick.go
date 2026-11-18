package comment

import (
	"bufio"
	"errors"
	"fmt"
	"io"
	"strconv"
	"strings"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/ticket"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
)

// ErrCancelled reports that the user left the comment pick list; the command ends without a
// change and without an error.
var ErrCancelled = errors.New("cancelled")

// ErrNoComments reports that the ticket has no comment to pick.
var ErrNoComments = errors.New("No comments found on this ticket.") //nolint:staticcheck // ST1005: shown to the user verbatim

// Pick lists the comments of ticket t by number and returns the id of the one the user picks.
// "q" or the end of input returns ErrCancelled. The list read is a lookup, so it stays out of
// the --json output.
func Pick(c *client.Client, t ticket.Target, in *bufio.Reader, src io.Reader, out io.Writer) (string, error) {
	comments, err := List(c.Unrecorded(), t)
	if err != nil {
		return "", err
	}
	if len(comments) == 0 {
		return "", ErrNoComments
	}
	_, _ = fmt.Fprintln(out, "Select a comment:")
	for i, cm := range comments {
		_, _ = fmt.Fprintf(out, "  %d  %s (%s): %s\n", i+1,
			author(cm.AuthorFirstName, cm.AuthorLastName, cm.AuthorEmail), guardrail.OneLine(cm.CreatedAt), preview(cm.Content))
	}
	for {
		answer, eof, err := terminal.Ask(in, src, out, "Select comment (or 'q' to quit): ")
		if err != nil {
			return "", err
		}
		if eof || strings.EqualFold(answer, "q") {
			return "", ErrCancelled
		}
		number, convErr := strconv.Atoi(answer)
		if convErr != nil || number < 1 || number > len(comments) {
			_, _ = fmt.Fprintf(out, "Invalid selection. Enter a number between 1 and %d.\n", len(comments))
			continue
		}
		return comments[number-1].ID, nil
	}
}
