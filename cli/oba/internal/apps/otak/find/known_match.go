package find

import (
	"github.com/korurlabs/oba-cli/internal/apps/otak/relation"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// KnownCompanies indexes the organization's relations by normalized name and by website host,
// so that a candidate Claude Code proposes can be checked against them. Each key maps to the
// name of the relation as stored, which the skipped mark shows.
type KnownCompanies struct {
	byName map[string]string
	byHost map[string]string
}

// NewKnownCompanies indexes the given relations.
func NewKnownCompanies(relations []client.RelationListItem) KnownCompanies {
	known := KnownCompanies{
		byName: make(map[string]string, len(relations)),
		byHost: make(map[string]string, len(relations)),
	}
	for _, item := range relations {
		if name := relation.NormalizeName(item.Name); name != "" {
			if _, ok := known.byName[name]; !ok {
				known.byName[name] = item.Name
			}
		}
		if item.Website == nil {
			continue
		}
		if host := relation.WebsiteHost(*item.Website); host != "" {
			if _, ok := known.byHost[host]; !ok {
				known.byHost[host] = item.Name
			}
		}
	}
	return known
}

// Match returns the name of the known relation the candidate duplicates, by name first, then
// by website host; ok is false for a new company.
func (k KnownCompanies) Match(name, website string) (string, bool) {
	if key := relation.NormalizeName(name); key != "" {
		if existing, ok := k.byName[key]; ok {
			return existing, true
		}
	}
	if host := relation.WebsiteHost(website); host != "" {
		if existing, ok := k.byHost[host]; ok {
			return existing, true
		}
	}
	return "", false
}
