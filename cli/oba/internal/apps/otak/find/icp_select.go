package find

import (
	"bufio"
	"encoding/json"
	"fmt"
	"io"
	"net/url"
	"strconv"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
)

// ListIcps reads every ICP of the organization.
func ListIcps(c *client.Client) ([]client.Icp, error) {
	return client.ListPages[client.Icp](c, icpsPath, url.Values{})
}

// GetIcp reads one ICP with its description.
func GetIcp(c *client.Client, id string) (client.Icp, error) {
	body, err := c.Get(icpsPath + "/" + url.PathEscape(id))
	if err != nil {
		return client.Icp{}, err
	}
	var resp client.APIResponse[client.Icp]
	if err := json.Unmarshal(body, &resp); err != nil {
		return client.Icp{}, fmt.Errorf("failed to parse ICP %s: %w", id, err)
	}
	return resp.Data, nil
}

// MatchIcp finds the ICP a user named: the id exactly first, then the name without regard to
// case. A name several ICPs share is refused rather than guessed.
func MatchIcp(icps []client.Icp, value string) (client.Icp, error) {
	value = strings.TrimSpace(value)
	for _, icp := range icps {
		if icp.ID == value {
			return icp, nil
		}
	}
	var matches []client.Icp
	for _, icp := range icps {
		if strings.EqualFold(icp.Name, value) {
			matches = append(matches, icp)
		}
	}
	switch len(matches) {
	case 0:
		return client.Icp{}, fmt.Errorf("no ICP matches %q; run 'oba otak find' without --icp to pick from the list", value)
	case 1:
		return matches[0], nil
	default:
		ids := make([]string, 0, len(matches))
		for _, icp := range matches {
			ids = append(ids, icp.ID)
		}
		return client.Icp{}, fmt.Errorf("several ICPs are named %q: %s; pass --icp <id>", value, strings.Join(ids, ", "))
	}
}

// PromptIcp lists the ICPs as numbered rows, one per ICP in rows (the caller renders them, so
// that every command shows an ICP alike), and asks for a number until it gets a valid one; an
// empty answer or the end of input cancels. src is the reader under in: input waiting before
// each ask is discarded, so that nothing typed ahead picks an ICP.
func PromptIcp(in *bufio.Reader, src io.Reader, out io.Writer, icps []client.Icp, rows []string) (client.Icp, bool, error) {
	for i, row := range rows {
		_, _ = fmt.Fprintf(out, "  %d. %s\n", i+1, row)
	}
	for {
		line, eof, err := terminal.Ask(in, src, out, "> ")
		if err != nil {
			return client.Icp{}, false, err
		}
		if line == "" {
			return client.Icp{}, false, nil
		}
		number, convErr := strconv.Atoi(line)
		if convErr == nil && number >= 1 && number <= len(icps) {
			return icps[number-1], true, nil
		}
		_, _ = fmt.Fprintf(out, "Enter a number from 1 to %d.\n", len(icps))
		if eof {
			return client.Icp{}, false, nil
		}
	}
}
