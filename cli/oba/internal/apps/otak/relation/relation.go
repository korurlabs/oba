// Package relation creates, updates and deletes the leads, customers and suppliers of Otak from
// the JSON object a user gives, checked against the live API document, and says what identifies
// a company: its normalized name and its website host.
package relation

import (
	"encoding/json"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

const (
	relationsPath    = "/otak/relations"
	createSchema     = "RelationCreateRequest"
	updateSchema     = "RelationUpdateRequest"
	fieldType        = "type"
	fieldStage       = "stage"
	fieldOwnerUserID = "ownerUserId"

	// StepFields, StepType and StepStage name the calls of an update, in their order.
	StepFields = "the fields (PUT)"
	StepType   = "the type (PATCH type)"
	StepStage  = "the stage (PATCH stage)"
)

func parseRelation(body []byte) (client.Relation, error) {
	var resp client.APIResponse[client.Relation]
	if err := json.Unmarshal(body, &resp); err != nil {
		return client.Relation{}, err
	}
	return resp.Data, nil
}
