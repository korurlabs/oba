package ticket

import (
	"errors"
	"fmt"
	"io"
	"net/http"
	"strings"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/board"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/userinput"
)

// Target is a ticket a user named: its id, the board it lives on and how the user named it. Key
// is the ticket's key with the board's spelling of its prefix, empty when the ticket was named by
// its id. Found is the ticket the lookup by key read, nil for an id, which is not read.
type Target struct {
	Ref     string
	ID      string
	BoardID string
	Key     string
	Found   *client.Ticket
}

// Label is the key, or the id when the ticket was named by its id.
func (t Target) Label() string {
	if t.Key != "" {
		return t.Key
	}
	return t.ID
}

// Resolve resolves one ticket ref, a key or an id, as Resolver.Ticket does, for a command that
// names a single ticket; boardFlag is the --board value and warn receives the warning that a
// key's board differs from it.
func Resolve(c *client.Client, ref, boardFlag string, warn io.Writer) (Target, error) {
	return NewResolver(c, boardFlag, warn).Ticket(ref)
}

// Resolver finds the tickets and boards a command names in the organization of c. Its lookups
// stay out of the --json output, and the board tree is read once per run.
type Resolver struct {
	c         *client.Client
	boards    *board.Resolver
	boardFlag string
	warn      io.Writer
	selected  string
}

// NewResolver returns a resolver; boardFlag is the --board value and warn receives the warning
// that a key's board differs from --board.
func NewResolver(c *client.Client, boardFlag string, warn io.Writer) *Resolver {
	return &Resolver{c: c.Unrecorded(), boards: board.NewResolver(c), boardFlag: strings.TrimSpace(boardFlag), warn: warn}
}

// Client returns the unrecorded client the resolver reads with, for other lookups of the run.
func (r *Resolver) Client() *client.Client {
	return r.c
}

// SelectedBoard returns the id of the board --board names, else of the organization's default
// board; board.ErrNoBoard with neither.
func (r *Resolver) SelectedBoard() (string, error) {
	if r.selected != "" {
		return r.selected, nil
	}
	var ref board.Ref
	var err error
	if r.boardFlag != "" {
		ref, err = r.boards.Resolve(r.boardFlag)
	} else {
		ref, err = board.Selected(r.c, "")
	}
	if err != nil {
		return "", err
	}
	r.selected = ref.ID
	return ref.ID, nil
}

// Board returns the id of the board ref names: a name, key prefix or id.
func (r *Resolver) Board(ref string) (string, error) {
	named, err := r.boards.Resolve(ref)
	if err != nil {
		return "", err
	}
	return named.ID, nil
}

// Ticket resolves a ticket key, such as OPS-42 in any case, or a ticket id.
//
// An id carries no board, so the board is --board or the default board. A key names its board
// through its prefix: the board is found among the user's boards, child boards included, and the
// ticket is read by key; the board it names is used for every later call. That board wins over a
// different --board, with a warning, so that scripts passing a default board keep working for
// keys of other boards. Every not-found case is a 404, so the command exits 2: a missing ticket
// cannot be told from one on a board the user has no access to.
func (r *Resolver) Ticket(ref string) (Target, error) {
	ref = strings.TrimSpace(ref)
	if userinput.IsID(ref) {
		boardID, err := r.SelectedBoard()
		if err != nil {
			return Target{}, err
		}
		return Target{Ref: ref, ID: ref, BoardID: boardID}, nil
	}
	prefix, number, err := ParseKey(ref)
	if err != nil {
		return Target{}, err
	}
	owner, err := r.boardByPrefix(prefix)
	if err != nil {
		return Target{}, err
	}
	// The board's own spelling of its prefix makes the key exact for the lookup.
	key := owner.Prefix + "-" + number
	found, err := r.c.GetTicketByKey(key)
	if err != nil {
		var apiErr *client.APIError
		if errors.As(err, &apiErr) && apiErr.Status == http.StatusNotFound {
			return Target{}, notFound(fmt.Sprintf("ticket %q not found", key))
		}
		return Target{}, fmt.Errorf("failed to read ticket %q: %w", key, err)
	}
	if found.Board == nil || found.Board.ID == "" {
		return Target{}, fmt.Errorf("the API names no board for ticket %q", key)
	}
	r.warnOnOtherBoard(key, *found.Board)
	return Target{Ref: ref, ID: found.ID, BoardID: found.Board.ID, Key: key, Found: &found}, nil
}

// TicketID returns the id of the ticket ref names, resolved as Ticket does.
func (r *Resolver) TicketID(ref string) (string, error) {
	target, err := r.Ticket(ref)
	return target.ID, err
}

func (r *Resolver) boardByPrefix(prefix string) (board.FlatBoard, error) {
	tree, err := r.boards.Tree()
	if err != nil {
		return board.FlatBoard{}, err
	}
	for _, b := range board.Flatten(tree) {
		if b.Prefix != "" && strings.EqualFold(b.Prefix, prefix) {
			return b, nil
		}
	}
	return board.FlatBoard{}, notFound(fmt.Sprintf("no board with prefix %q is available to you", strings.ToUpper(prefix)))
}

// warnOnOtherBoard warns when --board names another board than the one the ticket lives on.
func (r *Resolver) warnOnOtherBoard(key string, owner client.TicketBoard) {
	if r.boardFlag == "" {
		return
	}
	given, err := r.boards.Resolve(r.boardFlag)
	if err != nil {
		_, _ = fmt.Fprintf(r.warn, "Warning: ignoring --board %q (%v); %s is on board %s (%s)\n",
			r.boardFlag, err, key, cell(owner.Name), cell(owner.Prefix))
		return
	}
	if !strings.EqualFold(given.ID, owner.ID) {
		_, _ = fmt.Fprintf(r.warn, "Warning: %s is on board %s (%s), not on --board %q; using the ticket's board\n",
			key, cell(owner.Name), cell(owner.Prefix), r.boardFlag)
	}
}

// notFound is a lookup miss that exits 2, as an API 404 does.
func notFound(message string) error {
	return &client.APIError{Status: http.StatusNotFound, Message: message}
}
