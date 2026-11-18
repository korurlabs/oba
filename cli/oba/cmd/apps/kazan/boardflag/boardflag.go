// Package boardflag holds the --board flag of oba kazan: the board a board-scoped command acts
// on, by name, key prefix or id. The kazan command adds it once, for every Kazan command; the
// commands read it through Selected.
package boardflag

import (
	"strings"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/board"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// Name is the flag's name.
const Name = "board"

// Add adds --board to cmd and every command below it.
func Add(cmd *cobra.Command) {
	cmd.PersistentFlags().String(Name, "", "the board to act on: its name, key prefix or id "+
		"(default the one chosen with 'oba kazan board use')")
}

// Value returns the --board value of the running command, empty when it is not given or the
// command has no such flag.
func Value(cmd *cobra.Command) string {
	f := cmd.Flag(Name)
	if f == nil {
		return ""
	}
	return strings.TrimSpace(f.Value.String())
}

// Selected returns the board the running command acts on in the organization of c: --board,
// else the organization's default board; board.ErrNoBoard with neither.
func Selected(cmd *cobra.Command, c *client.Client) (board.Ref, error) {
	return board.Selected(c, Value(cmd))
}
