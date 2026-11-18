package contact

import (
	"encoding/json"
	"fmt"
	"io"
	"net/url"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// foundRequest is the ContactCreateRequest body of a found contact: the found values plus the
// keys a search never fills, which the API requires. Kind is always PERSON: a search looks for
// people at the company. Both main flags are false; the API still makes the first person of a
// relation its main person contact.
type foundRequest struct {
	FoundContact
	LinkedinURL         *string `json:"linkedinUrl"`
	DecisionMaker       bool    `json:"decisionMaker"`
	Kind                string  `json:"kind"`
	MainPersonContact   bool    `json:"mainPersonContact"`
	MainBusinessContact bool    `json:"mainBusinessContact"`
}

// createdContact is the part of the ContactDetailResponse that CreateFound prints.
type createdContact struct {
	ID string `json:"id"`
}

// CreateFound posts each found contact of relation relationID and prints one line per contact.
// Unlike Create, which sends JSON the user typed after checking its keys, it sends the typed
// values a search found, as Clean left them. It returns how many failed; a contact whose
// response cannot be read was still created, so it does not count as failed.
func CreateFound(c *client.Client, out io.Writer, relationID string, contacts []FoundContact) int {
	failed := 0
	path := relationsPath + "/" + url.PathEscape(relationID) + "/contacts"
	for _, contact := range contacts {
		name := guardrail.OneLine(contact.Name)
		body, err := c.Post(path, foundRequest{FoundContact: contact, Kind: kindPerson})
		if err != nil {
			failed++
			_, _ = fmt.Fprintf(out, "    ✗ contact %s: %v\n", name, err)
			continue
		}
		var resp client.APIResponse[createdContact]
		if err := json.Unmarshal(body, &resp); err != nil {
			_, _ = fmt.Fprintf(out, "    + contact %s (created, but the response could not be read: %v)\n", name, err)
			continue
		}
		_, _ = fmt.Fprintf(out, "    + contact %s (%s)\n", name, guardrail.OneLine(resp.Data.ID))
	}
	return failed
}
