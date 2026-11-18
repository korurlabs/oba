package milestone

import "github.com/korurlabs/oba-cli/internal/dojo/client"

// Undepend removes the dependency of the milestone arg names on the one on names.
func Undepend(c *client.Client, boardID, arg, on string) (Dependency, error) {
	return changeDependency(c, boardID, arg, on, c.RemoveMilestoneDependency)
}
