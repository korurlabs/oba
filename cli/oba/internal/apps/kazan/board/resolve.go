package board

import (
	"fmt"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/userinput"
)

// Ref is a board a user named. Name and Prefix are empty when the user gave an id, since an id
// is used without a lookup.
type Ref struct {
	ID     string
	Name   string
	Prefix string
}

// Label is "Name (PREFIX)", or the id when the board was named by its id.
func (r Ref) Label() string {
	if r.Name == "" {
		return r.ID
	}
	return fmt.Sprintf("%s (%s)", r.Name, r.Prefix)
}

// Resolver finds the boards a user names in one organization. It reads the board tree once per
// run, on the first name or prefix, and keeps its reads out of the --json output, since they are
// lookups and not the command's result.
type Resolver struct {
	c      *client.Client
	tree   []client.Board
	loaded bool
}

// NewResolver returns a resolver on the organization of c.
func NewResolver(c *client.Client) *Resolver {
	return &Resolver{c: c.Unrecorded()}
}

// Resolve returns the board ref names: an id as it is, without a call; else the one board, at
// any depth of the tree, whose name or key prefix equals ref, case ignored. A ref that matches
// two boards is refused rather than guessed, so that tickets never land on the wrong board.
func (r *Resolver) Resolve(ref string) (Ref, error) {
	ref = strings.TrimSpace(ref)
	if ref == "" {
		return Ref{}, fmt.Errorf("no board given; name a board by its name, key prefix or id")
	}
	// An id is used as it is; the API refuses a board the user is not a member of.
	if userinput.IsID(ref) {
		return Ref{ID: ref}, nil
	}
	tree, err := r.Tree()
	if err != nil {
		return Ref{}, err
	}
	var matches []Ref
	for _, b := range Flatten(tree) {
		if strings.EqualFold(b.Name, ref) || (b.Prefix != "" && strings.EqualFold(b.Prefix, ref)) {
			matches = append(matches, Ref{ID: b.ID, Name: b.Name, Prefix: b.Prefix})
		}
	}
	switch len(matches) {
	case 0:
		return Ref{}, fmt.Errorf("no board of yours matches %q; run 'oba kazan board list'", ref)
	case 1:
		return matches[0], nil
	default:
		labels := make([]string, 0, len(matches))
		for _, m := range matches {
			labels = append(labels, fmt.Sprintf("%s (%s, %s)", m.Name, m.Prefix, m.ID))
		}
		return Ref{}, fmt.Errorf("several boards match %q: %s; use the id instead", ref, strings.Join(labels, ", "))
	}
}

// Tree returns the boards of the organization the user is a member of, child boards nested
// under their parents, read on the first call.
func (r *Resolver) Tree() ([]client.Board, error) {
	if r.loaded {
		return r.tree, nil
	}
	tree, err := r.c.ListBoards(true)
	if err != nil {
		return nil, err
	}
	r.tree, r.loaded = tree, true
	return tree, nil
}

// Resolve returns the board ref names in the organization of c; see Resolver.Resolve.
func Resolve(c *client.Client, ref string) (Ref, error) {
	return NewResolver(c).Resolve(ref)
}
