// Package delivery builds the oba kaya commands that put a checkout's signed config release in
// place and keep it current: sync, refresh, status, launch and watch.
package delivery

import "github.com/spf13/cobra"

// NewCmds builds the delivery commands, which sit directly under oba kaya.
func NewCmds() []*cobra.Command {
	return []*cobra.Command{newSyncCmd(), newRefreshCmd(), newStatusCmd(), newLaunchCmd(), newWatchCmd()}
}
