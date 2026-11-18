package pr

import (
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

func TestLink(t *testing.T) {
	channel, message := "ch", "m 1"
	share := client.PullRequestShare{ChannelID: &channel, MessageID: &message}

	t.Setenv(EnvWebURL, "")
	if got, ok := Link(share); ok {
		t.Errorf("a link without %s: %q", EnvWebURL, got)
	}
	t.Setenv(EnvWebURL, "http://localhost:3002/")
	if got, ok := Link(share); !ok || got != "http://localhost:3002/pull-requests?jump=m+1" {
		t.Errorf("got %q, %v", got, ok)
	}
	if _, ok := Link(client.PullRequestShare{ChannelID: &channel}); ok {
		t.Error("link without a message")
	}
}
