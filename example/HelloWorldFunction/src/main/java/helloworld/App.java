package helloworld;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.entities.Entity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.cloudwatchlogs.emf.model.DimensionSet;
import software.amazon.cloudwatchlogs.emf.model.Unit;
import software.amazon.lambda.powertools.logging.LoggingUtils;
import software.amazon.lambda.powertools.logging.Logging;
import software.amazon.lambda.powertools.metrics.Metrics;
import software.amazon.lambda.powertools.tracing.TracingUtils;
import software.amazon.lambda.powertools.tracing.Tracing;

import static software.amazon.lambda.powertools.metrics.MetricsUtils.metricsLogger;
import static software.amazon.lambda.powertools.metrics.MetricsUtils.withSingleMetric;
import static software.amazon.lambda.powertools.tracing.TracingUtils.putMetadata;
import static software.amazon.lambda.powertools.tracing.TracingUtils.withEntitySubsegment;

/**
 * Handler for requests to Lambda function.
 */
public class App implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    Logger log = LogManager.getLogger();

    @Logging(logEvent = true, samplingRate = 0.7)
    @Tracing(captureError = false, captureResponse = false)
    @Metrics(namespace = "ServerlessAirline", service = "payment", captureColdStart = true)
    public APIGatewayProxyResponseEvent handleRequest(final APIGatewayProxyRequestEvent input, final Context context) {
        Map<String, String> headers = new HashMap<>();

        headers.put("Content-Type", "application/json");
        headers.put("X-Custom-Header", "application/json");

        metricsLogger().putMetric("CustomMetric1", 1, Unit.COUNT);

        withSingleMetric("CustomMetrics2", 1, Unit.COUNT, "Another", (metric) -> {
            metric.setDimensions(DimensionSet.of("AnotherService", "CustomService"));
            metric.setDimensions(DimensionSet.of("AnotherService1", "CustomService1"));
        });

        LoggingUtils.appendKey("test", "willBeLogged");

        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent()
                .withHeaders(headers);
        try {
            final String pageContents = this.getPageContents("https://checkip.amazonaws.com");
            log.info(pageContents);
            TracingUtils.putAnnotation("Test", "New");
            String output = String.format("{ \"message\": \"hello world\", \"location\": \"%s\" }", pageContents);

            TracingUtils.withSubsegment("loggingResponse", subsegment -> {
                String sampled = "log something out";
                log.info(sampled);
                log.info(output);
            });

            threadOption1();

            threadOption2();

            log.info("After output");
            return response
                    .withStatusCode(200)
                    .withBody(output);
        } catch (IOException | InterruptedException e) {
            return response
                    .withBody("{}")
                    .withStatusCode(500);
        }
    }

    private void threadOption1() throws InterruptedException {
        Entity traceEntity = AWSXRay.getTraceEntity();
        Thread thread = new Thread(() -> {
            AWSXRay.setTraceEntity(traceEntity);
            log();
        });
        thread.start();
        thread.join();
    }

    private void threadOption2() throws InterruptedException {
        Entity traceEntity = AWSXRay.getTraceEntity();
        Thread anotherThread = new Thread(() -> withEntitySubsegment("inlineLog", traceEntity, subsegment -> {
            String var = "somethingToProcess";
            log.info("inside threaded logging inline {}", var);
        }));
        anotherThread.start();
        anotherThread.join();
    }

    @Tracing
    private void log() {
        log.info("inside threaded logging for function");
    }


    @Tracing(namespace = "getPageContents", captureResponse = false, captureError = false)
    private String getPageContents(String address) throws IOException {
        URL url = new URL(address);
        putMetadata("getPageContents", address);
        try (BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()))) {
            return br.lines().collect(Collectors.joining(System.lineSeparator()));
        }
    }
}
