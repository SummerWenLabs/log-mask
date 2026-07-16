package io.github.summerwenlabs.log.mask.http.governance;

import io.github.summerwenlabs.log.mask.governance.MaskType;
import io.github.summerwenlabs.log.mask.strategy.MaskStrategies;
import io.github.summerwenlabs.log.mask.strategy.MaskStrategyRegistry;
import io.github.summerwenlabs.log.mask.strategy.MaskTypeDefinition;

/**
 * Compiles one HTTP value declaration into a reusable governance action.
 *
 * <p>Rule configuration errors fail compilation. Runtime strategy failures are
 * isolated to the current value, replaced with fixed redaction, and reported
 * through the paired fallback state.
 *
 * @author SummerWen
 * @since 0.1
 */
final class CompiledHttpValueRule {

    private static final String REDACTION = "<redacted>";

    private final Action action;
    private final MaskTypeDefinition definition;

    private CompiledHttpValueRule(Action action, MaskTypeDefinition definition) {
        this.action = action;
        this.definition = definition;
    }

    static CompiledHttpValueRule compile(
            HttpRuleType type,
            String typeCode,
            MaskStrategyRegistry strategyRegistry,
            boolean excludeAllowed,
            String location) {
        if ((type == null) == (typeCode == null)) {
            throw new IllegalArgumentException(
                    location + " must configure exactly one of type or typeCode");
        }
        if (typeCode != null) {
            MaskTypeDefinition custom = strategyRegistry.find(typeCode).orElse(null);
            if (custom == null) {
                throw new IllegalArgumentException(
                        location + " has an unknown or invalid typeCode: " + typeCode);
            }
            return new CompiledHttpValueRule(Action.MASK, custom);
        }
        if (type == HttpRuleType.REDACT) {
            return new CompiledHttpValueRule(Action.REDACT, null);
        }
        if (type == HttpRuleType.EXCLUDE) {
            if (!excludeAllowed) {
                throw new IllegalArgumentException(location + " does not allow EXCLUDE");
            }
            return new CompiledHttpValueRule(Action.EXCLUDE, null);
        }
        return new CompiledHttpValueRule(Action.MASK, MaskStrategies.builtIn(maskType(type)));
    }

    Result apply(String value) {
        if (action == Action.REDACT) {
            return Result.success(REDACTION);
        }
        if (action == Action.EXCLUDE) {
            throw new IllegalStateException("EXCLUDE cannot produce a governed value");
        }
        try {
            String masked = definition.mask(value);
            return masked == null ? Result.fallback() : Result.success(masked);
        } catch (RuntimeException exception) {
            return Result.fallback();
        }
    }

    boolean observesValue() {
        return action == Action.MASK;
    }

    static Result fallback() {
        return Result.fallback();
    }

    private static MaskType maskType(HttpRuleType type) {
        switch (type) {
            case PHONE:
                return MaskType.PHONE;
            case EMAIL:
                return MaskType.EMAIL;
            case ID_CARD:
                return MaskType.ID_CARD;
            case BANK_CARD:
                return MaskType.BANK_CARD;
            case FULL:
                return MaskType.FULL;
            default:
                throw new IllegalArgumentException("Not a content masking type: " + type);
        }
    }

    private enum Action {
        REDACT,
        EXCLUDE,
        MASK
    }

    static final class Result {
        private final String value;
        private final boolean fallbackApplied;

        private Result(String value, boolean fallbackApplied) {
            this.value = value;
            this.fallbackApplied = fallbackApplied;
        }

        static Result success(String value) {
            return new Result(value, false);
        }

        static Result fallback() {
            return new Result(REDACTION, true);
        }

        String value() {
            return value;
        }

        boolean fallbackApplied() {
            return fallbackApplied;
        }
    }
}
