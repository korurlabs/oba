/** Endpoints Otak calls; the current user (`/users/me`) lives in `@oba/shared`. */
export const API_ROUTES = {
  relations: {
    list: "/otak/relations",
    battlefield: "/otak/battlefield",
    ownerCandidates: "/otak/relations/owner-candidates",
    detail: (relationId: string) => `/otak/relations/${relationId}`,
    stage: (relationId: string) => `/otak/relations/${relationId}/stage`,
    type: (relationId: string) => `/otak/relations/${relationId}/type`,
  },
  contacts: {
    list: (relationId: string) => `/otak/relations/${relationId}/contacts`,
    detail: (relationId: string, contactId: string) =>
      `/otak/relations/${relationId}/contacts/${contactId}`,
  },
  icps: {
    list: "/otak/icps",
    detail: (icpId: string) => `/otak/icps/${icpId}`,
  },
  mailTemplates: {
    list: "/otak/mail-templates",
    detail: (mailTemplateId: string) => `/otak/mail-templates/${mailTemplateId}`,
  },
  documentTemplates: {
    list: "/document-templates",
    detail: (templateId: string) => `/document-templates/${templateId}`,
    files: (templateId: string) => `/document-templates/${templateId}/files`,
    attachments: (templateId: string) => `/document-templates/${templateId}/attachments`,
  },
  documents: {
    list: "/documents",
    detail: (documentId: string) => `/documents/${documentId}`,
    files: (documentId: string) => `/documents/${documentId}/files`,
  },
  legalEntities: {
    list: "/legal-entities",
  },
  outreaches: {
    list: "/otak/outreaches",
    detail: (outreachId: string) => `/otak/outreaches/${outreachId}`,
    send: (outreachId: string) => `/otak/outreaches/${outreachId}/send`,
    complete: (outreachId: string) => `/otak/outreaches/${outreachId}/complete`,
  },
  mailSenders: {
    list: "/otak/mail-senders",
    detail: (mailSenderId: string) => `/otak/mail-senders/${mailSenderId}`,
  },
  organizationMembers: {
    list: (organizationId: string) => `/organizations/${organizationId}/members`,
  },
  events: {
    list: "/otak/events",
    detail: (eventId: string) => `/otak/events/${eventId}`,
  },
} as const;
