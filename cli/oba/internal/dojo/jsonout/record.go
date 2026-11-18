package jsonout

import (
	"encoding/json"
	"net/http"
)

// nullData stands for a response without data, such as a 204 or an envelope whose data is
// absent.
var nullData = json.RawMessage("null")

// Record keeps the data of one successful response, the "data" field of the API envelope. It
// does nothing while --json is off.
func Record(method string, body []byte) {
	mu.Lock()
	defer mu.Unlock()
	if dest == nil {
		return
	}
	results = append(results, result{method: method, data: envelopeData(body)})
}

// RecordItems keeps the items of one list read. Items recorded right after another list read
// join its array, because a command that reads one list in several series of calls (one per
// type, for example) shows them as one list.
func RecordItems(items []json.RawMessage) {
	mu.Lock()
	defer mu.Unlock()
	if dest == nil {
		return
	}
	if n := len(results); n > 0 && results[n-1].list {
		results[n-1].items = append(results[n-1].items, items...)
		return
	}
	results = append(results, result{method: http.MethodGet, items: append([]json.RawMessage{}, items...), list: true})
}

func envelopeData(body []byte) json.RawMessage {
	var env struct {
		Data json.RawMessage `json:"data"`
	}
	if len(body) == 0 || json.Unmarshal(body, &env) != nil || len(env.Data) == 0 {
		return nullData
	}
	return env.Data
}
