package contact

import (
	"strings"
	"unicode/utf8"

	"github.com/korurlabs/oba-cli/internal/apps/otak/relation"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// The API's limits on a contact (ContactCreateRequest). A source URL shares the rule of a website.
const (
	nameMaxRunes   = 200
	roleMaxRunes   = 100
	emailMaxRunes  = 254
	phoneMaxRunes  = 50
	sourceMaxBytes = 500
)

// Clean keeps the found contacts of a company that the API would accept and that come from the
// company itself, so that what a pick list shows is exactly what is created. website is the
// company's own site. Every value is sanitized onto one line. A contact without a name, with a
// name over the API's limit, or without a valid source URL is dropped; a role over the limit is
// nulled; an e-mail address is kept only on the company's own domain (or a subdomain of it, or
// it a subdomain of the address's), since an address elsewhere may be private; a phone number
// is kept only when it holds digits and phone punctuation alone. A name repeated (case ignored)
// keeps its first contact, and at most MaxFound remain.
func Clean(website string, contacts []FoundContact) []FoundContact {
	host := relation.WebsiteHost(website)
	seen := make(map[string]bool, len(contacts))
	var kept []FoundContact
	for _, contact := range contacts {
		if len(kept) == MaxFound {
			break
		}
		name := guardrail.OneLine(contact.Name)
		if name == "" || utf8.RuneCountInString(name) > nameMaxRunes {
			continue
		}
		source := guardrail.OneLine(contact.SourceURL)
		if len(source) > sourceMaxBytes || !relation.ValidWebsite(source) {
			continue
		}
		key := relation.NormalizeName(name)
		if seen[key] {
			continue
		}
		seen[key] = true
		kept = append(kept, FoundContact{
			Name:      name,
			Role:      cleanRole(contact.Role),
			Email:     cleanEmail(contact.Email, host),
			Phone:     cleanPhone(contact.Phone),
			SourceURL: source,
		})
	}
	return kept
}

func cleanRole(role *string) *string {
	value := guardrail.OneLine(orEmpty(role))
	if value == "" || utf8.RuneCountInString(value) > roleMaxRunes {
		return nil
	}
	return &value
}

// cleanEmail keeps an address with one "@" whose domain is the company's website host, a
// subdomain of it, or its parent domain. Without a website host no address can be checked.
func cleanEmail(email *string, host string) *string {
	value := guardrail.OneLine(orEmpty(email))
	if value == "" || host == "" || utf8.RuneCountInString(value) > emailMaxRunes ||
		strings.ContainsRune(value, ' ') || strings.Count(value, "@") != 1 {
		return nil
	}
	local, domain, _ := strings.Cut(value, "@")
	domain = strings.ToLower(domain)
	if local == "" || domain == "" {
		return nil
	}
	if domain != host && !strings.HasSuffix(domain, "."+host) && !strings.HasSuffix(host, "."+domain) {
		return nil
	}
	return &value
}

// cleanPhone keeps a number of digits, spaces and the characters +()-./ that holds a digit.
func cleanPhone(phone *string) *string {
	value := guardrail.OneLine(orEmpty(phone))
	if value == "" || utf8.RuneCountInString(value) > phoneMaxRunes {
		return nil
	}
	hasDigit := false
	for _, r := range value {
		switch {
		case r >= '0' && r <= '9':
			hasDigit = true
		case r == ' ' || strings.ContainsRune("+()-./", r):
		default:
			return nil
		}
	}
	if !hasDigit {
		return nil
	}
	return &value
}
