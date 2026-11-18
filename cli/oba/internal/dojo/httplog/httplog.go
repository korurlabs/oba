// Package httplog writes one block per HTTP request and response to stderr while --debug is on.
// Every HTTP client of the CLI (the API, the sign-in, the API document and signed downloads)
// sends through Transport, so that one flag shows all traffic. Credentials never reach the log.
package httplog

import (
	"io"
	"sync"
)

var (
	mu  sync.Mutex
	out io.Writer
)

// Enable starts logging to w; nil stops it.
func Enable(w io.Writer) {
	mu.Lock()
	defer mu.Unlock()
	out = w
}

func writer() io.Writer {
	mu.Lock()
	defer mu.Unlock()
	return out
}
