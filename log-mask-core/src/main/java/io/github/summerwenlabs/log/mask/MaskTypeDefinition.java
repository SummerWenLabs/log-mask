package io.github.summerwenlabs.log.mask;

/**
 * One String content masking strategy.
 * Application-defined instances are registered under an exact custom code.
 */
public interface MaskTypeDefinition {

    String getTypeCode();

    String mask(String value);
}
