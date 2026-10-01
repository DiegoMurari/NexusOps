package com.nexusops.iam.service;

import com.nexusops.iam.domain.Permission;
import com.nexusops.iam.domain.Role;
import com.nexusops.iam.dto.CreateRoleRequest;
import com.nexusops.iam.dto.UpdateRoleRequest;
import com.nexusops.iam.infrastructure.repository.PermissionRepository;
import com.nexusops.iam.infrastructure.repository.RoleRepository;
import com.nexusops.iam.infrastructure.repository.UserRepository;
import com.nexusops.iam.mapper.RoleMapperImpl;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    private static final String TENANT = "tenant-1";
    private static final Set<String> CALLER = Set.of("TICKET:READ:TENANT", "TICKET:UPDATE:TENANT");

    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PermissionRepository permissionRepository;
    @Mock
    private UserRepository userRepository;

    private RoleService service;

    @BeforeEach
    void setUp() {
        service = new RoleService(roleRepository, permissionRepository, userRepository, new RoleMapperImpl());
    }

    private Permission permission(String key) {
        Permission p = new Permission();
        p.setPermissionKey(key);
        return p;
    }

    private Role custom(String tenant) {
        return Role.builder().id(UUID.randomUUID()).name("QA_LEAD").tenantId(tenant).isSystem(false)
            .permissions(new HashSet<>(Set.of("TICKET:READ:TENANT"))).build();
    }

    @Test
    void create_forcesTenantFromServer_normalizesName_andIsNotSystem() {
        when(roleRepository.findByName("QA_LEAD")).thenReturn(Optional.empty());
        when(permissionRepository.findByKeys(any())).thenReturn(List.of(permission("TICKET:READ:TENANT")));
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.createRole(CreateRoleRequest.builder().name(" qa_lead ").tenantId("other-tenant")
            .permissions(Set.of("TICKET:READ:TENANT")).build(), TENANT, CALLER);

        assertThat(response.getName()).isEqualTo("QA_LEAD");
        assertThat(response.getTenantId()).isEqualTo(TENANT);
        assertThat(response.getIsSystem()).isFalse();
        assertThat(response.getPermissions()).containsExactly("TICKET:READ:TENANT");
    }

    @Test
    void create_rejectsBadNamesAndDuplicates() {
        assertThatThrownBy(() -> service.createRole(CreateRoleRequest.builder().name("bad name!").build(), TENANT, CALLER))
            .isInstanceOf(ValidationException.class);
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(custom(TENANT)));
        assertThatThrownBy(() -> service.createRole(CreateRoleRequest.builder().name("admin").build(), TENANT, CALLER))
            .isInstanceOf(ValidationException.class);
        verify(roleRepository, never()).save(any());
    }

    @Test
    void create_refusesToGrantPermissionCallerDoesNotHold() {
        when(roleRepository.findByName("QA_LEAD")).thenReturn(Optional.empty());
        when(permissionRepository.findByKeys(any())).thenReturn(List.of(permission("ROLE:UPDATE:TENANT")));

        assertThatThrownBy(() -> service.createRole(CreateRoleRequest.builder().name("QA_LEAD")
            .permissions(Set.of("ROLE:UPDATE:TENANT")).build(), TENANT, CALLER))
            .isInstanceOf(ValidationException.class).hasMessageContaining("do not hold");
        verify(roleRepository, never()).save(any());
    }

    @Test
    void create_refusesUnknownPermission() {
        when(roleRepository.findByName("QA_LEAD")).thenReturn(Optional.empty());
        when(permissionRepository.findByKeys(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.createRole(CreateRoleRequest.builder().name("QA_LEAD")
            .permissions(Set.of("TICKET:READ:TENANT")).build(), TENANT, CALLER))
            .isInstanceOf(ValidationException.class).hasMessageContaining("Unknown");
    }

    @Test
    void systemRoles_areImmutable() {
        Role system = Role.builder().id(UUID.randomUUID()).name("ADMIN").tenantId("sys").isSystem(true).build();
        when(roleRepository.findById(system.getId())).thenReturn(Optional.of(system));

        assertThatThrownBy(() -> service.updateRole(system.getId(), new UpdateRoleRequest(), TENANT, CALLER))
            .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.deleteRole(system.getId(), TENANT)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.removePermission(system.getId(), "X", TENANT)).isInstanceOf(ValidationException.class);
        verify(roleRepository, never()).save(any());
        verify(roleRepository, never()).delete(any());
    }

    @Test
    void customRoleOfAnotherTenant_isNotFound() {
        Role foreign = custom("tenant-2");
        when(roleRepository.findById(foreign.getId())).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.getRoleById(foreign.getId(), TENANT)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.deleteRole(foreign.getId(), TENANT)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_replacesPermissionsWithinCallerCeiling() {
        Role role = custom(TENANT);
        when(roleRepository.findById(role.getId())).thenReturn(Optional.of(role));
        when(permissionRepository.findByKeys(any())).thenReturn(List.of(permission("TICKET:UPDATE:TENANT")));
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.updateRole(role.getId(), UpdateRoleRequest.builder().description("QA")
            .permissions(Set.of("TICKET:UPDATE:TENANT")).build(), TENANT, CALLER);

        assertThat(response.getPermissions()).containsExactly("TICKET:UPDATE:TENANT");
        assertThat(response.getDescription()).isEqualTo("QA");
    }

    @Test
    void delete_isBlockedWhileAssignedToUsers() {
        Role role = custom(TENANT);
        when(roleRepository.findById(role.getId())).thenReturn(Optional.of(role));
        when(userRepository.countByRole("QA_LEAD")).thenReturn(2L);

        assertThatThrownBy(() -> service.deleteRole(role.getId(), TENANT)).isInstanceOf(ValidationException.class);
        verify(roleRepository, never()).delete(any());
    }

    @Test
    void delete_removesUnusedCustomRole() {
        Role role = custom(TENANT);
        when(roleRepository.findById(role.getId())).thenReturn(Optional.of(role));
        when(userRepository.countByRole("QA_LEAD")).thenReturn(0L);

        service.deleteRole(role.getId(), TENANT);

        verify(roleRepository).delete(role);
    }
}
