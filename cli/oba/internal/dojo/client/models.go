package client

import "encoding/json"

// User is the part of GET /users/me (UserDetailResponse) the CLI reads.
type User struct {
	ID                    string         `json:"id"`
	Email                 string         `json:"email"`
	DefaultOrganizationID *string        `json:"defaultOrganizationId"`
	Organizations         []Organization `json:"organizations"`
	Profile               *UserProfile   `json:"profile"`
}

// UserProfile is the name part of the signed-in user's profile; nil when the user has none.
type UserProfile struct {
	FirstName string `json:"firstName"`
	LastName  string `json:"lastName"`
}

// Organization is one membership of the signed-in user (UserOrganizationEmbeddedResponse):
// the organization, the user's role in it and the organization's status.
type Organization struct {
	ID     string `json:"id"`
	Name   string `json:"name"`
	Role   string `json:"role"`
	Status string `json:"organizationStatus"`
}

// Relation is one Otak relation as GET /otak/relations/{id} returns it (RelationDetailResponse).
// Values keeps every property as the API sent it, so that an update writes back the unchanged
// fields byte for byte; Type, Stage and Owner are read from it for the update steps.
type Relation struct {
	ID     string
	Type   string
	Stage  *string
	Owner  *RelationOwner
	Values map[string]json.RawMessage
}

// RelationOwner is the owner of a relation; the update request takes its id as ownerUserId.
type RelationOwner struct {
	ID string `json:"id"`
}

// UnmarshalJSON reads the typed fields and keeps every property in Values.
func (r *Relation) UnmarshalJSON(data []byte) error {
	var typed struct {
		ID    string         `json:"id"`
		Type  string         `json:"type"`
		Stage *string        `json:"stage"`
		Owner *RelationOwner `json:"owner"`
	}
	if err := json.Unmarshal(data, &typed); err != nil {
		return err
	}
	var values map[string]json.RawMessage
	if err := json.Unmarshal(data, &values); err != nil {
		return err
	}
	*r = Relation{ID: typed.ID, Type: typed.Type, Stage: typed.Stage, Owner: typed.Owner, Values: values}
	return nil
}

// Icp is one Ideal Customer Profile as GET /otak/icps lists it (IcpListResponse) and
// GET /otak/icps/{id} returns it (IcpDetailResponse); Description is set by the detail call
// only. Optional fields are nil when the profile leaves them open. Values keeps every property
// as the API sent it, so that an update writes back the unchanged fields byte for byte.
type Icp struct {
	ID               string                     `json:"id"`
	Name             string                     `json:"name"`
	Industry         string                     `json:"industry"`
	Country          *string                    `json:"country"`
	CompanySizeMin   *string                    `json:"companySizeMin"`
	CompanySizeMax   *string                    `json:"companySizeMax"`
	TargetPersonRole *string                    `json:"targetPersonRole"`
	Description      *string                    `json:"description"`
	Values           map[string]json.RawMessage `json:"-"`
}

// UnmarshalJSON reads the typed fields and keeps every property in Values.
func (i *Icp) UnmarshalJSON(data []byte) error {
	type plain Icp
	var typed plain
	if err := json.Unmarshal(data, &typed); err != nil {
		return err
	}
	if err := json.Unmarshal(data, &typed.Values); err != nil {
		return err
	}
	*i = Icp(typed)
	return nil
}

// RelationListItem is the part of one GET /otak/relations item (RelationListResponse) that
// identifies a known company: its name and website.
type RelationListItem struct {
	ID      string  `json:"id"`
	Name    string  `json:"name"`
	Website *string `json:"website"`
}

// LeadCreateRequest is the RelationCreateRequest body of a lead found by oba otak find.
// Website, Country, City and Size are left out when empty, so that the API stores null for them.
type LeadCreateRequest struct {
	Name    string `json:"name"`
	Type    string `json:"type"`
	Stage   string `json:"stage"`
	Source  string `json:"source"`
	Website string `json:"website,omitempty"`
	Country string `json:"country,omitempty"`
	City    string `json:"city,omitempty"`
	Size    string `json:"size,omitempty"`
	IcpID   string `json:"icpId"`
	Notes   string `json:"notes"`
}

// OrganizationProfile is the part of OrganizationDetailResponse, as GET /organizations/{id},
// POST /organizations and PUT /organizations/{id} return it, that identifies and describes the
// company: its id, what it is, where and how large. Optional fields are nil when unset. Values
// holds every property as the API sent it, so that an update writes back the unchanged fields
// byte for byte.
type OrganizationProfile struct {
	ID          string                     `json:"id"`
	Name        string                     `json:"name"`
	Description *string                    `json:"description"`
	Industry    *string                    `json:"industry"`
	CompanySize *string                    `json:"companySize"`
	Country     *string                    `json:"country"`
	Website     *string                    `json:"website"`
	Values      map[string]json.RawMessage `json:"-"`
}

// UnmarshalJSON reads the typed fields and keeps every property in Values.
func (o *OrganizationProfile) UnmarshalJSON(data []byte) error {
	type plain OrganizationProfile
	var typed plain
	if err := json.Unmarshal(data, &typed); err != nil {
		return err
	}
	if err := json.Unmarshal(data, &typed.Values); err != nil {
		return err
	}
	*o = OrganizationProfile(typed)
	return nil
}
