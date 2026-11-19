package ru.pks.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BackendApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    static String admin;
    static String viewer;
    static String editor;
    static String employeeId;
    static String secondEmployeeId;
    static String bookingId;
    static long bookingVersion;

    @Test @Order(1) void contextAuthTemporaryPasswordAndRoles() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/api/bookings")).andExpect(status().isUnauthorized());
        admin = login("admin@parking.test", "Temporary123!Pass").get("accessToken").asText();
        request("GET", "/api/bookings", admin, null).andExpect(status().isForbidden());
        request("POST", "/api/auth/change-initial-password", admin, Map.of("newPassword", "Changed123!Pass"))
                .andExpect(status().isNoContent());
        request("GET", "/api/auth/me", admin, null).andExpect(status().isUnauthorized());
        admin = login("admin@parking.test", "Changed123!Pass").get("accessToken").asText();
        request("GET", "/v3/api-docs", admin, null).andExpect(status().isOk());
        viewer = createUser("viewer@parking.test", "VIEWER");
        editor = createUser("editor@parking.test", "EDITOR");
        request("GET", "/api/admin/users", viewer, null).andExpect(status().isForbidden());
        request("GET", "/api/admin/audit", editor, null).andExpect(status().isForbidden());
        request("POST", "/api/employees", editor, Map.of("name", "Нельзя", "email", "x@company.ru", "active", true, "version", 0))
                .andExpect(status().isForbidden());
    }

    @Test @Order(2) void employeesBookingsSearchVersionAndArchive() throws Exception {
        employeeId = body(request("POST", "/api/employees", admin,
                Map.of("name", "Анна Петрова", "email", "anna@company.ru", "active", true, "version", 0))
                .andExpect(status().isCreated())).get("id").asText();
        secondEmployeeId = body(request("POST", "/api/employees", admin,
                Map.of("name", "Иван Сидоров", "email", "ivan@company.ru", "active", true, "version", 0))
                .andExpect(status().isCreated())).get("id").asText();
        request("POST", "/api/bookings", viewer, booking("P-1", employeeId, 12, 0)).andExpect(status().isForbidden());
        JsonNode created = body(request("POST", "/api/bookings", editor, booking("P-1", employeeId, 12, 0))
                .andExpect(status().isCreated()));
        bookingId = created.get("id").asText();
        bookingVersion = created.get("version").asLong();
        var missingVersion = new HashMap<>(booking("P-1", employeeId, 12, 0));
        missingVersion.remove("version");
        request("PUT", "/api/bookings/" + bookingId, editor, missingVersion).andExpect(status().isBadRequest());
        request("POST", "/api/bookings", editor, booking("CONFLICT", secondEmployeeId, 12, 0)).andExpect(status().isConflict());
        request("POST", "/api/bookings", editor, booking("CONFLICT-EMP", employeeId, 13, 0)).andExpect(status().isConflict());
        String query = "/api/bookings?q=ПЕТРОВА&guest=true&tag=гость&urgency=SOON&spot=10&spotOp=gt&sort=spot,asc&size=1";
        request("GET", query, viewer, null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        request("GET", "/api/bookings?q=%25", viewer, null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        request("GET", "/api/bookings?size=101", viewer, null).andExpect(status().isBadRequest());
        request("GET", "/api/bookings?sort=unknown,asc", viewer, null).andExpect(status().isBadRequest());
        request("GET", "/api/bookings?spotOp=sql", viewer, null).andExpect(status().isBadRequest());
        request("GET", "/api/bookings?from=2026-12-02&to=2026-12-01", viewer, null).andExpect(status().isBadRequest());
        var changed = body(request("PUT", "/api/bookings/" + bookingId, editor, booking("P-1", employeeId, 14, bookingVersion))
                .andExpect(status().isOk()));
        request("PUT", "/api/bookings/" + bookingId, editor, booking("P-1", employeeId, 15, bookingVersion)).andExpect(status().isConflict());
        bookingVersion = changed.get("version").asLong();
        var archived = body(request("DELETE", "/api/bookings/" + bookingId + "?version=" + bookingVersion, editor, null)
                .andExpect(status().isOk()));
        bookingVersion = archived.get("version").asLong();
        request("GET", "/api/bookings", viewer, null).andExpect(jsonPath("$.totalElements").value(0));
        request("GET", "/api/bookings?archived=true", viewer, null).andExpect(jsonPath("$.totalElements").value(1));
        var replacement = body(request("POST", "/api/bookings", editor, booking("P-REPLACE", secondEmployeeId, 14, 0))
                .andExpect(status().isCreated()));
        request("POST", "/api/bookings/" + bookingId + "/restore", editor, Map.of("version", bookingVersion)).andExpect(status().isConflict());
        request("DELETE", "/api/bookings/" + replacement.get("id").asText() + "?version=" + replacement.get("version").asLong(), editor, null)
                .andExpect(status().isOk());
        var restored = body(request("POST", "/api/bookings/" + bookingId + "/restore", editor, Map.of("version", bookingVersion))
                .andExpect(status().isOk()));
        bookingVersion = restored.get("version").asLong();
    }

    @Test @Order(3) void notesBulkCsvAnalyticsAndAudit() throws Exception {
        var note = body(request("POST", "/api/bookings/" + bookingId + "/notes", editor,
                Map.of("body", "Встреча с гостем", "pinned", true, "version", 0)).andExpect(status().isOk()));
        request("GET", "/api/bookings/" + bookingId + "/notes", viewer, null).andExpect(jsonPath("$[0].pinned").value(true));
        request("PUT", "/api/bookings/" + bookingId + "/notes/" + note.get("id").asText(), editor,
                Map.of("body", "Встреча перенесена", "pinned", false, "version", note.get("version").asLong())).andExpect(status().isOk());
        request("POST", "/api/bookings/bulk", editor, Map.of("ids", List.of(bookingId, UUID.randomUUID().toString()), "action", "ADD_TAG", "tag", "срочно"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.successful.length()").value(1)).andExpect(jsonPath("$.errors.length()").value(1));
        request("GET", "/api/bookings/export?tag=срочно", viewer, null).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("P-1")));
        String csv = "code,employeeEmail,spot,date,status,purpose,guest,tags\n=CSV-1,ivan@company.ru,25," + LocalDate.now().plusDays(2) + ",CREATED,=1+1,false,импорт\nBROKEN,missing@company.ru,3," + LocalDate.now() + ",CREATED,Визит,false,\n";
        var file = new MockMultipartFile("file", "bookings.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        var preview = body(mvc.perform(multipart("/api/bookings/import/preview").file(file).header("Authorization", "Bearer " + editor))
                .andExpect(status().isOk()).andExpect(jsonPath("$.validRows").value(1)).andExpect(jsonPath("$.invalidRows").value(1)));
        request("GET", "/api/bookings?q=CSV-1", viewer, null).andExpect(jsonPath("$.totalElements").value(0));
        request("POST", "/api/bookings/import/apply", admin, Map.of("previewId", preview.get("previewId").asText())).andExpect(status().isBadRequest());
        request("POST", "/api/bookings/import/apply", editor, Map.of("previewId", preview.get("previewId").asText()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.successful").value(1));
        request("POST", "/api/bookings/import/apply", editor, Map.of("previewId", preview.get("previewId").asText())).andExpect(status().isBadRequest());
        request("GET", "/api/bookings?q=CSV-1", viewer, null).andExpect(jsonPath("$.totalElements").value(1));
        byte[] exported = request("GET", "/api/bookings/export?q=CSV-1", viewer, null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertTrue(new String(exported, StandardCharsets.UTF_8).contains("'=CSV-1"));
        var exportedFile = new MockMultipartFile("file", "export.csv", "text/csv", exported);
        mvc.perform(multipart("/api/bookings/import/preview").file(exportedFile).param("mode", "UPSERT")
                .header("Authorization", "Bearer " + editor)).andExpect(status().isOk()).andExpect(jsonPath("$.validRows").value(1));
        request("GET", "/api/analytics/summary", viewer, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3)).andExpect(jsonPath("$.active").value(2)).andExpect(jsonPath("$.archived").value(1));
        request("GET", "/api/admin/audit?q=LOGIN_SUCCESS", admin, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].actorEmail").isNotEmpty());
        request("GET", "/api/bookings/not-a-uuid", viewer, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400)).andExpect(jsonPath("$.correlationId").isNotEmpty()).andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test @Order(4) void refreshRotationLogoutAndLoginLimit() throws Exception {
        JsonNode session = login("viewer@parking.test", "UserChanged123!Pass");
        String refresh = session.get("refreshToken").asText();
        var rotated = body(request("POST", "/api/auth/refresh", null, Map.of("refreshToken", refresh)).andExpect(status().isOk()));
        request("POST", "/api/auth/logout", null, Map.of("refreshToken", rotated.get("refreshToken").asText())).andExpect(status().isNoContent());
        request("POST", "/api/auth/refresh", null, Map.of("refreshToken", rotated.get("refreshToken").asText())).andExpect(status().isUnauthorized());
        for (int attempt = 0; attempt < 5; attempt++)
            request("POST", "/api/auth/login", null, Map.of("email", "viewer@parking.test", "password", "Wrong12345!Pass")).andExpect(status().isUnauthorized());
        request("POST", "/api/auth/login", null, Map.of("email", "viewer@parking.test", "password", "UserChanged123!Pass")).andExpect(status().isUnauthorized());
    }

    private Map<String, Object> booking(String code, String employee, int spot, long version) {
        return Map.of("code", code, "employeeId", employee, "spot", spot, "date", LocalDate.now().toString(),
                "status", "CREATED", "purpose", "Рабочий визит", "guest", true, "tags", List.of("гость"), "version", version);
    }

    private String createUser(String email, String role) throws Exception {
        request("POST", "/api/admin/users", admin, Map.of("email", email, "displayName", role,
                "temporaryPassword", "UserTemporary123!Pass", "roles", List.of(role))).andExpect(status().isCreated());
        String temporary = login(email, "UserTemporary123!Pass").get("accessToken").asText();
        request("POST", "/api/auth/change-initial-password", temporary, Map.of("newPassword", "UserChanged123!Pass")).andExpect(status().isNoContent());
        return login(email, "UserChanged123!Pass").get("accessToken").asText();
    }

    private JsonNode login(String email, String password) throws Exception {
        return body(request("POST", "/api/auth/login", null, Map.of("email", email, "password", password)).andExpect(status().isOk()));
    }

    private ResultActions request(String method, String url, String token, Object body) throws Exception {
        var builder = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(method), url);
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(builder);
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
