package pack

import (
	"encoding/json"
	"errors"
	"net/http"
	"reflect"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/client"
)

func TestPackRequired_marksThePackOfThatNameRequired(t *testing.T) {
	fake := setup(t, 3)

	out, err := run(t, "pack", "required", "web")

	if err != nil || out != "Pack web (p-web): required, 3 releases created.\n" {
		t.Fatalf("out = %q, err = %v", out, err)
	}
	want := []put{{id: "p-web", body: map[string]any{"required": true}}}
	if got := fake.sent(); !reflect.DeepEqual(got, want) {
		t.Fatalf("puts = %+v, want %+v", got, want)
	}
}

func TestPackRequired_offMarksItNotRequired(t *testing.T) {
	fake := setup(t, 1)

	out, err := run(t, "pack", "required", "base", "--off")

	if err != nil || out != "Pack base (p-base): not required, 1 release created.\n" {
		t.Fatalf("out = %q, err = %v", out, err)
	}
	want := []put{{id: "p-base", body: map[string]any{"required": false}}}
	if got := fake.sent(); !reflect.DeepEqual(got, want) {
		t.Fatalf("puts = %+v, want %+v", got, want)
	}
}

func TestPackRequired_anUnchangedFlagNamesNoRelease(t *testing.T) {
	setup(t, 0)

	out, err := run(t, "pack", "required", "base")

	if err != nil || out != "Pack base (p-base): required, no release created.\n" {
		t.Fatalf("out = %q, err = %v", out, err)
	}
}

func TestPackRequired_jsonIsKayasAnswer(t *testing.T) {
	setup(t, 2)

	out, err := run(t, "pack", "required", "web", "--json")

	var answer map[string]any
	if err != nil || json.Unmarshal([]byte(out), &answer) != nil {
		t.Fatalf("out = %q, err = %v", out, err)
	}
	want := map[string]any{"packId": "p-web", "required": true, "releaseCount": float64(2)}
	if !reflect.DeepEqual(answer, want) {
		t.Fatalf("answer = %v, want %v", answer, want)
	}
}

func TestPackRequired_anUnknownNameListsThePacksAndChangesNothing(t *testing.T) {
	fake := setup(t, 1)

	_, err := run(t, "pack", "required", "mobile")

	if err == nil || err.Error() != `the organization has no pack named "mobile"; its packs are: base, web` {
		t.Fatalf("err = %v", err)
	}
	if puts := fake.sent(); len(puts) != 0 {
		t.Fatalf("an unknown name sent %+v", puts)
	}
}

func TestPackRequired_anOrganizationWithoutPacksSaysSo(t *testing.T) {
	fake := setup(t, 1)
	fake.set(func(f *fakePacks) { f.packs = []map[string]any{} })

	_, err := run(t, "pack", "required", "web")

	if err == nil || err.Error() != `the organization has no pack named "web": it has no packs yet` {
		t.Fatalf("err = %v", err)
	}
}

func TestPackRequired_aForbiddenChangeNeedsAnOwner(t *testing.T) {
	fake := setup(t, 1)
	fake.set(func(f *fakePacks) {
		f.answer = func(string, bool) (int, map[string]any) {
			return http.StatusForbidden, map[string]any{"success": false,
				"error": map[string]any{"code": "GEN_SEC_002", "message": "Insufficient permissions"}}
		}
	})

	out, err := run(t, "pack", "required", "web", "--json")

	if err == nil || !strings.HasPrefix(err.Error(), "only an OWNER of the organization may mark a pack required or not required: ") {
		t.Fatalf("err = %v", err)
	}
	var apiErr *client.APIError
	if !errors.As(err, &apiErr) || apiErr.Status != http.StatusForbidden {
		t.Fatalf("the refusal is not kept: %v", err)
	}
	if out != "" {
		t.Fatalf("a refused change printed %q", out)
	}
}

func TestPackRequired_aRefusalListsEveryViolation(t *testing.T) {
	fake := setup(t, 1)
	fake.set(func(f *fakePacks) {
		f.answer = func(string, bool) (int, map[string]any) {
			return http.StatusUnprocessableEntity, map[string]any{"success": false, "error": map[string]any{
				"code": "KAY_CFG_031", "message": "A pack below overrides an item",
				"errorValues": map[string]any{"violations": []any{
					map[string]any{"path": ".agents/rules/style.md", "rule": "LOCKED_ITEM_OVERRIDDEN", "message": "web overrides it"},
					map[string]any{"rule": "GUARDRAIL_SETTINGS_MISSING", "message": "no settings\x1b[31m"},
				}},
			}}
		}
	})

	_, err := run(t, "pack", "required", "base")

	want := "Kaya API answered 422 KAY_CFG_031: A pack below overrides an item\n" +
		"  .agents/rules/style.md: LOCKED_ITEM_OVERRIDDEN: web overrides it\n" +
		"  GUARDRAIL_SETTINGS_MISSING: no settings"
	if err == nil || err.Error() != want {
		t.Fatalf("err = %q, want %q", err, want)
	}
	if !client.IsCode(err, "KAY_CFG_031") {
		t.Fatalf("the refusal is not kept: %v", err)
	}
}

func TestPackRequired_takesExactlyOneName(t *testing.T) {
	setup(t, 1)

	if _, err := run(t, "pack", "required"); err == nil {
		t.Fatal("no name was accepted")
	}
	if _, err := run(t, "pack", "required", "web", "base"); err == nil {
		t.Fatal("two names were accepted")
	}
}
