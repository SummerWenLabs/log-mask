package io.github.summerwenlabs.log.mask;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable registry of custom content masking strategies.
 */
public final class MaskStrategyRegistry {

    private static final MaskStrategyRegistry EMPTY =
            new MaskStrategyRegistry(Collections.<MaskTypeCode, MaskTypeDefinition>emptyMap());

    private final Map<MaskTypeCode, MaskTypeDefinition> definitions;

    private MaskStrategyRegistry(Map<MaskTypeCode, MaskTypeDefinition> definitions) {
        this.definitions = definitions;
    }

    public static MaskStrategyRegistry empty() {
        return EMPTY;
    }

    public static MaskStrategyRegistry of(
            Iterable<? extends MaskTypeDefinition> definitions) {
        Objects.requireNonNull(definitions, "definitions");
        Map<MaskTypeCode, MaskTypeDefinition> byCode =
                new LinkedHashMap<MaskTypeCode, MaskTypeDefinition>();
        for (MaskTypeDefinition definition : definitions) {
            Objects.requireNonNull(definition, "definition");
            MaskTypeCode code = MaskTypeCode.of(definition.getTypeCode());
            if (byCode.put(code, definition) != null) {
                throw new IllegalArgumentException("Duplicate custom mask type code: " + code);
            }
        }
        return byCode.isEmpty()
                ? EMPTY
                : new MaskStrategyRegistry(
                        Collections.unmodifiableMap(
                                new LinkedHashMap<MaskTypeCode, MaskTypeDefinition>(byCode)));
    }

    public Optional<MaskTypeDefinition> find(String typeCode) {
        if (!MaskTypeCode.isValid(typeCode)) {
            return Optional.empty();
        }
        return Optional.ofNullable(definitions.get(MaskTypeCode.of(typeCode)));
    }
}
