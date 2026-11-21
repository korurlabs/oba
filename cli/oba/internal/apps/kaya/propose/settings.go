package propose

import (
	"bytes"
	"encoding/json"
	"fmt"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/delivery"
	"github.com/korurlabs/oba-cli/internal/dojo/jsonedit"
)

// withoutMergedDeny removes from the checkout's .claude/settings.json the deny rules the CLI
// merged in from the release's guardrails, so that a proposal never writes them into the
// settings file itself: a guardrail rule belongs to its guardrail file and ends when that
// guardrail changes, not through an unrelated settings change. A rule the delivered settings file
// itself denies stays. Content that is not a JSON object is returned unchanged, for the settings
// check to refuse.
func withoutMergedDeny(content, base []byte, guardrails []string) ([]byte, error) {
	if len(guardrails) == 0 {
		return content, nil
	}
	merged, err := delivery.MergeDeny(base, guardrails...)
	if err != nil {
		return nil, err
	}
	if bytes.Equal(content, merged) {
		return base, nil
	}
	settings, err := jsonedit.Parse(content)
	// rule-10-no-op -- reason: settings that are not a JSON object are sent as written, and the settings check refuses them with the parse error
	if err != nil {
		return content, nil
	}
	baseDeny, baseHasPermissions, baseHasDeny, err := denyRules(base)
	if err != nil {
		return nil, fmt.Errorf("%s of the release: %w", delivery.SettingsPath, err)
	}
	permissions, err := settings.Object("permissions")
	// rule-10-no-op -- reason: permissions that are not an object are the proposer's own change; Kaya validates them
	if err != nil {
		return content, nil
	}
	raw, ok := permissions.Get("deny")
	if !ok {
		return content, nil
	}
	var deny []string
	// rule-10-no-op -- reason: a deny list of another shape holds no merged rules to remove; Kaya validates it
	if err := json.Unmarshal(raw, &deny); err != nil {
		return content, nil
	}
	added := map[string]bool{}
	for _, rule := range guardrails {
		added[rule] = !baseDeny[rule]
	}
	kept := []string{}
	for _, rule := range deny {
		if !added[rule] {
			kept = append(kept, rule)
		}
	}
	if len(kept) == len(deny) {
		return content, nil
	}
	if len(kept) == 0 && !baseHasDeny {
		permissions.Delete("deny")
	} else if err := permissions.Set("deny", kept); err != nil {
		return nil, err
	}
	if permissions.Len() == 0 && !baseHasPermissions {
		settings.Delete("permissions")
	} else if err := settings.Set("permissions", permissions); err != nil {
		return nil, err
	}
	return settings.Marshal()
}

// denyRules reads permissions.deny of a settings file, and whether the file has permissions and
// a deny list at all.
func denyRules(content []byte) (rules map[string]bool, hasPermissions, hasDeny bool, err error) {
	rules = map[string]bool{}
	settings, err := jsonedit.Parse(content)
	if err != nil {
		return nil, false, false, err
	}
	if _, hasPermissions = settings.Get("permissions"); !hasPermissions {
		return rules, false, false, nil
	}
	permissions, err := settings.Object("permissions")
	if err != nil {
		return nil, false, false, err
	}
	raw, hasDeny := permissions.Get("deny")
	if !hasDeny {
		return rules, true, false, nil
	}
	var deny []string
	if err := json.Unmarshal(raw, &deny); err != nil {
		return nil, false, false, fmt.Errorf("permissions.deny is not a list of rules: %w", err)
	}
	for _, rule := range deny {
		rules[rule] = true
	}
	return rules, true, true, nil
}
