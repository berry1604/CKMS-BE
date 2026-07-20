package com.swp.ckms.config;

import com.swp.ckms.entity.Privilege;
import com.swp.ckms.entity.Role;
import com.swp.ckms.entity.User;
import com.swp.ckms.enums.AppPrivilege;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.swp.ckms.entity.CentralKitchen;
import com.swp.ckms.entity.FranchiseStore;
import com.swp.ckms.entity.KitchenWarehouse;
import com.swp.ckms.entity.StoreWarehouse;
import com.swp.ckms.entity.PaymentMethod;
import com.swp.ckms.entity.Material;
import com.swp.ckms.entity.Product;
import com.swp.ckms.entity.Category;
import com.swp.ckms.entity.Recipe;
import com.swp.ckms.entity.RecipeDetail;
import com.swp.ckms.entity.KitchenStockItem;
import com.swp.ckms.enums.UnitType;
import com.swp.ckms.repository.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
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
        private final PaymentMethodRepository paymentMethodRepository;
        private final MaterialRepository materialRepository;
        private final ProductRepository productRepository;
        private final CategoryRepository categoryRepository;
        private final RecipeRepository recipeRepository;
        private final RecipeDetailRepository recipeDetailRepository;
        private final KitchenStockItemRepository kitchenStockItemRepository;

        @Override
        @Transactional
        public void run(String... args) throws Exception {
                Set<Privilege> allPrivileges = seedPrivileges();

                // Ensure Kitchen and Stores exist BEFORE users are seeded
                seedKitchenAndWarehouse();

                // Now seed users and attach them to stores
                seedUsersAndRoles(allPrivileges);

                seedPaymentMethods();
                seedBusinessData();
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
                // 0. Fetch default kitchen and store for attachment
                CentralKitchen defaultKitchen = centralKitchenRepository.findById(1L).orElse(null);
                FranchiseStore defaultStore = franchiseStoreRepository.findById(1L).orElse(null);

                // 1. ADMIN - Toàn quyền hệ thống & RBAC
                Role adminRole = seedRole("ADMIN", allPrivileges);
                seedUser("admin", "admin@ckms.com", "admin", "System Administrator", adminRole, null, null);

                // 2. MANAGER - Giám sát vận hành, Catalog, Recipe & Reports
                Set<String> managerCodes = new HashSet<>(Arrays.asList(
                                "MANAGE_CATALOG", "VIEW_CATEGORY", "CREATE_CATEGORY", "UPDATE_CATEGORY",
                                "VIEW_MATERIAL", "CREATE_MATERIAL", "UPDATE_MATERIAL",
                                "VIEW_PRODUCT", "CREATE_PRODUCT", "UPDATE_PRODUCT",
                                "VIEW_RECIPE", "CREATE_RECIPE", "UPDATE_RECIPE",
                                "VIEW_KITCHEN_INVENTORY", "VIEW_STORE_INVENTORY",
                                "VIEW_BILLING", "VIEW_INVOICE", "VIEW_REPORTS", "VIEW_DASHBOARD",
                                "VIEW_STORE_ORDER", "APPROVE_STORE_ORDER", "MANAGE_STORES",
                                "PAY_BILLING", "CONFIRM_PAYMENT", "MANAGE_KITCHEN_CONFIG", "VIEW_KITCHEN"));
                Set<Privilege> managerPrivileges = allPrivileges.stream()
                                .filter(p -> managerCodes.contains(p.getCode()))
                                .collect(Collectors.toSet());
                Role managerRole = seedRole("MANAGER", managerPrivileges);
                seedUser("manager", "manager@ckms.com", "manager", "Operation Manager", managerRole, null, null);

                // 3. COORDINATOR - Planning: Duyệt đơn, Lập kế hoạch, Giao hàng
                Set<String> coordinatorCodes = new HashSet<>(Arrays.asList(
                                "VIEW_STORE_ORDER", "APPROVE_STORE_ORDER", "UPDATE_STORE_ORDER",
                                "ORGANIZE_PRODUCTION", "CREATE_PRODUCTION_PLAN", "VIEW_PRODUCTION_PLAN",
                                "UPDATE_PRODUCTION_PLAN",
                                "CREATE_SHIPMENT", "START_SHIPMENT", "CANCEL_SHIPMENT", "VIEW_SHIPMENT",
                                "VIEW_INVOICE", "VIEW_BILLING", "VIEW_KITCHEN_INVENTORY", "VIEW_DASHBOARD",
                                "VIEW_RECIPE", "PAY_BILLING", "CONFIRM_PAYMENT", "VIEW_PRODUCT", "VIEW_KITCHEN"));
                Set<Privilege> coordinatorPrivileges = allPrivileges.stream()
                                .filter(p -> coordinatorCodes.contains(p.getCode()))
                                .collect(Collectors.toSet());
                Role coordinatorRole = seedRole("COORDINATOR", coordinatorPrivileges);
                seedUser("coordinator", "coordinator@ckms.com", "coordinator", "Supply Coordinator", coordinatorRole,
                                null, null);

                // 4. KITCHEN_STAFF - Execution: Sản xuất & Kho bếp
                Set<String> kitchenCodes = new HashSet<>(Arrays.asList(
                                "VIEW_PRODUCTION_PLAN", "EXECUTE_PRODUCTION", "UPDATE_PRODUCTION_PLAN",
                                "VIEW_KITCHEN_INVENTORY", "UPDATE_KITCHEN_INVENTORY", "ADJUST_KITCHEN_STOCK",
                                "VIEW_PRODUCTION_PLAN",
                                "START_SHIPMENT", "VIEW_SHIPMENT", "VIEW_PRODUCT", "VIEW_MATERIAL",
                                "PREPARE_SHIPMENT", "VIEW_KITCHEN", "VIEW_STORE_ORDER"));
                Set<Privilege> kitchenPrivileges = allPrivileges.stream()
                                .filter(p -> kitchenCodes.contains(p.getCode()))
                                .collect(Collectors.toSet());
                Role kitchenStaffRole = seedRole("KITCHEN_STAFF", kitchenPrivileges);
                seedUser("kitchenstaff", "kitchen@ckms.com", "staff", "Kitchen Execution Staff", kitchenStaffRole, null,
                                defaultKitchen);

                // 5. STORE_STAFF - Execution: Đặt hàng & Kho cửa hàng
                Set<String> storeStaffCodes = new HashSet<>(Arrays.asList(
                                "CREATE_STORE_ORDER", "VIEW_STORE_ORDER", "UPDATE_STORE_ORDER",
                                "VIEW_STORE_INVENTORY", "UPDATE_STORE_INVENTORY",
                                "CONFIRM_SHIPMENT", "VIEW_SHIPMENT",
                                "VIEW_PRODUCT", "VIEW_BILLING", "PAY_BILLING"));
                Set<Privilege> storeStaffPrivileges = allPrivileges.stream()
                                .filter(p -> storeStaffCodes.contains(p.getCode()))
                                .collect(Collectors.toSet());
                Role storeStaffRole = seedRole("STORE_STAFF", storeStaffPrivileges);
                seedUser("storestaff", "storestaff@ckms.com", "staff", "Store Staff", storeStaffRole, defaultStore,
                                null);
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

        private void seedUser(String username, String email, String password, String fullName, Role role,
                        FranchiseStore store, CentralKitchen kitchen) {
                userRepository.findByUsername(username).ifPresentOrElse(
                                user -> {
                                        user.setStore(store);
                                        user.setKitchen(kitchen);
                                        user.setRole(role);
                                        userRepository.save(user);
                                        System.out.println(">>> Updated existing user: " + username);
                                },
                                () -> {
                                        userRepository.save(User.builder()
                                                        .username(username)
                                                        .email(email)
                                                        .password(passwordEncoder.encode(password))
                                                        .fullName(fullName)
                                                        .status(com.swp.ckms.enums.UserStatus.ACTIVE)
                                                        .role(role)
                                                        .isActive(true)
                                                        .store(store)
                                                        .kitchen(kitchen)
                                                        .build());
                                        System.out.println(">>> Seeded new user: " + username);
                                });
        }

        private void seedKitchenAndWarehouse() {
                // 1. Seed Bếp Trung Tâm và Kho Bếp (ID = 1)
                if (!centralKitchenRepository.existsById(1L)) {
                        CentralKitchen kitchen = centralKitchenRepository.save(CentralKitchen.builder()
                                        .name("CKMS - Central Kitchen")
                                        .address("117 Nguyễn Du, Phường Bến Thành, Quận 1, Thành phố Hồ Chí Minh")
                                        .maxDailyCapacity(java.math.BigDecimal.valueOf(10000))
                                        .latitude(10.7756)
                                        .longitude(106.6964)
                                        .phone("84945751684")
                                        .build());
                        System.out.println(">>> Seeded default Central Kitchen");

                        KitchenWarehouse kitchenWarehouse = KitchenWarehouse.builder()
                                        .kitchen(kitchen)
                                        .name("Kho Tổng Bếp Trung tâm")
                                        .maxCapacity(java.math.BigDecimal.valueOf(50000))
                                        .build();
                        warehouseRepository.save(kitchenWarehouse);
                        System.out.println(">>> Seeded default Kitchen Warehouse");
                }

                // 2. Seed Cửa Hàng Nhượng Quyền và Kho Cửa Hàng (ID = 1)
                if (!franchiseStoreRepository.existsById(1L)) {
                        FranchiseStore store = franchiseStoreRepository.save(FranchiseStore.builder()
                                        .name("Cửa Hàng Mẫu CKMS")
                                        .address("Số 1 Mạc Đĩnh Chi, Phường Đa Kao, Quận 1, Thành phố Hồ Chí Minh")
                                        .paymentCycle("MONTHLY")
                                        .phoneNumber("84377774968")
                                        .latitude(10.782140)
                                        .longitude(106.701484)
                                        .build());
                        System.out.println(">>> Seeded default Franchise Store");

                        storeWarehouseRepository.save(StoreWarehouse.builder()
                                        .name("Kho Cửa Hàng (Mẫu)")
                                        .store(store)
                                        .build());
                        System.out.println(">>> Seeded default Store Warehouse");
                }
        }

        private void seedPaymentMethods() {
                String[] methods = { "CASH", "BANK_TRANSFER" };
                for (String methodName : methods) {
                        if (paymentMethodRepository.findByName(methodName).isEmpty()) {
                                paymentMethodRepository.save(PaymentMethod.builder()
                                                .name(methodName)
                                                .build());
                                System.out.println(">>> Seeded Payment Method: " + methodName);
                        }
                }
        }

        private void seedBusinessData() {
                KitchenWarehouse defaultKitchenWarehouse = getDefaultKitchenWarehouse();
                if (defaultKitchenWarehouse == null) {
                        System.out.println(
                                        ">>> Skipped business data seeding because default kitchen warehouse was not found");
                        return;
                }

                User adminUser = userRepository.findByUsername("admin").orElse(null);

                Map<String, Category> categories = seedCategories(adminUser);
                Map<String, Material> materials = seedMaterialsAndStock(defaultKitchenWarehouse);
                seedProductsAndRecipes(categories, materials, adminUser);

                System.out.println(">>> Seeded categories, materials, product recipes, and kitchen stock items");
        }

        private KitchenWarehouse getDefaultKitchenWarehouse() {
                KitchenWarehouse byName = warehouseRepository.findByName("Kho Tổng Bếp Trung tâm").orElse(null);
                if (byName != null) {
                        return byName;
                }

                return centralKitchenRepository.findById(1L)
                                .flatMap(kitchen -> warehouseRepository.findByKitchen_KitchenId(kitchen.getKitchenId())
                                                .stream()
                                                .findFirst())
                                .orElse(null);
        }

        private Map<String, Category> seedCategories(User adminUser) {
                List<CategorySeed> categorySeeds = List.of(
                                new CategorySeed("bò chế biến sẵn",
                                                "Bán thành phẩm từ bò đã sơ chế, ướp và chế biến sẵn", true),
                                new CategorySeed("đồ ăn nhanh", "Bán thành phẩm cho nhóm món phục vụ nhanh", true),
                                new CategorySeed("nước uống", "Bán thành phẩm cho nhóm đồ uống", true),
                                new CategorySeed("gia vị", "Gia vị và hỗn hợp nêm nếm", true),
                                new CategorySeed("sốt bò", "Các loại sốt dùng cho món bò", true),
                                new CategorySeed("ăn kèm", "Món ăn kèm và topping phụ trợ", true));

                Map<String, Category> result = new LinkedHashMap<>();
                for (CategorySeed seed : categorySeeds) {
                        Category category = findCategoryByName(seed.name())
                                        .orElseGet(Category::new);
                        category.setName(seed.name());
                        category.setDescription(seed.description());
                        category.setCreatedByUser(adminUser);
                        category.setIsActive(seed.active());
                        result.put(seed.name(), categoryRepository.save(category));
                }
                return result;
        }

        private Map<String, Material> seedMaterialsAndStock(KitchenWarehouse warehouse) {
                List<MaterialSeed> materialSeeds = List.of(
                                new MaterialSeed("Thịt bò nạm", UnitType.KG, BigDecimal.valueOf(45), 7),
                                new MaterialSeed("Thịt bò bắp", UnitType.KG, BigDecimal.valueOf(35), 7),
                                new MaterialSeed("Hành tây", UnitType.KG, BigDecimal.valueOf(25), 14),
                                new MaterialSeed("Tỏi", UnitType.KG, BigDecimal.valueOf(12), 30),
                                new MaterialSeed("Gừng", UnitType.KG, BigDecimal.valueOf(10), 21),
                                new MaterialSeed("Sả", UnitType.KG, BigDecimal.valueOf(15), 14),
                                new MaterialSeed("Ớt", UnitType.KG, BigDecimal.valueOf(6), 10),
                                new MaterialSeed("Tiêu đen", UnitType.KG, BigDecimal.valueOf(5), 180),
                                new MaterialSeed("Muối biển", UnitType.KG, BigDecimal.valueOf(30), 365),
                                new MaterialSeed("Đường", UnitType.KG, BigDecimal.valueOf(25), 365),
                                new MaterialSeed("Nước mắm", UnitType.LITER, BigDecimal.valueOf(40), 365),
                                new MaterialSeed("Dầu ăn", UnitType.LITER, BigDecimal.valueOf(30), 365),
                                new MaterialSeed("Nước tương", UnitType.LITER, BigDecimal.valueOf(25), 365),
                                new MaterialSeed("Chanh tươi", UnitType.KG, BigDecimal.valueOf(20), 14),
                                new MaterialSeed("Nước lọc", UnitType.LITER, BigDecimal.valueOf(200), 365));

                Map<String, Material> result = new LinkedHashMap<>();
                for (MaterialSeed seed : materialSeeds) {
                        Material material = findMaterialByName(seed.name())
                                        .orElseGet(Material::new);
                        material.setName(seed.name());
                        material.setUnit(seed.unit());
                        material.setIsActive(true);
                        Material savedMaterial = materialRepository.save(material);
                        result.put(seed.name(), savedMaterial);

                        upsertKitchenStockItem(warehouse, savedMaterial, seed.quantity(),
                                        LocalDate.now().plusDays(seed.expiryDays()));
                }
                return result;
        }

        private void seedProductsAndRecipes(Map<String, Category> categories, Map<String, Material> materials,
                        User adminUser) {
                List<ProductSeed> productSeeds = List.of(
                                new ProductSeed("Bò ướp sả tỏi", "Bán thành phẩm bò ướp sả tỏi cho nấu nhanh",
                                                "bò chế biến sẵn", UnitType.KG,
                                                BigDecimal.valueOf(45_000), BigDecimal.valueOf(4),
                                                "Ướp bò với sả, tỏi, tiêu, nước mắm và đường.",
                                                List.of(
                                                                new RecipeIngredientSeed("Thịt bò nạm",
                                                                                BigDecimal.valueOf(4)),
                                                                new RecipeIngredientSeed("Sả", BigDecimal.valueOf(0.8)),
                                                                new RecipeIngredientSeed("Tỏi",
                                                                                BigDecimal.valueOf(0.4)),
                                                                new RecipeIngredientSeed("Tiêu đen",
                                                                                BigDecimal.valueOf(0.08)),
                                                                new RecipeIngredientSeed("Nước mắm",
                                                                                BigDecimal.valueOf(0.3)),
                                                                new RecipeIngredientSeed("Đường",
                                                                                BigDecimal.valueOf(0.15)),
                                                                new RecipeIngredientSeed("Dầu ăn",
                                                                                BigDecimal.valueOf(0.15)))),
                                new ProductSeed("Bò xào hành tây",
                                                "Bán thành phẩm bò xào hành tây cho các món cơm và mì",
                                                "bò chế biến sẵn", UnitType.KG,
                                                BigDecimal.valueOf(42_000), BigDecimal.valueOf(5),
                                                "Bò xào sơ với hành tây, tỏi, nước tương và tiêu.",
                                                List.of(
                                                                new RecipeIngredientSeed("Thịt bò bắp",
                                                                                BigDecimal.valueOf(4)),
                                                                new RecipeIngredientSeed("Hành tây",
                                                                                BigDecimal.valueOf(1.5)),
                                                                new RecipeIngredientSeed("Tỏi",
                                                                                BigDecimal.valueOf(0.3)),
                                                                new RecipeIngredientSeed("Nước tương",
                                                                                BigDecimal.valueOf(0.4)),
                                                                new RecipeIngredientSeed("Tiêu đen",
                                                                                BigDecimal.valueOf(0.08)),
                                                                new RecipeIngredientSeed("Dầu ăn",
                                                                                BigDecimal.valueOf(0.2)),
                                                                new RecipeIngredientSeed("Đường",
                                                                                BigDecimal.valueOf(0.15)))),
                                new ProductSeed("Bò kho mềm", "Bò kho sơ chế dùng cho phục vụ nhanh", "bò chế biến sẵn",
                                                UnitType.KG,
                                                BigDecimal.valueOf(58_000), BigDecimal.valueOf(6),
                                                "Bò hầm mềm với gừng, sả, tỏi và gia vị cơ bản.",
                                                List.of(
                                                                new RecipeIngredientSeed("Thịt bò nạm",
                                                                                BigDecimal.valueOf(5)),
                                                                new RecipeIngredientSeed("Gừng",
                                                                                BigDecimal.valueOf(0.3)),
                                                                new RecipeIngredientSeed("Sả", BigDecimal.valueOf(0.5)),
                                                                new RecipeIngredientSeed("Tỏi",
                                                                                BigDecimal.valueOf(0.35)),
                                                                new RecipeIngredientSeed("Nước mắm",
                                                                                BigDecimal.valueOf(0.35)),
                                                                new RecipeIngredientSeed("Tiêu đen",
                                                                                BigDecimal.valueOf(0.05)),
                                                                new RecipeIngredientSeed("Đường",
                                                                                BigDecimal.valueOf(0.2)))),
                                new ProductSeed("Burger bò mini", "Bò chế biến sẵn cho burger mini", "đồ ăn nhanh",
                                                UnitType.PIECE,
                                                BigDecimal.valueOf(32_000), BigDecimal.valueOf(8),
                                                "Nhân bò mini đã nêm sẵn cho burger.",
                                                List.of(
                                                                new RecipeIngredientSeed("Thịt bò bắp",
                                                                                BigDecimal.valueOf(3)),
                                                                new RecipeIngredientSeed("Hành tây",
                                                                                BigDecimal.valueOf(0.8)),
                                                                new RecipeIngredientSeed("Tỏi",
                                                                                BigDecimal.valueOf(0.2)),
                                                                new RecipeIngredientSeed("Tiêu đen",
                                                                                BigDecimal.valueOf(0.06)),
                                                                new RecipeIngredientSeed("Muối biển",
                                                                                BigDecimal.valueOf(0.08)),
                                                                new RecipeIngredientSeed("Dầu ăn",
                                                                                BigDecimal.valueOf(0.15)))),
                                new ProductSeed("Bò cuộn xiên nướng", "Xiên bò ướp sẵn cho nướng nhanh", "đồ ăn nhanh",
                                                UnitType.PIECE,
                                                BigDecimal.valueOf(35_000), BigDecimal.valueOf(8),
                                                "Bò ướp đậm vị để cuộn và nướng.",
                                                List.of(
                                                                new RecipeIngredientSeed("Thịt bò nạm",
                                                                                BigDecimal.valueOf(4)),
                                                                new RecipeIngredientSeed("Tỏi",
                                                                                BigDecimal.valueOf(0.3)),
                                                                new RecipeIngredientSeed("Tiêu đen",
                                                                                BigDecimal.valueOf(0.07)),
                                                                new RecipeIngredientSeed("Nước tương",
                                                                                BigDecimal.valueOf(0.35)),
                                                                new RecipeIngredientSeed("Đường",
                                                                                BigDecimal.valueOf(0.12)),
                                                                new RecipeIngredientSeed("Ớt",
                                                                                BigDecimal.valueOf(0.05)),
                                                                new RecipeIngredientSeed("Dầu ăn",
                                                                                BigDecimal.valueOf(0.12)))),
                                new ProductSeed("Trà chanh sả", "Đồ uống chanh sả pha sẵn", "nước uống", UnitType.LITER,
                                                BigDecimal.valueOf(28_000), BigDecimal.valueOf(5),
                                                "Nền trà chanh sả nấu sẵn để pha lạnh.",
                                                List.of(
                                                                new RecipeIngredientSeed("Nước lọc",
                                                                                BigDecimal.valueOf(8)),
                                                                new RecipeIngredientSeed("Chanh tươi",
                                                                                BigDecimal.valueOf(1)),
                                                                new RecipeIngredientSeed("Sả",
                                                                                BigDecimal.valueOf(0.35)),
                                                                new RecipeIngredientSeed("Đường",
                                                                                BigDecimal.valueOf(0.6)),
                                                                new RecipeIngredientSeed("Muối biển",
                                                                                BigDecimal.valueOf(0.03)))),
                                new ProductSeed("Nước gừng chanh", "Đồ uống gừng chanh pha sẵn", "nước uống",
                                                UnitType.LITER,
                                                BigDecimal.valueOf(30_000), BigDecimal.valueOf(4),
                                                "Nền nước gừng chanh có vị ấm và chua nhẹ.",
                                                List.of(
                                                                new RecipeIngredientSeed("Nước lọc",
                                                                                BigDecimal.valueOf(8)),
                                                                new RecipeIngredientSeed("Gừng",
                                                                                BigDecimal.valueOf(0.4)),
                                                                new RecipeIngredientSeed("Chanh tươi",
                                                                                BigDecimal.valueOf(0.8)),
                                                                new RecipeIngredientSeed("Đường",
                                                                                BigDecimal.valueOf(0.5)))),
                                new ProductSeed("Muối tiêu chanh", "Gia vị chấm pha sẵn", "gia vị", UnitType.KG,
                                                BigDecimal.valueOf(18_000), BigDecimal.valueOf(2),
                                                "Hỗn hợp muối tiêu chanh cân vị.",
                                                List.of(
                                                                new RecipeIngredientSeed("Muối biển",
                                                                                BigDecimal.valueOf(6)),
                                                                new RecipeIngredientSeed("Tiêu đen",
                                                                                BigDecimal.valueOf(2)),
                                                                new RecipeIngredientSeed("Chanh tươi",
                                                                                BigDecimal.valueOf(1)),
                                                                new RecipeIngredientSeed("Ớt",
                                                                                BigDecimal.valueOf(0.2)))),
                                new ProductSeed("Sốt ướp bò", "Gia vị ướp bò pha sẵn", "gia vị", UnitType.LITER,
                                                BigDecimal.valueOf(22_000), BigDecimal.valueOf(6),
                                                "Sốt ướp bò đậm vị cho các món chế biến nhanh.",
                                                List.of(
                                                                new RecipeIngredientSeed("Nước tương",
                                                                                BigDecimal.valueOf(3)),
                                                                new RecipeIngredientSeed("Nước mắm",
                                                                                BigDecimal.valueOf(1.2)),
                                                                new RecipeIngredientSeed("Đường",
                                                                                BigDecimal.valueOf(1)),
                                                                new RecipeIngredientSeed("Tỏi",
                                                                                BigDecimal.valueOf(0.5)),
                                                                new RecipeIngredientSeed("Tiêu đen",
                                                                                BigDecimal.valueOf(0.15)),
                                                                new RecipeIngredientSeed("Dầu ăn",
                                                                                BigDecimal.valueOf(0.4)),
                                                                new RecipeIngredientSeed("Nước lọc",
                                                                                BigDecimal.valueOf(2)))),
                                new ProductSeed("Sốt bò tiêu đen", "Sốt bò vị tiêu đen", "sốt bò", UnitType.LITER,
                                                BigDecimal.valueOf(25_000), BigDecimal.valueOf(6),
                                                "Sốt tiêu đen cho bò áp chảo và cơm phần.",
                                                List.of(
                                                                new RecipeIngredientSeed("Nước tương",
                                                                                BigDecimal.valueOf(3)),
                                                                new RecipeIngredientSeed("Đường",
                                                                                BigDecimal.valueOf(1)),
                                                                new RecipeIngredientSeed("Tiêu đen",
                                                                                BigDecimal.valueOf(0.45)),
                                                                new RecipeIngredientSeed("Tỏi",
                                                                                BigDecimal.valueOf(0.45)),
                                                                new RecipeIngredientSeed("Nước lọc",
                                                                                BigDecimal.valueOf(2)),
                                                                new RecipeIngredientSeed("Dầu ăn",
                                                                                BigDecimal.valueOf(0.35)))),
                                new ProductSeed("Sốt bò mặn ngọt", "Sốt bò vị mặn ngọt cân bằng", "sốt bò",
                                                UnitType.LITER,
                                                BigDecimal.valueOf(24_000), BigDecimal.valueOf(6),
                                                "Sốt mặn ngọt để hoàn thiện bán thành phẩm bò.",
                                                List.of(
                                                                new RecipeIngredientSeed("Nước mắm",
                                                                                BigDecimal.valueOf(2)),
                                                                new RecipeIngredientSeed("Nước tương",
                                                                                BigDecimal.valueOf(2)),
                                                                new RecipeIngredientSeed("Đường",
                                                                                BigDecimal.valueOf(1.5)),
                                                                new RecipeIngredientSeed("Tỏi",
                                                                                BigDecimal.valueOf(0.4)),
                                                                new RecipeIngredientSeed("Ớt",
                                                                                BigDecimal.valueOf(0.15)),
                                                                new RecipeIngredientSeed("Nước lọc",
                                                                                BigDecimal.valueOf(2)))),
                                new ProductSeed("Hành tây chanh muối", "Món ăn kèm chua ngọt", "ăn kèm", UnitType.KG,
                                                BigDecimal.valueOf(16_000), BigDecimal.valueOf(3),
                                                "Hành tây trộn chanh muối dùng kèm món bò.",
                                                List.of(
                                                                new RecipeIngredientSeed("Hành tây",
                                                                                BigDecimal.valueOf(4)),
                                                                new RecipeIngredientSeed("Chanh tươi",
                                                                                BigDecimal.valueOf(1)),
                                                                new RecipeIngredientSeed("Đường",
                                                                                BigDecimal.valueOf(0.5)),
                                                                new RecipeIngredientSeed("Muối biển",
                                                                                BigDecimal.valueOf(0.18)),
                                                                new RecipeIngredientSeed("Ớt",
                                                                                BigDecimal.valueOf(0.08)))));

                for (ProductSeed seed : productSeeds) {
                        Category category = categories.get(seed.categoryName());
                        if (category == null) {
                                continue;
                        }

                        Product product = findProductByName(seed.name())
                                        .orElseGet(Product::new);
                        product.setName(seed.name());
                        product.setDescription(seed.description());
                        product.setPrice(seed.unitPrice());
                        product.setCategory(category);
                        product.setUnit(seed.unit());
                        product.setIsActive(true);
                        Product savedProduct = productRepository.save(product);

                        upsertRecipe(savedProduct, seed, materials, adminUser);
                }
        }

        private void upsertRecipe(Product product, ProductSeed seed, Map<String, Material> materials, User adminUser) {
                Recipe recipe = recipeRepository.findByProductIdAndIsActiveTrue(product.getId())
                                .orElseGet(Recipe::new);

                recipe.setProduct(product);
                recipe.setCreatedByUser(adminUser);
                recipe.setVersion(recipe.getVersion() == null ? 1 : recipe.getVersion());
                recipe.setIsActive(true);
                recipe.setYield(seed.yield());
                recipe.setInstructions(seed.instructions());

                if (recipe.getRecipeDetails() == null) {
                        recipe.setRecipeDetails(new ArrayList<>());
                } else {
                        recipe.getRecipeDetails().clear();
                }

                for (RecipeIngredientSeed ingredientSeed : seed.ingredients()) {
                        Material material = materials.get(ingredientSeed.materialName());
                        if (material == null) {
                                continue;
                        }

                        RecipeDetail detail = RecipeDetail.builder()
                                        .recipe(recipe)
                                        .material(material)
                                        .quantityNeeded(ingredientSeed.quantityNeeded())
                                        .build();
                        recipe.getRecipeDetails().add(detail);
                }

                recipeRepository.save(recipe);
        }

        private void upsertKitchenStockItem(KitchenWarehouse warehouse, Material material, BigDecimal quantity,
                        LocalDate expiryDate) {
                KitchenStockItem stockItem = kitchenStockItemRepository
                                .findByWarehouse_WarehouseIdAndMaterial_Id(warehouse.getWarehouseId(), material.getId())
                                .stream()
                                .findFirst()
                                .orElseGet(KitchenStockItem::new);

                stockItem.setWarehouse(warehouse);
                stockItem.setMaterial(material);
                stockItem.setProduct(null);
                stockItem.setQuantity(quantity);
                stockItem.setExpiryDate(expiryDate);
                kitchenStockItemRepository.save(stockItem);
        }

        private java.util.Optional<Category> findCategoryByName(String name) {
                return categoryRepository.findAll().stream()
                                .filter(category -> name.equalsIgnoreCase(category.getName()))
                                .findFirst();
        }

        private java.util.Optional<Material> findMaterialByName(String name) {
                return materialRepository.findAll().stream()
                                .filter(material -> name.equalsIgnoreCase(material.getName()))
                                .findFirst();
        }

        private java.util.Optional<Product> findProductByName(String name) {
                return productRepository.findAll().stream()
                                .filter(product -> name.equalsIgnoreCase(product.getName()))
                                .findFirst();
        }

        private record CategorySeed(String name, String description, boolean active) {
        }

        private record MaterialSeed(String name, UnitType unit, BigDecimal quantity, int expiryDays) {
        }

        private record RecipeIngredientSeed(String materialName, BigDecimal quantityNeeded) {
        }

        private record ProductSeed(String name,
                        String description,
                        String categoryName,
                        UnitType unit,
                        BigDecimal unitPrice,
                        BigDecimal yield,
                        String instructions,
                        List<RecipeIngredientSeed> ingredients) {
        }
}
