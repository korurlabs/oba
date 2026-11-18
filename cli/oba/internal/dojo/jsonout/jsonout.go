// Package jsonout holds the --json output of one command run. While it is on, the API client
// records the data of every successful response, and the command prints, instead of its human
// text, the data of its last call. A list read across several pages or calls counts as one
// array, so that a script gets the whole list and never one page of it.
package jsonout

import (
	"encoding/json"
	"io"
	"sync"
)

// result is the data of one call, or the items of one list read.
type result struct {
	method string
	data   json.RawMessage
	items  []json.RawMessage
	list   bool
}

var (
	mu         sync.Mutex
	dest       io.Writer
	results    []result
	writesOnly bool
	keep       bool
)

// Start turns recording on for one run; Flush writes the result to w.
func Start(w io.Writer) {
	mu.Lock()
	defer mu.Unlock()
	dest = w
	results = nil
	writesOnly = false
	keep = false
}

// Stop turns recording off and forgets what was recorded.
func Stop() {
	mu.Lock()
	defer mu.Unlock()
	dest = nil
	results = nil
	writesOnly = false
	keep = false
}

// Enabled reports whether --json is on for this run.
func Enabled() bool {
	mu.Lock()
	defer mu.Unlock()
	return dest != nil
}

// Destination is the writer the result goes to, nil when --json is off.
func Destination() io.Writer {
	mu.Lock()
	defer mu.Unlock()
	return dest
}
