package com.nexusops.iam.service;

import com.nexusops.iam.domain.Role;
import com.nexusops.iam.domain.User;
import com.nexusops.iam.dto.CreateUserRequest;
import com.nexusops.iam.dto.UpdateProfileRequest;
import com.nexusops.iam.dto.UpdateUserRequest;
import com.nexusops.iam.infrastructure.repository.RoleRepository;
import com.nexusops.iam.infrastructure.repository.UserRepository;
import com.nexusops.iam.mapper.UserMapperImpl;
import com.nexusops.shared.directory.LocationDirectory;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
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
    private static final Set<String> ADMIN_CALLER = Set.of("ADMIN");
    private static final Set<String> SUPER_CALLER = Set.of("SUPER_ADMIN");
    private static final Set<String> NO_ROLES = Set.of();

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private LocationDirectory locationDirectory;

    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(userRepository, roleRepository, new UserMapperImpl(), passwordEncoder, locationDirectory);
    }

    private CreateUserRequest request(Set<String> roles) {
        return CreateUserRequest.builder().email("new@example.com").password("S3cretPass!")
            .tenantId("attacker-tenant").roles(roles).build();
    }

    private Role role(String name, boolean system, String tenant, Set<String> permissions) {
        return Role.builder().id(UUID.randomUUID()).name(name).isSystem(system).tenantId(tenant)
            .permissions(new HashSet<>(permissions)).build();
    }

    private User user(String email, Set<String> roles) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail(email);
        u.setTenantId(TENANT);
        u.setFirstName("Ana");
        u.setRoles(new HashSet<>(roles));
        when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));
        return u;
    }

    // ---- criação -------------------------------------------------------------------------------

    @Test
    void create_usesCallerTenant_andDefaultRoleWhenNoneGiven() {
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.createUser(request(null), "admin@example.com", TENANT, CALLER, NO_ROLES);

        assertThat(response.getTenantId()).isEqualTo(TENANT);
        assertThat(response.getRoles()).containsExactly("END_USER");
    }

    @Test
    void create_refusesRoleWithPermissionsCallerDoesNotHold() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(roleRepository.findByName("SUPER_ADMIN")).thenReturn(Optional.of(
            role("SUPER_ADMIN", true, "sys", Set.of("TICKET:READ:TENANT", "TENANT:DELETE:GLOBAL"))));

        assertThatThrownBy(() -> service.createUser(request(Set.of("SUPER_ADMIN")), "a", TENANT, CALLER, SUPER_CALLER))
            .isInstanceOf(ValidationException.class).hasMessageContaining("do not hold");
        verify(userRepository, never()).save(any());
    }

    @Test
    void create_refusesUnknownOrForeignTenantRole() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(roleRepository.findByName("GHOST")).thenReturn(Optional.empty());
        when(roleRepository.findByName("OTHER")).thenReturn(Optional.of(role("OTHER", false, "tenant-2", Set.of())));

        assertThatThrownBy(() -> service.createUser(request(Set.of("GHOST")), "a", TENANT, CALLER, NO_ROLES))
            .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.createUser(request(Set.of("OTHER")), "a", TENANT, CALLER, NO_ROLES))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    void create_acceptsRoleWithinCallerCeiling() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(roleRepository.findByName("VIEWER")).thenReturn(Optional.of(role("VIEWER", false, TENANT, Set.of("TICKET:READ:TENANT"))));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.createUser(request(Set.of("VIEWER")), "a", TENANT, CALLER, NO_ROLES);

        assertThat(response.getRoles()).containsExactly("VIEWER");
    }

    // ---- contas administrativas ----------------------------------------------------------------

    @Test
    void create_adminByAnAdmin_isDeniedEvenWithEnoughPermissions() {
        when(userRepository.existsByEmail(any())).thenReturn(false);

        assertThatThrownBy(() -> service.createUser(request(Set.of("ADMIN")), "a", TENANT, CALLER, ADMIN_CALLER))
            .isInstanceOf(AccessDeniedException.class).hasMessageContaining("super administrator");
        verify(userRepository, never()).save(any());
    }

    @Test
    void create_superAdminByAnAdmin_isForbidden_notAPermissionCeilingError() {
        when(userRepository.existsByEmail(any())).thenReturn(false);

        // O papel exige permissões que o chamador não tem, mas a resposta correta é 403 (privilégio), checado antes.
        assertThatThrownBy(() -> service.createUser(request(Set.of(" SUPER_ADMIN ")), "a", TENANT, CALLER, ADMIN_CALLER))
            .isInstanceOf(AccessDeniedException.class);
        verify(roleRepository, never()).findByName(any());
    }

    @Test
    void create_adminAndSuperAdmin_bySuperAdmin_isAllowed() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(role("ADMIN", true, "sys", Set.of("TICKET:READ:TENANT"))));
        when(roleRepository.findByName("SUPER_ADMIN")).thenReturn(Optional.of(role("SUPER_ADMIN", true, "sys", Set.of("TICKET:READ:TENANT"))));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.createUser(request(Set.of("ADMIN")), "s", TENANT, CALLER, SUPER_CALLER).getRoles()).containsExactly("ADMIN");
        assertThat(service.createUser(request(Set.of("SUPER_ADMIN")), "s", TENANT, CALLER, SUPER_CALLER).getRoles()).containsExactly("SUPER_ADMIN");
    }

    @Test
    void update_anAdminAccount_byAnAdmin_isDenied() {
        User admin = user("other.admin@example.com", Set.of("ADMIN"));

        assertThatThrownBy(() -> service.updateUser(admin.getId(), UpdateUserRequest.builder().firstName("X").build(),
            "boss@example.com", TENANT, CALLER, ADMIN_CALLER)).isInstanceOf(AccessDeniedException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void update_promotingToAdmin_requiresSuperAdmin() {
        User agent = user("agent@example.com", Set.of("AGENT"));
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(role("ADMIN", true, "sys", Set.of("TICKET:READ:TENANT"))));
        var promote = UpdateUserRequest.builder().roles(Set.of("ADMIN")).build();

        assertThatThrownBy(() -> service.updateUser(agent.getId(), promote, "boss@example.com", TENANT, CALLER, ADMIN_CALLER))
            .isInstanceOf(AccessDeniedException.class);

        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        var response = service.updateUser(agent.getId(), promote, "super@example.com", TENANT, CALLER, SUPER_CALLER);
        assertThat(response.getRoles()).containsExactly("ADMIN");
    }

    @Test
    void update_nobodyChangesOwnRoles_orDeactivatesThemselves() {
        User me = user("super@example.com", Set.of("SUPER_ADMIN"));

        assertThatThrownBy(() -> service.updateUser(me.getId(), UpdateUserRequest.builder().roles(Set.of("AGENT")).build(),
            "super@example.com", TENANT, CALLER, SUPER_CALLER))
            .isInstanceOf(ValidationException.class).hasMessageContaining("own roles");
        assertThatThrownBy(() -> service.updateUser(me.getId(), UpdateUserRequest.builder().status("INACTIVE").build(),
            "super@example.com", TENANT, CALLER, SUPER_CALLER))
            .isInstanceOf(ValidationException.class).hasMessageContaining("deactivate your own");
    }

    @Test
    void update_demotingTheLastSuperAdmin_isRefused() {
        User last = user("last@example.com", Set.of("SUPER_ADMIN"));
        when(roleRepository.findByName("AGENT")).thenReturn(Optional.of(role("AGENT", true, "sys", Set.of("TICKET:READ:TENANT"))));
        when(userRepository.countActiveByRoleAndTenantId("SUPER_ADMIN", TENANT)).thenReturn(1L);

        assertThatThrownBy(() -> service.updateUser(last.getId(), UpdateUserRequest.builder().roles(Set.of("AGENT")).build(),
            "other.super@example.com", TENANT, CALLER, SUPER_CALLER))
            .isInstanceOf(ValidationException.class).hasMessageContaining("at least one active super administrator");
    }

    @Test
    void update_rejectsUnknownStatus() {
        User u = user("agent@example.com", Set.of("AGENT"));

        assertThatThrownBy(() -> service.updateUser(u.getId(), UpdateUserRequest.builder().status("BANANA").build(),
            "boss@example.com", TENANT, CALLER, ADMIN_CALLER)).isInstanceOf(ValidationException.class);
    }

    @Test
    void update_nullFieldsKeepTheCurrentValues() {
        User u = user("agent@example.com", Set.of("AGENT"));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.updateUser(u.getId(), UpdateUserRequest.builder().phone("+55 16 3333-0000").build(),
            "boss@example.com", TENANT, CALLER, ADMIN_CALLER);

        assertThat(response.getFirstName()).isEqualTo("Ana");
        assertThat(response.getPhone()).isEqualTo("+55 16 3333-0000");
    }

    @Test
    void delete_nobodyDeletesThemselves_andAdminsNeedASuperAdmin() {
        User me = user("me@example.com", Set.of("AGENT"));
        User admin = user("admin@example.com", Set.of("ADMIN"));

        assertThatThrownBy(() -> service.deleteUser(me.getId(), TENANT, "me@example.com", ADMIN_CALLER))
            .isInstanceOf(ValidationException.class).hasMessageContaining("own account");
        assertThatThrownBy(() -> service.deleteUser(admin.getId(), TENANT, "boss@example.com", ADMIN_CALLER))
            .isInstanceOf(AccessDeniedException.class);
        verify(userRepository, never()).delete(any());
    }

    @Test
    void delete_theLastSuperAdmin_isRefused() {
        User last = user("last@example.com", Set.of("SUPER_ADMIN"));
        when(userRepository.countActiveByRoleAndTenantId("SUPER_ADMIN", TENANT)).thenReturn(1L);

        assertThatThrownBy(() -> service.deleteUser(last.getId(), TENANT, "other@example.com", SUPER_CALLER))
            .isInstanceOf(ValidationException.class).hasMessageContaining("at least one active super administrator");
        verify(userRepository, never()).delete(any());
    }

    // ---- perfil e localidade -------------------------------------------------------------------

    @Test
    void profile_updatesOnlyGivenFields_andValidatesTheLocation() {
        User me = new User();
        me.setEmail("ana@example.com");
        me.setTenantId(TENANT);
        me.setFirstName("Ana");
        me.setRoles(new HashSet<>(Set.of("END_USER")));
        when(userRepository.findByEmailAndTenantId("ana@example.com", TENANT)).thenReturn(Optional.of(me));
        when(locationDirectory.isActiveInTenant("loc-franca", TENANT)).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.updateProfile("ana@example.com",
            UpdateProfileRequest.builder().jobTitle("Analista Fiscal").defaultLocationId("loc-franca").build(), TENANT);

        assertThat(response.getFirstName()).isEqualTo("Ana");
        assertThat(response.getJobTitle()).isEqualTo("Analista Fiscal");
        assertThat(response.getDefaultLocationId()).isEqualTo("loc-franca");
        assertThat(response.getRoles()).containsExactly("END_USER");
    }

    @Test
    void profile_refusesUnknownOrInactiveLocation_andBlankClearsIt() {
        User me = new User();
        me.setEmail("ana@example.com");
        me.setTenantId(TENANT);
        me.setDefaultLocationId("loc-franca");
        me.setRoles(new HashSet<>());
        when(userRepository.findByEmailAndTenantId("ana@example.com", TENANT)).thenReturn(Optional.of(me));
        when(locationDirectory.isActiveInTenant("loc-ghost", TENANT)).thenReturn(false);

        assertThatThrownBy(() -> service.updateProfile("ana@example.com",
            UpdateProfileRequest.builder().defaultLocationId("loc-ghost").build(), TENANT))
            .isInstanceOf(ValidationException.class).hasMessageContaining("Unknown or inactive location");
        assertThat(me.getDefaultLocationId()).isEqualTo("loc-franca");

        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        var cleared = service.updateProfile("ana@example.com", UpdateProfileRequest.builder().defaultLocationId(" ").build(), TENANT);
        assertThat(cleared.getDefaultLocationId()).isNull();
    }

    @Test
    void create_withUnknownLocation_isRefused() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(locationDirectory.isActiveInTenant("loc-ghost", TENANT)).thenReturn(false);
        var req = CreateUserRequest.builder().email("new@example.com").password("S3cretPass!").defaultLocationId("loc-ghost").build();

        assertThatThrownBy(() -> service.createUser(req, "a", TENANT, CALLER, NO_ROLES)).isInstanceOf(ValidationException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void userOfAnotherTenant_isNotFound() {
        User foreign = new User();
        foreign.setId(UUID.randomUUID());
        foreign.setTenantId("tenant-2");
        when(userRepository.findById(foreign.getId())).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.getUserById(foreign.getId(), TENANT)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.deleteUser(foreign.getId(), TENANT, "a", ADMIN_CALLER)).isInstanceOf(ResourceNotFoundException.class);
        verify(userRepository, never()).delete(any());
    }
}
