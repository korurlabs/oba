// Package member lists the members of a Kazan board, adds and removes them, and changes their
// role and specialties. Members are named by email or user id.
package member

// Specialties are the values of the API's BoardMemberSpecialtyType.
var Specialties = []string{
	"DEVELOPER",
	"CYBERSECURITY_SPECIALIST",
	"PRODUCT_OWNER",
	"SCRUM_MASTER",
	"QA_ENGINEER",
	"DEVOPS_ENGINEER",
	"DESIGNER",
	"ARCHITECT",
}
