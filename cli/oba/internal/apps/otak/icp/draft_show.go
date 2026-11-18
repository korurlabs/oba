package icp

import (
	"encoding/json"
	"io"

	"github.com/korurlabs/oba-cli/internal/dojo/apidocs"
)

// PrintDraft writes the draft one field per line, in the order of the request fields. A field
// left out or null shows as "-"; a string shows without quotes, any other value as its JSON.
func PrintDraft(out io.Writer, draft map[string]json.RawMessage, fields []apidocs.Field) {
	printProfile(out, "Draft ICP:", draft, fields)
}
