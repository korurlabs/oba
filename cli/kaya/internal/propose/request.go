package propose

import (
	"encoding/base64"
	"encoding/json"
	"fmt"
	"unicode/utf8"

	"github.com/korurlabs/kaya-cli/internal/client"
)

// Request is the body of POST /kaya/delivery/proposals. Text is sent as UTF-8, anything else as
// base64, so that no byte is replaced on the way. It refuses a body over Kaya's 8 MiB limit.
func Request(remoteURL string, release int, reason string, changes []Change) (client.ProposalRequest, error) {
	request := client.ProposalRequest{RemoteURL: remoteURL, ReleaseNumber: release, Reason: reason}
	for _, change := range changes {
		file := client.ProposalFile{Path: change.Path}
		switch {
		case change.Kind == Deleted:
			file.Deleted = true
		case change.Mode == client.ModeSymlink:
			file.Mode = client.ModeSymlink
			file.SymlinkTarget = change.Target
		default:
			file.Mode = change.Mode
			content := string(change.Content)
			file.Encoding = client.EncodingUTF8
			if !utf8.Valid(change.Content) {
				content = base64.StdEncoding.EncodeToString(change.Content)
				file.Encoding = client.EncodingBase64
			}
			file.Content = &content
		}
		request.Files = append(request.Files, file)
	}
	body, err := json.Marshal(request)
	if err != nil {
		return client.ProposalRequest{}, fmt.Errorf("encode the proposal: %w", err)
	}
	if len(body) > MaxBodyBytes {
		return client.ProposalRequest{}, &Refusal{
			Summary: fmt.Sprintf("The proposal is %d bytes; Kaya takes at most %d in one request.", len(body), MaxBodyBytes),
			Hint:    "Propose the files in smaller groups.",
		}
	}
	return request, nil
}

// Describe is one line per change, as the command lists them: A added, M modified, D removed,
// with a change of mode or link target.
func Describe(changes []Change) []string {
	lines := make([]string, 0, len(changes))
	for _, change := range changes {
		switch change.Kind {
		case Added:
			line := "A " + change.Path
			switch change.Mode {
			case client.ModeSymlink:
				line += " -> " + change.Target
			case client.ModeExecutable:
				line += " (executable)"
			}
			lines = append(lines, line)
		case Deleted:
			lines = append(lines, "D "+change.Path)
		default:
			line := "M " + change.Path
			if change.Base != nil && change.Base.Mode != change.Mode {
				line += fmt.Sprintf(" (%s -> %s)", change.Base.Mode, change.Mode)
			}
			if change.Mode == client.ModeSymlink {
				line += " -> " + change.Target
			}
			lines = append(lines, line)
		}
	}
	return lines
}
