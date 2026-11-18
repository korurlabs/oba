// Package icp lists, creates, updates and deletes the ICPs (Ideal Customer Profiles) of Otak.
// A create or update takes a JSON object checked against the live IcpCreateRequest or
// IcpUpdateRequest schema; a create can also start from a draft Claude Code writes from the
// user's description, or from a proposal of a brainstorm with Claude Code.
package icp

// createSchema is the body of a create, updateSchema the body of an update, which replaces every
// field.
const (
	createSchema = "IcpCreateRequest"
	updateSchema = "IcpUpdateRequest"
)
