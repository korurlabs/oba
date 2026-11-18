package pr

import (
	"fmt"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/shrine/organization"
)

// applyReviewers applies --reviewers, or --remove-reviewers then --add-reviewers. Removing
// someone who is not a reviewer, adding someone who already is, and a result over the limit are
// refused before any write.
func (e *Edit) applyReviewers(c *client.Client, f EditFlags, me MeFunc) error {
	if f.ReplaceReviewer {
		ids, err := ResolveReviewers(c, f.Reviewers, me)
		if err != nil {
			return fmt.Errorf("--reviewers: %w", err)
		}
		e.ReviewerIDs = ids
		for i, id := range ids {
			e.nameOnce(id, f.Reviewers[i])
		}
		return nil
	}

	if len(f.RemoveReviewers) > 0 {
		ids, err := organization.ResolveMemberIDs(c, f.RemoveReviewers)
		if err != nil {
			return fmt.Errorf("--remove-reviewers: %w", err)
		}
		for i, id := range ids {
			at := indexOfID(e.ReviewerIDs, id)
			if at < 0 {
				return fmt.Errorf("--remove-reviewers %s: not a reviewer of this share", f.RemoveReviewers[i])
			}
			e.ReviewerIDs = append(e.ReviewerIDs[:at:at], e.ReviewerIDs[at+1:]...)
		}
	}

	if len(f.AddReviewers) > 0 {
		ids, err := ResolveReviewers(c, f.AddReviewers, me)
		if err != nil {
			return fmt.Errorf("--add-reviewers: %w", err)
		}
		for i, id := range ids {
			if indexOfID(e.ReviewerIDs, id) >= 0 {
				return fmt.Errorf("--add-reviewers %s: already a reviewer of this share", f.AddReviewers[i])
			}
			e.ReviewerIDs = append(e.ReviewerIDs, id)
			e.nameOnce(id, f.AddReviewers[i])
		}
	}

	if len(e.ReviewerIDs) > MaxReviewers {
		return fmt.Errorf("the share would name %d reviewers; a share can name at most %d", len(e.ReviewerIDs), MaxReviewers)
	}
	return nil
}

// nameOnce names a reviewer id that has no name yet with the ref the user typed.
func (e *Edit) nameOnce(id, ref string) {
	if _, known := e.Names[strings.ToLower(id)]; !known {
		e.Names[strings.ToLower(id)] = ref
	}
}
