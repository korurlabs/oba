package outreach

import (
	"encoding/json"
	"net/http"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

const (
	outreachID     = "99999999-9999-4999-8999-999999999999"
	contactID      = "dddddddd-dddd-4ddd-8ddd-dddddddddddd"
	templateID     = "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee"
	mailID         = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
	outreachesPath = "/otak/outreaches"
)

func outreachJSON(status, extra string) string {
	return typedOutreachJSON("MAIL", status, extra)
}

func typedOutreachJSON(outreachType, status, extra string) string {
	return `{"id":"` + outreachID + `","title":"First contact","type":"` + outreachType + `","status":"` + status + `","relationId":"` +
		clitest.RelationID + `","contact":{"id":"` + contactID + `","name":"Jan de Vries"},"sentTo":null,"sentAt":null,` +
		`"mailSenderAddress":null,"createdAt":"2026-11-14T10:00:00Z"` + extra + `}`
}

type createBody struct {
	RelationID          string          `json:"relationId"`
	ContactID           string          `json:"contactId"`
	Title               string          `json:"title"`
	Type                string          `json:"type"`
	MailTemplateID      *string         `json:"mailTemplateId"`
	Mail                map[string]any  `json:"mail"`
	DocumentTemplateIDs json.RawMessage `json:"documentTemplateIds"`
	AttachmentIDs       json.RawMessage `json:"attachmentIds"`
}

func decodeCreate(t *testing.T, body string) createBody {
	t.Helper()
	var b createBody
	if err := json.Unmarshal([]byte(body), &b); err != nil {
		t.Fatalf("body %q: %v", body, err)
	}
	return b
}

func TestOutreachCreateFromTemplateWithDocumentTemplates(t *testing.T) {
	api := clitest.NewSignedInAPI(t)
	mail := `,"mail":{"id":"` + mailID + `","mailTemplateId":"` + templateID + `","subject":"Hallo","body":"B","language":"NL"}`
	api.Respond("POST "+outreachesPath, http.StatusCreated, `{"data":`+outreachJSON("DRAFT", mail)+`}`)

	out, _, err := runCLI(t, "otak", "outreach", "create", "--relation", clitest.RelationID, "--contact", contactID,
		"--type", "mail", "--title", "First contact", "--template", templateID, "--attach", "dt-1", "--attach", "dt-2, dt-3")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(out, "Created outreach "+outreachID+" (First contact, MAIL, DRAFT)") ||
		!strings.Contains(out, "Mail "+mailID+": Hallo") {
		t.Errorf("out = %q", out)
	}
	b := decodeCreate(t, api.Request("POST "+outreachesPath).Body)
	if b.RelationID != clitest.RelationID || b.ContactID != contactID || b.Title != "First contact" ||
		b.Type != "MAIL" || b.MailTemplateID == nil || *b.MailTemplateID != templateID || b.Mail != nil ||
		string(b.DocumentTemplateIDs) != `["dt-1","dt-2","dt-3"]` || b.AttachmentIDs != nil {
		t.Errorf("body = %+v (%s)", b, b.DocumentTemplateIDs)
	}
}

func TestOutreachCreateFromMailFile(t *testing.T) {
	api := clitest.NewSignedInAPI(t)
	file := writeFile(t, t.TempDir(), "mail.md", "Introduction\n\nDear Jan,\n")
	api.Respond("POST "+outreachesPath, http.StatusCreated, `{"data":`+outreachJSON("DRAFT", "")+`}`)

	_, _, err := runCLI(t, "otak", "outreach", "create", "--relation", clitest.RelationID, "--contact", contactID,
		"--type", "MAIL", "--title", "First contact", "--mail-file", file, "--lang", "us")
	if err != nil {
		t.Fatal(err)
	}
	b := decodeCreate(t, api.Request("POST "+outreachesPath).Body)
	if b.MailTemplateID != nil || b.Mail["subject"] != "Introduction" || b.Mail["body"] != "Dear Jan," ||
		b.Mail["language"] != "EN" || string(b.DocumentTemplateIDs) != `[]` {
		t.Errorf("body = %+v (%s)", b, b.DocumentTemplateIDs)
	}
}

func TestOutreachCreateFromMailFileWithoutLangSendsNL(t *testing.T) {
	api := clitest.NewSignedInAPI(t)
	file := writeFile(t, t.TempDir(), "mail.md", "Kennismaking\n\nBeste Jan,\n")
	api.Respond("POST "+outreachesPath, http.StatusCreated, `{"data":`+outreachJSON("DRAFT", "")+`}`)

	_, _, err := runCLI(t, "otak", "outreach", "create", "--relation", clitest.RelationID, "--contact", contactID,
		"--type", "mail", "--title", "First contact", "--mail-file", file)
	if err != nil {
		t.Fatal(err)
	}
	if b := decodeCreate(t, api.Request("POST "+outreachesPath).Body); b.Mail["language"] != "NL" {
		t.Errorf("mail = %v", b.Mail)
	}
}

func TestOutreachCreateNeedsExactlyOneMailSource(t *testing.T) {
	api := clitest.NewSignedInAPI(t)
	file := writeFile(t, t.TempDir(), "mail.md", "S\n\nB")
	base := []string{"otak", "outreach", "create", "--relation", clitest.RelationID, "--contact", contactID, "--title", "T", "--type", "mail"}
	for _, extra := range [][]string{nil, {"--template", templateID, "--mail-file", file}} {
		_, _, err := runCLI(t, append(append([]string{}, base...), extra...)...)
		if err == nil || !strings.Contains(err.Error(), "exactly one of --template and --mail-file") {
			t.Errorf("%v: err = %v", extra, err)
		}
	}
	_, _, err := runCLI(t, append(append([]string{}, base...), "--template", templateID, "--lang", "nl")...)
	if err == nil || !strings.Contains(err.Error(), "--lang applies to --mail-file only") {
		t.Errorf("err = %v", err)
	}
	if got := api.Calls(); len(got) != 0 {
		t.Errorf("calls = %v", got)
	}
}

func TestOutreachCreateRequiredFlags(t *testing.T) {
	clitest.NewSignedInAPI(t)
	_, _, err := runCLI(t, "otak", "outreach", "create", "--relation", clitest.RelationID, "--template", templateID)
	if err == nil || !strings.Contains(err.Error(), `"contact"`) || !strings.Contains(err.Error(), `"title"`) ||
		!strings.Contains(err.Error(), `"type"`) {
		t.Fatalf("err = %v", err)
	}
}

func TestOutreachCreateShowsAPIError(t *testing.T) {
	api := clitest.NewSignedInAPI(t)
	api.Respond("POST "+outreachesPath, http.StatusUnprocessableEntity,
		`{"error":{"code":"DOC_VAL_002","message":"The document template can not be used here.","errorValues":{"templateId":"dt-1"}}}`)
	_, _, err := runCLI(t, "otak", "outreach", "create", "--relation", clitest.RelationID, "--contact", contactID,
		"--title", "T", "--type", "mail", "--template", templateID, "--attach", "dt-1")
	if err == nil || !strings.Contains(err.Error(), "DOC_VAL_002: The document template can not be used here.") || !strings.Contains(err.Error(), "templateId: dt-1") {
		t.Fatalf("err = %v", err)
	}
}

func TestOutreachList(t *testing.T) {
	api := clitest.NewSignedInAPI(t)
	var query string
	sent := `,"sentTo":"jan@acme.nl","sentAt":"2026-11-14T11:00:00Z","mailSenderAddress":"sales@example.com"`
	api.Handle("GET "+outreachesPath, func(w http.ResponseWriter, r *http.Request) {
		query = r.URL.RawQuery
		withContact := strings.Replace(outreachJSON("SENT", ""), `"sentTo":null,"sentAt":null,"mailSenderAddress":null,`, "", 1)
		clitest.WriteJSON(w, `{"data":[`+strings.TrimSuffix(withContact, "}")+sent+`}],"pagination":{"currentPage":1,"last":true}}`)
	})
	out, _, err := runCLI(t, "otak", "outreach", "list", "--relation", clitest.RelationID, "--status", "sent")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(query, "relationId="+clitest.RelationID) || !strings.Contains(query, "status=SENT") {
		t.Errorf("query = %q", query)
	}
	lines := strings.Split(strings.TrimSpace(out), "\n")
	if len(lines) != 2 || !strings.HasPrefix(lines[0], "TITLE") {
		t.Fatalf("out =\n%s", out)
	}
	for _, want := range []string{"First contact", "MAIL", "SENT", "Jan de Vries", "jan@acme.nl", "sales@example.com", outreachID} {
		if !strings.Contains(lines[1], want) {
			t.Errorf("row lacks %q: %q", want, lines[1])
		}
	}
}

func TestOutreachListEmpty(t *testing.T) {
	api := clitest.NewSignedInAPI(t)
	api.Respond("GET "+outreachesPath, http.StatusOK, `{"data":[],"pagination":{"currentPage":1,"last":true}}`)
	out, _, err := runCLI(t, "otak", "outreach", "list")
	if err != nil || out != "No outreaches.\n" {
		t.Fatalf("out %q, err %v", out, err)
	}
}

func TestOutreachCreateCallSendsNoMail(t *testing.T) {
	api := clitest.NewSignedInAPI(t)
	api.Respond("POST "+outreachesPath, http.StatusCreated, `{"data":`+typedOutreachJSON("CALL", "DRAFT", `,"mail":null`)+`}`)

	out, _, err := runCLI(t, "otak", "outreach", "create", "--relation", clitest.RelationID, "--contact", contactID,
		"--title", "Intro call", "--type", "call")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(out, "(First contact, CALL, DRAFT)") || strings.Contains(out, "Mail ") {
		t.Errorf("out = %q", out)
	}
	b := decodeCreate(t, api.Request("POST "+outreachesPath).Body)
	if b.Type != "CALL" || b.MailTemplateID != nil || b.Mail != nil || string(b.DocumentTemplateIDs) != `[]` {
		t.Errorf("body = %+v (%s)", b, b.DocumentTemplateIDs)
	}
}

func TestOutreachCreateWhatsAppRefusesMailFlags(t *testing.T) {
	api := clitest.NewSignedInAPI(t)
	_, _, err := runCLI(t, "otak", "outreach", "create", "--relation", clitest.RelationID, "--contact", contactID,
		"--title", "T", "--type", "whatsapp", "--template", templateID, "--attach", "dt-1")
	if err == nil || !strings.Contains(err.Error(), "a WHATSAPP outreach has no mail; leave out --template, --attach") {
		t.Fatalf("err = %v", err)
	}
	if got := api.Calls(); len(got) != 0 {
		t.Errorf("calls = %v", got)
	}
}

func TestOutreachCreateUnknownType(t *testing.T) {
	api := clitest.NewSignedInAPI(t)
	_, _, err := runCLI(t, "otak", "outreach", "create", "--relation", clitest.RelationID, "--contact", contactID,
		"--title", "T", "--type", "sms")
	if err == nil || !strings.Contains(err.Error(), `unknown outreach type "sms"; use mail, whatsapp or call`) {
		t.Fatalf("err = %v", err)
	}
	if got := api.Calls(); len(got) != 0 {
		t.Errorf("calls = %v", got)
	}
}

func TestOutreachComplete(t *testing.T) {
	api := clitest.NewSignedInAPI(t)
	route := "POST " + outreachesPath + "/" + outreachID + "/complete"
	done := strings.Replace(typedOutreachJSON("WHATSAPP", "SENT", `,"mail":null`),
		`"sentTo":null,"sentAt":null`, `"sentTo":"+31 6 1234 5678","sentAt":"2026-11-17T09:00:00Z"`, 1)
	api.Respond(route, http.StatusOK, `{"data":`+done+`}`)

	out, _, err := runCLI(t, "otak", "outreach", "complete", outreachID)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(out, "Recorded outreach "+outreachID+" as done (First contact, WHATSAPP, SENT to +31 6 1234 5678)") {
		t.Errorf("out = %q", out)
	}
	if body := api.Request(route).Body; body != "" {
		t.Errorf("body = %q", body)
	}
}

func TestOutreachCompleteShowsAPIError(t *testing.T) {
	api := clitest.NewSignedInAPI(t)
	api.Respond("POST "+outreachesPath+"/"+outreachID+"/complete", http.StatusUnprocessableEntity,
		`{"error":{"code":"OTK_VAL_014","message":"This action does not fit the outreach's channel."}}`)
	_, _, err := runCLI(t, "otak", "outreach", "complete", outreachID)
	if err == nil || !strings.Contains(err.Error(), "OTK_VAL_014") {
		t.Fatalf("err = %v", err)
	}
}

func TestOutreachCampaign(t *testing.T) {
	const campaignID = "cccccccc-cccc-4ccc-8ccc-cccccccccccc"
	api := clitest.NewSignedInAPI(t)
	api.Respond("POST "+outreachesPath, http.StatusCreated, `{"data":`+typedOutreachJSON("CALL", "DRAFT", `,"mail":null`)+`}`)
	var query string
	api.Handle("GET "+outreachesPath, func(w http.ResponseWriter, r *http.Request) {
		query = r.URL.RawQuery
		clitest.WriteJSON(w, `{"data":[],"pagination":{"currentPage":1,"last":true}}`)
	})

	if _, _, err := runCLI(t, "otak", "outreach", "create", "--relation", clitest.RelationID, "--contact", contactID,
		"--title", "Intro call", "--type", "call", "--campaign", " "+campaignID+" "); err != nil {
		t.Fatal(err)
	}
	var body map[string]any
	if err := json.Unmarshal([]byte(api.Request("POST "+outreachesPath).Body), &body); err != nil {
		t.Fatal(err)
	}
	if body["campaignId"] != campaignID {
		t.Errorf("body = %v", body)
	}
	if _, _, err := runCLI(t, "otak", "outreach", "list", "--campaign", campaignID); err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(query, "campaignId="+campaignID) {
		t.Errorf("query = %q", query)
	}
}

func TestOutreachCreateWithoutCampaignSendsNone(t *testing.T) {
	api := clitest.NewSignedInAPI(t)
	api.Respond("POST "+outreachesPath, http.StatusCreated, `{"data":`+typedOutreachJSON("CALL", "DRAFT", `,"mail":null`)+`}`)
	if _, _, err := runCLI(t, "otak", "outreach", "create", "--relation", clitest.RelationID, "--contact", contactID,
		"--title", "Intro call", "--type", "call"); err != nil {
		t.Fatal(err)
	}
	if body := api.Request("POST " + outreachesPath).Body; strings.Contains(body, "campaignId") {
		t.Errorf("body = %s", body)
	}
}
