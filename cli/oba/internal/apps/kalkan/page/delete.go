package page

import (
	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// Delete soft-deletes page and every page below it.
func Delete(c *client.Client, page client.KalkanPageListItem) error {
	return c.DeleteKalkanPage(page.ID)
}
