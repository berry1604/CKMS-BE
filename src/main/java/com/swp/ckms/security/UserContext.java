package com.swp.ckms.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserContext {
    private Long userId;
    private String username;
    private Set<String> roles;
    private Long storeId;
    private Long coordinatorId; // Linked to kitchenId if applicable
    private String scope; // SYSTEM, STORE, etc.
}
