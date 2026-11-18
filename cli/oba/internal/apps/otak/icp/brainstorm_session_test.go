package icp

import (
	"strings"
	"testing"
)

func TestBrainstormBriefHoldsConversation(t *testing.T) {
	b := NewBrainstorm(testFields, BrainstormContext{})
	b.AddUser(" We sell audits. ")
	b.AddReply(Reply{Message: "Which region?", Questions: []string{"Which region?"}})
	b.AddUser("Benelux")
	b.AddCreated("Benelux banks")
	brief := b.Brief()
	want := "Conversation so far, oldest first:\n\n[User]\nWe sell audits.\n\n[You]\nMessage: Which region?\n" +
		"Question 1: Which region?\n\n[User]\nBenelux\n\n[Note]\nThe user created the ICP \"Benelux banks\".\n"
	if !strings.HasSuffix(brief, want) || b.Turns() != 1 {
		t.Errorf("turns %d brief:\n%s", b.Turns(), brief)
	}
}
