package io.github.summerwenlabs.log.mask.strategy;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import io.github.summerwenlabs.log.mask.governance.MaskType;

/**
 * Access to the built-in content masking strategies.
 *
 * @author SummerWen
 * @since 0.1
 */
public final class MaskStrategies {

    private static final Map<MaskType, MaskTypeDefinition> BUILT_INS = builtIns();

    private MaskStrategies() {
    }

    /**
     * Return the shared implementation for one content-aware built-in type.
     * @param type PHONE, EMAIL, ID_CARD, BANK_CARD, or FULL
     * @return a stateless strategy safe for concurrent reuse
     * @throws NullPointerException if {@code type} is {@code null}
     * @throws IllegalArgumentException if {@code type} is not content-aware
     */
    public static MaskTypeDefinition builtIn(MaskType type) {
        Objects.requireNonNull(type, "type");
        MaskTypeDefinition definition = BUILT_INS.get(type);
        if (definition == null) {
            throw new IllegalArgumentException("Not a built-in content masking type: " + type);
        }
        return definition;
    }

    private static Map<MaskType, MaskTypeDefinition> builtIns() {
        Map<MaskType, MaskTypeDefinition> definitions =
                new EnumMap<MaskType, MaskTypeDefinition>(MaskType.class);
        definitions.put(MaskType.PHONE, new BuiltInMaskTypeDefinition(MaskType.PHONE));
        definitions.put(MaskType.EMAIL, new BuiltInMaskTypeDefinition(MaskType.EMAIL));
        definitions.put(MaskType.ID_CARD, new BuiltInMaskTypeDefinition(MaskType.ID_CARD));
        definitions.put(MaskType.BANK_CARD, new BuiltInMaskTypeDefinition(MaskType.BANK_CARD));
        definitions.put(MaskType.FULL, new BuiltInMaskTypeDefinition(MaskType.FULL));
        return definitions;
    }
}
