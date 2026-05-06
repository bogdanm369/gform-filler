package ro.bogdanm.tools.model;

import java.net.URI;
import java.util.List;

public record GoogleFormMetadata(
        URI formUrl,
        URI actionUrl,
        String title,
        String description,
        String fbzx,
        String fvv,
        String pageHistory,
        String partialResponse,
        String submissionTimestamp,
        List<QuestionDescriptor> questions,
        List<FormPage> pages
) {
}

