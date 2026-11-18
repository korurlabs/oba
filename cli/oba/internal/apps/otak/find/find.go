// Package find has Claude Code search the web for new companies that fit an ICP of the
// organization, and creates the ones the user picks as leads. It reads the organization's
// relations through the client only, to keep the companies it already knows out of the search.
package find

import (
	"strings"
)

const (
	icpsPath      = "/otak/icps"
	relationsPath = "/otak/relations"
	createSchema  = "RelationCreateRequest"
	fieldType     = "type"
	fieldStage    = "stage"
	fieldCountry  = "country"
	fieldSize     = "size"

	leadType         = "LEAD"
	pipelineStageNew = "NEW"
	sourceResearch   = "RESEARCH"

	foundNotesPrefix = "Found by oba otak find: "
	// notesMaxRunes and websiteMaxLength are the API's limits on a relation's notes and website.
	notesMaxRunes    = 10000
	websiteMaxLength = 500
	// cityMaxRunes is the API's limit on a relation's city.
	cityMaxRunes = 100

	// skippedReason is why a candidate that matches a known relation cannot be picked.
	skippedReason = "is already in Otak and cannot be picked"

	pickRefine = "r"
	pickQuit   = "q"
)

func valueOr(value *string, fallback string) string {
	if value == nil || strings.TrimSpace(*value) == "" {
		return fallback
	}
	return *value
}

// optional is the value of an optional string, "" when it is unset.
func optional(value *string) string {
	if value == nil {
		return ""
	}
	return *value
}
