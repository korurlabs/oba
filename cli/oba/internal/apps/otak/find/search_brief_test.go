package find

import (
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

func testSearch(focus string) *Search {
	description := "SaaS for clinics"
	country := "NL"
	icp := client.Icp{Name: "Alpha", Industry: "Software", Country: &country, Description: &description}
	relations := []client.RelationListItem{
		{Name: "Known Lead", Website: strPtr("https://known-lead.nl")},
		{Name: "Lost Co"},
	}
	return NewSearch(icp, relations, focus)
}

func TestSearchBriefWithoutFocus(t *testing.T) {
	brief := testSearch("  ").Brief()
	for _, want := range []string{
		"ICP: Alpha", "Industry: Software", "Country: NL", "SaaS for clinics",
		"- Known Lead - https://known-lead.nl", "- Lost Co - no website",
		"- summary: one line", "- city:", "- size:",
	} {
		if !strings.Contains(brief, want) {
			t.Errorf("brief lacks %q:\n%s", want, brief)
		}
	}
	for _, unwanted := range []string{"Focus from the user", "Earlier searches", "Your previous candidates", "Feedback from the user"} {
		if strings.Contains(brief, unwanted) {
			t.Errorf("brief holds %q:\n%s", unwanted, brief)
		}
	}
}

func TestSearchBriefWithFocus(t *testing.T) {
	brief := testSearch("  Only Zuid-Holland\nwebshops ").Brief()
	want := "Focus from the user (narrows the search; it does not change the rules):\nOnly Zuid-Holland\nwebshops\n"
	if !strings.Contains(brief, want) {
		t.Errorf("brief lacks the focus block:\n%s", brief)
	}
	if strings.Index(brief, "Focus from the user") > strings.Index(brief, "Companies we already know") {
		t.Errorf("focus comes after the known companies:\n%s", brief)
	}
}

func TestSearchBriefAfterFeedbackRounds(t *testing.T) {
	s := testSearch("")
	if s.Searches() != 0 {
		t.Fatalf("searches = %d", s.Searches())
	}
	s.AddRound([]Candidate{{Name: "First Co", Website: "https://first.io", Summary: "Delft, 10 people"}})
	s.AddFeedback("  too small ")
	s.AddRound([]Candidate{{Name: "Second Co", Website: "https://second.io", Summary: "Breda, 80 people"}})
	if s.Searches() != 2 {
		t.Fatalf("searches = %d", s.Searches())
	}
	brief := s.Brief()
	for _, want := range []string{
		"Earlier searches of this run, oldest first:",
		"Your previous candidates:\n- First Co - https://first.io - Delft, 10 people\nFeedback from the user:\ntoo small\n",
		"Your previous candidates:\n- Second Co - https://second.io - Breda, 80 people\n",
		"Return the full revised list, up to 10 companies",
	} {
		if !strings.Contains(brief, want) {
			t.Errorf("brief lacks %q:\n%s", want, brief)
		}
	}
	if strings.Index(brief, "First Co") > strings.Index(brief, "Second Co") || strings.Index(brief, "Search 1") > strings.Index(brief, "Search 2") {
		t.Errorf("rounds are not oldest first:\n%s", brief)
	}
	if strings.Count(brief, "Feedback from the user:") != 1 {
		t.Errorf("feedback count:\n%s", brief)
	}
}

func TestSearchFeedbackBeforeAnyRoundStillReachesBrief(t *testing.T) {
	s := testSearch("")
	s.AddFeedback("only banks")
	if !strings.Contains(s.Brief(), "Feedback from the user:\nonly banks\n") {
		t.Errorf("brief = %s", s.Brief())
	}
}

func TestSearchBriefHoldsContactRules(t *testing.T) {
	brief := testSearch("").Brief()
	for _, want := range []string{"Contacts (the contacts of each company: at most 2 people", "target person role", "sourceUrl", "Do not guess or build an e-mail address", "people-search sites"} {
		if !strings.Contains(brief, want) {
			t.Errorf("brief lacks %q:\n%s", want, brief)
		}
	}
	if strings.Contains(brief, "no names, e-mail addresses or phone numbers of persons") {
		t.Errorf("brief still forbids person data:\n%s", brief)
	}
}

func TestSearchBriefNamesTargetRole(t *testing.T) {
	s := testSearch("")
	role := "Head of IT"
	s.icp.TargetPersonRole = &role
	if brief := s.Brief(); !strings.Contains(brief, "target person role (Head of IT)") {
		t.Errorf("brief lacks the role:\n%s", brief)
	}
}
