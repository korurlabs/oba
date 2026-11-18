package find

import (
	"encoding/json"
	"reflect"
	"testing"
)

func TestDecode(t *testing.T) {
	got, err := Decode(json.RawMessage(`{"candidates":[
		{"name":"Acme","website":"https://acme.example","country":"NL","reason":"fits","summary":"Delft, 20 people","city":"Delft","size":"SIZE_11_50"},
		{"name":"Beta","website":"https://beta.example","country":"NL","reason":"fits","summary":"","city":null,"size":null}]}`))
	if err != nil {
		t.Fatal(err)
	}
	city, size := "Delft", "SIZE_11_50"
	want := []Candidate{
		{Name: "Acme", Website: "https://acme.example", Country: "NL", Reason: "fits", Summary: "Delft, 20 people", City: &city, Size: &size},
		{Name: "Beta", Website: "https://beta.example", Country: "NL", Reason: "fits"},
	}
	if !reflect.DeepEqual(got, want) {
		t.Errorf("got %+v, want %+v", got, want)
	}
}

func TestDecodeRefusesOtherShapes(t *testing.T) {
	cases := map[string]string{
		"candidates not a list": `{"candidates":"x"}`,
		"not an object":         `[1]`,
	}
	for name, raw := range cases {
		t.Run(name, func(t *testing.T) {
			if _, err := Decode(json.RawMessage(raw)); err == nil {
				t.Error("want an error")
			}
		})
	}
}

func TestDecodeDropsDuplicates(t *testing.T) {
	raw := json.RawMessage(`{"candidates":[
		{"name":"Acme","website":"https://www.Acme.example/","country":"NL","reason":"first"},
		{"name":"Acme Holding","website":"http://acme.example/about","country":"NL","reason":"same host"},
		{"name":"Beta  GmbH","website":"","country":"DE","reason":"first beta"},
		{"name":" beta gmbh ","website":"","country":"DE","reason":"same name"},
		{"name":"Beta GmbH","website":"https://beta.example","country":"DE","reason":"own host"},
		{"name":"","website":"","country":"","reason":"no key one"},
		{"name":"","website":"","country":"","reason":"no key two"}
	]}`)
	got, err := Decode(raw)
	if err != nil {
		t.Fatal(err)
	}
	var reasons []string
	for _, c := range got {
		reasons = append(reasons, c.Reason)
	}
	want := []string{"first", "first beta", "own host", "no key one", "no key two"}
	if !reflect.DeepEqual(reasons, want) {
		t.Errorf("kept %q, want %q", reasons, want)
	}
}

func TestDedupeHostDoesNotEqualName(t *testing.T) {
	got := Dedupe([]Candidate{
		{Name: "acme.example", Reason: "by name"},
		{Name: "Other", Website: "acme.example", Reason: "by host"},
	})
	if len(got) != 2 {
		t.Errorf("kept %+v, want both", got)
	}
}

func TestDecodeReadsAndCleansContacts(t *testing.T) {
	got, err := Decode(json.RawMessage(`{"candidates":[
		{"name":"Acme","website":"https://acme.nl","country":"NL","reason":"r","summary":"","city":null,"size":null,
		 "contacts":[
		  {"name":"Jan","role":"Director","email":"jan@acme.nl","phone":null,"sourceUrl":"https://acme.nl/team"},
		  {"name":"Piet","role":null,"email":"piet@gmail.com","phone":null,"sourceUrl":"ftp://acme.nl"}]}]}`))
	if err != nil {
		t.Fatal(err)
	}
	if len(got) != 1 || len(got[0].Contacts) != 1 {
		t.Fatalf("got %+v", got)
	}
	c := got[0].Contacts[0]
	if c.Name != "Jan" || c.Role == nil || *c.Role != "Director" || c.Email == nil || *c.Email != "jan@acme.nl" ||
		c.Phone != nil || c.SourceURL != "https://acme.nl/team" {
		t.Errorf("contact = %+v", c)
	}
}
