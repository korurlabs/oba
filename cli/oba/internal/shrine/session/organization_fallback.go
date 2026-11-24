package session

import "strings"

// UseOrganizationUnlessSet makes one run act in the organization value names, by id or by name,
// only when --org left the override empty, so that a value from a project file never wins over
// the flag. An empty value changes nothing.
func UseOrganizationUnlessSet(value string) {
	value = strings.TrimSpace(value)
	if value == "" {
		return
	}
	overrideMu.Lock()
	defer overrideMu.Unlock()
	if override == "" {
		override = value
	}
}
