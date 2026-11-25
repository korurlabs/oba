package campaign

import (
	"encoding/json"

	"github.com/korurlabs/oba-cli/internal/dojo/apidocs"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// Create checks the keys of obj against the request fields and sends body, the text obj was
// parsed from, as it is: encoding/json only compacts a RawMessage, so every value, the texts
// included, reaches the API as it was given.
func Create(c *client.Client, fields []apidocs.Field, body json.RawMessage, obj map[string]json.RawMessage) (client.Campaign, error) {
	if err := apidocs.CheckKeys(obj, fields); err != nil {
		return client.Campaign{}, err
	}
	return c.CreateCampaign(body)
}
