package shrine

import (
	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/shrine/legalentity"
)

// legalEntityFlags are the field flags create and update share, one per field of a legal entity.
type legalEntityFlags struct {
	shortName, legalName, country, registrationNumber, vatNumber string
	street, postalCode, city, governingLaw, court                string
}

// bind registers the field flags on cmd. requiredNote marks the fields create needs; clearNote
// tells how update clears an optional field.
func (f *legalEntityFlags) bind(cmd *cobra.Command, requiredNote, clearNote string) {
	fl := cmd.Flags()
	fl.StringVar(&f.shortName, "short-name", "", "short name shown in pickers, unique in the organization, max 100"+requiredNote)
	fl.StringVar(&f.legalName, "legal-name", "", "full registered name, max 200"+requiredNote)
	fl.StringVar(&f.country, "country", "", "ISO 3166-1 alpha-3 country code, as NLD"+requiredNote)
	fl.StringVar(&f.registrationNumber, "registration-number", "", "trade register number, max 50"+clearNote)
	fl.StringVar(&f.vatNumber, "vat-number", "", "VAT number, max 50"+clearNote)
	fl.StringVar(&f.street, "street", "", "street and number, max 200"+clearNote)
	fl.StringVar(&f.postalCode, "postal-code", "", "postal code, max 20"+clearNote)
	fl.StringVar(&f.city, "city", "", "city, max 100"+clearNote)
	fl.StringVar(&f.governingLaw, "governing-law", "", "law the contracts name, max 200"+clearNote)
	fl.StringVar(&f.court, "court", "", "court the contracts name, max 200"+clearNote)
}

// input returns the values of the flags that were given; a flag not given stays nil, so that an
// empty value given on purpose is still seen.
func (f *legalEntityFlags) input(cmd *cobra.Command) legalentity.Input {
	given := func(name, value string) *string {
		if !cmd.Flags().Changed(name) {
			return nil
		}
		return &value
	}
	return legalentity.Input{
		ShortName:          given("short-name", f.shortName),
		LegalName:          given("legal-name", f.legalName),
		Country:            given("country", f.country),
		RegistrationNumber: given("registration-number", f.registrationNumber),
		VATNumber:          given("vat-number", f.vatNumber),
		Street:             given("street", f.street),
		PostalCode:         given("postal-code", f.postalCode),
		City:               given("city", f.city),
		GoverningLaw:       given("governing-law", f.governingLaw),
		Court:              given("court", f.court),
	}
}
