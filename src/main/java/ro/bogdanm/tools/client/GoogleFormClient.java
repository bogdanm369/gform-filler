package ro.bogdanm.tools.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ro.bogdanm.tools.config.AppConfig;
import ro.bogdanm.tools.model.FormPage;
import ro.bogdanm.tools.model.GoogleFormMetadata;
import ro.bogdanm.tools.model.QuestionDescriptor;
import ro.bogdanm.tools.model.SubmissionResult;
import ro.bogdanm.tools.service.AnswerSpecParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class GoogleFormClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(GoogleFormClient.class);

    private final AppConfig.HttpSettings httpSettings;
    private final HttpClient httpClient;
    private final GoogleFormMetadataParser metadataParser;
    private final AnswerSpecParser answerSpecParser;

    public GoogleFormClient(
            AppConfig.HttpSettings httpSettings,
            GoogleFormMetadataParser metadataParser,
            AnswerSpecParser answerSpecParser
    ) {
        this.httpSettings = httpSettings;
        this.metadataParser = metadataParser;
        this.answerSpecParser = answerSpecParser;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(httpSettings.connectTimeout())
                .build();
    }

    public GoogleFormMetadata fetchMetadata(URI formUrl) throws IOException, InterruptedException {
        HttpRequest request = baseRequest(formUrl)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) {
            throw new IOException("Failed to fetch form " + formUrl + ": HTTP " + response.statusCode());
        }
        return metadataParser.parse(formUrl, response.body());
    }

    public SubmissionResult submit(GoogleFormMetadata metadata, Map<Integer, String> configuredAnswers) throws IOException, InterruptedException {
        GoogleFormMetadata currentPageMetadata = metadata;
        HttpResponse<String> response = null;

        for (int pageIndex = 0; pageIndex < metadata.pages().size(); pageIndex++) {
            FormPage page = metadata.pages().get(pageIndex);
            boolean continueToNextPage = pageIndex < metadata.pages().size() - 1;
            List<Map.Entry<String, String>> formEntries = buildFormEntries(currentPageMetadata, page.questions(), configuredAnswers, continueToNextPage);

            HttpRequest request = baseRequest(currentPageMetadata.actionUrl())
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .header("Referer", currentPageMetadata.formUrl().toString())
                    .POST(HttpRequest.BodyPublishers.ofString(encode(formEntries), StandardCharsets.UTF_8))
                    .build();

            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                break;
            }
            if (continueToNextPage) {
                currentPageMetadata = metadataParser.parse(metadata.formUrl(), response.body());
            }
        }

        if (response == null) {
            throw new IOException("No Google Form submission request was sent because no form pages were parsed");
        }

        boolean success = response.statusCode() < 400 && !response.body().contains("id=\"mG61Hd\"");
        if (!success) {
            LOGGER.debug("Non-success response body for {}:{}{}", metadata.formUrl(), System.lineSeparator(), response.body());
        }
        return new SubmissionResult(success, response.statusCode(), response.uri());
    }

    List<Map.Entry<String, String>> buildFormEntries(
            GoogleFormMetadata metadata,
            List<QuestionDescriptor> pageQuestions,
            Map<Integer, String> configuredAnswers,
            boolean continueToNextPage
    ) {
        List<Map.Entry<String, String>> formEntries = new ArrayList<>();
        formEntries.add(Map.entry("fvv", metadata.fvv()));
        formEntries.add(Map.entry("fbzx", metadata.fbzx()));
        formEntries.add(Map.entry("pageHistory", metadata.pageHistory()));
        formEntries.add(Map.entry("partialResponse", metadata.partialResponse()));
        formEntries.add(Map.entry("submissionTimestamp", metadata.submissionTimestamp()));
        if (continueToNextPage) {
            formEntries.add(Map.entry("continue", "1"));
        }

        for (QuestionDescriptor question : pageQuestions) {
            String configuredValue = configuredAnswers.getOrDefault(question.index(), "");
            List<String> values = answerSpecParser.resolve(question, configuredValue);
            if (question.type().isChoiceBased()) {
                formEntries.add(Map.entry("entry." + question.entryId() + "_sentinel", ""));
            }
            for (String value : values) {
                formEntries.add(Map.entry("entry." + question.entryId(), value));
            }
        }

        return List.copyOf(formEntries);
    }

    private HttpRequest.Builder baseRequest(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(httpSettings.readTimeout())
                .header("User-Agent", httpSettings.userAgent())
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache");
    }

    static String encode(List<Map.Entry<String, String>> entries) {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> entry : entries) {
            if (entry.getValue() == null) {
                continue;
            }
            if (!body.isEmpty()) {
                body.append('&');
            }
            body.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            body.append('=');
            body.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return body.toString();
    }
}

