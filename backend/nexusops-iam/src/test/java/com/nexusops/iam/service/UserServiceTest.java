package com.nexusops.iam.service;

import com.nexusops.iam.domain.Role;
import com.nexusops.iam.domain.User;
import com.nexusops.iam.dto.CreateUserRequest;
import com.nexusops.iam.infrastructure.repository.RoleRepository;
import com.nexusops.iam.infrastructure.repository.UserRepository;
import com.nexusops.iam.mapper.UserMapperImpl;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
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
class UserServiceTest {

    private static final String TENANT = "tenant-1";
    private static final Set<String> CALLER = Set.of("TICKET:READ:TENANT", "USER:CREATE:TENANT");

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(userRepository, roleRepository, new UserMapperImpl(), passwordEncoder);
    }

    private CreateUserRequest request(Set<String> roles) {
        return CreateUserRequest.builder().email("new@example.com").password("S3cretPass!")
            .tenantId("attacker-tenant").roles(roles).build();
    }

    private Role role(String name, boolean system, String tenant, Set<String> permissions) {
        return Role.builder().id(UUID.randomUUID()).name(name).isSystem(system).tenantId(tenant)
            .permissions(new HashSet<>(permissions)).build();
    }

    @Test
    void create_usesCallerTenant_andDefaultRoleWhenNoneGiven() {
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.createUser(request(null), "admin@example.com", TENANT, CALLER);

        assertThat(response.getTenantId()).isEqualTo(TENANT);
        assertThat(response.getRoles()).containsExactly("END_USER");
    }

    @Test
    void create_refusesRoleWithPermissionsCallerDoesNotHold() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(roleRepository.findByName("SUPER_ADMIN")).thenReturn(Optional.of(
            role("SUPER_ADMIN", true, "sys", Set.of("TICKET:READ:TENANT", "TENANT:DELETE:GLOBAL"))));

        assertThatThrownBy(() -> service.createUser(request(Set.of("SUPER_ADMIN")), "a", TENANT, CALLER))
            .isInstanceOf(ValidationException.class).hasMessageContaining("do not hold");
        verify(userRepository, never()).save(any());
    }

    @Test
    void create_refusesUnknownOrForeignTenantRole() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(roleRepository.findByName("GHOST")).thenReturn(Optional.empty());
        when(roleRepository.findByName("OTHER")).thenReturn(Optional.of(role("OTHER", false, "tenant-2", Set.of())));

        assertThatThrownBy(() -> service.createUser(request(Set.of("GHOST")), "a", TENANT, CALLER))
            .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.createUser(request(Set.of("OTHER")), "a", TENANT, CALLER))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    void create_acceptsRoleWithinCallerCeiling() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(roleRepository.findByName("VIEWER")).thenReturn(Optional.of(role("VIEWER", false, TENANT, Set.of("TICKET:READ:TENANT"))));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.createUser(request(Set.of("VIEWER")), "a", TENANT, CALLER);

        assertThat(response.getRoles()).containsExactly("VIEWER");
    }

    @Test
    void userOfAnotherTenant_isNotFound() {
        User foreign = new User();
        foreign.setId(UUID.randomUUID());
        foreign.setTenantId("tenant-2");
        when(userRepository.findById(foreign.getId())).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.getUserById(foreign.getId(), TENANT)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.deleteUser(foreign.getId(), TENANT)).isInstanceOf(ResourceNotFoundException.class);
        verify(userRepository, never()).delete(any());
    }
}
