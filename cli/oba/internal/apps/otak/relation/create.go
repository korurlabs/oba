package relation

import (
	"encoding/json"
	"fmt"

	"github.com/korurlabs/oba-cli/internal/dojo/apidocs"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// Create checks the keys of obj against the create schema and sends data, the text obj was
// parsed from, as the body. encoding/json only compacts a RawMessage, so every value reaches the
// API as it was typed.
func Create(c *client.Client, data []byte, obj map[string]json.RawMessage) (client.Relation, error) {
	fields, err := loadFields(c.BaseURL, CreateFields)
	if err != nil {
		return client.Relation{}, err
	}
	if err := apidocs.CheckKeys(obj, fields); err != nil {
		return client.Relation{}, err
	}
	body, err := c.Post(relationsPath, json.RawMessage(data))
	if err != nil {
		return client.Relation{}, err
	}
	created, err := parseRelation(body)
	if err != nil {
		return client.Relation{}, fmt.Errorf("the relation was created, but the response could not be read: %w", err)
	}
	return created, nil
}
