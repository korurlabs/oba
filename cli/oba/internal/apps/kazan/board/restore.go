package board

import "github.com/korurlabs/oba-cli/internal/dojo/client"

// Restore restores the deleted board ref names. A deleted board is not in the board tree, so a
// name or prefix is found only while it is live; the id always works.
func Restore(c *client.Client, ref string) (Ref, error) {
	named, err := Resolve(c, ref)
	if err != nil {
		return Ref{}, err
	}
	return named, c.RestoreBoard(named.ID)
}
