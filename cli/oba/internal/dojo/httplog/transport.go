package httplog

import (
	"fmt"
	"io"
	"net/http"
	"time"
)

// Transport is the http.RoundTripper of every client of the CLI. It sends through
// http.DefaultTransport and, while logging is on, writes the request and the response.
var Transport http.RoundTripper = transport{}

type transport struct{}

func (transport) RoundTrip(req *http.Request) (*http.Response, error) {
	w := writer()
	if w == nil {
		return http.DefaultTransport.RoundTrip(req)
	}
	logRequest(w, req)
	start := time.Now()
	resp, err := http.DefaultTransport.RoundTrip(req)
	elapsed := time.Since(start).Round(time.Millisecond)
	if err != nil {
		_, _ = fmt.Fprintf(w, "< failed after %s: %v\n\n", elapsed, err)
		return nil, err
	}
	logResponse(w, resp, elapsed)
	return resp, nil
}

func logRequest(w io.Writer, req *http.Request) {
	_, _ = fmt.Fprintf(w, "> %s %s\n", req.Method, redactURL(req.URL))
	writeHeaders(w, ">", req.Header)
	if req.ContentLength > 0 {
		_, _ = fmt.Fprintf(w, "> (body %d bytes)\n", req.ContentLength)
	}
}

func logResponse(w io.Writer, resp *http.Response, elapsed time.Duration) {
	_, _ = fmt.Fprintf(w, "< %s (%s)\n", resp.Status, elapsed)
	writeHeaders(w, "<", resp.Header)
	_, _ = fmt.Fprintln(w)
}
