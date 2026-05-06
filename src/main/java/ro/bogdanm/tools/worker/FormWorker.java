package ro.bogdanm.tools.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ro.bogdanm.tools.client.GoogleFormClient;
import ro.bogdanm.tools.config.AppConfig;
import ro.bogdanm.tools.model.GoogleFormMetadata;
import ro.bogdanm.tools.model.SubmissionResult;
import ro.bogdanm.tools.service.QuestionFileRepository;

import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FormWorker implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(FormWorker.class);

    private final AppConfig.FormJobConfig job;
    private final GoogleFormClient client;
    private final QuestionFileRepository questionFileRepository;
    private final AtomicBoolean stopRequested;

    public FormWorker(
            AppConfig.FormJobConfig job,
            GoogleFormClient client,
            QuestionFileRepository questionFileRepository,
            AtomicBoolean stopRequested
    ) {
        this.job = job;
        this.client = client;
        this.questionFileRepository = questionFileRepository;
        this.stopRequested = stopRequested;
    }

    @Override
    public void run() {
        LOGGER.info("Worker {} started; interval={}s, questionFile={}, fillCount={}",
                job.name(),
                job.interval().displayValue(),
                job.questionFile(),
                job.runsForever() ? "unlimited" : job.fillCount());
        int completedRuns = 0;

        while (!stopRequested.get() && (job.runsForever() || completedRuns < job.fillCount())) {
            Duration targetInterval = job.interval().nextDuration();
            Instant startedAt = Instant.now();
            try {
                GoogleFormMetadata metadata = client.fetchMetadata(job.link());
                if (Files.notExists(job.questionFile())) {
                    questionFileRepository.writeTemplate(job.questionFile(), metadata);
                    LOGGER.warn("Question file {} did not exist, so a template was generated. Configure question.*.values and the worker will submit on the next tick.", job.questionFile());
                } else {
                    Map<Integer, String> answers = questionFileRepository.loadAnswers(job.questionFile());
                    SubmissionResult result = client.submit(metadata, answers);
                    completedRuns++;
                    if (result.success()) {
                        LOGGER.info("Worker {} submitted response #{} with HTTP {}", job.name(), completedRuns, result.statusCode());
                    } else {
                        LOGGER.warn("Worker {} received HTTP {} and the response still looked like a form page; check {}", job.name(), result.statusCode(), job.questionFile());
                    }
                }
            } catch (Exception exception) {
                LOGGER.error("Worker {} failed: {}", job.name(), exception.getMessage(), exception);
            }

            sleepUntilNextTick(startedAt, targetInterval);
        }

        LOGGER.info("Worker {} stopped", job.name());
    }

    private void sleepUntilNextTick(Instant startedAt, Duration targetInterval) {
        Duration elapsed = Duration.between(startedAt, Instant.now());
        Duration sleepDuration = targetInterval.minus(elapsed);
        if (sleepDuration.isNegative() || sleepDuration.isZero()) {
            return;
        }
        try {
            Thread.sleep(sleepDuration.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}

