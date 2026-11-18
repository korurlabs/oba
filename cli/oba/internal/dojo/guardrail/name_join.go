package guardrail

// PersonName is a person's "first last" on one terminal-safe line, else fallback (such as the
// e-mail or a placeholder) when both names are blank. It is empty only when fallback is too.
func PersonName(first, last, fallback string) string {
	if name := OneLine(first + " " + last); name != "" {
		return name
	}
	return OneLine(fallback)
}
