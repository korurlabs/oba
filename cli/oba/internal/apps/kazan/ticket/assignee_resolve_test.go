package ticket

import (
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

func boardAgent(userID, first, last string) client.BoardMember {
	return client.BoardMember{UserID: userID, FirstName: first, LastName: last,
		UserKindFields: client.UserKindFields{Kind: client.UserKindAgent}}
}

func TestFindAgentMatchesTheNameWithoutRegardToCase(t *testing.T) {
	members := []client.BoardMember{
		{UserID: "u-ada", FirstName: "Scout", LastName: "Bot"},
		boardAgent("u-scout", "Scout", "Bot"),
		boardAgent("u-ranger", "Ranger", ""),
	}

	got, err := findAgent(members, "  scout   BOT ")

	if err != nil || got.UserID != "u-scout" {
		t.Fatalf("got %v, err %v", got.UserID, err)
	}
}

func TestFindAgentLeavesPeopleOut(t *testing.T) {
	members := []client.BoardMember{{UserID: "u-ada", FirstName: "Ada", LastName: "Lovelace"}}

	_, err := findAgent(members, "Ada Lovelace")

	if err == nil || !strings.Contains(err.Error(), "the board has no agents") ||
		!strings.Contains(err.Error(), "name a person by email or user id") {
		t.Fatalf("err = %v", err)
	}
	if code := client.ExitCode(err); code != 2 {
		t.Errorf("exit code = %d", code)
	}
}

func TestFindAgentUnknownNameListsTheAgents(t *testing.T) {
	members := []client.BoardMember{boardAgent("u-scout", "Scout", ""), boardAgent("u-ranger", "Ranger", "")}

	_, err := findAgent(members, "Scot")

	if err == nil || !strings.Contains(err.Error(), `no agent named "Scot"; agents on the board: Scout (u-scout), Ranger (u-ranger)`) {
		t.Fatalf("err = %v", err)
	}
}

func TestFindAgentSharedNameListsTheMatches(t *testing.T) {
	members := []client.BoardMember{boardAgent("u-1", "Scout", ""), boardAgent("u-2", "scout", ""),
		boardAgent("u-3", "Ranger", "")}

	_, err := findAgent(members, "Scout")

	if err == nil || !strings.Contains(err.Error(), `2 agents on the board are named "Scout": Scout (u-1), scout (u-2); name one by its user id`) {
		t.Fatalf("err = %v", err)
	}
	if strings.Contains(err.Error(), "Ranger") {
		t.Errorf("err lists an agent that does not match: %v", err)
	}
}

func TestNamesPersonTellsAnEmailOrIDFromAName(t *testing.T) {
	cases := map[string]bool{
		"a@b.c":                                true,
		"0e000000-0000-4000-8000-000000000001": true,
		"Scout":                                false,
		"Scout Bot":                            false,
	}
	for ref, want := range cases {
		if got := namesPerson(ref); got != want {
			t.Errorf("namesPerson(%q) = %v", ref, got)
		}
	}
}
