package com.swp.ckms.config;

import com.swp.ckms.entity.Privilege;
import com.swp.ckms.entity.Role;
import com.swp.ckms.entity.User;
import com.swp.ckms.enums.AppPrivilege;
import com.swp.ckms.repository.PrivilegeRepository;
import com.swp.ckms.repository.RoleRepository;
import com.swp.ckms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PrivilegeRepository privilegeRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        Set<Privilege> allPrivileges = seedPrivileges();
        seedAdminUser(allPrivileges);
    }

    private Set<Privilege> seedPrivileges() {
        return Arrays.stream(AppPrivilege.values())
                .map(appPrivilege -> privilegeRepository.findByCode(appPrivilege.getCode())
                        .orElseGet(() -> privilegeRepository.save(Privilege.builder()
                                .code(appPrivilege.getCode())
                                .description(appPrivilege.getDescription())
                                .build())))
                .collect(Collectors.toSet());
    }

    private void seedAdminUser(Set<Privilege> allPrivileges) {
        // 1. Seed ADMIN Role & User
        Role adminRole = seedRole("ADMIN", allPrivileges);
        seedUser("admin", "admin@ckms.com", "admin", "System Administrator", adminRole);

        // 2. Seed MANAGER Role & User
        Set<Privilege> managerPrivileges = allPrivileges.stream()
                .filter(p -> p.getCode().contains("CATEGORY") || p.getCode().contains("MATERIAL"))
                .collect(Collectors.toSet());
        Role managerRole = seedRole("MANAGER", managerPrivileges);
        seedUser("manager", "manager@ckms.com", "manager", "Store Manager", managerRole);

        // 3. Seed STAFF Role & User (View only)
        Set<Privilege> staffPrivileges = allPrivileges.stream()
                .filter(p -> p.getCode().startsWith("VIEW_"))
                .collect(Collectors.toSet());
        Role staffRole = seedRole("STAFF", staffPrivileges);
        seedUser("staff", "staff@ckms.com", "staff", "Kitchen Staff", staffRole);
    }

    private Role seedRole(String roleName, Set<Privilege> privileges) {
        return roleRepository.findByRoleName(roleName)
                .map(role -> {
                    role.setPrivileges(privileges);
                    return roleRepository.save(role);
                })
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .roleName(roleName)
                        .privileges(privileges)
                        .build()));
    }

    private void seedUser(String username, String email, String password, String fullName, Role role) {
        if (!userRepository.existsByUsername(username)) {
            userRepository.save(User.builder()
                    .username(username)
                    .email(email)
                    .password(passwordEncoder.encode(password))
                    .fullName(fullName)
                    .status(com.swp.ckms.enums.UserStatus.ACTIVE)
                    .role(role)
                    .isActive(true)
                    .build());
            System.out.println(">>> Seeded user: " + username);
        }
    }
}
