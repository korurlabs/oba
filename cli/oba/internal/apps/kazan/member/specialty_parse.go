package member

import (
	"errors"
	"fmt"
	"slices"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/userinput"
)

// errNoSpecialties refuses a list that names no specialty, which would clear them all by mistake.
var errNoSpecialties = errors.New("--specialties is required")

// ParseSpecialties splits a comma-separated list and checks each value against Specialties, case
// ignored, so that a typo fails before the request instead of as a 400.
func ParseSpecialties(raw string) ([]string, error) {
	var specialties []string
	for _, part := range userinput.SplitCSV(raw) {
		value := strings.ToUpper(part)
		if !slices.Contains(Specialties, value) {
			return nil, fmt.Errorf("invalid specialty %q: use one of %s", part, strings.Join(Specialties, ", "))
		}
		specialties = append(specialties, value)
	}
	if len(specialties) == 0 {
		return nil, errNoSpecialties
	}
	return specialties, nil
}
