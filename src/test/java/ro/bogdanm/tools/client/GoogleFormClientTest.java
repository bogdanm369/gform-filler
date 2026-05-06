package ro.bogdanm.tools.client;

import org.junit.jupiter.api.Test;
import ro.bogdanm.tools.config.AppConfig;
import ro.bogdanm.tools.model.FormPage;
import ro.bogdanm.tools.model.GoogleFormMetadata;
import ro.bogdanm.tools.model.QuestionDescriptor;
import ro.bogdanm.tools.model.QuestionType;
import ro.bogdanm.tools.service.AnswerSpecParser;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoogleFormClientTest {

    private final GoogleFormClient client = new GoogleFormClient(
            new AppConfig.HttpSettings(Duration.ofSeconds(5), Duration.ofSeconds(5), "JUnit"),
            new GoogleFormMetadataParser(),
            new AnswerSpecParser()
    );

    @Test
    void addsSentinelForChoiceQuestions() {
        List<QuestionDescriptor> questions = List.of(
                new QuestionDescriptor(1, 1282540542L, "Gen", "", QuestionType.SINGLE_CHOICE, true, List.of("Masculin", "Feminin")),
                new QuestionDescriptor(2, 117810624L, "Vârsta", "", QuestionType.SHORT_TEXT, true, List.of())
        );
        GoogleFormMetadata metadata = new GoogleFormMetadata(
                URI.create("https://docs.google.com/forms/d/e/test/viewform"),
                URI.create("https://docs.google.com/forms/d/e/test/formResponse"),
                "Test form",
                "",
                "fbzx-1",
                "1",
                "0",
                "[null,null,\"fbzx-1\"]",
                "-1",
                questions,
                List.of(new FormPage(0, questions))
        );

        List<Map.Entry<String, String>> entries = client.buildFormEntries(metadata, metadata.pages().getFirst().questions(), Map.of(
                1, "1,2",
                2, "29"
        ), true);

        assertTrue(entries.contains(Map.entry("entry.1282540542_sentinel", "")));
        assertTrue(entries.stream().anyMatch(entry -> entry.getKey().equals("entry.1282540542")
                && List.of("Masculin", "Feminin").contains(entry.getValue())));
        assertTrue(entries.contains(Map.entry("entry.117810624", "29")));
        assertTrue(entries.contains(Map.entry("continue", "1")));

        String encoded = GoogleFormClient.encode(entries);
        assertTrue(encoded.contains("entry.1282540542_sentinel="));
        assertTrue(encoded.contains("entry.117810624=29"));
        assertTrue(encoded.contains("continue=1"));
    }

    @Test
    void doesNotAddSentinelForTextQuestions() {
        List<QuestionDescriptor> questions = List.of(
                new QuestionDescriptor(1, 206179761L, "Departament", "", QuestionType.SHORT_TEXT, true, List.of())
        );
        GoogleFormMetadata metadata = new GoogleFormMetadata(
                URI.create("https://docs.google.com/forms/d/e/test/viewform"),
                URI.create("https://docs.google.com/forms/d/e/test/formResponse"),
                "Test form",
                "",
                "fbzx-2",
                "1",
                "0",
                "[null,null,\"fbzx-2\"]",
                "-1",
                questions,
                List.of(new FormPage(0, questions))
        );

        List<Map.Entry<String, String>> entries = client.buildFormEntries(metadata, metadata.pages().getFirst().questions(), Map.of(1, "Psihologie"), false);

        assertEquals(6, entries.size());
        assertTrue(entries.contains(Map.entry("entry.206179761", "Psihologie")));
        assertTrue(entries.stream().noneMatch(entry -> entry.getKey().endsWith("_sentinel")));
        assertTrue(entries.stream().noneMatch(entry -> entry.getKey().equals("continue")));
    }
}

