package pr

// PickStatus asks for the team's status. marked, when set, is the status shown with hint, such
// as the preset from GitHub or the current one.
func PickStatus(p *Prompter, marked, hint string) (string, error) {
	options := make([]string, len(Statuses))
	for i, s := range Statuses {
		options[i] = statusLabels[s]
		if s == marked {
			options[i] += " (" + hint + ")"
		}
	}
	idx, err := p.Choose("Status", options)
	if err != nil {
		return "", err
	}
	return Statuses[idx], nil
}
