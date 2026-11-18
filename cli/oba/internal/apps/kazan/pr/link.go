package pr

import (
	"net/url"
	"os"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// EnvWebURL names the origin of the Kazan web app the share links open. Neither the API nor the
// sign-in names the web app, and its host differs from the API's, so without it no link is
// printed.
const EnvWebURL = "OBA_KAZAN_WEB_URL"

// webURL is EnvWebURL without a trailing slash, and false when it is not set.
func webURL() (string, bool) {
	v := strings.TrimRight(strings.TrimSpace(os.Getenv(EnvWebURL)), "/")
	return v, v != ""
}

// Link is the web app link that opens the share's card in the PR channel, the format of the
// app's "Copy link". A card always sits in the organization's single PR channel, which the app
// serves at /pull-requests, so the link names only the message. It is false when the card is
// gone or EnvWebURL is not set.
func Link(s client.PullRequestShare) (string, bool) {
	if s.ChannelID == nil || *s.ChannelID == "" || s.MessageID == nil || *s.MessageID == "" {
		return "", false
	}
	base, ok := webURL()
	if !ok {
		return "", false
	}
	return base + "/pull-requests?jump=" + url.QueryEscape(*s.MessageID), true
}
