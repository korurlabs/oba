package contact

import (
	"encoding/json"

	"github.com/korurlabs/oba-cli/internal/dojo/apidocs"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// Create checks the keys of obj against the request fields and sends body, the text obj was
// parsed from, as it is: encoding/json only compacts a RawMessage, so every value reaches the
// API as it was given.
func Create(c *client.Client, relationID string, fields []apidocs.Field, body json.RawMessage, obj map[string]json.RawMessage) (client.Contact, error) {
	if err := apidocs.CheckKeys(obj, fields); err != nil {
		return client.Contact{}, err
	}
	return c.CreateContact(relationID, body)
}
