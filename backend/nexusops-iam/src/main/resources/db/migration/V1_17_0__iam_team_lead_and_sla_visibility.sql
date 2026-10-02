-- Papéis operacionais.
-- 1) TEAM_LEAD foi semeado sem nenhuma permissão: quem tinha esse papel não lia nem um chamado. Passa a ter o mesmo
--    conjunto do AGENT (chamados, ativos, conhecimento, notificações) mais a leitura de relatórios.
-- 2) Quem atende precisa enxergar o SLA (violações e metas) para trabalhar nos prazos; a edição das definições
--    continua restrita a ADMIN/SUPER_ADMIN. AGENT, TEAM_LEAD e MANAGER ganham SLA:READ.
INSERT INTO iam.role_permissions (role_id, permission)
SELECT '00000000-0000-0000-0000-000000000004', permission_key FROM iam.permissions
WHERE scope IN ('TENANT', 'TEAM', 'OWN') AND resource IN ('TICKET', 'ASSET', 'KNOWLEDGE', 'NOTIFICATION')
ON CONFLICT DO NOTHING;

INSERT INTO iam.role_permissions (role_id, permission)
SELECT '00000000-0000-0000-0000-000000000004', permission_key FROM iam.permissions
WHERE permission_key = 'REPORT:READ:TENANT'
ON CONFLICT DO NOTHING;

INSERT INTO iam.role_permissions (role_id, permission)
SELECT r.id, 'SLA:READ:TENANT'
FROM (VALUES ('00000000-0000-0000-0000-000000000003'::uuid),
             ('00000000-0000-0000-0000-000000000004'::uuid),
             ('00000000-0000-0000-0000-000000000005'::uuid)) AS r(id)
WHERE EXISTS (SELECT 1 FROM iam.permissions WHERE permission_key = 'SLA:READ:TENANT')
ON CONFLICT DO NOTHING;
