package ro.bogdanm.tools.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import ro.bogdanm.tools.client.GoogleFormClient;
import ro.bogdanm.tools.client.GoogleFormMetadataParser;
import ro.bogdanm.tools.service.AnswerSpecParser;
import ro.bogdanm.tools.service.QuestionFileRepository;

import java.io.IOException;
import java.nio.file.Path;

@Configuration
public class RuntimeConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(RuntimeConfiguration.class);

    @Bean
    AppConfig appConfig(Environment environment) throws IOException {
        Path configPath = FormFillerConfigLoader.resolveConfigPath(environment);
        LOGGER.info("Using form filler config {}", configPath.toAbsolutePath());
        return FormFillerConfigLoader.load(configPath);
    }

    @Bean
    GoogleFormMetadataParser googleFormMetadataParser() {
        return new GoogleFormMetadataParser();
    }

    @Bean
    AnswerSpecParser answerSpecParser() {
        return new AnswerSpecParser();
    }

    @Bean
    QuestionFileRepository questionFileRepository() {
        return new QuestionFileRepository();
    }

    @Bean
    GoogleFormClient googleFormClient(
            AppConfig appConfig,
            GoogleFormMetadataParser metadataParser,
            AnswerSpecParser answerSpecParser
    ) {
        return new GoogleFormClient(appConfig.httpSettings(), metadataParser, answerSpecParser);
    }
}

