package icp

import (
	"encoding/json"
	"strings"
	"testing"
)

func TestDecodeReply(t *testing.T) {
	raw := json.RawMessage(`{"message":" Hi. ","questions":[" Who buys? ",""],"proposals":[` +
		`{"name":"A","industry":"OTHER"},{"name":"B","industry":"OTHER","extra":1},{"name":"C"}]}`)
	reply, err := DecodeReply(raw, testFields)
	if err != nil {
		t.Fatal(err)
	}
	if reply.Message != "Hi." || strings.Join(reply.Questions, "|") != "Who buys?" {
		t.Errorf("reply = %+v", reply)
	}
	if len(reply.Proposals) != 1 || string(reply.Proposals[0]["name"]) != `"A"` || len(reply.Skipped) != 2 {
		t.Errorf("proposals %v skipped %v", reply.Proposals, reply.Skipped)
	}
}

func TestDecodeReplyRefusesNonObject(t *testing.T) {
	for _, raw := range []string{``, `[]`, `{"message":1}`} {
		if _, err := DecodeReply(json.RawMessage(raw), testFields); err == nil {
			t.Errorf("%q: expected an error", raw)
		}
	}
}
