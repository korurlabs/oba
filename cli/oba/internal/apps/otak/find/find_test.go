package find

func strPtr(s string) *string { return &s }

func candidate(name, website, country, reason string) Candidate {
	return Candidate{Name: name, Website: website, Country: country, Reason: reason}
}
