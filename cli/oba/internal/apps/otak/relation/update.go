package relation

import (
	"encoding/json"
	"fmt"
	"net/url"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/apidocs"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// Result is the outcome of an update: the steps it applied, none when the relation already had
// the given values, and the relation before and after. After is the response of the last call
// sent, or the relation as read when nothing was sent, so a caller reports the type and stage the
// API set rather than the ones it was given: a stage of CLOSED turns a lead into a customer.
type Result struct {
	Applied []string
	Before  client.Relation
	After   client.Relation
}

// Update changes only the given keys of relation id. The update runs as up to three calls: the
// fields (PUT), then the type, then the stage, each only when needed. A failed step is an
// *UpdateStepError that names the steps already applied.
func Update(c *client.Client, id string, given map[string]json.RawMessage) (Result, error) {
	doc, err := apidocs.FetchDoc(c.BaseURL)
	if err != nil {
		return Result{}, err
	}
	fields, err := UpdateFields(doc)
	if err != nil {
		return Result{}, apidocs.FieldListError(c.BaseURL, err)
	}
	if err := apidocs.CheckKeys(given, fields); err != nil {
		return Result{}, err
	}
	putFields, err := doc.Fields(updateSchema)
	if err != nil {
		return Result{}, apidocs.FieldListError(c.BaseURL, err)
	}

	path := relationsPath + "/" + url.PathEscape(id)
	body, err := c.Get(path)
	if err != nil {
		return Result{}, err
	}
	before, err := parseRelation(body)
	if err != nil {
		return Result{}, fmt.Errorf("failed to read relation %s: %w", id, err)
	}

	update, needPut, err := mergeUpdate(before, given, putFields)
	if err != nil {
		return Result{}, err
	}

	result := Result{Before: before, After: before}
	if needPut {
		after, err := sendUpdateStep(c.Put, path, update)
		if err != nil {
			return result, &UpdateStepError{Step: StepFields, Applied: result.Applied, Err: err}
		}
		result.After = after
		result.Applied = append(result.Applied, StepFields)
	}
	if raw, ok := given[fieldType]; ok && differs(raw, &result.After.Type) {
		after, err := sendUpdateStep(c.Patch, path+"/type", map[string]json.RawMessage{fieldType: raw})
		if err != nil {
			return result, &UpdateStepError{Step: StepType, Applied: result.Applied, Err: err}
		}
		result.After = after
		result.Applied = append(result.Applied, StepType)
	}
	// The stage is compared with the relation after the type step, since a type change can set
	// or clear it.
	if raw, ok := given[fieldStage]; ok && differs(raw, result.After.Stage) {
		after, err := sendUpdateStep(c.Patch, path+"/stage", map[string]json.RawMessage{fieldStage: raw})
		if err != nil {
			return result, &UpdateStepError{Step: StepStage, Applied: result.Applied, Err: err}
		}
		result.After = after
		result.Applied = append(result.Applied, StepStage)
	}
	return result, nil
}

// mergeUpdate builds the PUT body: every property of the update schema with its current value,
// and the given keys laid over it, null included. The bool reports whether any key other than
// type and stage was given, the only case that needs a PUT. A property the relation detail does
// not carry, and that was not given, is refused: the PUT would clear it without the user asking.
func mergeUpdate(current client.Relation, given map[string]json.RawMessage, putFields []apidocs.Field) (map[string]json.RawMessage, bool, error) {
	needPut := false
	for key := range given {
		if key != fieldType && key != fieldStage {
			needPut = true
			break
		}
	}
	if !needPut {
		return nil, false, nil
	}

	body := make(map[string]json.RawMessage, len(putFields))
	for _, field := range putFields {
		if value, ok := given[field.Name]; ok {
			body[field.Name] = value
			continue
		}
		if field.Name == fieldOwnerUserID {
			body[field.Name] = ownerID(current)
			continue
		}
		value, ok := current.Values[field.Name]
		if !ok {
			return nil, false, fmt.Errorf("cannot keep field %q: the relation as read has no such field; give its value", field.Name)
		}
		body[field.Name] = value
	}
	return body, true, nil
}

func ownerID(current client.Relation) json.RawMessage {
	if current.Owner == nil || current.Owner.ID == "" {
		return json.RawMessage("null")
	}
	encoded, err := json.Marshal(current.Owner.ID)
	if err != nil {
		// rule-10-no-op -- reason: encoding a Go string to JSON cannot fail
		return json.RawMessage("null")
	}
	return encoded
}

// differs reports whether a given type or stage value is other than the current one, ignoring
// case as the API binds enums. A value that is not a string is passed on for the API to judge.
func differs(raw json.RawMessage, current *string) bool {
	if string(raw) == "null" {
		return current != nil
	}
	var value string
	if json.Unmarshal(raw, &value) != nil {
		return true
	}
	return current == nil || !strings.EqualFold(value, *current)
}

func sendUpdateStep(send func(string, any) ([]byte, error), path string, payload any) (client.Relation, error) {
	body, err := send(path, payload)
	if err != nil {
		return client.Relation{}, err
	}
	relation, err := parseRelation(body)
	if err != nil {
		return client.Relation{}, fmt.Errorf("the call succeeded, but its response could not be read: %w", err)
	}
	return relation, nil
}

// UpdateStepError is a failed update step. It names the steps already applied, since the
// update is not atomic, and unwraps to the API error so that the exit code follows it.
type UpdateStepError struct {
	Step    string
	Applied []string
	Err     error
}

func (e *UpdateStepError) Error() string {
	done := "Nothing was changed."
	if len(e.Applied) > 0 {
		done = "Already applied: " + strings.Join(e.Applied, ", ") + ". The update is not atomic; later steps were not run."
	}
	return fmt.Sprintf("%v\nThe update stopped at %s. %s", e.Err, e.Step, done)
}

func (e *UpdateStepError) Unwrap() error {
	return e.Err
}
