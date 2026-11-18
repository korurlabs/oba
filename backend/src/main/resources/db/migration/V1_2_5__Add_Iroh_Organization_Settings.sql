-- Create organization_settings table (1:1 with org)
CREATE TABLE iroh.organization_settings (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    base_currency   VARCHAR(3) NOT NULL DEFAULT 'EUR',
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_iroh_organization_settings PRIMARY KEY (id),
    CONSTRAINT fk_iroh_organization_settings_organization FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id),
    CONSTRAINT uk_iroh_organization_settings_organization UNIQUE (organization_id)
);

CREATE INDEX idx_iroh_organization_settings_organization ON iroh.organization_settings(organization_id);

-- Drop base_currency column from invoices
ALTER TABLE iroh.invoices DROP COLUMN base_currency;
