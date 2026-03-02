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

import com.swp.ckms.entity.CentralKitchen;
import com.swp.ckms.entity.FranchiseStore;
import com.swp.ckms.entity.KitchenWarehouse;
import com.swp.ckms.entity.StoreWarehouse;
import com.swp.ckms.repository.CentralKitchenRepository;
import com.swp.ckms.repository.FranchiseStoreRepository;
import com.swp.ckms.repository.KitchenWarehouseRepository;
import com.swp.ckms.repository.PrivilegeRepository;
import com.swp.ckms.repository.RoleRepository;
import com.swp.ckms.repository.StoreWarehouseRepository;

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
    private final CentralKitchenRepository centralKitchenRepository;
    private final KitchenWarehouseRepository warehouseRepository;
    private final FranchiseStoreRepository franchiseStoreRepository;
    private final StoreWarehouseRepository storeWarehouseRepository;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        Set<Privilege> allPrivileges = seedPrivileges();
        
        // Ensure Kitchen and Stores exist BEFORE users are seeded
        seedKitchenAndWarehouse();
        
        // Now seed users and attach them to stores
        seedUsersAndRoles(allPrivileges);
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

    private void seedUsersAndRoles(Set<Privilege> allPrivileges) {
        // 1. ADMIN - Toàn quyền hệ thống & RBAC
        Role adminRole = seedRole("ADMIN", allPrivileges);
        seedUser("admin", "admin@ckms.com", "admin", "System Administrator", adminRole, null);

        // 2. MANAGER - Giám sát vận hành, Catalog, Recipe & Reports
        Set<String> managerCodes = new HashSet<>(Arrays.asList(
                "MANAGE_CATALOG", "VIEW_CATEGORY", "CREATE_CATEGORY", "UPDATE_CATEGORY",
                "VIEW_MATERIAL", "CREATE_MATERIAL", "UPDATE_MATERIAL",
                "VIEW_PRODUCT", "CREATE_PRODUCT", "UPDATE_PRODUCT",
                "VIEW_RECIPE", "CREATE_RECIPE", "UPDATE_RECIPE",
                "VIEW_KITCHEN_INVENTORY", "VIEW_STORE_INVENTORY",
                "VIEW_BILLING", "VIEW_INVOICE", "VIEW_REPORTS", "VIEW_DASHBOARD"
        ));
        Set<Privilege> managerPrivileges = allPrivileges.stream()
                .filter(p -> managerCodes.contains(p.getCode()))
                .collect(Collectors.toSet());
        Role managerRole = seedRole("MANAGER", managerPrivileges);
        seedUser("manager", "manager@ckms.com", "manager", "Operation Manager", managerRole, null);

        // 3. COORDINATOR - Planning: Duyệt đơn, Lập kế hoạch, Giao hàng
        Set<String> coordinatorCodes = new HashSet<>(Arrays.asList(
                "VIEW_STORE_ORDER", "APPROVE_STORE_ORDER", "UPDATE_STORE_ORDER",
                "ORGANIZE_PRODUCTION", "CREATE_PRODUCTION_PLAN", "VIEW_PRODUCTION_PLAN", "UPDATE_PRODUCTION_PLAN",
                "CREATE_SHIPMENT", "VIEW_SHIPMENT",
                "VIEW_INVOICE", "VIEW_BILLING", "VIEW_KITCHEN_INVENTORY", "VIEW_DASHBOARD"
        ));
        Set<Privilege> coordinatorPrivileges = allPrivileges.stream()
                .filter(p -> coordinatorCodes.contains(p.getCode()))
                .collect(Collectors.toSet());
        Role coordinatorRole = seedRole("COORDINATOR", coordinatorPrivileges);
        seedUser("coordinator", "coordinator@ckms.com", "coordinator", "Supply Coordinator", coordinatorRole, null);

        // 4. KITCHEN_STAFF - Execution: Sản xuất & Kho bếp
        Set<String> kitchenCodes = new HashSet<>(Arrays.asList(
                "VIEW_PRODUCTION_PLAN", "EXECUTE_PRODUCTION", "UPDATE_PRODUCTION_PLAN",
                "VIEW_KITCHEN_INVENTORY", "UPDATE_KITCHEN_INVENTORY", "ADJUST_KITCHEN_STOCK",
                "PREPARE_SHIPMENT", "VIEW_SHIPMENT"
        ));
        Set<Privilege> kitchenPrivileges = allPrivileges.stream()
                .filter(p -> kitchenCodes.contains(p.getCode()))
                .collect(Collectors.toSet());
        Role kitchenStaffRole = seedRole("KITCHEN_STAFF", kitchenPrivileges);
        seedUser("kitchenstaff", "kitchen@ckms.com", "staff", "Kitchen Execution Staff", kitchenStaffRole, null);

        // 5. STORE_STAFF - Execution: Đặt hàng & Kho cửa hàng
        Set<String> storeStaffCodes = new HashSet<>(Arrays.asList(
                "CREATE_STORE_ORDER", "VIEW_STORE_ORDER", "UPDATE_STORE_ORDER",
                "VIEW_STORE_INVENTORY", "UPDATE_STORE_INVENTORY",
                "CONFIRM_SHIPMENT", "VIEW_SHIPMENT",
                "VIEW_PRODUCT", "VIEW_BILLING"
        ));
        Set<Privilege> storeStaffPrivileges = allPrivileges.stream()
                .filter(p -> storeStaffCodes.contains(p.getCode()))
                .collect(Collectors.toSet());
        Role storeStaffRole = seedRole("STORE_STAFF", storeStaffPrivileges);
        
        FranchiseStore store = franchiseStoreRepository.findById(1L).orElse(null);
        seedUser("storestaff", "storestaff@ckms.com", "staff", "Store Staff", storeStaffRole, store);
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

    private void seedUser(String username, String email, String password, String fullName, Role role, FranchiseStore store) {
        if (!userRepository.existsByUsername(username)) {
            userRepository.save(User.builder()
                    .username(username)
                    .email(email)
                    .password(passwordEncoder.encode(password))
                    .fullName(fullName)
                    .status(com.swp.ckms.enums.UserStatus.ACTIVE)
                    .role(role)
                    .isActive(true)
                    .store(store)
                    .build());
            System.out.println(">>> Seeded user: " + username);
        }
    }

    private void seedKitchenAndWarehouse() {
        // 1. Seed Bếp Trung Tâm và Kho Bếp (ID = 1)
        if (!centralKitchenRepository.existsById(1L)) {
            CentralKitchen kitchen = centralKitchenRepository.save(CentralKitchen.builder()
                    .name("Bếp Trung Tâm Chính")
                    .address("Hệ Thống Bếp Mặc Định")
                    .build());
            System.out.println(">>> Seeded default Central Kitchen");

            warehouseRepository.save(KitchenWarehouse.builder()
                    .name("Kho Bếp Trung Tâm")
                    .kitchen(kitchen)
                    .build());
            System.out.println(">>> Seeded default Kitchen Warehouse");
        }

        // 2. Seed Cửa Hàng Nhượng Quyền và Kho Cửa Hàng (ID = 1)
        if (!franchiseStoreRepository.existsById(1L)) {
            FranchiseStore store = franchiseStoreRepository.save(FranchiseStore.builder()
                    .name("Cửa Hàng Mẫu CKMS")
                    .address("Số 1 Mạc Đĩnh Chi")
                    .paymentCycle("MONTHLY")
                    .build());
            System.out.println(">>> Seeded default Franchise Store");

            storeWarehouseRepository.save(StoreWarehouse.builder()
                    .name("Kho Cửa Hàng (Mẫu)")
                    .store(store)
                    .maxCapacity(java.math.BigDecimal.valueOf(1000))
                    .build());
            System.out.println(">>> Seeded default Store Warehouse");
        }
    }
}
