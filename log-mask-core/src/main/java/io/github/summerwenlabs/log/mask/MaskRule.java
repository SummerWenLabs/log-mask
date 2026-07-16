package io.github.summerwenlabs.log.mask;

/**
 * Represents the resolved action for one Jackson logical property.
 *
 * <p>Resolution separates invalid declarations from serialization so a broken
 * rule can be replaced with field-level redaction without failing the object.
 *
 * @author SummerWen
 * @since 0.1
 */
final class MaskRule {

    enum Action {
        NONE,
        REDACT,
        CONTENT
    }

    private static final MaskRule NONE = new MaskRule(Action.NONE, null, null, null);
    private static final MaskRule REDACT = new MaskRule(Action.REDACT, null, null, null);

    private final Action action;
    private final MaskType builtInType;
    private final MaskTypeDefinition definition;
    private final MaskFailureReason failureReason;

    private MaskRule(
            Action action,
            MaskType builtInType,
            MaskTypeDefinition definition,
            MaskFailureReason failureReason) {
        this.action = action;
        this.builtInType = builtInType;
        this.definition = definition;
        this.failureReason = failureReason;
    }

    static MaskRule none() {
        return NONE;
    }

    static MaskRule redact() {
        return REDACT;
    }

    static MaskRule redact(MaskFailureReason failureReason) {
        return new MaskRule(Action.REDACT, null, null, failureReason);
    }

    static MaskRule content(MaskType type) {
        return new MaskRule(Action.CONTENT, type, null, null);
    }

    static MaskRule content(MaskTypeDefinition definition) {
        return new MaskRule(Action.CONTENT, null, definition, null);
    }

    Action action() {
        return action;
    }

    MaskType builtInType() {
        return builtInType;
    }

    MaskTypeDefinition definition() {
        return definition;
    }

    MaskFailureReason failureReason() {
        return failureReason;
    }
}
