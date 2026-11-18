package ticket

import (
	"encoding/json"
	"errors"
	"fmt"
	"strings"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/cmd/jsonresult"
	"github.com/korurlabs/oba-cli/internal/apps/kazan/ticket"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/jsonout"
)

const createHelp = `Creates a ticket on the board --board names, else on the default board, in the backlog unless
--sprint or --backlog says otherwise. A sub-ticket (--parent) lives on its parent's board and in
its parent's sprint or backlog.

--title, --type, --priority, --status and --mission-rank are required. Each --attach adds a local
file to the new ticket; the files are checked before the ticket is created.

With --attach and --json, the output is {"ticket": ..., "attachments": [...]}, plus
"attachmentError" when the ticket was created but its files could not be attached.

  oba kazan ticket create --title "Login crash" --type BUG --priority HIGH --status TODO \
    --mission-rank S --attach ./screenshot.png --attach ./server.log`

func newCreateCmd() *cobra.Command {
	var in ticket.CreateInput
	var storyPoints int
	cmd := &cobra.Command{
		Use:   "create",
		Short: "Create a ticket",
		Long:  createHelp,
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			in.StoryPoints = changedInt(cmd, "story-points", storyPoints)
			return runCreate(cmd, in)
		},
	}
	f := cmd.Flags()
	f.StringVar(&in.Title, "title", "", "ticket title (required)")
	f.StringVar(&in.Type, "type", "", "ticket type (required): "+strings.Join(ticket.Types, ", "))
	f.StringVar(&in.Priority, "priority", "", "priority (required): LOWEST, LOW, MEDIUM, HIGH, HIGHEST (all types except INCIDENT) or P1, P2, P3 (INCIDENT)")
	f.StringVar(&in.Status, "status", "", "status (required): "+strings.Join(ticket.Statuses, ", "))
	f.StringVar(&in.MissionRank, "mission-rank", "", "mission rank (required): "+strings.Join(ticket.MissionRanks, ", "))
	f.StringVar(&in.Description, "description", "", "ticket description")
	f.StringVar(&in.Assignee, "assignee", "", "assignee emails or user ids (comma separated)")
	f.StringVar(&in.Reviewer, "reviewer", "", "reviewer emails or user ids (comma separated)")
	f.StringVar(&in.Label, "label", "", "label ids (comma separated)")
	f.StringVar(&in.Project, "project", "", "project id")
	f.StringVar(&in.Sprint, "sprint", "", "sprint name ('sprint' for the running one, else the newest)")
	f.StringVar(&in.Backlog, "backlog", "", "backlog id")
	f.StringVar(&in.Milestone, "milestone", "", "milestone id or name, on this board or a related board")
	f.IntVar(&storyPoints, "story-points", 0, "story points (weight)")
	f.StringVar(&in.DueDate, "due-date", "", "due date (YYYY-MM-DD, local midnight, or an RFC 3339 timestamp)")
	f.StringVar(&in.Parent, "parent", "", "parent ticket key or id; a sub-ticket lives on its parent's board")
	f.StringVar(&in.RCA, "rca", "", "root cause analysis")
	f.StringVar(&in.LessonsLearned, "lessons-learned", "", "lessons learned")
	f.StringArrayVarP(&in.Attach, "attach", "a", nil, "attach a local file to the new ticket; repeat for more files (12 MB together at most)")
	return cmd
}

func runCreate(cmd *cobra.Command, in ticket.CreateInput) error {
	prepared, err := ticket.PrepareCreate(in)
	if err != nil {
		return err
	}
	warnRetiredType(cmd, prepared.RetiredType)
	c, r, err := start(cmd)
	if err != nil {
		return err
	}
	created, err := ticket.Create(c, r, prepared, cmd.ErrOrStderr())
	if err != nil {
		return err
	}
	out := cmd.OutOrStdout()
	_, _ = fmt.Fprintf(out, "Created ticket %s: %s\n", created.Ticket.Key, created.Ticket.Title)
	if len(created.Files) == 0 {
		return nil
	}
	if jsonout.Enabled() {
		if err := setCreateResult(created); err != nil {
			// The upload error is the cause the user needs; the encoding error must not hide it.
			return errors.Join(created.UploadErr, err)
		}
	}
	if created.UploadErr == nil {
		names := make([]string, len(created.Attachments))
		for i, a := range created.Attachments {
			names[i] = a.FileName
		}
		_, _ = fmt.Fprintf(out, "Attached %d file(s): %s\n", len(created.Attachments), strings.Join(names, ", "))
		return nil
	}
	// The ticket exists and only the upload failed: say so on stderr, since stdout may be read
	// as JSON, and keep the exit code non-zero.
	errOut := cmd.ErrOrStderr()
	_, _ = fmt.Fprintf(errOut, "Ticket %s WAS created and is not lost: only the attachment upload failed.\n", created.Ticket.Key)
	_, _ = fmt.Fprintf(errOut, "Not attached (%d): %s\n", len(created.Files), strings.Join(created.Files, ", "))
	return fmt.Errorf("ticket %s created, but uploading its attachments failed: %w", created.Ticket.Key, created.UploadErr)
}

// createResult is the --json result of a create with attachments.
type createResult struct {
	Ticket          json.RawMessage     `json:"ticket"`
	Attachments     []client.Attachment `json:"attachments"`
	AttachmentError *attachmentError    `json:"attachmentError,omitempty"`
}

type attachmentError struct {
	Message string   `json:"message"`
	Files   []string `json:"files"`
}

func setCreateResult(created ticket.Created) error {
	result := createResult{Ticket: created.Raw, Attachments: created.Attachments}
	if result.Attachments == nil {
		result.Attachments = []client.Attachment{}
	}
	if created.UploadErr != nil {
		result.AttachmentError = &attachmentError{Message: created.UploadErr.Error(), Files: created.Files}
	}
	encoded, err := json.Marshal(result)
	if err != nil {
		return fmt.Errorf("failed to encode the JSON output: %w", err)
	}
	return jsonresult.SetPartial(encoded)
}
