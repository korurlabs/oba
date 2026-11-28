// Package content lists, creates, updates and deletes the contents of an Otak campaign: each one
// thing actually shared, such as one LinkedIn post, with one channel, one language, one text and
// the legal entity that publishes it. A create or update takes a JSON object checked against the
// live CampaignContentRequest schema, which serves both.
package content

const (
	// requestSchema is the body of a create and of an update, which replaces every field.
	requestSchema = "CampaignContentRequest"

	// fieldLegalEntityID is the legal entity that publishes the content; --legal-entity sets it.
	fieldLegalEntityID = "legalEntityId"
)
