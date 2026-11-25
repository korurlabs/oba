package cyberscore

import "github.com/korurlabs/oba-cli/internal/dojo/client"

// DeleteCheck deletes check ch, found with ResolveCheck; the score is recalculated without it.
func DeleteCheck(c *client.Client, ch client.CyberscoreCheck) error {
	return c.DeleteCyberscoreCheck(ch.ID)
}
