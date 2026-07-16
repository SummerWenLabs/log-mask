/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.strategy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.github.summerwenlabs.log.mask.governance.MaskType;

/**
 * Immutable registry of custom content masking strategies.
 *
 * <p>Codes are non-empty, have no surrounding whitespace, and are matched
 * exactly with case preserved. The custom namespace is independent of built-in
 * {@link MaskType} names.
 *
 * @author SummerWen
 * @since 0.1
 */
public final class MaskStrategyRegistry {

    private static final MaskStrategyRegistry EMPTY =
            new MaskStrategyRegistry(Collections.<MaskTypeCode, MaskTypeDefinition>emptyMap());

    private final Map<MaskTypeCode, MaskTypeDefinition> definitions;

    private MaskStrategyRegistry(Map<MaskTypeCode, MaskTypeDefinition> definitions) {
        this.definitions = definitions;
    }

    /**
     * Return the shared empty registry.
     * @return an immutable registry with no custom strategies
     */
    public static MaskStrategyRegistry empty() {
        return EMPTY;
    }

    /**
     * Create an immutable registry from application-defined strategies.
     * @param definitions strategy definitions in registration order
     * @return an immutable registry, or the shared empty registry
     * @throws NullPointerException if the iterable or any definition is
     * {@code null}
     * @throws IllegalArgumentException if a code is invalid or duplicated
     */
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

    /**
     * Find a strategy by its exact custom code.
     * @param typeCode code to find; invalid codes are treated as absent
     * @return the matching strategy, if registered
     */
    public Optional<MaskTypeDefinition> find(String typeCode) {
        if (!MaskTypeCode.isValid(typeCode)) {
            return Optional.empty();
        }
        return Optional.ofNullable(definitions.get(MaskTypeCode.of(typeCode)));
    }
}
