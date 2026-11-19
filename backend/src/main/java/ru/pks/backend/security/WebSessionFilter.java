package ru.pks.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.pks.backend.repository.UserAccountRepository;
import java.io.IOException;
import java.util.Set;

public class WebSessionFilter extends OncePerRequestFilter {
    private static final Set<String> PUBLIC_PATHS = Set.of("/login", "/logout", "/styles.css", "/favicon.svg", "/error");
    private final UserAccountRepository users;

    public WebSessionFilter(UserAccountRepository users) {
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getServletPath();
        if (path.isEmpty()) path = request.getRequestURI();
        var context = SecurityContextHolder.getContext();
        var authentication = context.getAuthentication();
        if (!PUBLIC_PATHS.contains(path) && authentication != null
                && authentication.getPrincipal() instanceof UserPrincipal savedUser) {
            var account = users.findById(savedUser.id()).orElse(null);
            if (account == null || !account.isEnabled() || account.getAuthVersion() != savedUser.authVersion()) {
                request.getSession().invalidate();
                SecurityContextHolder.clearContext();
            } else {
                var user = UserPrincipal.from(account);
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
                if (user.passwordChangeRequired() && !path.equals("/password") && !path.equals("/password/initial")) {
                    response.sendRedirect(request.getContextPath() + "/password");
                    return;
                }
            }
        }
        chain.doFilter(request, response);
    }
}
