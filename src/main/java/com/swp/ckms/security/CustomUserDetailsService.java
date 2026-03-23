package com.swp.ckms.security;

import com.swp.ckms.entity.User;
import com.swp.ckms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        List<SimpleGrantedAuthority> authorities = user.getRole().getPrivileges().stream()
                .map(privilege -> new SimpleGrantedAuthority(privilege.getCode()))
                .collect(Collectors.toList());

        // Add Role as authority too
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().getRoleName().toUpperCase()));

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .authorities(authorities)
                // Account is disabled if status is NOT ACTIVE
                .disabled(user.getStatus() != com.swp.ckms.enums.UserStatus.ACTIVE)
                .build();
    }
}
