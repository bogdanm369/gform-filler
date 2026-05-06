package ro.bogdanm.tools.model;

public enum QuestionType {
    SHORT_TEXT(true, true),
    PARAGRAPH_TEXT(true, true),
    SINGLE_CHOICE(true, false),
    DROPDOWN(true, false),
    CHECKBOX(true, false),
    LINEAR_SCALE(true, false),
    DATE(true, true),
    TIME(true, true),
    UNSUPPORTED(true, false),
    SECTION(false, false),
    IMAGE(false, false);

    private final boolean answerable;
    private final boolean textual;

    QuestionType(boolean answerable, boolean textual) {
        this.answerable = answerable;
        this.textual = textual;
    }

    public boolean isAnswerable() {
        return answerable;
    }

    public boolean isTextual() {
        return textual;
    }

    public boolean isChoiceBased() {
        return answerable && !textual && this != UNSUPPORTED;
    }

    public static QuestionType fromGoogleType(int googleTypeCode) {
        return switch (googleTypeCode) {
            case 0 -> SHORT_TEXT;
            case 1 -> PARAGRAPH_TEXT;
            case 2 -> SINGLE_CHOICE;
            case 3 -> DROPDOWN;
            case 4 -> CHECKBOX;
            case 5 -> LINEAR_SCALE;
            case 8 -> SECTION;
            case 9 -> DATE;
            case 10 -> TIME;
            case 11 -> IMAGE;
            default -> UNSUPPORTED;
        };
    }
}

