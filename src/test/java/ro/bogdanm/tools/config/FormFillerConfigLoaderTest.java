package ro.bogdanm.tools.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormFillerConfigLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvesRelativeQuestionFileAgainstConfigDirectory() throws Exception {
        Path configFile = tempDir.resolve("formfiller.properties");
        Files.writeString(configFile, """
                formfiller.names=one
                formfiller.one.link=https://docs.google.com/forms/d/e/test/viewform
                formfiller.one.questions-file=questionnaires/one.questions.properties
                formfiller.one.interval-seconds=45
                formfiller.one.fill-count=2
                """);

        AppConfig config = FormFillerConfigLoader.load(configFile);

        assertEquals(1, config.jobs().size());
        assertEquals("one", config.jobs().getFirst().name());
        assertEquals(45, config.jobs().getFirst().interval().minSeconds());
        assertEquals(45, config.jobs().getFirst().interval().maxSeconds());
        assertEquals(2, config.jobs().getFirst().fillCount());
        assertTrue(config.jobs().getFirst().questionFile().startsWith(tempDir));
    }

    @Test
    void supportsLegacyMaxRunsAlias() throws Exception {
        Path configFile = tempDir.resolve("legacy.properties");
        Files.writeString(configFile, """
                formfiller.names=one
                formfiller.one.link=https://docs.google.com/forms/d/e/test/viewform
                formfiller.one.max-runs=3
                """);

        AppConfig config = FormFillerConfigLoader.load(configFile);

        assertEquals(3, config.jobs().getFirst().fillCount());
    }

    @Test
    void fillCountOverridesLegacyMaxRunsWhenBothArePresent() throws Exception {
        Path configFile = tempDir.resolve("both.properties");
        Files.writeString(configFile, """
                formfiller.names=one
                formfiller.one.link=https://docs.google.com/forms/d/e/test/viewform
                formfiller.one.fill-count=4
                formfiller.one.max-runs=9
                """);

        AppConfig config = FormFillerConfigLoader.load(configFile);

        assertEquals(4, config.jobs().getFirst().fillCount());
    }

    @Test
    void resolvesRelativeConfigPathAgainstProjectLocationWhenWorkingDirectoryDiffers() throws Exception {
        Path projectRoot = tempDir.resolve("gform-filler");
        Path configFile = projectRoot.resolve("config/formfiller.properties");
        Files.createDirectories(configFile.getParent());
        Files.writeString(configFile, "formfiller.names=one");

        Path unrelatedWorkingDirectory = tempDir.resolve("other-workspace/start-project");
        Files.createDirectories(unrelatedWorkingDirectory);

        Path resolved = FormFillerConfigLoader.resolvePath(
                "config/formfiller.properties",
                unrelatedWorkingDirectory,
                List.of(projectRoot.resolve("target/classes"))
        );

        assertEquals(configFile.toAbsolutePath().normalize(), resolved);
    }

    @Test
    void parsesIntervalRange() {
        AppConfig.IntervalSeconds interval = FormFillerConfigLoader.parseIntervalSeconds("100-300", "formfiller.test.interval-seconds");

        assertEquals(100, interval.minSeconds());
        assertEquals(300, interval.maxSeconds());
        assertTrue(!interval.isFixed());
    }

    @Test
    void rejectsInvalidDescendingRange() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> FormFillerConfigLoader.parseIntervalSeconds("300-100", "formfiller.test.interval-seconds")
        );

        assertTrue(exception.getMessage().contains("minimum cannot be greater than maximum"));
    }

    @Test
    void rejectsNonPositiveInterval() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> FormFillerConfigLoader.parseIntervalSeconds("0", "formfiller.test.interval-seconds")
        );

        assertTrue(exception.getMessage().contains("value must be positive"));
    }

    @Test
    void missingConfigMessageIncludesWorkingDirectory() {
        Path missing = tempDir.resolve("missing.properties");

        IOException exception = assertThrows(IOException.class, () -> FormFillerConfigLoader.load(missing));

        assertTrue(exception.getMessage().contains("working directory"));
    }

    @Test
    void rejectsNegativeFillCount() throws Exception {
        Path configFile = tempDir.resolve("invalid.properties");
        Files.writeString(configFile, """
                formfiller.names=one
                formfiller.one.link=https://docs.google.com/forms/d/e/test/viewform
                formfiller.one.fill-count=-1
                """);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> FormFillerConfigLoader.load(configFile));

        assertTrue(exception.getMessage().contains("fill-count"));
    }
}

