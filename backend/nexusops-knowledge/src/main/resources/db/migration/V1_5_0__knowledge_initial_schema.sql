-- NexusOps Knowledge Module - Initial Schema
-- Version: 1.0.0
-- Description: Create Knowledge schema and tables

CREATE SCHEMA IF NOT EXISTS knowledge;

-- Categories table
CREATE TABLE knowledge.categories (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    tenant_id VARCHAR(36) NOT NULL,
    parent_id VARCHAR(36),
    icon VARCHAR(100),
    color VARCHAR(7),
    sort_order INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    article_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(36),
    updated_by VARCHAR(36),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_kb_categories_tenant_slug UNIQUE (tenant_id, slug)
);

CREATE INDEX idx_kb_categories_tenant ON knowledge.categories(tenant_id);
CREATE INDEX idx_kb_categories_parent ON knowledge.categories(parent_id);

-- Articles table
CREATE TABLE knowledge.articles (
    id VARCHAR(36) PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    slug VARCHAR(500) NOT NULL,
    content TEXT,
    content_html TEXT,
    excerpt TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    tenant_id VARCHAR(36) NOT NULL,
    category_id VARCHAR(36),
    author_id VARCHAR(36) NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    published_at TIMESTAMP WITH TIME ZONE,
    published_by VARCHAR(36),
    seo_title VARCHAR(255),
    seo_description VARCHAR(500),
    seo_keywords VARCHAR(500),
    view_count BIGINT NOT NULL DEFAULT 0,
    helpful_count BIGINT NOT NULL DEFAULT 0,
    not_helpful_count BIGINT NOT NULL DEFAULT 0,
    featured BOOLEAN NOT NULL DEFAULT FALSE,
    allow_comments BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(36),
    updated_by VARCHAR(36),
    jpa_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_articles_tenant_slug UNIQUE (tenant_id, slug)
);

CREATE INDEX idx_articles_tenant ON knowledge.articles(tenant_id);
CREATE INDEX idx_articles_status ON knowledge.articles(status);
CREATE INDEX idx_articles_category ON knowledge.articles(category_id);
CREATE INDEX idx_articles_author ON knowledge.articles(author_id);

-- Article Tags junction table
CREATE TABLE knowledge.article_tags (
    article_id VARCHAR(36) NOT NULL REFERENCES knowledge.articles(id) ON DELETE CASCADE,
    tag VARCHAR(100) NOT NULL,
    PRIMARY KEY (article_id, tag)
);

-- Article Translations table
CREATE TABLE knowledge.article_translations (
    article_id VARCHAR(36) NOT NULL REFERENCES knowledge.articles(id) ON DELETE CASCADE,
    locale VARCHAR(10) NOT NULL,
    translation JSONB,
    PRIMARY KEY (article_id, locale)
);

-- Tags table
CREATE TABLE knowledge.tags (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    tenant_id VARCHAR(36) NOT NULL,
    description VARCHAR(500),
    usage_count INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uq_tags_tenant_name UNIQUE (tenant_id, name)
);

CREATE INDEX idx_tags_tenant ON knowledge.tags(tenant_id);

-- Article Feedback table
CREATE TABLE knowledge.article_feedback (
    id VARCHAR(36) PRIMARY KEY,
    article_id VARCHAR(36) NOT NULL REFERENCES knowledge.articles(id) ON DELETE CASCADE,
    tenant_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    helpful BOOLEAN NOT NULL,
    comment TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_feedback_article ON knowledge.article_feedback(article_id);
CREATE INDEX idx_feedback_user ON knowledge.article_feedback(user_id);
CREATE INDEX idx_feedback_tenant ON knowledge.article_feedback(tenant_id);
