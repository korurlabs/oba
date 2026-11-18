package board

import (
	"fmt"
	"io"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// Details is one board with its place in the board tree: its parent, nil for a top-level board,
// and its child boards.
type Details struct {
	Board    client.Board
	Parent   *client.Board
	Children []client.Board
}

// Get reads the board ref names. Its parent and children come from the board tree, which lists
// only the boards the user is a member of.
func Get(c *client.Client, ref string) (Details, error) {
	resolver := NewResolver(c)
	named, err := resolver.Resolve(ref)
	if err != nil {
		return Details{}, err
	}
	tree, err := resolver.Tree()
	if err != nil {
		return Details{}, err
	}
	b, err := c.GetBoard(named.ID)
	if err != nil {
		return Details{}, err
	}
	details := Details{Board: b}
	if node, parent, found := Locate(tree, named.ID); found {
		details.Parent = parent
		details.Children = node.ChildBoards
	}
	return details, nil
}

// PrintDetails writes one board as labelled lines, its parent and its direct children.
func PrintDetails(out io.Writer, d Details) {
	b := d.Board
	_, _ = fmt.Fprintf(out, "Name:        %s\n", cell(b.Name))
	_, _ = fmt.Fprintf(out, "ID:          %s\n", cell(b.ID))
	_, _ = fmt.Fprintf(out, "Key:         %s\n", cell(b.Prefix))
	_, _ = fmt.Fprintf(out, "Slug:        %s\n", cell(b.Slug))
	_, _ = fmt.Fprintf(out, "Description: %s\n", cell(b.Description))
	if d.Parent != nil {
		_, _ = fmt.Fprintf(out, "Parent:      %s (%s)\n", cell(d.Parent.Name), cell(d.Parent.Prefix))
	}
	if len(d.Children) > 0 {
		_, _ = fmt.Fprintln(out, "Children:")
		for _, ch := range d.Children {
			_, _ = fmt.Fprintf(out, "  - %s (%s)\n", cell(ch.Name), cell(ch.Prefix))
		}
	}
}
