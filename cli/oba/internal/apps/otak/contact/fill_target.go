package contact

import (
	"encoding/json"
	"fmt"
	"net/url"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// FillTarget is one relation contact fill searches contacts for. Existing holds the names of
// the contacts it already has, so that the search proposes none of them again.
type FillTarget struct {
	ID       string
	Name     string
	Website  string
	Existing []string
}

// fillRelation is the part of GET /otak/relations/{id} (RelationDetailResponse) that fill
// needs to decide whether it may search the relation.
type fillRelation struct {
	ID      string  `json:"id"`
	Name    string  `json:"name"`
	Website *string `json:"website"`
}

// GetFillTarget reads one relation and its contacts. It refuses a relation that has no
// website, since a contact is kept only when its source is the
// company's own site or register and its e-mail address is on the company's domain.
func GetFillTarget(c *client.Client, relationID string) (FillTarget, error) {
	body, err := c.Get(relationsPath + "/" + url.PathEscape(relationID))
	if err != nil {
		return FillTarget{}, err
	}
	var resp client.APIResponse[fillRelation]
	if err := json.Unmarshal(body, &resp); err != nil {
		return FillTarget{}, fmt.Errorf("failed to read the relation: %w", err)
	}
	detail := resp.Data
	website := strings.TrimSpace(orEmpty(detail.Website))
	if website == "" {
		return FillTarget{}, fmt.Errorf("%s has no website; add one with 'oba otak relation update %s' first", guardrail.OneLine(detail.Name), detail.ID)
	}
	contacts, err := listContactNames(c, detail.ID)
	if err != nil {
		return FillTarget{}, err
	}
	return FillTarget{ID: detail.ID, Name: detail.Name, Website: website, Existing: contacts}, nil
}

func listContactNames(c *client.Client, relationID string) ([]string, error) {
	contacts, err := c.ListContacts(relationID)
	if err != nil {
		return nil, fmt.Errorf("failed to read the contacts of the relation: %w", err)
	}
	names := make([]string, 0, len(contacts))
	for _, contact := range contacts {
		names = append(names, contact.Name)
	}
	return names, nil
}
