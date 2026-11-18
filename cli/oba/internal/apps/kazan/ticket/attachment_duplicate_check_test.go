package ticket

import (
	"bytes"
	"io"
	"mime"
	"mime/multipart"
	"net/http"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

func TestCheckAttachmentDuplicatesSendsTheMetadataAndTheFile(t *testing.T) {
	clitest.SetupHome(t)
	api := clitest.NewMockAPI(t)
	var metadata, fileName, data string
	api.Handle("POST /storage/attachments/exists", func(w http.ResponseWriter, r *http.Request) {
		_, params, _ := mime.ParseMediaType(r.Header.Get("Content-Type"))
		reader := multipart.NewReader(r.Body, params["boundary"])
		for {
			part, err := reader.NextPart()
			if err != nil {
				break
			}
			body, _ := io.ReadAll(part)
			switch part.FormName() {
			case "metadata":
				metadata = string(body)
			case "file":
				fileName, data = part.FileName(), string(body)
			}
		}
		clitest.WriteJSON(w, `{"data":{"exists":false}}`)
	})
	c := client.NewWithToken(api.URL(), clitest.OrgIDAcme, "tok")
	files := []client.AttachmentFile{{FileName: "a.txt", ContentType: "text/plain", Data: []byte("abc")}}
	if err := CheckAttachmentDuplicates(c, files, &bytes.Buffer{}); err != nil {
		t.Fatal(err)
	}
	if metadata != `{"fileName":"a.txt","entityType":"TICKET"}` || fileName != "a.txt" || data != "abc" {
		t.Errorf("metadata %s, file %s %q", metadata, fileName, data)
	}
}

func TestCheckAttachmentDuplicatesRefusesAnExistingFile(t *testing.T) {
	clitest.SetupHome(t)
	api := clitest.NewMockAPI(t)
	api.Respond("POST /storage/attachments/exists", http.StatusOK, `{"data":{"exists":true}}`)
	c := client.NewWithToken(api.URL(), clitest.OrgIDAcme, "tok")
	files := []client.AttachmentFile{{FileName: "a.txt", ContentType: "text/plain", Data: []byte("abc")}}
	err := CheckAttachmentDuplicates(c, files, &bytes.Buffer{})
	if err == nil || !strings.Contains(err.Error(), `"a.txt" has the same content as a file already attached to a ticket`) {
		t.Fatalf("err = %v", err)
	}
}

func TestCheckAttachmentDuplicatesWarnsAndGoesOnWhenTheCheckFails(t *testing.T) {
	clitest.SetupHome(t)
	api := clitest.NewMockAPI(t)
	api.Respond("POST /storage/attachments/exists", http.StatusInternalServerError, `{"error":{"code":"X","message":"boom"}}`)
	c := client.NewWithToken(api.URL(), clitest.OrgIDAcme, "tok")
	files := []client.AttachmentFile{{FileName: "a.txt", Data: []byte("a")}, {FileName: "b.txt", Data: []byte("b")}}
	var warn bytes.Buffer
	if err := CheckAttachmentDuplicates(c, files, &warn); err != nil {
		t.Fatal(err)
	}
	if strings.Count(warn.String(), "could not check") != 2 {
		t.Errorf("warn = %q", warn.String())
	}
}
