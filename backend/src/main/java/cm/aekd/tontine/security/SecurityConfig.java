package cm.aekd.tontine.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configuration de sécurité de base (CLAUDE.md §4 : Spring Security + JWT + BCrypt).
 *
 * - Session stateless (authentification 100% par JWT, pas de session serveur).
 * - "/api/auth/**" reste public : c'est le futur point d'entrée de login
 *   (CLAUDE.md §28), dont le contrôleur sera implémenté à l'étape 13.
 * - Toute autre requête nécessite une authentification.
 *
 * Les règles d'autorisation fines par rôle (ADMIN / TRESORIER / MEMBRE,
 * détaillées en CLAUDE.md §6) sont ajoutées au niveau des contrôleurs via
 * {@code @PreAuthorize} (voir par ex. SessionController), au fur et à
 * mesure de leur création, pour rester alignées avec les permissions
 * réelles de chaque endpoint plutôt que d'anticiper des règles.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/error").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
