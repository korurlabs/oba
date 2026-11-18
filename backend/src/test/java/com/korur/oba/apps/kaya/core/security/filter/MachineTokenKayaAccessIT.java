package com.korur.oba.apps.kaya.core.security.filter;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

// 2. Third-party
import com.jayway.jsonpath.JsonPath;

// 3. Java SDK
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 4. Internal
import com.korur.oba.apps.kaya.support.AbstractIngestIT;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The machine-token filter closes every delivery path to a token whose user has no Kaya grant, whatever
 * the spelling of the path, and keeps ingest open: a plain path and a percent-encoded path both answer
 * 403 APP_SEC_001 on delivery and pass the filter on ingest.
 */
class MachineTokenKayaAccessIT extends AbstractIngestIT {

    private static final String APP_ACCESS_DENIED = "APP_SEC_001";
    private static final Pattern PATH_VARIABLE = Pattern.compile("\\{(\\w+)(?::[^}]*)?}");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    private record Call(HttpMethod method, String template) {
    }

    private Machine machineWithoutKaya() throws Exception {
        Tenant tenant = newTenant();
        Machine machine = registerMachine(tenant, signIn(tenant, "ada"), "laptop");
        jdbcTemplate.update("UPDATE shrine.organization_app_grants SET deleted = TRUE "
                + "WHERE organization_id = ? AND app = 'KAYA'::shrine_app_grant_app_enum", tenant.organization().getId());
        return machine;
    }

    private List<Call> deliveryCalls() {
        TreeSet<String> seen = new TreeSet<>();
        List<Call> calls = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            for (String pattern : entry.getKey().getPatternValues()) {
                if (!pattern.startsWith("/kaya/delivery")) {
                    continue;
                }
                var methods = entry.getKey().getMethodsCondition().getMethods();
                HttpMethod method = methods.isEmpty() ? HttpMethod.GET : HttpMethod.valueOf(methods.iterator().next().name());
                if (seen.add(method + " " + pattern)) {
                    calls.add(new Call(method, pattern));
                }
            }
        }
        assertFalse(calls.isEmpty(), "No delivery handler found");
        return calls;
    }

    private static String filled(String template) {
        Matcher matcher = PATH_VARIABLE.matcher(template);
        StringBuilder path = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(path, UUID.randomUUID().toString());
        }
        matcher.appendTail(path);
        return path.toString();
    }

    /** Percent-encodes one letter: the second segment, and the third when it starts with a letter. */
    private static List<String> spellings(String path) {
        List<String> result = new ArrayList<>();
        result.add(path);
        result.add(path.replaceFirst("^/kaya/delivery", "/kaya/%64elivery"));
        String[] segments = path.split("/");
        if (segments.length > 3 && Character.isLetter(segments[3].charAt(0)) && !segments[3].matches("[0-9a-f-]{36}")) {
            segments[3] = "%" + Integer.toHexString(segments[3].charAt(0)) + segments[3].substring(1);
            result.add(String.join("/", segments));
        }
        return result;
    }

    private MvcResult call(Machine machine, HttpMethod method, String path) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.request(method, URI.create(path))
                        .header("Authorization", "Bearer " + machine.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn();
    }

    @Test
    void deliveryPaths_tokenOfUserWithoutKaya_plainAndEncodedAnswer403AppAccessDenied() throws Exception {
        Machine machine = machineWithoutKaya();
        List<String> failures = new ArrayList<>();
        int checked = 0;

        for (Call call : deliveryCalls()) {
            for (String path : spellings(filled(call.template()))) {
                MvcResult result = call(machine, call.method(), path);
                checked++;
                String body = result.getResponse().getContentAsString();
                String code = body.isBlank() ? null : JsonPath.read(body, "$.error.code");
                if (result.getResponse().getStatus() != 403 || !APP_ACCESS_DENIED.equals(code)) {
                    failures.add(call.method() + " " + path + " answered " + result.getResponse().getStatus() + " " + code);
                }
            }
        }

        assertTrue(checked >= 6, "Too few delivery calls were checked: " + checked);
        assertTrue(failures.isEmpty(), "Delivery calls that were not refused:\n" + String.join("\n", failures));
    }

    @Test
    void deliveryPaths_encodedProposalsAndLessons_answer403AppAccessDenied() throws Exception {
        Machine machine = machineWithoutKaya();

        for (String path : List.of("/kaya/%64elivery/proposals", "/kaya/delivery/%6cessons/" + UUID.randomUUID(),
                "/kaya/delivery/proposals")) {
            mockMvc.perform(MockMvcRequestBuilders.get(URI.create(path)).header("Authorization", "Bearer " + machine.token()))
                    .andExpect(status().isForbidden());
            MvcResult result = call(machine, HttpMethod.GET, path);
            assertTrue(result.getResponse().getContentAsString().contains(APP_ACCESS_DENIED), path);
        }
    }

    @Test
    void ingest_tokenOfUserWithoutKaya_plainAndEncodedPathPassTheFilter() throws Exception {
        Machine machine = machineWithoutKaya();
        String body = json(batch(machine, sessionId(), remote(), List.of(event(1, "SessionStart"))));

        for (String path : List.of("/kaya/ingest/batches", "/kaya/%69ngest/batches", "/kaya/ingest/%62atches")) {
            mockMvc.perform(MockMvcRequestBuilders.post(URI.create(path))
                            .header("Authorization", "Bearer " + machine.token())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk());
        }
    }
}
