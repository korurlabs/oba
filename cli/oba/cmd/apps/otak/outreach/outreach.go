// Package outreach builds oba otak outreach: prepare reach-outs to a contact of a relation, list
// them and record a WhatsApp message or a call as done.
package outreach

import (
	"fmt"
	"strings"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/otak/outreach"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const (
	outreachListHelp = `Lists the outreaches of the current organization, newest first: TITLE, TYPE (MAIL, WHATSAPP
or CALL), STATUS (DRAFT, SENT or FAILED), CONTACT, SENT TO, SENT AT, SENDER, ID. SENT TO is the
mail address of a MAIL and the phone of a WHATSAPP or CALL; SENT is "done" for those two.
--relation narrows to one relation and --status to one status.`
	outreachCreateHelp = `Prepares one reach-out to one contact of a relation and stores it as a DRAFT. Nothing is
sent. --type is the channel: mail, whatsapp or call.

A MAIL is sent from the lead page in the Otak web app, where you choose the mailbox. Its mail
comes from exactly one of:
  --template <mailTemplateId>  a mail template ('oba otak mail-template list'); the API copies
                               it into the outreach's own mail
  --mail-file <file.md>        a mail written for this outreach: the subject on the first line,
                               a blank line, then the body as plain text. --lang (nl, tr, en or
                               us) sets its language; it is NL without it.
--attach copies the PDF of a document template onto the new mail as an attachment; repeat it
or give a comma list. 'oba otak template list' lists the ids. Add other files after create
with 'oba otak attachment add --outreach <outreachId> --file <path>'.

A WHATSAPP or CALL happens outside Oba and takes none of these flags; record it as done with
'oba otak outreach complete <outreachId>'. 'oba otak contact list <relationId>' lists the
contacts.

  oba otak outreach create --relation <relationId> --contact <contactId> --type mail \
    --title "First contact" --template <mailTemplateId> --attach <documentTemplateId>
  oba otak outreach create --relation <relationId> --contact <contactId> --type call \
    --title "Intro call"`
	outreachCompleteHelp = `Records a DRAFT WHATSAPP or CALL outreach as done: it becomes SENT, now, to the contact's
phone. A MAIL is sent from the Otak web app instead (OTK_VAL_014); an outreach already done is
refused with OTK_STA_011, and a contact without a phone with OTK_VAL_015.

  oba otak outreach complete <outreachId>`
)

// NewCmd builds oba otak outreach with its subcommands.
func NewCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "outreach",
		Short: "Prepare, list and complete reach-outs to the contacts of a relation",
	}

	var listRelation, listStatus string
	listCmd := &cobra.Command{
		Use:   "list",
		Short: "List the outreaches",
		Long:  outreachListHelp,
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			return runOutreachList(cmd, strings.TrimSpace(listRelation), listStatus)
		},
	}
	listCmd.Flags().StringVar(&listRelation, "relation", "", "only outreaches of this relation")
	listCmd.Flags().StringVar(&listStatus, "status", "", "only outreaches with this status: DRAFT, SENT or FAILED")

	var create outreach.CreateInput
	createCmd := &cobra.Command{
		Use:   "create",
		Short: "Store a DRAFT outreach to a contact of a relation",
		Long:  outreachCreateHelp,
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			in := create
			in.RelationID = strings.TrimSpace(in.RelationID)
			in.ContactID = strings.TrimSpace(in.ContactID)
			in.TemplateID = strings.TrimSpace(in.TemplateID)
			in.Type = strings.TrimSpace(in.Type)
			return runOutreachCreate(cmd, in)
		},
	}
	createCmd.Flags().StringVar(&create.RelationID, "relation", "", "the relation id (required)")
	createCmd.Flags().StringVar(&create.ContactID, "contact", "", "the contact id, a contact of the relation (required)")
	createCmd.Flags().StringVar(&create.Title, "title", "", "the outreach title, at most 200 characters (required)")
	createCmd.Flags().StringVar(&create.Type, "type", "", "the channel: mail, whatsapp or call (required)")
	createCmd.Flags().StringVar(&create.TemplateID, "template", "", "the mail template to copy the mail from (MAIL only)")
	createCmd.Flags().StringVar(&create.MailFile, "mail-file", "", "the .md file with the mail: subject on the first line, then the body (MAIL only)")
	createCmd.Flags().StringVar(&create.Language, "lang", "", "the language of --mail-file: nl, tr, en or us (English)")
	createCmd.Flags().StringSliceVar(&create.DocumentTemplateIDs, "attach", nil,
		"a document template whose PDF to attach; repeat it or give a comma list (MAIL only)")
	for _, name := range []string{"relation", "contact", "title", "type"} {
		_ = createCmd.MarkFlagRequired(name)
	}

	completeCmd := &cobra.Command{
		Use:   "complete <outreachId>",
		Short: "Record a WhatsApp or call outreach as done",
		Long:  outreachCompleteHelp,
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			return runOutreachComplete(cmd, strings.TrimSpace(args[0]))
		},
	}

	cmd.AddCommand(listCmd, createCmd, completeCmd)
	return cmd
}

func runOutreachList(cmd *cobra.Command, relationID, status string) error {
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	outreaches, err := outreach.List(c, relationID, status)
	if err != nil {
		return err
	}
	return outreach.PrintList(cmd.OutOrStdout(), outreaches)
}

func runOutreachCreate(cmd *cobra.Command, in outreach.CreateInput) error {
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	created, err := outreach.Create(c, in)
	if err != nil {
		return err
	}
	_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Created outreach %s (%s, %s, %s)\n", created.ID, created.Title, created.Type, created.Status)
	if created.Mail != nil {
		_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Mail %s: %s\n", created.Mail.ID, guardrail.OneLine(created.Mail.Subject))
	}
	return nil
}

func runOutreachComplete(cmd *cobra.Command, id string) error {
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	done, err := outreach.Complete(c, id)
	if err != nil {
		return err
	}
	sentTo := "-"
	if done.SentTo != nil {
		sentTo = guardrail.OneLine(*done.SentTo)
	}
	_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Recorded outreach %s as done (%s, %s, %s to %s)\n",
		done.ID, guardrail.OneLine(done.Title), done.Type, done.Status, sentTo)
	return nil
}
