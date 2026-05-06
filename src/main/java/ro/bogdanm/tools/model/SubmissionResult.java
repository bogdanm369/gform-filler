package ro.bogdanm.tools.model;

import java.net.URI;

public record SubmissionResult(boolean success, int statusCode, URI responseUri) {
}

