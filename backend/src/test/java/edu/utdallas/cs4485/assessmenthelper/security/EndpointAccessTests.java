package edu.utdallas.cs4485.assessmenthelper.security;

import static java.util.Comparator.comparing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import edu.utdallas.cs4485.assessmenthelper.repository.ProfessorRepository;
import edu.utdallas.cs4485.assessmenthelper.service.ObserverRequestService;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

// Gets CSRF tokens the way the frontend does; AuthenticationTests explains why the csrf() helper is not used.
@SpringBootTest
@AutoConfigureMockMvc
@Import(EndpointAccessTests.FixedTime.class)
@Transactional
class EndpointAccessTests {

    // Two days after the committee sent Avery's Fall 2026 observer list, so Avery can still send requests.
    @TestConfiguration
    static class FixedTime {
        @Bean
        @Primary
        Clock accessClock() {
            return Clock.fixed(Instant.parse("2026-10-21T15:00:00Z"), ZoneId.of("America/Chicago"));
        }
    }

    private static final Set<String> PUBLIC = Set.of("GET /api/auth/csrf");

    private static final Set<String> OPEN_TO_EVERYONE_SIGNED_IN = Set.of(
            "GET /api/auth/me",
            "GET /api/semesters",
            "GET /api/courses",
            "GET /api/sections",
            "GET /api/evaluation-policies",
            "GET /api/evaluation-policies/{professorRank}");

    private static final Set<String> CHECKED_BY_THE_SERVICE = Set.of(
            "GET /api/observations/{observationId}",
            "POST /api/observations/{observationId}/start",
            "PUT /api/observations/{observationId}/responses",
            "POST /api/observations/{observationId}/submit",
            "POST /api/observations/{observationId}/observee-sign",
            "POST /api/observations/{observationId}/forward",
            "GET /api/feedback-surveys",
            "POST /api/feedback-surveys/{cycleId}");

    private static final String COMMITTEE_ONLY = "hasRole('AC')";
    private static final Pattern PROFESSOR_RULE =
            Pattern.compile("@professorAccess\\.(canView|isSelf)\\(principal, #(\\w+)\\)");
    private static final Pattern PATH_VARIABLE = Pattern.compile("\\{(\\w+)}");
    private static final String UNKNOWN_ID = "999999";

    // From data.sql: Kaia is on the Jonsson School's committee, and Isla teaches in a different school.
    private static final String KAIA = "kaia.clearbrook@example.edu";
    private static final String DARIO = "dario.hollowell@example.edu";
    private static final String LEANDER = "leander.coldwell@example.edu";
    private static final String KEIRA = "keira.larkspur@example.edu";
    private static final String AVERY = "avery.hazelwood@example.edu";
    private static final String ISLA = "isla.wrenfield@example.edu";

    private record Endpoint(String method, String path, HandlerMethod handler, String rule) {
        String key() {
            return method + " " + path;
        }

        String professorVariable() {
            Matcher matcher = rule == null ? null : PROFESSOR_RULE.matcher(rule);
            return matcher != null && matcher.matches() ? matcher.group(2) : null;
        }

        boolean onlyForThatProfessor() {
            return rule != null && rule.startsWith("@professorAccess.isSelf");
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mappings;

    @Autowired
    private AccountDetailsService accounts;

    @Autowired
    private ProfessorRepository professors;

    @Autowired
    private ObserverRequestService requests;

    private Cookie csrf;

    @BeforeEach
    void csrfToken() throws Exception {
        csrf = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    }

    @Test
    void everyEndpointWithoutAnAccessRuleIsOnAReviewedList() {
        Set<String> withoutRule = endpoints().stream()
                .filter(endpoint -> endpoint.rule() == null)
                .map(Endpoint::key)
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> reviewed = Stream.of(PUBLIC, OPEN_TO_EVERYONE_SIGNED_IN, CHECKED_BY_THE_SERVICE)
                .flatMap(Set::stream)
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(withoutRule).isEqualTo(reviewed);
    }

    @Test
    void signedOutRequestsAreRefusedEverywhereExceptTheCsrfToken() throws Exception {
        List<Endpoint> signedIn = endpoints().stream()
                .filter(endpoint -> !PUBLIC.contains(endpoint.key()))
                .toList();

        assertThat(statusesOtherThan(401, signedIn, endpoint -> request(endpoint, Map.of())))
                .isEmpty();
    }

    @Test
    void professorsAreRefusedEveryCommitteeEndpoint() throws Exception {
        AccountPrincipal dario = accounts.loadUserByUsername(DARIO);
        AccountPrincipal kaia = accounts.loadUserByUsername(KAIA);
        List<Endpoint> committeeOnly = endpoints().stream()
                .filter(endpoint -> COMMITTEE_ONLY.equals(endpoint.rule()))
                .toList();

        assertThat(committeeOnly).isNotEmpty();
        assertThat(statusesOtherThan(
                        403,
                        committeeOnly,
                        endpoint -> request(endpoint, Map.of()).with(user(dario))))
                .isEmpty();
        assertThat(statuses(
                        committeeOnly, endpoint -> request(endpoint, Map.of()).with(user(kaia))))
                .doesNotContainValue(401)
                .doesNotContainValue(403);
    }

    @Test
    void professorsAreRefusedAnotherProfessorsData() throws Exception {
        AccountPrincipal dario = accounts.loadUserByUsername(DARIO);
        int leander = accounts.loadUserByUsername(LEANDER).user().professorId();
        List<Endpoint> professorScoped = professorScoped();

        assertThat(professorScoped).isNotEmpty();
        assertThat(statusesOtherThan(
                        403,
                        professorScoped,
                        endpoint -> forProfessor(endpoint, leander).with(user(dario))))
                .isEmpty();
        assertThat(statuses(
                        professorScoped,
                        endpoint -> forProfessor(endpoint, dario.user().professorId())
                                .with(user(dario))))
                .doesNotContainValue(401)
                .doesNotContainValue(403);
    }

    @Test
    void committeeMembersAreRefusedProfessorsInAnotherSchool() throws Exception {
        AccountPrincipal kaia = accounts.loadUserByUsername(KAIA);
        int isla = professorId(ISLA);
        int dario = accounts.loadUserByUsername(DARIO).user().professorId();
        List<Endpoint> professorScoped = professorScoped();
        List<Endpoint> committeeCanView = professorScoped.stream()
                .filter(endpoint -> !endpoint.onlyForThatProfessor())
                .toList();

        assertThat(statusesOtherThan(
                        403,
                        professorScoped,
                        endpoint -> forProfessor(endpoint, isla).with(user(kaia))))
                .isEmpty();
        assertThat(statuses(
                        committeeCanView,
                        endpoint -> forProfessor(endpoint, dario).with(user(kaia))))
                .doesNotContainValue(401)
                .doesNotContainValue(403);
    }

    @Test
    void onlyTheRequestedObserverCanReadOrConfirmAnObserverRequest() throws Exception {
        int requestId = averyAsksKeira();
        String path = "/api/observer-requests/" + requestId;
        AccountPrincipal dario = accounts.loadUserByUsername(DARIO);
        AccountPrincipal keira = accounts.loadUserByUsername(KEIRA);

        mvc.perform(withCsrf(get(path)).with(user(dario))).andExpect(status().isForbidden());
        mvc.perform(withCsrf(post(path + "/confirm")).with(user(dario))).andExpect(status().isForbidden());
        assertThat(requests.findById(requestId).status()).isEqualTo("PENDING");

        mvc.perform(withCsrf(get(path)).with(user(keira))).andExpect(status().isOk());
        String dates = mvc.perform(get(
                                "/api/observer-eligibility/faculty/{id}/requests/{requestId}/dates",
                                professorId(KEIRA),
                                requestId)
                        .with(user(keira)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String date = JsonPath.read(dates, "$[0]");
        mvc.perform(withCsrf(post(path + "/confirm"))
                        .with(user(keira))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"observationDate\":\"" + date + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void confirmingWithoutAnAvailableClassDateLeavesTheInvitationPending() throws Exception {
        int requestId = averyAsksKeira();
        String path = "/api/observer-requests/" + requestId + "/confirm";
        AccountPrincipal keira = accounts.loadUserByUsername(KEIRA);

        mvc.perform(withCsrf(post(path)).with(user(keira))).andExpect(status().isBadRequest());
        assertThat(requests.findById(requestId).status()).isEqualTo("PENDING");

        mvc.perform(withCsrf(post(path))
                        .with(user(keira))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"observationDate\":\"2026-10-01\"}"))
                .andExpect(status().isBadRequest());
        assertThat(requests.findById(requestId).status()).isEqualTo("PENDING");
    }

    private int averyAsksKeira() {
        int avery = professorId(AVERY);
        var signup = requests.options(avery).getFirst();
        var keira = signup.candidates().stream()
                .filter(candidate -> candidate.observerName().equals("Keira Larkspur"))
                .findFirst()
                .orElseThrow();
        return requests.createFromMatch(avery, signup.signupId(), keira.matchId(), null)
                .requestId();
    }

    private List<Endpoint> endpoints() {
        List<Endpoint> endpoints = new ArrayList<>();
        mappings.getHandlerMethods().forEach((info, handler) -> {
            for (String path : info.getPatternValues()) {
                if (!path.startsWith("/api/")) continue;
                for (RequestMethod method : info.getMethodsCondition().getMethods())
                    endpoints.add(new Endpoint(method.name(), path, handler, rule(handler)));
            }
        });
        endpoints.sort(comparing(Endpoint::key));
        return endpoints;
    }

    private List<Endpoint> professorScoped() {
        return endpoints().stream()
                .filter(endpoint -> endpoint.professorVariable() != null)
                .toList();
    }

    private static String rule(HandlerMethod handler) {
        PreAuthorize rule = AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), PreAuthorize.class);
        if (rule == null) rule = AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), PreAuthorize.class);
        return rule == null ? null : rule.value();
    }

    private MockHttpServletRequestBuilder forProfessor(Endpoint endpoint, int professorId) {
        return request(endpoint, Map.of(endpoint.professorVariable(), String.valueOf(professorId)));
    }

    // Ids not named in pathVariables point at nothing, and every query parameter and body is filled in, so that a
    // refusal can only come from the access rule and an allowed request never changes a real row.
    private MockHttpServletRequestBuilder request(Endpoint endpoint, Map<String, String> pathVariables) {
        String uri = PATH_VARIABLE
                .matcher(endpoint.path())
                .replaceAll(variable -> pathVariables.getOrDefault(variable.group(1), UNKNOWN_ID));
        MockHttpServletRequestBuilder request =
                withCsrf(MockMvcRequestBuilders.request(HttpMethod.valueOf(endpoint.method()), uri));
        for (MethodParameter parameter : endpoint.handler().getMethodParameters()) {
            RequestParam param = parameter.getParameterAnnotation(RequestParam.class);
            if (param != null) request.param(parameterName(parameter, param), UNKNOWN_ID);
            if (parameter.hasParameterAnnotation(RequestBody.class))
                request.contentType(MediaType.APPLICATION_JSON).content("{}");
        }
        return request;
    }

    private static String parameterName(MethodParameter parameter, RequestParam param) {
        if (!param.name().isEmpty()) return param.name();
        if (!param.value().isEmpty()) return param.value();
        parameter.initParameterNameDiscovery(new DefaultParameterNameDiscoverer());
        return parameter.getParameterName();
    }

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request) {
        return request.cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue());
    }

    private Map<String, Integer> statuses(
            List<Endpoint> endpoints, Function<Endpoint, MockHttpServletRequestBuilder> request) throws Exception {
        Map<String, Integer> statuses = new TreeMap<>();
        for (Endpoint endpoint : endpoints)
            statuses.put(
                    endpoint.key(),
                    mvc.perform(request.apply(endpoint))
                            .andReturn()
                            .getResponse()
                            .getStatus());
        return statuses;
    }

    private Map<String, Integer> statusesOtherThan(
            int expected, List<Endpoint> endpoints, Function<Endpoint, MockHttpServletRequestBuilder> request)
            throws Exception {
        Map<String, Integer> statuses = statuses(endpoints, request);
        statuses.values().removeIf(status -> status == expected);
        return statuses;
    }

    private int professorId(String email) {
        return professors.findAll().stream()
                .filter(professor -> professor.email().equals(email))
                .findFirst()
                .orElseThrow()
                .professorId();
    }
}
