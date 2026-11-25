package page

import (
	"bytes"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

var detail = client.KalkanPageDetail{
	ID: "p1", Path: "engineering/plans", Compliance: true, Version: 4, Level: "EDIT",
	Translations: []client.KalkanPageTranslation{
		{Locale: "TR", Title: "Planlar", Body: "# Planlar\n", UpdatedAt: "2026-11-24T09:00:00Z", UpdatedByName: "Ada"},
		{Locale: "EN", Title: "Plans", Body: "# Plans", UpdatedAt: "2026-11-25T10:00:00Z", UpdatedByName: "Bo"},
	},
}

func TestPrintPageWritesEveryTranslation(t *testing.T) {
	var out bytes.Buffer
	PrintPage(&out, detail)
	want := "engineering/plans (version 4, your level EDIT, compliance document)\n" +
		"\n== TR: Planlar (updated 2026-11-24T09:00:00Z by Ada)\n\n# Planlar\n" +
		"\n== EN: Plans (updated 2026-11-25T10:00:00Z by Bo)\n\n# Plans\n"
	if out.String() != want {
		t.Errorf("out =\n%q\nwant\n%q", out.String(), want)
	}
}

func TestPrintBodyWritesTheRawBodyWithANewline(t *testing.T) {
	var out bytes.Buffer
	if err := PrintBody(&out, detail, client.KalkanLocaleEN); err != nil {
		t.Fatal(err)
	}
	if out.String() != "# Plans\n" {
		t.Errorf("out = %q", out.String())
	}
	out.Reset()
	if err := PrintBody(&out, detail, client.KalkanLocaleTR); err != nil {
		t.Fatal(err)
	}
	if out.String() != "# Planlar\n" {
		t.Errorf("out = %q", out.String())
	}
}

func TestPrintBodyWithoutTheTranslation(t *testing.T) {
	page := client.KalkanPageDetail{Path: "a", Translations: []client.KalkanPageTranslation{{Locale: "TR", Body: "x"}}}
	var out bytes.Buffer
	err := PrintBody(&out, page, client.KalkanLocaleEN)
	if err == nil || err.Error() != "the page a has no en translation" || out.Len() != 0 {
		t.Errorf("err = %v, out = %q", err, out.String())
	}
}
