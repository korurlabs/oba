package contact

// PickedContacts returns the contacts of the picked numbers (1-based, numbered as
// ShowFillResults numbers them), grouped per relation in the order of results.
func PickedContacts(results []FillResult, picks []int) []FillResult {
	picked := make(map[int]bool, len(picks))
	for _, number := range picks {
		picked[number] = true
	}
	var grouped []FillResult
	number := 0
	for _, result := range results {
		var contacts []FoundContact
		for _, contact := range result.Contacts {
			number++
			if picked[number] {
				contacts = append(contacts, contact)
			}
		}
		if len(contacts) > 0 {
			grouped = append(grouped, FillResult{Target: result.Target, Contacts: contacts})
		}
	}
	return grouped
}
