package ro.bogdanm.tools.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import ro.bogdanm.tools.client.GoogleFormClient;
import ro.bogdanm.tools.config.AppConfig;
import ro.bogdanm.tools.worker.FormWorker;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public final class FormFillerRunner implements SmartLifecycle {

    private static final Logger LOGGER = LoggerFactory.getLogger(FormFillerRunner.class);

    private final AppConfig config;
    private final GoogleFormClient client;
    private final QuestionFileRepository questionFileRepository;
    private final ConfigurableApplicationContext applicationContext;
    private final boolean autostart;
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private final List<Thread> workerThreads = new ArrayList<>();
    private volatile boolean running;

    public FormFillerRunner(
            AppConfig config,
            GoogleFormClient client,
            QuestionFileRepository questionFileRepository,
            ConfigurableApplicationContext applicationContext,
            @Value("${formfiller.autostart:true}") boolean autostart
    ) {
        this.config = config;
        this.client = client;
        this.questionFileRepository = questionFileRepository;
        this.applicationContext = applicationContext;
        this.autostart = autostart;
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;

        if (!autostart) {
            LOGGER.info("Form filler autostart is disabled");
            return;
        }

        for (AppConfig.FormJobConfig job : config.jobs()) {
            if (!job.enabled()) {
                LOGGER.info("Skipping disabled job {}", job.name());
                continue;
            }
            FormWorker worker = new FormWorker(job, client, questionFileRepository, stopRequested);
            Thread thread = Thread.ofPlatform().name("formfiller-" + job.name()).unstarted(worker);
            thread.start();
            workerThreads.add(thread);
        }

        if (workerThreads.isEmpty()) {
            LOGGER.warn("No enabled jobs found. Application will stop immediately.");
            running = false;
            shutdownApplication();
            return;
        }

        Thread.ofPlatform().name("formfiller-watcher").start(this::waitForWorkers);
        LOGGER.info("Started {} worker(s)", workerThreads.size());
    }

    @Override
    public synchronized void stop() {
        if (!running && stopRequested.get()) {
            return;
        }

        if (stopRequested.compareAndSet(false, true)) {
            LOGGER.info("Stopping {} worker(s)", workerThreads.size());
            for (Thread workerThread : workerThreads) {
                workerThread.interrupt();
            }
        }
        running = false;
    }

    @Override
    public void stop(Runnable callback) {
        try {
            stop();
        } finally {
            callback.run();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    private void waitForWorkers() {
        for (Thread workerThread : workerThreads) {
            try {
                workerThread.join();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        running = false;
        if (!stopRequested.get()) {
            LOGGER.info("All workers completed. Closing the application context.");
            shutdownApplication();
        }
    }

    private void shutdownApplication() {
        if (applicationContext.isActive()) {
            SpringApplication.exit(applicationContext, () -> 0);
        }
    }
}

