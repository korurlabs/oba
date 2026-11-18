package ticket

import (
	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// DeleteResult is the outcome for one ticket of bulk-delete, as --json prints it.
type DeleteResult struct {
	Key    string  `json:"key"`
	OK     bool    `json:"ok"`
	Error  string  `json:"error,omitempty"`
	target *Target `json:"-"`
}

// ResolveForDelete resolves every ticket refs names before anything is deleted, so that the
// confirmation counts only tickets that exist. A ticket that does not resolve is a failed result,
// reported like a failed delete.
func ResolveForDelete(r *Resolver, refs []string) []DeleteResult {
	results := make([]DeleteResult, len(refs))
	for i, ref := range refs {
		results[i].Key = ref
		target, err := r.Ticket(ref)
		if err != nil {
			results[i].Error = err.Error()
			continue
		}
		results[i].target = &target
	}
	return results
}

// Resolved counts the results whose ticket was found.
func Resolved(results []DeleteResult) int {
	n := 0
	for _, res := range results {
		if res.target != nil {
			n++
		}
	}
	return n
}

// DeleteResolved deletes each resolved ticket with its own call on its own board, so that one
// failure does not stop the others; each failure is kept in its result. It returns how many
// tickets could not be deleted, unresolved ones included.
func DeleteResolved(c *client.Client, results []DeleteResult) int {
	failed := 0
	for i := range results {
		res := &results[i]
		if res.target == nil {
			failed++
			continue
		}
		if err := c.DeleteTicket(res.target.BoardID, res.target.ID); err != nil {
			res.Error = err.Error()
			failed++
			continue
		}
		res.OK = true
	}
	return failed
}
