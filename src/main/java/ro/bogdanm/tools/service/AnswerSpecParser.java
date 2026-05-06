package ro.bogdanm.tools.service;

import ro.bogdanm.tools.model.QuestionDescriptor;
import ro.bogdanm.tools.model.QuestionType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AnswerSpecParser {

    private static final String MULTI_PREFIX = "multi:";
    private static final Pattern WEIGHTED_TOKEN_PATTERN = Pattern.compile("(.+?)\\((\\d+(?:\\.\\d+)?)%\\)");

    private final RandomSource randomSource;

    public AnswerSpecParser() {
        this(new ThreadLocalRandomSource());
    }

    AnswerSpecParser(RandomSource randomSource) {
        this.randomSource = Objects.requireNonNull(randomSource, "randomSource");
    }

    public List<String> resolve(QuestionDescriptor question, String configuredValue) {
        String spec = configuredValue == null ? "" : configuredValue.trim();
        if (spec.isEmpty()) {
            if (question.required()) {
                throw new IllegalArgumentException("Question " + question.index() + " is required but question." + question.index() + ".values is blank");
            }
            return List.of();
        }

        if (question.type().isTextual()) {
            if (looksLikeWeightedSpec(spec)) {
                if (looksLikeWeightedNumericSpec(spec)) {
                    WeightedSelection<Integer> weightedSelection = parseWeightedNumericSelection(spec, question.index());
                    return List.of(Integer.toString(weightedSelection.select(randomSource)));
                }
                WeightedSelection<String> weightedSelection = parseWeightedTextSelection(spec, question.index());
                return List.of(weightedSelection.select(randomSource));
            }
            if (looksLikeNumericChoiceSpec(spec)) {
                List<Integer> candidates = expandIndices(spec);
                int selectedValue = candidates.get(randomSource.nextInt(candidates.size()));
                return List.of(Integer.toString(selectedValue));
            }
            if (looksLikeTextChoiceList(spec)) {
                List<String> candidates = parseTextCandidates(spec, question.index());
                return List.of(candidates.get(randomSource.nextInt(candidates.size())));
            }
            return List.of(unquote(spec));
        }

        if (question.type() == QuestionType.UNSUPPORTED) {
            throw new IllegalArgumentException("Question " + question.index() + " has unsupported type " + question.type() + " and cannot be filled automatically yet");
        }

        if (spec.toLowerCase(Locale.ROOT).startsWith(MULTI_PREFIX)) {
            if (looksLikeWeightedSpec(spec)) {
                throw new IllegalArgumentException("Question " + question.index() + " uses weighted percentages together with multi:, which is not supported");
            }
            String rawSelection = spec.substring(MULTI_PREFIX.length()).trim();
            return mapIndices(question, expandIndices(rawSelection));
        }

        if (looksLikeWeightedSpec(spec)) {
            WeightedSelection<Integer> weightedSelection = parseWeightedNumericSelection(spec, question.index());
            return mapIndices(question, List.of(weightedSelection.select(randomSource)));
        }

        List<Integer> candidates = expandIndices(spec);
        int selectedIndex = candidates.get(randomSource.nextInt(candidates.size()));
        return mapIndices(question, List.of(selectedIndex));
    }

    private static boolean looksLikeWeightedSpec(String spec) {
        String normalized = normalizeStructuredSpec(spec);
        if (!normalized.contains("%")) {
            return false;
        }
        for (String token : splitStructuredTokens(normalized)) {
            if (WEIGHTED_TOKEN_PATTERN.matcher(token.trim()).matches()) {
                return true;
            }
        }
        return false;
    }

    private static boolean looksLikeWeightedNumericSpec(String spec) {
        if (!looksLikeWeightedSpec(spec)) {
            return false;
        }
        String normalized = normalizeStructuredSpec(spec);
        for (String token : splitStructuredTokens(normalized)) {
            Matcher matcher = WEIGHTED_TOKEN_PATTERN.matcher(token.trim());
            if (matcher.matches()) {
                if (!looksLikeNumericChoiceSpec(matcher.group(1).trim())) {
                    return false;
                }
                continue;
            }
            if (!looksLikeNumericChoiceSpec(token.trim())) {
                return false;
            }
        }
        return true;
    }

    private static boolean looksLikeNumericChoiceSpec(String spec) {
        if (spec.toLowerCase(Locale.ROOT).startsWith(MULTI_PREFIX)) {
            return false;
        }
        return normalizeStructuredSpec(spec).matches("\\d+(\\s*-\\s*\\d+)?(\\s*,\\s*\\d+(\\s*-\\s*\\d+)?)*");
    }

    private static boolean looksLikeTextChoiceList(String spec) {
        String normalized = normalizeStructuredSpec(spec);
        List<String> tokens = splitStructuredTokens(normalized);
        return tokens.size() > 1;
    }

    private static WeightedSelection<Integer> parseWeightedNumericSelection(String rawSpec, int questionIndex) {
        String spec = normalizeStructuredSpec(rawSpec);
        List<String> baseTokens = new ArrayList<>();
        Map<Integer, Double> overridePercentages = new LinkedHashMap<>();

        for (String tokenPart : splitStructuredTokens(spec)) {
            String token = tokenPart.trim();
            if (token.isEmpty()) {
                continue;
            }

            Matcher matcher = WEIGHTED_TOKEN_PATTERN.matcher(token);
            if (!matcher.matches()) {
                baseTokens.add(token);
                continue;
            }

            List<Integer> overrideValues = expandIndices(matcher.group(1).trim());
            if (overrideValues.size() != 1) {
                throw new IllegalArgumentException("Question " + questionIndex + " weighted values must point to exactly one answer, but got: " + matcher.group(1).trim());
            }

            double percentage = Double.parseDouble(matcher.group(2));
            if (percentage <= 0 || percentage > 100) {
                throw new IllegalArgumentException("Question " + questionIndex + " weighted percentages must be greater than 0 and at most 100, but got: " + matcher.group(2) + "%");
            }

            Double previous = overridePercentages.putIfAbsent(overrideValues.getFirst(), percentage);
            if (previous != null) {
                throw new IllegalArgumentException("Question " + questionIndex + " repeats weighted value " + overrideValues.getFirst() + " more than once");
            }
        }

        List<Integer> baseCandidates = List.of();
        if (!baseTokens.isEmpty()) {
            baseCandidates = new ArrayList<>(new LinkedHashSet<>(expandIndices(String.join(",", baseTokens))));
        }
        return buildWeightedSelection(questionIndex, baseCandidates, overridePercentages, "weighted value ");
    }

    private static WeightedSelection<String> parseWeightedTextSelection(String rawSpec, int questionIndex) {
        String spec = normalizeStructuredSpec(rawSpec);
        List<String> baseTokens = new ArrayList<>();
        Map<String, Double> overridePercentages = new LinkedHashMap<>();

        for (String tokenPart : splitStructuredTokens(spec)) {
            String token = tokenPart.trim();
            if (token.isEmpty()) {
                continue;
            }

            Matcher matcher = WEIGHTED_TOKEN_PATTERN.matcher(token);
            if (!matcher.matches()) {
                baseTokens.add(token);
                continue;
            }

            String overrideValue = unquote(matcher.group(1).trim());
            if (overrideValue.isEmpty()) {
                throw new IllegalArgumentException("Question " + questionIndex + " weighted text value cannot be blank");
            }

            double percentage = Double.parseDouble(matcher.group(2));
            if (percentage <= 0 || percentage > 100) {
                throw new IllegalArgumentException("Question " + questionIndex + " weighted percentages must be greater than 0 and at most 100, but got: " + matcher.group(2) + "%");
            }

            Double previous = overridePercentages.putIfAbsent(overrideValue, percentage);
            if (previous != null) {
                throw new IllegalArgumentException("Question " + questionIndex + " repeats weighted text value '" + overrideValue + "' more than once");
            }
        }

        List<String> baseCandidates = List.of();
        if (!baseTokens.isEmpty()) {
            baseCandidates = new ArrayList<>(new LinkedHashSet<>(parseTextCandidatesFromTokens(baseTokens, questionIndex)));
        }
        return buildWeightedSelection(questionIndex, baseCandidates, overridePercentages, "weighted text value '");
    }

    private static <T> WeightedSelection<T> buildWeightedSelection(
            int questionIndex,
            List<T> baseCandidates,
            Map<T, Double> overridePercentages,
            String overlapPrefix
    ) {
        double totalPercentage = 0;
        for (Map.Entry<T, Double> override : overridePercentages.entrySet()) {
            if (!baseCandidates.isEmpty() && baseCandidates.contains(override.getKey())) {
                throw new IllegalArgumentException("Question " + questionIndex + " " + overlapPrefix + override.getKey() + (overlapPrefix.endsWith("'") ? "'" : "") + " overlaps with the normal fallback range/list");
            }
            totalPercentage += override.getValue();
        }

        if (overridePercentages.isEmpty()) {
            throw new IllegalArgumentException("Question " + questionIndex + " weighted syntax did not contain any values");
        }

        if (totalPercentage > 100.0d) {
            throw new IllegalArgumentException("Question " + questionIndex + " weighted percentages add up to more than 100%: " + totalPercentage + "%");
        }

        List<WeightedOverride<T>> overrides = overridePercentages.entrySet().stream()
                .map(entry -> new WeightedOverride<>(entry.getKey(), entry.getValue()))
                .toList();
        return new WeightedSelection<>(List.copyOf(baseCandidates), overrides, totalPercentage);
    }

    private static String normalizeStructuredSpec(String spec) {
        String normalized = spec == null ? "" : spec.trim();
        return normalized.startsWith("=") ? normalized.substring(1).trim() : normalized;
    }

    private static List<String> parseTextCandidates(String rawSpec, int questionIndex) {
        return parseTextCandidatesFromTokens(splitStructuredTokens(normalizeStructuredSpec(rawSpec)), questionIndex);
    }

    private static List<String> parseTextCandidatesFromTokens(List<String> tokens, int questionIndex) {
        List<String> candidates = new ArrayList<>();
        for (String tokenPart : tokens) {
            String token = unquote(tokenPart.trim());
            if (token.isEmpty()) {
                continue;
            }
            candidates.add(token);
        }
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("Question " + questionIndex + " did not contain any text values to choose from");
        }
        return List.copyOf(candidates);
    }

    private static List<String> splitStructuredTokens(String spec) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean insideQuotes = false;
        for (int index = 0; index < spec.length(); index++) {
            char currentChar = spec.charAt(index);
            if (currentChar == '"') {
                insideQuotes = !insideQuotes;
                current.append(currentChar);
                continue;
            }
            if (currentChar == ',' && !insideQuotes) {
                tokens.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(currentChar);
        }
        if (insideQuotes) {
            throw new IllegalArgumentException("Unbalanced quotes in values spec: " + spec);
        }
        tokens.add(current.toString());
        return tokens;
    }

    private static String unquote(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1).replace("\\\"", "\"");
        }
        return trimmed;
    }

    private static List<Integer> expandIndices(String raw) {
        List<Integer> indices = new ArrayList<>();
        for (String part : splitStructuredTokens(raw)) {
            String token = part.trim();
            if (token.isEmpty()) {
                continue;
            }
            int dash = token.indexOf('-');
            if (dash > 0) {
                int start = Integer.parseInt(token.substring(0, dash).trim());
                int end = Integer.parseInt(token.substring(dash + 1).trim());
                if (start <= end) {
                    for (int i = start; i <= end; i++) {
                        indices.add(i);
                    }
                } else {
                    for (int i = start; i >= end; i--) {
                        indices.add(i);
                    }
                }
            } else {
                indices.add(Integer.parseInt(token));
            }
        }
        if (indices.isEmpty()) {
            throw new IllegalArgumentException("No option indexes were parsed from value: " + raw);
        }
        return indices;
    }

    private static List<String> mapIndices(QuestionDescriptor question, List<Integer> indices) {
        if (question.options().isEmpty()) {
            throw new IllegalArgumentException("Question " + question.index() + " has no configured options, but the values spec expected a choice-based question");
        }

        Set<String> resolved = new LinkedHashSet<>();
        for (int oneBasedIndex : indices) {
            if (oneBasedIndex < 1 || oneBasedIndex > question.options().size()) {
                throw new IllegalArgumentException("Question " + question.index() + " only has " + question.options().size() + " option(s), but values requested " + oneBasedIndex);
            }
            resolved.add(question.options().get(oneBasedIndex - 1));
        }
        return List.copyOf(resolved);
    }

    interface RandomSource {
        int nextInt(int bound);

        double nextDouble();
    }

    private record WeightedOverride<T>(T value, double percentage) {
    }

    private record WeightedSelection<T>(List<T> baseCandidates, List<WeightedOverride<T>> overrides, double totalPercentage) {
        private T select(RandomSource randomSource) {
            double roll = randomSource.nextDouble() * (baseCandidates.isEmpty() ? totalPercentage : 100.0d);
            double cumulative = 0.0d;
            for (WeightedOverride<T> override : overrides) {
                cumulative += override.percentage();
                if (roll < cumulative) {
                    return override.value();
                }
            }
            if (baseCandidates.isEmpty()) {
                return overrides.getLast().value();
            }
            return baseCandidates.get(randomSource.nextInt(baseCandidates.size()));
        }
    }

    private static final class ThreadLocalRandomSource implements RandomSource {
        @Override
        public int nextInt(int bound) {
            return ThreadLocalRandom.current().nextInt(bound);
        }

        @Override
        public double nextDouble() {
            return ThreadLocalRandom.current().nextDouble();
        }
    }
}

