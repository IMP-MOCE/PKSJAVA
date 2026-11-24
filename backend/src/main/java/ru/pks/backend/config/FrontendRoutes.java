package ru.pks.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class FrontendRoutes implements WebMvcConfigurer {
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        String[] routes = {"/login", "/password", "/bookings", "/bookings/**", "/employees", "/employees/**",
                "/analytics", "/import", "/admin/users", "/admin/users/**", "/admin/audit"};
        for (String route : routes) {
            registry.addViewController(route).setViewName("forward:/index.html");
        }
    }
}
