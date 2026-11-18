// Package project lists, reads, creates, changes and deletes the projects of a Kazan board. A
// project groups tickets of one board and has a name and a color only.
package project

import (
	"errors"
	"strings"
)

// errNoProject refuses an empty project id before any call, which would address the list route.
var errNoProject = errors.New("no project given; name the project by its id")

func checkID(id string) (string, error) {
	id = strings.TrimSpace(id)
	if id == "" {
		return "", errNoProject
	}
	return id, nil
}

// color is the project's color, empty when it has none.
func color(p *string) string {
	if p == nil {
		return ""
	}
	return *p
}
