package find

import (
	"fmt"
	"net/url"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// relationTypes are the relation types whose companies Claude Code must not propose again.
var relationTypes = []string{leadType, "CUSTOMER", "SUPPLIER"}

// ListKnownRelations reads the leads, customers and suppliers of the organization. Leads are
// read with every stage named, because the list leaves out LOST and PARKED leads otherwise, and
// a lost lead must not come back as a new one. Customers are read with no stage named, which
// gives every customer, CLOSED and DIRECT alike.
func ListKnownRelations(c *client.Client, stages []string) ([]client.RelationListItem, error) {
	var all []client.RelationListItem
	for _, relationType := range relationTypes {
		query := url.Values{fieldType: {relationType}}
		if relationType == leadType {
			query[fieldStage] = stages
		}
		relations, err := client.ListPages[client.RelationListItem](c, relationsPath, query)
		if err != nil {
			return nil, fmt.Errorf("failed to read the %s relations: %w", strings.ToLower(relationType), err)
		}
		all = append(all, relations...)
	}
	return all, nil
}
