package com.swp.ckms.util;

import com.swp.ckms.entity.User;
import com.swp.ckms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class RecipientResolver {

    private final UserRepository userRepository;

    public String resolveStoreManagerEmail(Long storeId, Long createdByUserId) {
        // 1. Try to find active Store Manager for the store
        List<User> managers = userRepository.findAll().stream() // Ideally use a custom repo method
                .filter(u -> u.getStore() != null && u.getStore().getStoreId().equals(storeId))
                .filter(u -> u.getRole() != null && "STORE_MANAGER".equals(u.getRole().getRoleName()))
                .filter(User::getIsActive)
                .toList();

        if (!managers.isEmpty()) {
            return managers.get(0).getEmail();
        }

        // 2. Fallback to the user who created the order/item
        if (createdByUserId != null) {
            Optional<User> creator = userRepository.findById(createdByUserId);
            if (creator.isPresent() && creator.get().getEmail() != null) {
                return creator.get().getEmail();
            }
        }

        log.warn("Could not resolve recipient email for storeId: {}", storeId);
        return null;
    }

    public String resolveCoordinatorEmail(Long kitchenId) {
        List<User> coordinators = userRepository.findAll().stream()
                .filter(u -> u.getKitchen() != null && u.getKitchen().getKitchenId().equals(kitchenId))
                .filter(u -> u.getRole() != null && "COORDINATOR".equals(u.getRole().getRoleName()))
                .filter(User::getIsActive)
                .toList();

        if (!coordinators.isEmpty()) {
            return coordinators.get(0).getEmail();
        }

        log.warn("Could not resolve coordinator email for kitchenId: {}", kitchenId);
        return null;
    }
}
