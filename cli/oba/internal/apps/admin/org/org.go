// Package org creates the organizations of Oba and updates the profile of the current one, each
// from a JSON object checked against the live OrganizationCreateRequest or
// OrganizationUpdateRequest schema. The caller becomes the owner of each one it creates.
package org

const (
	// createSchema is the body of POST /organizations. updateSchema is the body of
	// PUT /organizations/{id}, which replaces every field.
	createSchema = "OrganizationCreateRequest"
	updateSchema = "OrganizationUpdateRequest"
)
