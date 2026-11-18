package find

import (
	"encoding/json"
	"fmt"

	"github.com/korurlabs/oba-cli/internal/apps/otak/contact"
	"github.com/korurlabs/oba-cli/internal/apps/otak/relation"
)

// Decode reads the candidates from the final output of Claude Code, with duplicates dropped
// (see Dedupe) and each candidate's contacts cleaned (see contact.Clean), so that the pick list
// and the creates use the same contacts.
func Decode(raw json.RawMessage) ([]Candidate, error) {
	var out output
	if err := json.Unmarshal(raw, &out); err != nil {
		return nil, fmt.Errorf("the output of Claude Code is not a candidate list: %w", err)
	}
	for i := range out.Candidates {
		out.Candidates[i].Contacts = contact.Clean(out.Candidates[i].Website, out.Candidates[i].Contacts)
	}
	return Dedupe(out.Candidates), nil
}

// Dedupe keeps the first candidate of each company, so that one run cannot offer, and create,
// the same company twice. A company is its website host (no scheme, "www.", path or case), else
// its name with case and spacing ignored. A candidate with neither is kept.
func Dedupe(candidates []Candidate) []Candidate {
	seen := make(map[string]bool, len(candidates))
	unique := make([]Candidate, 0, len(candidates))
	for _, candidate := range candidates {
		key := candidateKey(candidate)
		if key != "" {
			if seen[key] {
				continue
			}
			seen[key] = true
		}
		unique = append(unique, candidate)
	}
	return unique
}

// candidateKey prefixes the host and the name so that a name never equals a host.
func candidateKey(candidate Candidate) string {
	if host := relation.WebsiteHost(candidate.Website); host != "" {
		return "host:" + host
	}
	if name := relation.NormalizeName(candidate.Name); name != "" {
		return "name:" + name
	}
	return ""
}
