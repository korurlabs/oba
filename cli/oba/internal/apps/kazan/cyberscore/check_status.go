package cyberscore

import "github.com/korurlabs/oba-cli/internal/dojo/client"

// SetStatus sets the status of the check arg names, and nothing else, and returns the check. A
// value that is not a status is refused before any call.
func SetStatus(c *client.Client, arg, status string) (client.CyberscoreCheck, error) {
	parsed, err := ParseStatus(status)
	if err != nil {
		return client.CyberscoreCheck{}, err
	}
	ch, err := ResolveCheck(c, arg)
	if err != nil {
		return client.CyberscoreCheck{}, err
	}
	return c.SetCyberscoreCheckStatus(ch.ID, parsed)
}
