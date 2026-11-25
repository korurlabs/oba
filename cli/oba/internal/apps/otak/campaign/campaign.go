// Package campaign lists, creates, updates and deletes the campaigns of Otak: the posts and
// mailings of the organization, one per channel, with a text per language and images as
// attachments. A create or update takes a JSON object checked against the live
// CampaignCreateRequest or CampaignUpdateRequest schema.
package campaign

// createSchema is the body of a create, updateSchema the body of an update, which replaces every
// field and the whole set of texts.
const (
	createSchema = "CampaignCreateRequest"
	updateSchema = "CampaignUpdateRequest"
)
