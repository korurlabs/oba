package icp

import (
	"encoding/json"
	"fmt"
	"io"

	"github.com/korurlabs/oba-cli/internal/dojo/apidocs"
)

// PrintProposal writes proposal number as PrintDraft writes a draft.
func PrintProposal(out io.Writer, number int, proposal map[string]json.RawMessage, fields []apidocs.Field) {
	printProfile(out, fmt.Sprintf("Proposal %d:", number), proposal, fields)
}
