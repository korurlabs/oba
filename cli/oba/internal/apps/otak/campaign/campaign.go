// Package campaign lists, creates, updates and deletes the campaigns of Otak: the umbrellas of
// the organization's related posts and mailings, with a name, description, date range, owner,
// event and ICP. Its contents are in the subpackage content. A create or update takes a JSON
// object checked against the live CampaignCreateRequest or CampaignUpdateRequest schema.
package campaign

// createSchema is the body of a create, updateSchema the body of an update, which replaces every
// field.
const (
	createSchema = "CampaignCreateRequest"
	updateSchema = "CampaignUpdateRequest"
)
