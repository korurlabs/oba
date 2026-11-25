package cyberscore

import "github.com/korurlabs/oba-cli/internal/dojo/client"

// SetOutcome records outcome on the check arg names and returns the check with the status the
// API derived from it. A value that is not an outcome is refused before any call.
func SetOutcome(c *client.Client, arg, outcome string) (client.CyberscoreCheck, error) {
	parsed, err := ParseOutcome(outcome)
	if err != nil {
		return client.CyberscoreCheck{}, err
	}
	ch, err := ResolveCheck(c, arg)
	if err != nil {
		return client.CyberscoreCheck{}, err
	}
	return c.SetCyberscoreCheckOutcome(ch.ID, parsed)
}
