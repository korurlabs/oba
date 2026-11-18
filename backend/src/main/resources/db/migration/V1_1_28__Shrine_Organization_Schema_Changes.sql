-- Add description column to organizations
ALTER TABLE shrine.organizations ADD COLUMN description VARCHAR(512) NULL;

-- Drop redundant company_name column (name field already serves this purpose)
ALTER TABLE shrine.organizations DROP COLUMN company_name;
