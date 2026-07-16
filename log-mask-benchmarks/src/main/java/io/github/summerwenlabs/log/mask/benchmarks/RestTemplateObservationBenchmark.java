package io.github.summerwenlabs.log.mask.benchmarks;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.helpers.NOPAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.summerwenlabs.log.mask.resttemplate.boot2.ObservedRestTemplate;
import io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure.LogMaskRestTemplateAutoConfiguration;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.AbstractClientHttpRequest;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestTemplate;

/**
 * Reproducible local measurements for approved RestTemplate observation
 * scenarios.
 *
 * <p>Each scenario uses the same deterministic in-memory transport. Setup,
 * Spring context creation, fixture validation, and logger replacement remain
 * outside timed methods so results isolate per-exchange adapter overhead.
 *
 * @author SummerWen
 * @since 0.1
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgs = {"-Xms512m", "-Xmx512m", "-XX:+UseG1GC"})
@Threads(1)
@State(Scope.Benchmark)
public class RestTemplateObservationBenchmark {

    private static final String EVENT_LOGGER_NAME = "log.mask.http";
    private static final String SPRING_LOGGER_NAME = "org.springframework";
    private static final URI EXCHANGE_URI = URI.create("http://benchmark.invalid/exchange");
    private static final int ONE_KIB_BYTES = 1024;
    private static final int SIXTY_FOUR_KIB_BYTES = 64 * 1024;
    private static final int EMPTY_PAYLOAD_JSON_BYTES = 14;

    private RestTemplate nativeRestTemplate;
    private BenchmarkScenario starterLoggingDisabled;
    private BenchmarkScenario loggingEnabledNoBody;
    private BenchmarkScenario loggingEnabledOneKiB;
    private BenchmarkScenario loggingEnabledSixtyFourKiB;
    private HttpEntity<Void> noBodyRequest;
    private HttpEntity<SizedPayload> oneKiBRequest;
    private HttpEntity<SizedPayload> sixtyFourKiBRequest;
    private DiscardingEventLogger discardingEventLogger;

    /** Set up five complete RestTemplate paths outside timed methods. */
    @Setup(org.openjdk.jmh.annotations.Level.Trial)
    public void setUp() throws IOException {
        discardingEventLogger = DiscardingEventLogger.install();

        ObjectMapper fixtureMapper = new ObjectMapper();
        SizedPayload oneKiBPayload = sizedPayload(ONE_KIB_BYTES);
        SizedPayload sixtyFourKiBPayload = sizedPayload(SIXTY_FOUR_KIB_BYTES);
        verifyUtf8JsonSize(fixtureMapper, oneKiBPayload, ONE_KIB_BYTES);
        verifyUtf8JsonSize(fixtureMapper, sixtyFourKiBPayload, SIXTY_FOUR_KIB_BYTES);

        noBodyRequest = new HttpEntity<Void>(new HttpHeaders());
        oneKiBRequest = new HttpEntity<SizedPayload>(oneKiBPayload, jsonHeaders());
        sixtyFourKiBRequest = new HttpEntity<SizedPayload>(sixtyFourKiBPayload, jsonHeaders());

        nativeRestTemplate = new RestTemplate(ControlledClientHttpRequestFactory.noContent());
        starterLoggingDisabled = startStarterScenario(
                false,
                ControlledClientHttpRequestFactory.noContent());
        loggingEnabledNoBody = startStarterScenario(
                true,
                ControlledClientHttpRequestFactory.noContent());
        loggingEnabledOneKiB = startStarterScenario(
                true,
                ControlledClientHttpRequestFactory.json(serialize(fixtureMapper, oneKiBPayload)));
        loggingEnabledSixtyFourKiB = startStarterScenario(
                true,
                ControlledClientHttpRequestFactory.json(
                        serialize(fixtureMapper, sixtyFourKiBPayload)));
    }

    /** Measure RestTemplate without the starter or observation chain. */
    @Benchmark
    public void nativeRestTemplate(Blackhole blackhole) {
        executeNoBody(nativeRestTemplate, blackhole);
    }

    /** Measure a present starter with exchange logging disabled. */
    @Benchmark
    public void starterPresentLoggingDisabled(Blackhole blackhole) {
        executeNoBody(starterLoggingDisabled.restTemplate(), blackhole);
    }

    /** Measure logging a complete bodyless request and response. */
    @Benchmark
    public void loggingEnabledNoBody(Blackhole blackhole) {
        executeNoBody(loggingEnabledNoBody.restTemplate(), blackhole);
    }

    /** Measure a 1 KiB typed JSON request and response. */
    @Benchmark
    public void loggingEnabledTypedDtoOneKiB(Blackhole blackhole) {
        executeTyped(loggingEnabledOneKiB.restTemplate(), oneKiBRequest, blackhole);
    }

    /** Measure a 64 KiB typed JSON request and response. */
    @Benchmark
    public void loggingEnabledTypedDtoSixtyFourKiB(Blackhole blackhole) {
        executeTyped(loggingEnabledSixtyFourKiB.restTemplate(), sixtyFourKiBRequest, blackhole);
    }

    /** Close local Spring contexts and restore the host logger after a trial. */
    @TearDown(org.openjdk.jmh.annotations.Level.Trial)
    public void tearDown() {
        close(starterLoggingDisabled);
        close(loggingEnabledNoBody);
        close(loggingEnabledOneKiB);
        close(loggingEnabledSixtyFourKiB);
        if (discardingEventLogger != null) {
            discardingEventLogger.close();
        }
    }

    private void executeNoBody(RestTemplate restTemplate, Blackhole blackhole) {
        ResponseEntity<Void> response = restTemplate.exchange(
                EXCHANGE_URI,
                HttpMethod.GET,
                noBodyRequest,
                Void.class);
        blackhole.consume(response);
    }

    private void executeTyped(
            RestTemplate restTemplate,
            HttpEntity<SizedPayload> request,
            Blackhole blackhole) {
        ResponseEntity<SizedPayload> response = restTemplate.exchange(
                EXCHANGE_URI,
                HttpMethod.POST,
                request,
                SizedPayload.class);
        blackhole.consume(response.getBody());
    }

    private static BenchmarkScenario startStarterScenario(
            boolean loggingEnabled,
            ControlledClientHttpRequestFactory transport) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put(
                "log-mask.logging.rest-template.enabled",
                Boolean.toString(loggingEnabled));
        context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("benchmark", properties));
        context.getBeanFactory().registerSingleton("benchmarkTransport", transport);
        context.register(BenchmarkRestTemplateConfiguration.class);
        context.register(LogMaskRestTemplateAutoConfiguration.class);
        context.refresh();

        RestTemplate restTemplate = context.getBean(RestTemplate.class);
        boolean observationInstalled = !restTemplate.getInterceptors().isEmpty();
        if (observationInstalled != loggingEnabled) {
            context.close();
            throw new IllegalStateException(
                    "RestTemplate observation installation does not match logging.enabled");
        }
        return new BenchmarkScenario(context, restTemplate);
    }

    private static HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private static SizedPayload sizedPayload(int expectedJsonBytes) {
        int contentLength = expectedJsonBytes - EMPTY_PAYLOAD_JSON_BYTES;
        StringBuilder payload = new StringBuilder(contentLength);
        for (int index = 0; index < contentLength; index++) {
            payload.append('x');
        }
        return new SizedPayload(payload.toString());
    }

    private static byte[] serialize(ObjectMapper objectMapper, SizedPayload payload)
            throws IOException {
        return objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);
    }

    private static void verifyUtf8JsonSize(
            ObjectMapper objectMapper,
            SizedPayload payload,
            int expectedBytes) throws IOException {
        int actualBytes = serialize(objectMapper, payload).length;
        if (actualBytes != expectedBytes) {
            throw new IllegalStateException(
                    "Expected UTF-8 JSON fixture size " + expectedBytes
                            + " bytes but was " + actualBytes);
        }
    }

    private static void close(BenchmarkScenario scenario) {
        if (scenario != null) {
            scenario.close();
        }
    }

    /** Provides the user-owned RestTemplate that the auto-configuration may observe. */
    @Configuration(proxyBeanMethods = false)
    static class BenchmarkRestTemplateConfiguration {

        @Bean
        @ObservedRestTemplate
        RestTemplate benchmarkRestTemplate(ControlledClientHttpRequestFactory transport) {
            return new RestTemplate(transport);
        }
    }

    /** A small mutable DTO whose serialized shape is deliberately fixed. */
    public static final class SizedPayload {

        private String payload;

        public SizedPayload() {
        }

        private SizedPayload(String payload) {
            this.payload = payload;
        }

        public String getPayload() {
            return payload;
        }

        public void setPayload(String payload) {
            this.payload = payload;
        }
    }

    private static final class BenchmarkScenario {

        private final AnnotationConfigApplicationContext context;
        private final RestTemplate restTemplate;

        private BenchmarkScenario(
                AnnotationConfigApplicationContext context,
                RestTemplate restTemplate) {
            this.context = context;
            this.restTemplate = restTemplate;
        }

        private RestTemplate restTemplate() {
            return restTemplate;
        }

        private void close() {
            context.close();
        }
    }

    private static final class ControlledClientHttpRequestFactory
            implements ClientHttpRequestFactory {

        private final HttpStatus status;
        private final byte[] responseBody;
        private final MediaType contentType;

        private ControlledClientHttpRequestFactory(
                HttpStatus status,
                byte[] responseBody,
                MediaType contentType) {
            this.status = status;
            this.responseBody = responseBody;
            this.contentType = contentType;
        }

        private static ControlledClientHttpRequestFactory noContent() {
            return new ControlledClientHttpRequestFactory(
                    HttpStatus.NO_CONTENT,
                    new byte[0],
                    null);
        }

        private static ControlledClientHttpRequestFactory json(byte[] responseBody) {
            return new ControlledClientHttpRequestFactory(
                    HttpStatus.OK,
                    responseBody,
                    MediaType.APPLICATION_JSON);
        }

        @Override
        public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) {
            return new ControlledClientHttpRequest(
                    uri,
                    httpMethod,
                    status,
                    responseBody,
                    contentType);
        }
    }

    private static final class ControlledClientHttpRequest extends AbstractClientHttpRequest {

        private final URI uri;
        private final HttpMethod method;
        private final HttpStatus status;
        private final byte[] responseBody;
        private final MediaType contentType;
        private final ByteArrayOutputStream requestBody = new ByteArrayOutputStream();

        private ControlledClientHttpRequest(
                URI uri,
                HttpMethod method,
                HttpStatus status,
                byte[] responseBody,
                MediaType contentType) {
            this.uri = uri;
            this.method = method;
            this.status = status;
            this.responseBody = responseBody;
            this.contentType = contentType;
        }

        @Override
        public HttpMethod getMethod() {
            return method;
        }

        @Override
        public String getMethodValue() {
            return method.name();
        }

        @Override
        public URI getURI() {
            return uri;
        }

        @Override
        protected OutputStream getBodyInternal(HttpHeaders headers) {
            return requestBody;
        }

        @Override
        protected ClientHttpResponse executeInternal(HttpHeaders headers) {
            return new ControlledClientHttpResponse(status, responseBody, contentType);
        }
    }

    private static final class ControlledClientHttpResponse implements ClientHttpResponse {

        private final HttpStatus status;
        private final byte[] responseBody;
        private final HttpHeaders headers = new HttpHeaders();

        private ControlledClientHttpResponse(
                HttpStatus status,
                byte[] responseBody,
                MediaType contentType) {
            this.status = status;
            this.responseBody = responseBody;
            if (contentType != null) {
                headers.setContentType(contentType);
            }
            headers.setContentLength(responseBody.length);
        }

        @Override
        public HttpStatus getStatusCode() {
            return status;
        }

        @Override
        public int getRawStatusCode() {
            return status.value();
        }

        @Override
        public String getStatusText() {
            return status.getReasonPhrase();
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }

        @Override
        public ByteArrayInputStream getBody() {
            return new ByteArrayInputStream(responseBody);
        }

        @Override
        public void close() {
            // The byte-array response has no external resource to release.
        }
    }

    private static final class DiscardingEventLogger {

        private final Logger logger;
        private final Level originalLevel;
        private final boolean originalAdditive;
        private final Logger springLogger;
        private final Level originalSpringLevel;
        private final List<Appender<ILoggingEvent>> originalAppenders;
        private final NOPAppender<ILoggingEvent> appender;

        private DiscardingEventLogger(Logger logger, Logger springLogger) {
            this.logger = logger;
            this.originalLevel = logger.getLevel();
            this.originalAdditive = logger.isAdditive();
            this.springLogger = springLogger;
            this.originalSpringLevel = springLogger.getLevel();
            this.originalAppenders = new ArrayList<Appender<ILoggingEvent>>();
            Iterator<Appender<ILoggingEvent>> appenders = logger.iteratorForAppenders();
            while (appenders.hasNext()) {
                originalAppenders.add(appenders.next());
            }
            for (Appender<ILoggingEvent> originalAppender : originalAppenders) {
                logger.detachAppender(originalAppender);
            }
            appender = new NOPAppender<ILoggingEvent>();
            appender.start();
            logger.setLevel(Level.INFO);
            logger.setAdditive(false);
            logger.addAppender(appender);
            springLogger.setLevel(Level.WARN);
        }

        private static DiscardingEventLogger install() {
            ILoggerFactory loggerFactory = LoggerFactory.getILoggerFactory();
            if (!(loggerFactory instanceof LoggerContext)) {
                throw new IllegalStateException("JMH benchmark requires Logback");
            }
            LoggerContext context = (LoggerContext) loggerFactory;
            return new DiscardingEventLogger(
                    context.getLogger(EVENT_LOGGER_NAME),
                    context.getLogger(SPRING_LOGGER_NAME));
        }

        private void close() {
            logger.detachAppender(appender);
            appender.stop();
            for (Appender<ILoggingEvent> originalAppender : originalAppenders) {
                logger.addAppender(originalAppender);
            }
            logger.setAdditive(originalAdditive);
            logger.setLevel(originalLevel);
            springLogger.setLevel(originalSpringLevel);
        }
    }
}
