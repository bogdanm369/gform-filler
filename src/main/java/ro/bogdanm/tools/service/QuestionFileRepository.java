package ro.bogdanm.tools.service;

import ro.bogdanm.tools.model.GoogleFormMetadata;
import ro.bogdanm.tools.model.QuestionDescriptor;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class QuestionFileRepository {

    private static final Pattern ANSWER_KEY = Pattern.compile("question\\.(\\d+)\\.values");

    public void writeTemplate(Path questionFile, GoogleFormMetadata metadata) throws IOException {
        Path parent = questionFile.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        StringBuilder builder = new StringBuilder();
        builder.append("# Auto-generated at ").append(OffsetDateTime.now()).append(System.lineSeparator());
        builder.append("# Edit only question.N.values and leave the metadata lines intact.").append(System.lineSeparator());
        builder.append("# Choice syntax:").append(System.lineSeparator());
        builder.append("#   5         -> always option 5").append(System.lineSeparator());
        builder.append("#   1-3       -> choose one option randomly from 1, 2, 3").append(System.lineSeparator());
        builder.append("#   1,3,5     -> choose one option randomly from 1, 3, 5").append(System.lineSeparator());
        builder.append("#   5(90%),4(10%) -> weighted single-choice distribution").append(System.lineSeparator());
        builder.append("#   =4-5,3(10%),2(5%) -> about 85% pick from 4-5, 10% pick 3, 5% pick 2").append(System.lineSeparator());
        builder.append("#   multi:1,3-4 -> for checkbox questions, tick all listed options").append(System.lineSeparator());
        builder.append("# Notes:").append(System.lineSeparator());
        builder.append("#   weighted percentages are probabilistic per run, not exact global caps").append(System.lineSeparator());
        builder.append("#   if a pure weighted list totals less than 100%, the values are treated as relative weights").append(System.lineSeparator());
        builder.append("# Text syntax:").append(System.lineSeparator());
        builder.append("#   Any non-empty text is submitted as-is for text/date/time questions").append(System.lineSeparator());
        builder.append("#   HR,IT,Contabilitate -> randomly pick one text value").append(System.lineSeparator());
        builder.append("#   Didactic(60%),HR(5%),IT(5%) -> weighted text distribution").append(System.lineSeparator());
        builder.append("#   \"HR, Payroll\",IT -> quoted text keeps commas inside a single candidate").append(System.lineSeparator());
        builder.append("#   18,19,20   -> randomly pick one numeric value").append(System.lineSeparator());
        builder.append("#   18-30      -> randomly pick one numeric value in range").append(System.lineSeparator());
        builder.append("#   =18-30,45(10%) -> weighted numeric text distribution").append(System.lineSeparator()).append(System.lineSeparator());
        builder.append(property("form.title", metadata.title()));
        builder.append(property("form.url", metadata.formUrl().toString()));
        builder.append(property("question.count", Integer.toString(metadata.questions().size()))).append(System.lineSeparator());

        for (QuestionDescriptor question : metadata.questions()) {
            builder.append("# [").append(question.index()).append("] ").append(question.title()).append(System.lineSeparator());
            if (!question.helpText().isBlank()) {
                builder.append("# help: ").append(question.helpText()).append(System.lineSeparator());
            }
            builder.append(property("question." + question.index() + ".type", question.type().name()));
            builder.append(property("question." + question.index() + ".required", Boolean.toString(question.required())));
            builder.append(property("question." + question.index() + ".entry-id", Long.toString(question.entryId())));
            builder.append(property("question." + question.index() + ".title", question.title()));
            if (!question.options().isEmpty()) {
                String options = java.util.stream.IntStream.range(0, question.options().size())
                        .mapToObj(index -> (index + 1) + ": " + question.options().get(index))
                        .reduce((left, right) -> left + " | " + right)
                        .orElse("");
                builder.append(property("question." + question.index() + ".options", options));
            }
            builder.append(property("question." + question.index() + ".values", ""));
            builder.append(System.lineSeparator());
        }

        Files.writeString(questionFile, builder.toString(), StandardCharsets.UTF_8);
    }

    public Map<Integer, String> loadAnswers(Path questionFile) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(questionFile, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        Map<Integer, String> answers = new TreeMap<>();
        for (String key : properties.stringPropertyNames()) {
            Matcher matcher = ANSWER_KEY.matcher(key);
            if (!matcher.matches()) {
                continue;
            }
            answers.put(Integer.parseInt(matcher.group(1)), properties.getProperty(key, ""));
        }
        return Map.copyOf(answers);
    }

    private static String property(String key, String value) {
        return escape(key) + '=' + escape(value) + System.lineSeparator();
    }

    private static String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\r", "")
                .replace("\n", "\\n")
                .replace("=", "\\=")
                .replace(":", "\\:");
    }
}

