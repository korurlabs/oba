package content

import (
	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// Delete deletes content contentID of campaign campaignID with its images.
func Delete(c *client.Client, campaignID, contentID string) error {
	return c.DeleteCampaignContent(campaignID, contentID)
}
