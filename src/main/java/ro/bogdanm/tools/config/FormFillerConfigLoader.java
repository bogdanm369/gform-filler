package ro.bogdanm.tools.config;

import org.springframework.core.env.Environment;
import org.springframework.boot.system.ApplicationHome;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

public final class FormFillerConfigLoader {

    private static final String DEFAULT_CONFIG = "config/formfiller.properties";
    private static final String CONFIG_PROPERTY = "formfiller.config-file";
    private static final String DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36";

    private FormFillerConfigLoader() {
    }

    public static Path resolveConfigPath(Environment environment) {
        String fromProperty = environment.getProperty(CONFIG_PROPERTY);
        if (fromProperty != null && !fromProperty.isBlank()) {
            return resolvePath(fromProperty);
        }

        String fromSystemProperty = System.getProperty(CONFIG_PROPERTY);
        if (fromSystemProperty != null && !fromSystemProperty.isBlank()) {
            return resolvePath(fromSystemProperty);
        }

        String fromEnv = System.getenv("FORMFILLER_CONFIG");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return resolvePath(fromEnv);
        }

        return resolvePath(DEFAULT_CONFIG);
    }

    public static AppConfig load(Path configFile) throws IOException {
        Objects.requireNonNull(configFile, "configFile");
        if (Files.notExists(configFile)) {
            throw new IOException("Config file not found: " + configFile
                    + " (working directory: " + Paths.get("").toAbsolutePath().normalize() + ")");
        }

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        AppConfig.HttpSettings httpSettings = new AppConfig.HttpSettings(
                Duration.ofSeconds(getInt(properties, "formfiller.http.connect-timeout-seconds", 30)),
                Duration.ofSeconds(getInt(properties, "formfiller.http.read-timeout-seconds", 60)),
                properties.getProperty("formfiller.http.user-agent", DEFAULT_USER_AGENT).trim()
        );

        String namesRaw = require(properties, "formfiller.names");
        Path configDirectory = configFile.toAbsolutePath().getParent();
        List<AppConfig.FormJobConfig> jobs = new ArrayList<>();
        for (String candidate : namesRaw.split(",")) {
            String name = candidate.trim();
            if (name.isEmpty()) {
                continue;
            }

            String prefix = "formfiller." + name + '.';
            boolean enabled = getBoolean(properties, prefix + "enabled", true);
            URI link = URI.create(require(properties, prefix + "link").trim());
            String defaultQuestionFile = "questionnaires/" + name + ".questions.properties";
            Path questionFile = resolveAgainst(configDirectory, properties.getProperty(prefix + "questions-file", defaultQuestionFile).trim());
            AppConfig.IntervalSeconds interval = parseIntervalSeconds(properties.getProperty(prefix + "interval-seconds", "60"), prefix + "interval-seconds");
            int fillCount = getNonNegativeInt(properties, prefix + "fill-count",
                    getNonNegativeInt(properties, prefix + "max-runs", 0));
            jobs.add(new AppConfig.FormJobConfig(name, link, questionFile, interval, enabled, fillCount));
        }

        if (jobs.isEmpty()) {
            throw new IllegalArgumentException("No form jobs were configured in " + configFile);
        }

        return new AppConfig(configFile.toAbsolutePath().normalize(), httpSettings, List.copyOf(jobs));
    }

    static Path resolvePath(String rawValue) {
        return resolvePath(rawValue, Paths.get(""), discoverSearchRoots());
    }

    static Path resolvePath(String rawValue, Path workingDirectory, List<Path> searchRoots) {
        Objects.requireNonNull(rawValue, "rawValue");
        Objects.requireNonNull(workingDirectory, "workingDirectory");
        Objects.requireNonNull(searchRoots, "searchRoots");

        Path candidate = Paths.get(rawValue.trim());
        if (candidate.isAbsolute()) {
            return candidate.normalize();
        }

        Path normalizedWorkingDirectory = workingDirectory.toAbsolutePath().normalize();
        Path fromWorkingDirectory = normalizedWorkingDirectory.resolve(candidate).normalize();
        if (Files.exists(fromWorkingDirectory)) {
            return fromWorkingDirectory;
        }

        for (Path searchRoot : searchRoots) {
            Path resolved = resolveFromSearchRoot(searchRoot, candidate);
            if (resolved != null) {
                return resolved;
            }
        }

        return fromWorkingDirectory;
    }

    private static List<Path> discoverSearchRoots() {
        Set<Path> roots = new LinkedHashSet<>();
        roots.add(Paths.get("").toAbsolutePath().normalize());

        ApplicationHome applicationHome = new ApplicationHome(FormFillerConfigLoader.class);
        if (applicationHome.getSource() != null) {
            Path applicationSource = applicationHome.getSource().toPath().toAbsolutePath().normalize();
            roots.add(Files.isDirectory(applicationSource) ? applicationSource : applicationSource.getParent());
        }

        return List.copyOf(roots);
    }

    private static Path resolveFromSearchRoot(Path searchRoot, Path relativeCandidate) {
        if (searchRoot == null) {
            return null;
        }

        for (Path current = searchRoot.toAbsolutePath().normalize(); current != null; current = current.getParent()) {
            Path resolved = current.resolve(relativeCandidate).normalize();
            if (Files.exists(resolved)) {
                return resolved;
            }
        }

        return null;
    }

    private static Path resolveAgainst(Path baseDirectory, String value) {
        Path candidate = Paths.get(value);
        if (candidate.isAbsolute() || baseDirectory == null) {
            return candidate.normalize();
        }
        return baseDirectory.resolve(candidate).normalize();
    }

    private static String require(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required property: " + key);
        }
        return value;
    }

    private static int getInt(Properties properties, String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Integer.parseInt(value.trim());
    }

    private static int getNonNegativeInt(Properties properties, String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed < 0) {
                throw new IllegalArgumentException("Invalid value for " + key + ": value must be 0 or greater");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid value for " + key + ": " + value, exception);
        }
    }

    static AppConfig.IntervalSeconds parseIntervalSeconds(String rawValue, String propertyName) {
        String value = rawValue == null || rawValue.isBlank() ? "60" : rawValue.trim();
        int dashIndex = value.indexOf('-');
        if (dashIndex < 0) {
            int seconds = parsePositiveInt(value, propertyName);
            return new AppConfig.IntervalSeconds(seconds, seconds);
        }

        if (dashIndex == 0 || dashIndex == value.length() - 1 || value.indexOf('-', dashIndex + 1) >= 0) {
            throw new IllegalArgumentException("Invalid interval range for " + propertyName + ": " + rawValue);
        }

        int minSeconds = parsePositiveInt(value.substring(0, dashIndex).trim(), propertyName);
        int maxSeconds = parsePositiveInt(value.substring(dashIndex + 1).trim(), propertyName);
        if (minSeconds > maxSeconds) {
            throw new IllegalArgumentException("Invalid interval range for " + propertyName + ": minimum cannot be greater than maximum");
        }
        return new AppConfig.IntervalSeconds(minSeconds, maxSeconds);
    }

    private static int parsePositiveInt(String value, String propertyName) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new IllegalArgumentException("Invalid interval for " + propertyName + ": value must be positive");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid interval for " + propertyName + ": " + value, exception);
        }
    }

    private static boolean getBoolean(Properties properties, String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.trim());
    }
}

