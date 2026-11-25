package propose

import (
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"sort"
	"strings"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/client"
)

// Error codes POST /kaya/delivery/proposals answers.
const (
	codeValidation      = "KAY_CFG_001"
	codeStale           = "KAY_CFG_002"
	codePathInTwoOwners = "KAY_CFG_003"
	codeInReview        = "KAY_CFG_015"
	codeUnchanged       = "KAY_CFG_016"
	codeSpansOwners     = "KAY_CFG_019"
	codeNotFound        = "KAY_NF_001"
	codeArchived        = "KAY_VAL_002"
	codeBodyTooLarge    = "KAY_VAL_012"
	codeTokenRefused    = "KAY_SEC_003" // #nosec G101 -- an error code, not a credential
	codeNotMember       = "ORG_SEC_001"
	codeRoleTooLow      = "ORG_SEC_003"
	codeOrgDisabled     = "ORG_SEC_005"
	codeForbidden       = "GEN_SEC_002"
	codeOtherOrg        = "GEN_SEC_005"
	codeNoAppAccess     = "APP_SEC_001"
	codeFieldInvalid    = "GEN_VAL_001"
	codeBadRequest      = "GEN_API_001"
	keepEditsAndResync  = "Keep a copy of your edits: `oba kaya sync` (or `oba kaya refresh` while a session of this checkout runs) puts the delivered files back. Then apply them to the newer release and propose again."
)

// Explain turns Kaya's refusal of a proposal into a text that says what was refused and what to
// do next. Any other error is returned as it is.
func Explain(err error, release int) error {
	var apiErr *client.APIError
	if !errors.As(err, &apiErr) {
		return err
	}
	refusal := &Refusal{Err: err}
	values := apiErr.ErrorValues
	switch {
	case apiErr.Code == codeStale:
		refusal.Summary = fmt.Sprintf("Kaya changed these paths since release %d, so the proposal would overwrite a change you have not seen:", release)
		var conflicts []struct {
			Path          string  `json:"path"`
			DraftChange   *string `json:"draftChange"`
			CurrentChange *string `json:"currentChange"`
		}
		decode(values["conflicts"], &conflicts)
		for _, conflict := range conflicts {
			refusal.Lines = append(refusal.Lines, fmt.Sprintf("%s: you %s, Kaya %s", conflict.Path, changeWord(conflict.DraftChange, "left it as delivered"), changeWord(conflict.CurrentChange, "kept it")))
		}
		refusal.Hint = keepEditsAndResync
	case apiErr.Code == codeValidation || apiErr.Code == codePathInTwoOwners:
		refusal.Summary = "Kaya refused the proposal; it breaks these rules:"
		refusal.Lines = violations(values)
		refusal.Hint = "Fix these paths and run oba kaya propose again."
	case apiErr.Code == codeInReview:
		refusal.Summary = "Another change of this pack is already in review" + valueSuffix(values, "inReviewVersionId", " (version %s)") + "."
		refusal.Hint = "Kaya reviews one change of a pack at a time: wait until it is approved or rejected, then propose again."
	case apiErr.Code == codeUnchanged:
		refusal.Summary = "The files equal the current version of their pack; there is nothing to propose."
	case apiErr.Code == codeSpansOwners && values["candidates"] != nil:
		var candidates []string
		decode(values["candidates"], &candidates)
		if len(candidates) == 0 {
			refusal.Summary = "This repository uses no pack at its root in Kaya, so Kaya has nowhere to put a new path."
		} else {
			refusal.Summary = "Several packs at the root of this repository could take a new path, and Kaya does not choose one:"
			for _, id := range candidates {
				refusal.Lines = append(refusal.Lines, "pack "+id)
			}
		}
		refusal.Hint = "Add the new path in the pack's draft in the Kaya web app, or propose changes of delivered paths only."
	case apiErr.Code == codeSpansOwners:
		refusal.Summary = "A proposal changes the paths of one pack; these paths come from several:"
		var owners map[string][]string
		decode(values["owners"], &owners)
		ids := make([]string, 0, len(owners))
		for id := range owners {
			ids = append(ids, id)
		}
		sort.Strings(ids)
		for _, id := range ids {
			group := append([]string{}, owners[id]...)
			sort.Strings(group)
			refusal.Lines = append(refusal.Lines, fmt.Sprintf("owner %s: oba kaya propose --reason <why> %s", id, strings.Join(group, " ")))
		}
		refusal.Hint = "Propose each group on its own, with the commands above."
	case apiErr.Code == codeNotFound && values["release"] != nil:
		refusal.Summary = fmt.Sprintf("Kaya knows no release %d of this repository.", release)
		refusal.Hint = "Run `oba kaya sync` online and propose again."
	case apiErr.Code == codeNotFound && values["remoteUrl"] != nil:
		refusal.Summary = "Kaya knows no repository" + valueSuffix(values, "remoteUrl", " %s") + " in this organization."
		refusal.Hint = "Run `oba kaya sync`, which refreshes the repository list, then check which remote `oba kaya status` names, and the organization of your machine token."
	case apiErr.Code == codeNotFound:
		refusal.Summary = "Kaya did not find what the proposal names: " + apiErr.Message
		refusal.Lines = valueLines(values)
	case apiErr.Code == codeArchived:
		refusal.Summary = "The repository is archived in Kaya; it takes no config change."
	case apiErr.Code == codeForbidden && values["status"] != nil:
		refusal.Summary = "The Kaya user behind your machine token is not active" + valueSuffix(values, "status", " (%s)") + ", and only an active user proposes."
		refusal.Hint = "Ask an ADMIN of Kaya to enable your user."
	case apiErr.Code == codeOtherOrg:
		refusal.Summary = "The request named another organization" + valueSuffix(values, "OrganizationId", " (%s)") + " than the one your machine token belongs to, and a machine token acts only in its own organization."
		refusal.Hint = "Propose with the machine token of that organization, or register this machine in it and store the new token."
	case apiErr.Code == codeNotMember:
		refusal.Summary = "The user behind your machine token is not a member of this organization in Kaya, or its membership is not active."
		refusal.Hint = "Ask an ADMIN of the organization to add you, or to enable your membership."
	case apiErr.Code == codeRoleTooLow:
		refusal.Summary = "The user behind your machine token is a GUEST of this organization, and only a MEMBER or above proposes."
		refusal.Hint = "Ask an ADMIN of the organization to give you the MEMBER role."
	case apiErr.Code == codeOrgDisabled:
		refusal.Summary = "The organization is disabled, so it takes no proposal."
		refusal.Hint = "Ask an OWNER of the organization to reactivate it."
	case apiErr.Code == codeNoAppAccess:
		refusal.Summary = "The user behind your machine token is a member of this organization but has no access to Kaya, and only a user with Kaya access proposes."
		refusal.Hint = "Ask an ADMIN of the organization to grant you Kaya in the Ulak web app."
	case apiErr.Code == codeTokenRefused || apiErr.Status == http.StatusUnauthorized:
		refusal.Summary = "Kaya refused the machine token (KAYA_RECORDER_TOKEN): it is unknown, revoked or expired, or its machine is retired."
		refusal.Hint = "Register the machine again in Kaya and store the new token."
	case apiErr.Code == codeBodyTooLarge || apiErr.Status == http.StatusRequestEntityTooLarge:
		refusal.Summary = "The proposal is larger than Kaya takes in one request (8 MiB)."
		refusal.Hint = "Propose the files in smaller groups."
	case apiErr.Code == codeFieldInvalid:
		refusal.Summary = "Kaya refused a field of the proposal: " + apiErr.Message
		refusal.Lines = valueLines(values)
	case apiErr.Code == codeBadRequest:
		refusal.Summary = "Kaya did not accept the request: " + apiErr.Message
		refusal.Lines = valueLines(values)
		refusal.Hint = "Check which remote `oba kaya status` names for the checkout."
	default:
		return err
	}
	return refusal
}

func changeWord(change *string, none string) string {
	if change == nil {
		return none
	}
	switch Kind(*change) {
	case Added:
		return "added it"
	case Modified:
		return "changed it"
	case Deleted:
		return "removed it"
	}
	return strings.ToLower(*change)
}

func violations(values map[string]any) []string {
	var list []struct {
		Path    string `json:"path"`
		Rule    string `json:"rule"`
		Message string `json:"message"`
	}
	decode(values["violations"], &list)
	lines := make([]string, 0, len(list))
	for _, v := range list {
		line := v.Rule + ": " + v.Message
		if v.Path != "" {
			line = v.Path + ": " + line
		}
		lines = append(lines, line)
	}
	if len(lines) == 0 {
		lines = valueLines(values)
	}
	return lines
}

// decode copies one error value into a typed shape. A value of another shape leaves out
// untouched: the refusal still names the code and message.
func decode(value any, out any) {
	if value == nil {
		return
	}
	raw, err := json.Marshal(value)
	// rule-10-no-op -- reason: an error value of an unexpected shape only drops the detail lines; the refusal still carries Kaya's code and message
	if err != nil {
		return
	}
	// rule-10-no-op -- reason: as above, the detail lines are best effort
	_ = json.Unmarshal(raw, out)
}

func valueSuffix(values map[string]any, key, format string) string {
	if value, ok := values[key].(string); ok && value != "" {
		return fmt.Sprintf(format, value)
	}
	return ""
}

// valueLines prints error values that have no text of their own, one key each.
func valueLines(values map[string]any) []string {
	keys := make([]string, 0, len(values))
	for key := range values {
		keys = append(keys, key)
	}
	sort.Strings(keys)
	lines := make([]string, 0, len(keys))
	for _, key := range keys {
		raw, err := json.Marshal(values[key])
		if err != nil {
			raw = []byte(fmt.Sprint(values[key]))
		}
		lines = append(lines, key+": "+string(raw))
	}
	return lines
}
