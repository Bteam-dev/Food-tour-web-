package com.example.FoodTourApp.config.SecurityConfig;

import com.example.FoodTourApp.config.JWTConfig.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // Thêm dòng này để enable @PreAuthorize ở controller
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UserDetailsService userDetailsService;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                         UserDetailsService userDetailsService) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints - không cần authentication
                        .requestMatchers("/api/auth/**").permitAll()

                        // Category public endpoints
                        .requestMatchers("/api/categories", "/api/categories/tree",
                                "/api/categories/parents", "/api/categories/parent/**",
                                "/api/categories/{id}").permitAll()

                        // Shop public endpoints - xem thông tin shop
                        .requestMatchers("/api/shops", "/api/shops/{id}").permitAll()

                        // Address public endpoints - HERE API autocomplete/lookup
                        .requestMatchers("/api/addresses/autocomplete",
                                "/api/addresses/lookup", "/api/addresses/geocode").permitAll()

                        // Product public endpoints (sẽ tạo sau)
                        .requestMatchers("/api/products", "/api/products/{id}",
                                "/api/products/category/**", "/api/products/search").permitAll()

                        // Admin only endpoints
                        .requestMatchers("/api/categories/all").hasRole("ADMIN")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // Seller only endpoints - quản lý shop và product
                        .requestMatchers("/api/shops/my-shops").hasRole("SELLER")
                        .requestMatchers("/api/products/my-products").hasRole("SELLER")

                        // User endpoints
                        .requestMatchers("/api/user/**").hasAnyRole("USER", "SELLER", "ADMIN")

                        // Các request còn lại cần authentication
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }
}