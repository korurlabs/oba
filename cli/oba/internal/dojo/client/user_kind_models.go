package client

// User kinds the API sends in the kind field of a member or an assignee.
const (
	UserKindHuman = "HUMAN"
	UserKindAgent = "AGENT"
)

// UserKindFields tell a person from an agent on a member or assignee response. Kind is HUMAN or
// AGENT; AgentRole, AgentFlow and AgentProvider are set for an agent and nil for a person. Kind
// is empty from an API that predates agents, where every user is a person.
type UserKindFields struct {
	Kind          string  `json:"kind"`
	AgentRole     *string `json:"agentRole"`
	AgentFlow     *string `json:"agentFlow"`
	AgentProvider *string `json:"agentProvider"`
}

// IsAgent reports whether the user is an agent.
func (k UserKindFields) IsAgent() bool {
	return k.Kind == UserKindAgent
}

// KindOrHuman is Kind, or HUMAN when the API sent none, since only an API with agents sends it.
func (k UserKindFields) KindOrHuman() string {
	if k.Kind == "" {
		return UserKindHuman
	}
	return k.Kind
}
