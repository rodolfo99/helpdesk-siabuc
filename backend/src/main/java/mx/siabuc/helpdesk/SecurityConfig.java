package mx.siabuc.helpdesk;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean UserDetailsService userDetailsService(JdbcTemplate db) {
        return username -> {
            var rows = db.query("""
                SELECT u.username,u.password_hash,u.auth_version,u.role,u.active,(u.role IN ('ADMIN','BILLING','TECH') OR COALESCE(i.active,FALSE)) AS institution_active
                FROM app_users u LEFT JOIN institutions i ON i.id=u.institution_id WHERE u.username=?
                """, (r, n) -> new AccountDetails(r.getString("username"), r.getString("password_hash"),
                    r.getBoolean("active") && r.getBoolean("institution_active"), r.getString("role"), r.getLong("auth_version")), username);
            if (rows.isEmpty()) throw new UsernameNotFoundException("Credenciales inválidas.");
            return rows.get(0);
        };
    }

    @Bean SecurityFilterChain security(HttpSecurity http, HelpdeskService service, ObjectMapper json,
            @Value("${app.cookie-secure:false}") boolean secure) throws Exception {
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieCustomizer(cookie -> cookie.path("/").sameSite("Strict").secure(secure));
        http
            .csrf(csrf -> csrf.csrfTokenRepository(csrfRepository).csrfTokenRequestHandler(new SpaCsrfHandler()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/csrf", "/api/auth/login", "/api/health", "/error").permitAll()
                .requestMatchers("/api/users", "/api/users/**", "/api/audit", "/api/notifications", "/api/notifications/**").hasRole("ADMIN")
                .requestMatchers("/api/client-finance-access", "/api/client-finance-access/**", "/api/backups/**", "/api/satisfaction/reports", "/api/satisfaction/reports.csv", "/api/satisfaction/options").hasRole("ADMIN")
                .requestMatchers("/api/client-finance", "/api/client-finance/**").hasRole("USER")
                .requestMatchers("/api/quotes", "/api/quotes/**", "/api/renewals", "/api/renewals/**", "/api/payment-requests", "/api/payment-requests/**").hasAnyRole("ADMIN", "BILLING")
                .requestMatchers("/api/knowledge", "/api/knowledge/**").hasAnyRole("ADMIN", "TECH", "USER")
                .requestMatchers("/api/satisfaction/**").hasAnyRole("ADMIN", "USER")
                .requestMatchers("/api/support-staff", "/api/timers/**").hasAnyRole("ADMIN", "TECH")
                .requestMatchers("/api/saved-views", "/api/saved-views/**").hasAnyRole("ADMIN", "TECH", "USER")
                .requestMatchers("/api/reply-templates", "/api/reply-templates/**", "/api/incidents", "/api/incidents/**").hasAnyRole("ADMIN", "TECH")
                .requestMatchers(HttpMethod.GET, "/api/sla-alerts/settings").hasAnyRole("ADMIN", "TECH")
                .requestMatchers("/api/sla-alerts/**").hasRole("ADMIN")
                .requestMatchers("/api/support-packages", "/api/support-packages/**", "/api/installments", "/api/installments/**", "/api/reconciliation", "/api/reconciliation/**", "/api/statements", "/api/statements/**").hasAnyRole("ADMIN", "BILLING")
                .requestMatchers("/api/policies", "/api/policies/**", "/api/reports/billing", "/api/reports/billing.xlsx", "/api/reports/billing.pdf").hasAnyRole("ADMIN", "BILLING")
                .requestMatchers("/api/payments/*/attachments", "/api/payment-attachments/**", "/api/payments/*/receipt.pdf").hasAnyRole("ADMIN", "BILLING")
                .requestMatchers("/api/license-support-defaults").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/licenses").authenticated()
                .requestMatchers("/api/licenses", "/api/licenses/**").hasRole("ADMIN")
                .requestMatchers("/api/reports/**", "/api/support-settings", "/api/balance-alert-settings", "/api/balance-alert-settings/**").hasRole("ADMIN")
                .requestMatchers("/api/attachments/**", "/api/remote-sessions/**", "/api/case-profiles").hasAnyRole("ADMIN", "TECH", "USER")
                .requestMatchers("/api/cases", "/api/cases/**").hasAnyRole("ADMIN", "TECH", "USER")
                .requestMatchers(HttpMethod.GET, "/api/institutions").authenticated()
                .requestMatchers("/api/institutions", "/api/institutions/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/payments").hasAnyRole("ADMIN", "BILLING")
                .requestMatchers(HttpMethod.POST, "/api/payments").hasAnyRole("ADMIN", "BILLING")
                .requestMatchers("/api/payments/**").hasRole("ADMIN")
                .requestMatchers("/api/**").authenticated()
                .anyRequest().denyAll())
            .formLogin(form -> form.loginProcessingUrl("/api/auth/login")
                .successHandler((request, response, authentication) -> {
                    request.getSession().setAttribute("credentialVersion", ((AccountDetails) authentication.getPrincipal()).version);
                    write(json, response, 200, "Sesión iniciada.");
                })
                .failureHandler((request, response, exception) -> write(json, response, 401, "Usuario o contraseña incorrectos, o cuenta no disponible.")))
            .logout(logout -> logout.logoutUrl("/api/auth/logout").invalidateHttpSession(true).clearAuthentication(true)
                .deleteCookies("JSESSIONID").logoutSuccessHandler((request, response, auth) -> response.setStatus(204)))
            .requestCache(cache -> cache.disable())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) -> write(json, response, 401, "Inicie sesión para continuar."))
                .accessDeniedHandler((request, response, exception) -> write(json, response, 403, "Acceso denegado o token de seguridad inválido. Actualice la sesión.")))
            .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'")))
            .addFilterAfter(new ActiveAccountFilter(service, json), AnonymousAuthenticationFilter.class);
        return http.build();
    }
    static void write(ObjectMapper json, HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status); response.setContentType("application/json"); response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getWriter(), Map.of("message", message));
    }
    static final class AccountDetails extends org.springframework.security.core.userdetails.User {
        final long version;
        AccountDetails(String username, String password, boolean enabled, String role, long version) {
            super(username, password, enabled, true, true, true, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
            this.version = version;
        }
    }
    /** Supports Angular's plain XSRF cookie header and Spring Security's XOR request attribute. */
    static final class SpaCsrfHandler implements CsrfTokenRequestHandler {
        private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
        private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();
        @Override public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> token) {
            xor.handle(request, response, token);
            request.setAttribute("rawCsrfToken", token.get()); // Materialize the cookie and expose the raw API token.
        }
        @Override public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken token) {
            return (StringUtils.hasText(request.getHeader(token.getHeaderName())) ? plain : xor).resolveCsrfTokenValue(request, token);
        }
    }
    static final class ActiveAccountFilter extends OncePerRequestFilter {
        private final HelpdeskService service;
        private final ObjectMapper json;
        ActiveAccountFilter(HelpdeskService service, ObjectMapper json) { this.service = service; this.json = json; }
        @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
                try {
                    Models.User current = service.activeUser(auth.getName());
                    Object version = request.getSession(false) == null ? null : request.getSession(false).getAttribute("credentialVersion");
                    if (!(version instanceof Long) || (Long) version != service.credentialVersion(current.username()))
                        throw new ApiException(401, "La contraseña cambió. Inicie sesión nuevamente.");
                    // Refresh roles every request; administrative edits take effect in existing sessions.
                    var refreshed = UsernamePasswordAuthenticationToken.authenticated(current.username(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + current.role().name())));
                    refreshed.setDetails(auth.getDetails());
                    SecurityContextHolder.getContext().setAuthentication(refreshed);
                } catch (ApiException e) {
                    if (request.getSession(false) != null) request.getSession(false).invalidate();
                    SecurityContextHolder.clearContext();
                    write(json, response, 401, e.getMessage()); return;
                } catch (org.springframework.dao.DataAccessException e) {
                    write(json, response, 503, "Base de datos no disponible. Inténtelo de nuevo."); return;
                }
            }
            chain.doFilter(request, response);
        }
    }
}
