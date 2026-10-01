-- The authenticated principal name (an e-mail address) is what SecurityUtils.getCurrentUserId()
-- returns, and it is persisted into author/owner/created_by style columns that were declared
-- VARCHAR(36). E-mails longer than 36 characters caused HTTP 500 on create.
-- Widen every such column, in every module schema, to VARCHAR(255). Idempotent and safe when a
-- module's tables do not exist. The iam schema is excluded (its user references are real UUID FKs).
DO $$
DECLARE
    col RECORD;
BEGIN
    FOR col IN
        SELECT c.table_schema, c.table_name, c.column_name
        FROM information_schema.columns c
        JOIN information_schema.tables t
          ON t.table_schema = c.table_schema AND t.table_name = c.table_name
             AND t.table_type = 'BASE TABLE'
        -- partitions/child tables inherit the type from their parent; altering the parent suffices
        WHERE NOT EXISTS (
                SELECT 1 FROM pg_inherits i
                JOIN pg_class pc ON pc.oid = i.inhrelid
                JOIN pg_namespace pn ON pn.oid = pc.relnamespace
                WHERE pn.nspname = c.table_schema AND pc.relname = c.table_name)
          AND c.table_schema IN ('ticketing', 'sla', 'platform', 'asset', 'knowledge',
                                 'notification', 'reporting')
          AND c.column_name IN ('created_by', 'updated_by', 'author_id', 'owner_id', 'published_by',
                              'user_id', 'uploaded_by', 'assigned_to_id', 'assignee_id',
                              'reporter_id', 'target_user_id')
          AND c.data_type = 'character varying'
          AND c.character_maximum_length = 36
    LOOP
        EXECUTE format('ALTER TABLE %I.%I ALTER COLUMN %I TYPE VARCHAR(255)',
                       col.table_schema, col.table_name, col.column_name);
    END LOOP;
END $$;
