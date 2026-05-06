package ro.bogdanm.tools.service;

import org.junit.jupiter.api.Test;
import ro.bogdanm.tools.model.QuestionDescriptor;
import ro.bogdanm.tools.model.QuestionType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnswerSpecParserTest {

    private final AnswerSpecParser parser = new AnswerSpecParser();

    private static AnswerSpecParser deterministicParser(double nextDouble, int nextInt) {
        return new AnswerSpecParser(new AnswerSpecParser.RandomSource() {
            @Override
            public int nextInt(int bound) {
                return Math.floorMod(nextInt, bound);
            }

            @Override
            public double nextDouble() {
                return nextDouble;
            }
        });
    }

    @Test
    void resolvesFixedChoiceIndex() {
        QuestionDescriptor question = new QuestionDescriptor(2, 100L, "Gender", "", QuestionType.SINGLE_CHOICE, true, List.of("Male", "Female"));

        List<String> values = parser.resolve(question, "2");

        assertEquals(List.of("Female"), values);
    }

    @Test
    void resolvesRangeAsRandomChoiceInsideBounds() {
        QuestionDescriptor question = new QuestionDescriptor(3, 101L, "Scale", "", QuestionType.LINEAR_SCALE, true, List.of("1", "2", "3", "4", "5"));

        List<String> values = parser.resolve(question, "2-4");

        assertEquals(1, values.size());
        assertTrue(List.of("2", "3", "4").contains(values.getFirst()));
    }

    @Test
    void resolvesCheckboxMultiSelection() {
        QuestionDescriptor question = new QuestionDescriptor(4, 102L, "Interests", "", QuestionType.CHECKBOX, false, List.of("A", "B", "C", "D"));

        List<String> values = parser.resolve(question, "multi:1,3-4");

        assertEquals(List.of("A", "C", "D"), values);
    }

    @Test
    void keepsTextQuestionsAsIs() {
        QuestionDescriptor question = new QuestionDescriptor(5, 103L, "Department", "", QuestionType.SHORT_TEXT, true, List.of());

        List<String> values = parser.resolve(question, "Accounting");

        assertEquals(List.of("Accounting"), values);
    }

    @Test
    void resolvesTextListForTextQuestionsAsRandomSingleValue() {
        AnswerSpecParser weightedParser = deterministicParser(0.0d, 1);
        QuestionDescriptor question = new QuestionDescriptor(6, 104L, "Department", "", QuestionType.SHORT_TEXT, true, List.of());

        List<String> values = weightedParser.resolve(question, "HR,IT,Contabilitate");

        assertEquals(List.of("IT"), values);
    }

    @Test
    void keepsQuotedCommaSeparatedTextAsSingleLiteralValue() {
        QuestionDescriptor question = new QuestionDescriptor(7, 105L, "Department", "", QuestionType.SHORT_TEXT, true, List.of());

        List<String> values = parser.resolve(question, "\"HR, IT, Contabilitate\"");

        assertEquals(List.of("HR, IT, Contabilitate"), values);
    }

    @Test
    void resolvesQuotedStructuredTextOptionContainingComma() {
        AnswerSpecParser weightedParser = deterministicParser(0.0d, 0);
        QuestionDescriptor question = new QuestionDescriptor(8, 106L, "Department", "", QuestionType.SHORT_TEXT, true, List.of());

        List<String> values = weightedParser.resolve(question, "\"HR, Payroll\",IT,Contabilitate");

        assertEquals(List.of("HR, Payroll"), values);
    }

    @Test
    void resolvesNumericListForTextQuestionsAsRandomSingleValue() {
        QuestionDescriptor question = new QuestionDescriptor(9, 107L, "Age", "", QuestionType.SHORT_TEXT, true, List.of());

        List<String> values = parser.resolve(question, "18,19,20,21");

        assertEquals(1, values.size());
        assertTrue(List.of("18", "19", "20", "21").contains(values.getFirst()));
    }

    @Test
    void resolvesNumericRangeForTextQuestionsAsRandomSingleValue() {
        QuestionDescriptor question = new QuestionDescriptor(10, 108L, "Age", "", QuestionType.SHORT_TEXT, true, List.of());

        List<String> values = parser.resolve(question, "18-21");

        assertEquals(1, values.size());
        assertTrue(List.of("18", "19", "20", "21").contains(values.getFirst()));
    }

    @Test
    void resolvesWeightedChoiceOverrideWhenPercentageHits() {
        AnswerSpecParser weightedParser = deterministicParser(0.05d, 0);
        QuestionDescriptor question = new QuestionDescriptor(11, 109L, "Scale", "", QuestionType.LINEAR_SCALE, true, List.of("1", "2", "3", "4", "5"));

        List<String> values = weightedParser.resolve(question, "=4-5,3(10%),2(5%)");

        assertEquals(List.of("3"), values);
    }

    @Test
    void resolvesPureWeightedChoiceDistribution() {
        AnswerSpecParser weightedParser = deterministicParser(0.95d, 0);
        QuestionDescriptor question = new QuestionDescriptor(12, 110L, "Scale", "", QuestionType.LINEAR_SCALE, true, List.of("1", "2", "3", "4", "5"));

        List<String> values = weightedParser.resolve(question, "5(90%),4(10%)");

        assertEquals(List.of("4"), values);
    }

    @Test
    void resolvesWeightedChoiceFallbackRangeWhenPercentagesMiss() {
        AnswerSpecParser weightedParser = deterministicParser(0.80d, 1);
        QuestionDescriptor question = new QuestionDescriptor(13, 111L, "Scale", "", QuestionType.LINEAR_SCALE, true, List.of("1", "2", "3", "4", "5"));

        List<String> values = weightedParser.resolve(question, "4-5,3(10%),2(5%)");

        assertEquals(List.of("5"), values);
    }

    @Test
    void resolvesWeightedNumericTextOverrideWhenPercentageHits() {
        AnswerSpecParser weightedParser = deterministicParser(0.12d, 0);
        QuestionDescriptor question = new QuestionDescriptor(14, 112L, "Age", "", QuestionType.SHORT_TEXT, true, List.of());

        List<String> values = weightedParser.resolve(question, "=18-19,30(10%),40(5%)");

        assertEquals(List.of("40"), values);
    }

    @Test
    void resolvesPureWeightedTextDistribution() {
        AnswerSpecParser weightedParser = deterministicParser(0.68d, 0);
        QuestionDescriptor question = new QuestionDescriptor(15, 113L, "Department", "", QuestionType.SHORT_TEXT, true, List.of());

        List<String> values = weightedParser.resolve(question, "Didactic(60%),HR(5%),IT(5%),Contabilitate(10%),Administratie(10%)");

        assertEquals(List.of("HR"), values);
    }

    @Test
    void resolvesFallbackPlusWeightedTextDistribution() {
        AnswerSpecParser weightedParser = deterministicParser(0.70d, 0);
        QuestionDescriptor question = new QuestionDescriptor(16, 114L, "Department", "", QuestionType.SHORT_TEXT, true, List.of());

        List<String> values = weightedParser.resolve(question, "Didactic,HR(10%),IT(10%)");

        assertEquals(List.of("Didactic"), values);
    }

    @Test
    void resolvesQuotedWeightedTextValueContainingComma() {
        AnswerSpecParser weightedParser = deterministicParser(0.20d, 0);
        QuestionDescriptor question = new QuestionDescriptor(17, 115L, "Department", "", QuestionType.SHORT_TEXT, true, List.of());

        List<String> values = weightedParser.resolve(question, "\"HR, Payroll\"(60%),IT(40%)");

        assertEquals(List.of("HR, Payroll"), values);
    }

    @Test
    void rejectsWeightedPercentagesAboveOneHundredPercent() {
        QuestionDescriptor question = new QuestionDescriptor(18, 116L, "Scale", "", QuestionType.LINEAR_SCALE, true, List.of("1", "2", "3", "4", "5"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> parser.resolve(question, "=4-5,3(60%),2(50%)"));

        assertTrue(exception.getMessage().contains("more than 100%"));
    }

    @Test
    void allowsPureWeightedSyntaxBelowOneHundredPercentAsRelativeWeights() {
        AnswerSpecParser weightedParser = deterministicParser(0.95d, 0);
        QuestionDescriptor question = new QuestionDescriptor(19, 117L, "Scale", "", QuestionType.LINEAR_SCALE, true, List.of("1", "2", "3", "4", "5"));

        List<String> values = weightedParser.resolve(question, "5(90%),4(5%)");

        assertEquals(List.of("4"), values);
    }

    @Test
    void rejectsWeightedMultiSelectionSyntax() {
        QuestionDescriptor question = new QuestionDescriptor(20, 118L, "Interests", "", QuestionType.CHECKBOX, true, List.of("A", "B", "C", "D"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> parser.resolve(question, "multi:1,2(10%)"));

        assertTrue(exception.getMessage().contains("multi:"));
    }

    @Test
    void rejectsBlankRequiredValues() {
        QuestionDescriptor question = new QuestionDescriptor(21, 119L, "Required", "", QuestionType.SINGLE_CHOICE, true, List.of("Yes", "No"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> parser.resolve(question, "   "));

        assertTrue(exception.getMessage().contains("question.21.values"));
    }
}

