package page

import (
	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// Put writes one language of the page at up.Path: it creates the page when the path names none,
// otherwise it updates that language and keeps the others. A page edited in Kalkan since the last
// upload is refused with client.CodeKalkanPageChanged unless up.Force is set.
func Put(c *client.Client, up client.KalkanPageUpsert) (client.KalkanPageUpsertResult, error) {
	return c.UpsertKalkanPage(up, false)
}
