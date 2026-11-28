-- Oba local development seed data
-- Creates a default organization for local development, with its EVERYONE group
-- and a grant of every app to that group.
-- Users are auto-created by the backend on first login via Keycloak.
-- After login, run: UPDATE shrine.users SET default_organization_id = 'b0000000-...' WHERE ...

DO $$
DECLARE
    v_org_id UUID := 'b0000000-0000-0000-0000-000000000001';
BEGIN
    -- Skip if organization already exists (idempotent)
    IF EXISTS (SELECT 1 FROM shrine.organizations WHERE id = v_org_id) THEN
        RAISE NOTICE 'Organization already exists, skipping seed data';
        RETURN;
    END IF;

    -- Create default organization
    INSERT INTO shrine.organizations (id, name, industry, country, status)
    VALUES (v_org_id, 'Sensey', 'Technology', 'NL', 'ACTIVE');

    RAISE NOTICE 'Seed data created: org=Sensey';
END $$;

-- A member opens an app only through a grant, and an organization grants every
-- app to its EVERYONE group by default. The backfill in
-- V1_2_111__Shrine_Create_Groups_And_App_Grants.sql writes these rows for the
-- organizations that exist when it runs; this seed runs after the migrations, so
-- it writes the same rows for its own organization. Each statement skips the
-- rows that exist, so these statements also complete a database seeded before
-- the groups existed.

INSERT INTO shrine.organization_groups (organization_id, name, type)
SELECT o.id, 'Everyone', 'EVERYONE'
FROM shrine.organizations o
WHERE o.id = 'b0000000-0000-0000-0000-000000000001'
  AND NOT EXISTS (
      SELECT 1
      FROM shrine.organization_groups g
      WHERE g.organization_id = o.id
        AND g.type = 'EVERYONE'
        AND g.deleted = FALSE
  );

INSERT INTO shrine.organization_app_grants (organization_id, app, group_id, granted_by_user_id)
SELECT g.organization_id, a.app, g.id, NULL
FROM shrine.organization_groups g
CROSS JOIN (SELECT unnest(enum_range(NULL::shrine_app_grant_app_enum)) AS app) a
WHERE g.organization_id = 'b0000000-0000-0000-0000-000000000001'
  AND g.type = 'EVERYONE'
  AND g.deleted = FALSE
  AND NOT EXISTS (
      SELECT 1
      FROM shrine.organization_app_grants x
      WHERE x.group_id = g.id
        AND x.app = a.app
        AND x.deleted = FALSE
  );

-- Otak sample data, so the Relations and Outreaches pages show rows in a fresh
-- local stack. Every company, person and address below is fictional: websites
-- use the reserved .example domain and mail addresses use example.com. Fixed ids
-- and ON CONFLICT DO NOTHING let this section run again without duplicates. The
-- owner is the first user when one exists; the seed normally runs before anyone
-- has signed in, so the owner stays empty there.

INSERT INTO otak.relations (id, organization_id, name, website, sector, city, country, type, stage, size, source, expected_value, notes, owner_user_id)
SELECT v.id::uuid, 'b0000000-0000-0000-0000-000000000001', v.name, v.website, v.sector, v.city,
       v.country::otak_country_enum, v.type::otak_relation_type_enum, v.stage::otak_pipeline_stage_enum,
       v.size::otak_relation_size_enum, v.source::otak_relation_source_enum, v.expected_value::numeric, v.notes,
       (SELECT u.id FROM shrine.users u ORDER BY u.created_at LIMIT 1)
FROM (VALUES
    ('c1000000-0000-0000-0000-000000000001', 'Kolibrex Logistics', 'https://kolibrex.example', 'Logistics', 'Rotterdam', 'NL', 'LEAD', 'NEW', 'SIZE_51_200', 'RESEARCH', '12000', 'Found through a port logistics directory.'),
    ('c1000000-0000-0000-0000-000000000002', 'Dunevaart Retail', 'https://dunevaart.example', 'Retail', 'Utrecht', 'NL', 'LEAD', 'CONTACTED', 'SIZE_201_500', 'LINKEDIN', '18000', 'Asked for a short security scan offer.'),
    ('c1000000-0000-0000-0000-000000000003', 'Peltrino Health', 'https://peltrino.example', 'Healthcare', 'Antwerp', 'BE', 'LEAD', 'WARM', 'SIZE_11_50', 'REFERRAL', '9500', 'Referred by an existing customer.'),
    ('c1000000-0000-0000-0000-000000000004', 'Ostravel Finance', 'https://ostravel.example', 'Finance', 'Amsterdam', 'NL', 'LEAD', 'PROPOSAL', 'SIZE_501_1000', 'EVENT', '42000', 'Proposal sent for an ISO 27001 readiness track.'),
    ('c1000000-0000-0000-0000-000000000005', 'Marquill Energy', 'https://marquill.example', 'Energy', 'Eindhoven', 'NL', 'LEAD', 'LOST', 'SIZE_1001_5000', 'INBOUND', '60000', 'Chose an in-house team.'),
    ('c1000000-0000-0000-0000-000000000006', 'Glimmerhof Construction', 'https://glimmerhof.example', 'Construction', 'Istanbul', 'TR', 'LEAD', 'PARKED', 'SIZE_51_200', 'NETWORK', '15000', 'Revisit after their merger closes.'),
    ('c1000000-0000-0000-0000-000000000007', 'Fenvara Software', 'https://fenvara.example', 'Software', 'Delft', 'NL', 'CUSTOMER', 'CLOSED', 'SIZE_11_50', 'LINKEDIN', '24000', 'Yearly penetration test.'),
    ('c1000000-0000-0000-0000-000000000008', 'Quillmoor Insurance', 'https://quillmoor.example', 'Insurance', 'Ghent', 'BE', 'CUSTOMER', 'CLOSED', 'SIZE_201_500', 'PARTNER', '36000', 'Managed security monitoring contract.'),
    ('c1000000-0000-0000-0000-000000000009', 'Ruskwell Legal', 'https://ruskwell.example', 'Legal', 'The Hague', 'NL', 'CUSTOMER', 'DIRECT', 'SIZE_1_10', 'REFERRAL', '6000', 'Signed directly without a pipeline.'),
    ('c1000000-0000-0000-0000-000000000010', 'Brakkenveld Hosting', 'https://brakkenveld.example', 'IT services', 'Groningen', 'NL', 'SUPPLIER', NULL, 'SIZE_51_200', 'NETWORK', NULL, 'Hosts the scanning infrastructure.'),
    ('c1000000-0000-0000-0000-000000000011', 'Talvora Translations', 'https://talvora.example', 'Media', 'Ankara', 'TR', 'SUPPLIER', NULL, 'SIZE_1_10', 'OTHER', NULL, 'Translates reports into Turkish.'),
    ('c1000000-0000-0000-0000-000000000012', 'Wendelstok Accounting', 'https://wendelstok.example', 'Accounting', 'Haarlem', 'NL', 'SUPPLIER', NULL, 'SIZE_11_50', 'NETWORK', NULL, 'Bookkeeping and VAT returns.')
) AS v(id, name, website, sector, city, country, type, stage, size, source, expected_value, notes)
WHERE EXISTS (SELECT 1 FROM shrine.organizations o WHERE o.id = 'b0000000-0000-0000-0000-000000000001')
ON CONFLICT (id) DO NOTHING;

INSERT INTO otak.contacts (id, organization_id, relation_id, name, role, email, email_type, phone, decision_maker, main_person_contact)
SELECT v.id::uuid, 'b0000000-0000-0000-0000-000000000001', v.relation_id::uuid, v.name, v.role, v.email,
       'COMPANY'::otak_contact_email_type_enum, v.phone, v.decision_maker, v.main_person_contact
FROM (VALUES
    ('c2000000-0000-0000-0000-000000000001', 'c1000000-0000-0000-0000-000000000001', 'Sanne Verkade', 'IT Manager', 'sanne.verkade@example.com', '+31 6 1000 0001', TRUE, TRUE),
    ('c2000000-0000-0000-0000-000000000002', 'c1000000-0000-0000-0000-000000000002', 'Jeroen Bakkum', 'CTO', 'jeroen.bakkum@example.com', '+31 6 1000 0002', TRUE, TRUE),
    ('c2000000-0000-0000-0000-000000000003', 'c1000000-0000-0000-0000-000000000002', 'Ilse Morren', 'Operations', 'ilse.morren@example.com', '+31 6 1000 0003', FALSE, FALSE),
    ('c2000000-0000-0000-0000-000000000004', 'c1000000-0000-0000-0000-000000000003', 'Lotte Claes', 'CEO', 'lotte.claes@example.com', '+32 470 10 00 04', TRUE, TRUE),
    ('c2000000-0000-0000-0000-000000000005', 'c1000000-0000-0000-0000-000000000004', 'Ruben Dekkers', 'CISO', 'ruben.dekkers@example.com', '+31 6 1000 0005', TRUE, TRUE),
    ('c2000000-0000-0000-0000-000000000006', 'c1000000-0000-0000-0000-000000000004', 'Femke Aalders', 'Finance', 'femke.aalders@example.com', '+31 6 1000 0006', FALSE, FALSE),
    ('c2000000-0000-0000-0000-000000000007', 'c1000000-0000-0000-0000-000000000005', 'Pieter Wolthuis', 'CIO', 'pieter.wolthuis@example.com', '+31 6 1000 0007', TRUE, TRUE),
    ('c2000000-0000-0000-0000-000000000008', 'c1000000-0000-0000-0000-000000000006', 'Emre Kalaycı', 'Owner', 'emre.kalayci@example.com', '+90 532 100 00 08', TRUE, TRUE),
    ('c2000000-0000-0000-0000-000000000009', 'c1000000-0000-0000-0000-000000000007', 'Noor Hendriks', 'CTO', 'noor.hendriks@example.com', '+31 6 1000 0009', TRUE, TRUE),
    ('c2000000-0000-0000-0000-000000000010', 'c1000000-0000-0000-0000-000000000008', 'Wout Peeters', 'IT Manager', 'wout.peeters@example.com', '+32 470 10 00 10', TRUE, TRUE),
    ('c2000000-0000-0000-0000-000000000011', 'c1000000-0000-0000-0000-000000000008', 'An Vermeulen', 'CISO', 'an.vermeulen@example.com', '+32 470 10 00 11', FALSE, FALSE),
    ('c2000000-0000-0000-0000-000000000012', 'c1000000-0000-0000-0000-000000000009', 'Mirjam de Groot', 'Owner', 'mirjam.degroot@example.com', '+31 6 1000 0012', TRUE, TRUE),
    ('c2000000-0000-0000-0000-000000000013', 'c1000000-0000-0000-0000-000000000010', 'Thijs Oosterhuis', 'Account manager', 'thijs.oosterhuis@example.com', '+31 6 1000 0013', FALSE, TRUE),
    ('c2000000-0000-0000-0000-000000000014', 'c1000000-0000-0000-0000-000000000011', 'Zeynep Aydın', 'Owner', 'zeynep.aydin@example.com', '+90 532 100 00 14', TRUE, TRUE),
    ('c2000000-0000-0000-0000-000000000015', 'c1000000-0000-0000-0000-000000000012', 'Karin Smeets', 'Accountant', 'karin.smeets@example.com', '+31 6 1000 0015', FALSE, TRUE)
) AS v(id, relation_id, name, role, email, phone, decision_maker, main_person_contact)
WHERE EXISTS (SELECT 1 FROM otak.relations r WHERE r.id = v.relation_id::uuid)
ON CONFLICT (id) DO NOTHING;

-- A sent mail names the mailbox it went out from. added_by holds the user who
-- connected the mailbox; before anyone has signed in there is no user, so the
-- seed organization's own id stands in for one.
INSERT INTO otak.mail_senders (id, organization_id, mail_address, type, added_by)
SELECT 'c5000000-0000-0000-0000-000000000001', o.id, 'sales@example.com', 'SHARED'::otak_email_type_enum,
       COALESCE((SELECT u.id FROM shrine.users u ORDER BY u.created_at LIMIT 1), o.id)
FROM shrine.organizations o
WHERE o.id = 'b0000000-0000-0000-0000-000000000001'
ON CONFLICT DO NOTHING;

-- Every MAIL outreach carries its own mail; WHATSAPP and CALL outreaches have none.
INSERT INTO otak.mails (id, organization_id, relation_id, subject, body, language)
SELECT v.id::uuid, 'b0000000-0000-0000-0000-000000000001', v.relation_id::uuid, v.subject, v.body,
       'EN'::shrine_document_language_enum
FROM (VALUES
    ('c3000000-0000-0000-0000-000000000001', 'c1000000-0000-0000-0000-000000000002', 'A quick security scan for Dunevaart',
     'Hi Jeroen,' || chr(10) || chr(10) || 'As discussed, here is how a one-week external scan of your web shop would work.' || chr(10) || chr(10) || 'Kind regards'),
    ('c3000000-0000-0000-0000-000000000002', 'c1000000-0000-0000-0000-000000000004', 'Follow-up on the ISO 27001 readiness proposal',
     'Hi Ruben,' || chr(10) || chr(10) || 'Do you have any questions on the proposal we sent last week?' || chr(10) || chr(10) || 'Kind regards')
) AS v(id, relation_id, subject, body)
WHERE EXISTS (SELECT 1 FROM otak.relations r WHERE r.id = v.relation_id::uuid)
ON CONFLICT (id) DO NOTHING;

INSERT INTO otak.outreaches (id, organization_id, relation_id, contact_id, title, type, status, sent_to, sent_at, mail_id, mail_sender_id)
SELECT v.id::uuid, 'b0000000-0000-0000-0000-000000000001', v.relation_id::uuid, v.contact_id::uuid, v.title,
       v.type::otak_outreach_type_enum, v.status::otak_outreach_status_enum, v.sent_to, v.sent_at::timestamptz,
       v.mail_id::uuid, (SELECT s.id FROM otak.mail_senders s WHERE s.id = v.mail_sender_id::uuid)
FROM (VALUES
    ('c4000000-0000-0000-0000-000000000001', 'c1000000-0000-0000-0000-000000000002', 'c2000000-0000-0000-0000-000000000002', 'Security scan offer', 'MAIL', 'SENT', 'jeroen.bakkum@example.com', '2026-11-20T09:30:00Z', 'c3000000-0000-0000-0000-000000000001', 'c5000000-0000-0000-0000-000000000001'),
    ('c4000000-0000-0000-0000-000000000002', 'c1000000-0000-0000-0000-000000000004', 'c2000000-0000-0000-0000-000000000005', 'Proposal follow-up', 'MAIL', 'DRAFT', NULL, NULL, 'c3000000-0000-0000-0000-000000000002', NULL),
    ('c4000000-0000-0000-0000-000000000003', 'c1000000-0000-0000-0000-000000000003', 'c2000000-0000-0000-0000-000000000004', 'Intro after referral', 'WHATSAPP', 'SENT', '+32 470 10 00 04', '2026-11-24T14:05:00Z', NULL, NULL),
    ('c4000000-0000-0000-0000-000000000004', 'c1000000-0000-0000-0000-000000000006', 'c2000000-0000-0000-0000-000000000008', 'Check in after the merger', 'WHATSAPP', 'DRAFT', NULL, NULL, NULL, NULL),
    ('c4000000-0000-0000-0000-000000000005', 'c1000000-0000-0000-0000-000000000001', 'c2000000-0000-0000-0000-000000000001', 'First call', 'CALL', 'DRAFT', NULL, NULL, NULL, NULL),
    ('c4000000-0000-0000-0000-000000000006', 'c1000000-0000-0000-0000-000000000004', 'c2000000-0000-0000-0000-000000000005', 'Proposal walkthrough call', 'CALL', 'SENT', '+31 6 1000 0005', '2026-11-18T10:00:00Z', NULL, NULL)
) AS v(id, relation_id, contact_id, title, type, status, sent_to, sent_at, mail_id, mail_sender_id)
WHERE EXISTS (SELECT 1 FROM otak.relations r WHERE r.id = v.relation_id::uuid)
ON CONFLICT (id) DO NOTHING;
