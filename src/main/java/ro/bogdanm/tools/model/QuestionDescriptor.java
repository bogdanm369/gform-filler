package ro.bogdanm.tools.model;

import java.util.List;

public record QuestionDescriptor(
        int index,
        long entryId,
        String title,
        String helpText,
        QuestionType type,
        boolean required,
        List<String> options
) {
}

