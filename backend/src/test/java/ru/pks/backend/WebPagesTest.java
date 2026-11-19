package ru.pks.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import ru.pks.backend.domain.Role;
import ru.pks.backend.domain.UserAccount;
import ru.pks.backend.repository.BookingRepository;
import ru.pks.backend.repository.EmployeeRepository;
import ru.pks.backend.repository.UserAccountRepository;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:parking-web;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebPagesTest {
    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository users;
    @Autowired EmployeeRepository employees;
    @Autowired BookingRepository bookings;
    @Autowired PasswordEncoder encoder;

    @Test
    void htmlFormsWorkWithSessionAndCsrf() throws Exception {
        mvc.perform(get("/bookings")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(not(containsString("app.js"))));

        MockHttpSession temporary = login("admin@parking.test", "Temporary123!Pass");
        mvc.perform(get("/bookings").session(temporary)).andExpect(redirectedUrl("/password"));
        mvc.perform(get("/password").session(temporary)).andExpect(status().isOk())
                .andExpect(content().string(containsString("/password/initial")));
        mvc.perform(post("/password/initial").session(temporary).with(csrf())
                .param("newPassword", "Ready123!Secure")).andExpect(redirectedUrl("/login?changed"));
        MockHttpSession admin = login("admin@parking.test", "Ready123!Secure");

        mvc.perform(get("/api/bookings").session(admin)).andExpect(status().isUnauthorized());
        mvc.perform(post("/employees").session(admin)).andExpect(status().isForbidden());
        mvc.perform(post("/employees").session(admin).with(csrf())
                .param("name", "Анна Петрова").param("email", "anna@parking.test")
                .param("active", "true").param("version", "0")).andExpect(redirectedUrl("/employees"));
        var employee = employees.findByEmailIgnoreCase("anna@parking.test").orElseThrow();
        mvc.perform(get("/bookings/new").session(admin)).andExpect(status().isOk())
                .andExpect(content().string(containsString(employee.getUuid().toString())));

        mvc.perform(post("/bookings").session(admin).with(csrf())
                .param("code", "WEB-10").param("employeeId", employee.getUuid().toString())
                .param("spot", "10").param("date", LocalDate.now().toString())
                .param("status", "CREATED").param("purpose", "Встреча")
                .param("tags", "первый, второй").param("_guest", "on").param("version", "0"))
                .andExpect(redirectedUrl("/bookings"));
        var booking = bookings.findByCode("WEB-10").orElseThrow();
        mvc.perform(get("/bookings").session(admin).param("from", LocalDate.now().toString())
                .param("spot", "10").param("tag", "второй")).andExpect(status().isOk())
                .andExpect(content().string(containsString("WEB-10")));

        String bookingPath = "/bookings/" + booking.getUuid();
        mvc.perform(get(bookingPath + "/edit").session(admin)).andExpect(status().isOk());
        mvc.perform(post(bookingPath + "/notes").session(admin).with(csrf())
                .param("body", "Тестовая заметка").param("pinned", "true").param("version", "0"))
                .andExpect(redirectedUrl(bookingPath + "/notes"));
        mvc.perform(get(bookingPath + "/notes").session(admin)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Тестовая заметка")));
        mvc.perform(get("/bookings/export").session(admin).param("q", "WEB-10"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("WEB-10")));
        mvc.perform(post(bookingPath + "/archive").session(admin).with(csrf())
                .param("version", Long.toString(booking.getVersion()))).andExpect(redirectedUrl("/bookings"));
        mvc.perform(get("/bookings").session(admin)).andExpect(content().string(not(containsString("WEB-10"))));
        mvc.perform(get("/bookings").session(admin).param("archived", "true"))
                .andExpect(content().string(containsString("WEB-10")));

        for (String path : new String[]{"/employees", "/analytics", "/admin/users", "/admin/users/new", "/admin/audit", "/import", "/v3/api-docs"}) {
            mvc.perform(get(path).session(admin)).andExpect(status().isOk());
        }

        var file = new MockMultipartFile("file", "bookings.csv", "text/csv",
                ("code,employeeEmail,spot,date,status,purpose,guest,tags\nWEB-CSV,anna@parking.test,12,"
                        + LocalDate.now().plusDays(2) + ",CREATED,Visit,false,\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var preview = mvc.perform(multipart("/import/preview").file(file).session(admin).with(csrf())
                .param("mode", "NEW_ONLY")).andExpect(redirectedUrl("/import")).andReturn();
        mvc.perform(get("/import").session(admin).flashAttrs(preview.getFlashMap()))
                .andExpect(content().string(containsString("Корректных строк: 1")));
    }

    @Test
    void viewerCannotEditAndDisabledAccountLosesSessionAccess() throws Exception {
        var viewer = new UserAccount(UUID.randomUUID(), "readonly@parking.test", "Наблюдатель",
                encoder.encode("ReadOnly123!Pass"), Set.of(Role.VIEWER));
        viewer.setPasswordChangeRequired(false);
        users.saveAndFlush(viewer);
        MockHttpSession session = login(viewer.getEmail(), "ReadOnly123!Pass");
        mvc.perform(get("/bookings").session(session)).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Новая бронь"))));
        mvc.perform(get("/bookings/new").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/admin/users").session(session)).andExpect(status().isForbidden());
        viewer = users.findById(viewer.getId()).orElseThrow();
        viewer.setEnabled(false);
        users.saveAndFlush(viewer);
        mvc.perform(get("/bookings").session(session)).andExpect(status().is3xxRedirection());
    }

    private MockHttpSession login(String email, String password) throws Exception {
        return (MockHttpSession) mvc.perform(post("/login").with(csrf())
                        .param("email", email).param("password", password))
                .andExpect(redirectedUrl("/bookings")).andReturn().getRequest().getSession(false);
    }
}
