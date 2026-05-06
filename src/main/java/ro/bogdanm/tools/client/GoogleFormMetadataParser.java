package ro.bogdanm.tools.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import ro.bogdanm.tools.model.FormPage;
import ro.bogdanm.tools.model.GoogleFormMetadata;
import ro.bogdanm.tools.model.QuestionDescriptor;
import ro.bogdanm.tools.model.QuestionType;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GoogleFormMetadataParser {

    private static final Pattern LOAD_DATA_PATTERN = Pattern.compile("FB_PUBLIC_LOAD_DATA_\\s*=\\s*(\\[.*]);?", Pattern.DOTALL);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public GoogleFormMetadata parse(URI sourceUrl, String html) throws IOException {
        Document document = Jsoup.parse(html, sourceUrl.toString());
        Element form = document.selectFirst("form#mG61Hd");
        if (form == null) {
            throw new IOException("Google Form HTML did not contain form#mG61Hd");
        }

        String loadDataJson = extractLoadDataJson(document);
        JsonNode loadDataRoot = OBJECT_MAPPER.readTree(loadDataJson);
        JsonNode items = loadDataRoot.path(1).path(1);

        List<QuestionDescriptor> questions = new ArrayList<>();
        List<FormPage> pages = new ArrayList<>();
        List<QuestionDescriptor> currentPageQuestions = new ArrayList<>();
        int exportedIndex = 1;
        int pageIndex = 0;
        if (items.isArray()) {
            for (JsonNode item : items) {
                QuestionType type = QuestionType.fromGoogleType(item.path(3).asInt(-1));
                if (type == QuestionType.SECTION) {
                    if (!currentPageQuestions.isEmpty()) {
                        pages.add(new FormPage(pageIndex++, List.copyOf(currentPageQuestions)));
                        currentPageQuestions = new ArrayList<>();
                    }
                    continue;
                }
                if (!type.isAnswerable()) {
                    continue;
                }

                JsonNode questionConfig = item.path(4);
                JsonNode entry = questionConfig.isArray() && !questionConfig.isEmpty() ? questionConfig.get(0) : null;
                if (entry == null || !entry.isArray() || entry.isEmpty()) {
                    continue;
                }

                long entryId = entry.path(0).asLong(-1);
                if (entryId < 0) {
                    continue;
                }

                String title = normalize(item.path(1).asText("Untitled question"));
                String helpText = normalize(item.path(2).asText(""));
                boolean required = entry.path(2).asInt(0) == 1 || entry.path(2).asBoolean(false);
                List<String> options = extractOptions(type, entry);
                QuestionDescriptor question = new QuestionDescriptor(exportedIndex++, entryId, title, helpText, type, required, List.copyOf(options));
                questions.add(question);
                currentPageQuestions.add(question);
            }
        }

        if (!currentPageQuestions.isEmpty()) {
            pages.add(new FormPage(pageIndex, List.copyOf(currentPageQuestions)));
        }

        return new GoogleFormMetadata(
                sourceUrl,
                URI.create(form.absUrl("action")),
                normalize(document.selectFirst("div[role=heading][aria-level=1]") != null
                        ? document.selectFirst("div[role=heading][aria-level=1]").text()
                        : document.title()),
                normalize(document.selectFirst("div.cBGGJ") != null ? document.selectFirst("div.cBGGJ").text() : ""),
                hiddenValue(form, "fbzx", ""),
                hiddenValue(form, "fvv", "1"),
                hiddenValue(form, "pageHistory", "0"),
                hiddenValue(form, "partialResponse", "[]"),
                hiddenValue(form, "submissionTimestamp", "-1"),
                List.copyOf(questions),
                List.copyOf(pages)
        );
    }

    private static List<String> extractOptions(QuestionType type, JsonNode entry) {
        if (!type.isChoiceBased()) {
            return List.of();
        }
        JsonNode optionNodes = entry.path(1);
        if (!optionNodes.isArray()) {
            return List.of();
        }
        List<String> options = new ArrayList<>();
        for (JsonNode option : optionNodes) {
            if (option.isArray() && !option.isEmpty()) {
                options.add(normalize(option.get(0).asText("")));
            }
        }
        return options;
    }

    private static String hiddenValue(Element form, String name, String defaultValue) {
        Element input = form.selectFirst("input[name=" + name + "]");
        return input == null ? defaultValue : input.attr("value");
    }

    private static String extractLoadDataJson(Document document) throws IOException {
        for (Element script : document.select("script")) {
            String data = script.data();
            if (!data.contains("FB_PUBLIC_LOAD_DATA_")) {
                continue;
            }
            Matcher matcher = LOAD_DATA_PATTERN.matcher(data);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        throw new IOException("Could not locate FB_PUBLIC_LOAD_DATA_ in the Google Form HTML");
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00A0', ' ').trim().replaceAll("\\s+", " ");
    }
}

