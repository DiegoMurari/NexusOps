-- ADR-013 fase B: o papel ADMIN foi semeado só com permissões de escopo GLOBAL/TENANT, sem as de escopo
-- TEAM/OWN (ex.: NOTIFICATION:READ:OWN). Como um papel só pode ser concedido por quem detém todas as suas
-- permissões, o ADMIN não conseguia criar analistas (AGENT) nem gerentes. Passa a ter tudo, exceto a
-- administração global de tenants, que continua exclusiva do SUPER_ADMIN.
INSERT INTO iam.role_permissions (role_id, permission)
SELECT '00000000-0000-0000-0000-000000000002', permission_key
FROM iam.permissions
WHERE permission_key NOT LIKE 'TENANT:%'
ON CONFLICT DO NOTHING;
