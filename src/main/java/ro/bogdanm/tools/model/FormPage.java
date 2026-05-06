package ro.bogdanm.tools.model;

import java.util.List;

public record FormPage(
        int index,
        List<QuestionDescriptor> questions
) {
}

