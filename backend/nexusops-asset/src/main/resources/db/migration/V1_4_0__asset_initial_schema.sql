-- NexusOps Asset Module - Initial Schema
-- Version: 1.0.0
-- Description: Create Asset schema and tables

CREATE SCHEMA IF NOT EXISTS asset;

-- Assets table
CREATE TABLE asset.assets (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_tag VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    type VARCHAR(20) NOT NULL,
    lifecycle_status VARCHAR(20) NOT NULL DEFAULT 'PROCURED',
    tenant_id VARCHAR(36) NOT NULL,
    manufacturer VARCHAR(100),
    model VARCHAR(100),
    serial_number VARCHAR(100),
    specifications JSONB,
    location_id VARCHAR(36),
    assigned_to_id VARCHAR(36),
    purchase_date TIMESTAMP WITH TIME ZONE,
    warranty_expiration TIMESTAMP WITH TIME ZONE,
    purchase_cost DECIMAL(15,2),
    depreciation_method VARCHAR(20),
    discovery_source VARCHAR(50),
    last_discovered_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(36),
    updated_by VARCHAR(36),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_assets_tenant ON asset.assets(tenant_id);
CREATE INDEX idx_assets_type ON asset.assets(type);
CREATE INDEX idx_assets_status ON asset.assets(lifecycle_status);
CREATE INDEX idx_assets_location ON asset.assets(location_id);
CREATE INDEX idx_assets_serial ON asset.assets(serial_number);
CREATE INDEX idx_assets_assigned ON asset.assets(assigned_to_id);

-- CI Relationships table
CREATE TABLE asset.ci_relationships (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id VARCHAR(36) NOT NULL,
    target_id VARCHAR(36) NOT NULL,
    relationship_type VARCHAR(20) NOT NULL,
    tenant_id VARCHAR(36) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(36)
);

CREATE INDEX idx_ci_rel_source ON asset.ci_relationships(source_id);
CREATE INDEX idx_ci_rel_target ON asset.ci_relationships(target_id);
CREATE INDEX idx_ci_rel_type ON asset.ci_relationships(relationship_type);
CREATE INDEX idx_ci_rel_tenant ON asset.ci_relationships(tenant_id);

-- Software Licenses table
CREATE TABLE asset.software_licenses (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    vendor VARCHAR(100),
    product_version VARCHAR(50),
    license_type VARCHAR(20) NOT NULL DEFAULT 'PERPETUAL',
    tenant_id VARCHAR(36) NOT NULL,
    total_seats INTEGER NOT NULL DEFAULT 0,
    used_seats INTEGER NOT NULL DEFAULT 0,
    compliance_status VARCHAR(20) NOT NULL DEFAULT 'COMPLIANT',
    entitlements JSONB,
    purchase_date TIMESTAMP WITH TIME ZONE,
    expiration_date TIMESTAMP WITH TIME ZONE,
    renewal_date TIMESTAMP WITH TIME ZONE,
    cost DECIMAL(15,2),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(36),
    updated_by VARCHAR(36),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_sw_lic_tenant ON asset.software_licenses(tenant_id);
CREATE INDEX idx_sw_lic_product ON asset.software_licenses(product_name);
CREATE INDEX idx_sw_lic_compliance ON asset.software_licenses(compliance_status);

-- Discovery Jobs table
CREATE TABLE asset.discovery_jobs (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    tenant_id VARCHAR(36) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    configuration JSONB,
    normalization_rules JSONB,
    schedule_cron VARCHAR(100),
    last_run_at TIMESTAMP WITH TIME ZONE,
    next_run_at TIMESTAMP WITH TIME ZONE,
    last_run_status VARCHAR(20),
    last_run_duration_seconds BIGINT,
    assets_discovered INTEGER NOT NULL DEFAULT 0,
    assets_updated INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(36),
    updated_by VARCHAR(36),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_discovery_job_tenant ON asset.discovery_jobs(tenant_id);
CREATE INDEX idx_discovery_job_status ON asset.discovery_jobs(status);
CREATE INDEX idx_discovery_job_source ON asset.discovery_jobs(source_type);

-- Locations table
CREATE TABLE asset.locations (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    tenant_id VARCHAR(36) NOT NULL,
    parent_id VARCHAR(36),
    type VARCHAR(20) NOT NULL DEFAULT 'SITE',
    address TEXT,
    coordinates VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(36),
    updated_by VARCHAR(36),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_locations_tenant ON asset.locations(tenant_id);
CREATE INDEX idx_locations_parent ON asset.locations(parent_id);