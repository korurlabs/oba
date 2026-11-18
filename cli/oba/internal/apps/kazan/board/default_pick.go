package board

import (
	"bufio"
	"errors"
	"fmt"
	"io"
	"strconv"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
)

// ErrNoBoardPicked reports that the user ended the input without picking a board.
var ErrNoBoardPicked = errors.New("no board picked")

// Pick prints the board tree of the organization of c as a numbered table and asks for the
// number of the board to use, until a number in range is given. It returns the board's id.
func Pick(c *client.Client, in *bufio.Reader, src io.Reader, out io.Writer) (string, error) {
	tree, err := c.Unrecorded().ListBoards(true)
	if err != nil {
		return "", err
	}
	flat := Flatten(tree)
	if len(flat) == 0 {
		return "", errors.New("you are not a member of any board in this organization")
	}
	if err := PrintList(out, tree); err != nil {
		return "", err
	}
	for {
		answer, eof, err := terminal.Ask(in, src, out, "Board number: ")
		if err != nil {
			return "", err
		}
		if n, convErr := strconv.Atoi(answer); convErr == nil && n >= 1 && n <= len(flat) {
			return flat[n-1].ID, nil
		}
		if eof {
			_, _ = fmt.Fprintln(out)
			return "", ErrNoBoardPicked
		}
		_, _ = fmt.Fprintf(out, "Please give a number from 1 to %d.\n", len(flat))
	}
}
