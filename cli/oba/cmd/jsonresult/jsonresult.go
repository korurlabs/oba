// Package jsonresult sets the --json result of a command whose last API call is not its result,
// such as a lookup made after the call that matters, or a cancelled delete whose only calls were
// lookups. Without --json it does nothing.
package jsonresult

import (
	"encoding/json"
	"fmt"
	"net/http"

	"github.com/korurlabs/oba-cli/internal/dojo/jsonout"
)

// envelope is the API's response shape, the one jsonout reads the data from.
type envelope struct {
	Data json.RawMessage `json:"data"`
}

// Set makes value, as the API sent it, the result of the run in place of the data of the last
// call.
func Set(value json.RawMessage) error {
	body, err := json.Marshal(envelope{Data: value})
	if err != nil {
		return fmt.Errorf("failed to encode the JSON output: %w", err)
	}
	jsonout.Record(http.MethodGet, body)
	return nil
}

// SetList makes items, as the API sent them, the result of the run: one array, also when it is
// empty.
func SetList(items []json.RawMessage) {
	jsonout.RecordItems(items)
}

// Nothing makes the result null, for a run that ends without doing what it was asked, so that
// the data of its lookups is not taken for the result.
func Nothing() {
	jsonout.Record(http.MethodGet, nil)
}

// SetPartial makes value the result like Set, and keeps it when the run then fails: the command
// did part of its work, and a script needs that result together with the non-zero exit status.
func SetPartial(value json.RawMessage) error {
	if err := Set(value); err != nil {
		return err
	}
	jsonout.KeepOnFailure()
	return nil
}
