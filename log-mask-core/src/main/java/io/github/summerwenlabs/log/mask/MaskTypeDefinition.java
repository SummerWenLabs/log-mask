package io.github.summerwenlabs.log.mask;

/**
 * One dependency-aware custom content masking strategy registered under an exact code.
 */
public interface MaskTypeDefinition {

    String getTypeCode();

    String mask(String value);
}
