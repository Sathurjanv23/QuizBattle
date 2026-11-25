package com.quizbattle.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Domain model representing a quiz question.
 *
 * <p>Security note: {@code correctOptionIndex} is excluded when transmitted to clients
 * while the question is active to prevent client-side answer sniffing.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Question {

    private final String questionId;
    private final String text;
    private final List<String> options;
    private final Integer correctOptionIndex;
    private final int timeLimitSeconds;
    private final int pointValue;
    private final int questionNumber;
    private final int totalQuestions;
    private final long startTimeMillis;
    private final long deadlineMillis;

    @JsonCreator
    public Question(
            @JsonProperty("questionId") String questionId,
            @JsonProperty("text") String text,
            @JsonProperty("options") List<String> options,
            @JsonProperty("correctOptionIndex") Integer correctOptionIndex,
            @JsonProperty("timeLimitSeconds") int timeLimitSeconds,
            @JsonProperty("pointValue") int pointValue,
            @JsonProperty("questionNumber") int questionNumber,
            @JsonProperty("totalQuestions") int totalQuestions,
            @JsonProperty("startTimeMillis") long startTimeMillis,
            @JsonProperty("deadlineMillis") long deadlineMillis) {
        this.questionId = Objects.requireNonNull(questionId, "questionId must not be null");
        this.text = Objects.requireNonNull(text, "text must not be null");
        this.options = options != null ? Collections.unmodifiableList(options) : Collections.emptyList();
        this.correctOptionIndex = correctOptionIndex;
        this.timeLimitSeconds = timeLimitSeconds > 0 ? timeLimitSeconds : 20;
        this.pointValue = pointValue > 0 ? pointValue : 100;
        this.questionNumber = questionNumber;
        this.totalQuestions = totalQuestions;
        this.startTimeMillis = startTimeMillis;
        this.deadlineMillis = deadlineMillis;
    }

    public Question(String questionId, String text, List<String> options, Integer correctOptionIndex,
                    int timeLimitSeconds, int pointValue) {
        this(questionId, text, options, correctOptionIndex, timeLimitSeconds, pointValue, 0, 0, 0, 0);
    }

    public String getQuestionId() {
        return questionId;
    }

    public String getText() {
        return text;
    }

    public List<String> getOptions() {
        return options;
    }

    public Integer getCorrectOptionIndex() {
        return correctOptionIndex;
    }

    public int getTimeLimitSeconds() {
        return timeLimitSeconds;
    }

    public int getPointValue() {
        return pointValue;
    }

    public int getQuestionNumber() {
        return questionNumber;
    }

    public int getTotalQuestions() {
        return totalQuestions;
    }

    public long getStartTimeMillis() {
        return startTimeMillis;
    }

    public long getDeadlineMillis() {
        return deadlineMillis;
    }

    /**
     * Creates a sanitized copy suitable for broadcast to players (without correctOptionIndex).
     */
    public Question forClient() {
        return new Question(questionId, text, options, null, timeLimitSeconds, pointValue,
                questionNumber, totalQuestions, startTimeMillis, deadlineMillis);
    }

    public Question forClient(int questionNumber, int totalQuestions, long startTimeMillis, long deadlineMillis) {
        return new Question(questionId, text, options, null, timeLimitSeconds, pointValue,
                questionNumber, totalQuestions, startTimeMillis, deadlineMillis);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Question question)) return false;
        return Objects.equals(questionId, question.questionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(questionId);
    }

    @Override
    public String toString() {
        return "Question{" +
                "questionId='" + questionId + '\'' +
                ", text='" + text + '\'' +
                ", timeLimitSeconds=" + timeLimitSeconds +
                ", pointValue=" + pointValue +
                '}';
    }
}
