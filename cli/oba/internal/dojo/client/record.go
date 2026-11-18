package client

import "github.com/korurlabs/oba-cli/internal/dojo/jsonout"

// WithoutRecording keeps the responses of c out of the --json output, for a lookup the CLI
// makes for itself, such as resolving the organization --org names, whose data is not the
// command's result. It returns c.
func (c *Client) WithoutRecording() *Client {
	c.unrecorded = true
	return c
}

func (c *Client) record(method string, body []byte) {
	if !c.unrecorded {
		jsonout.Record(method, body)
	}
}

// Unrecorded returns a copy of c whose responses stay out of the --json output, for a lookup
// such as resolving a board name, so that c itself keeps recording the command's result.
func (c *Client) Unrecorded() *Client {
	cp := *c
	cp.unrecorded = true
	return &cp
}
