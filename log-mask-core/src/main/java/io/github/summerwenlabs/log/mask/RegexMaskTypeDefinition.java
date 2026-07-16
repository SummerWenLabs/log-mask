/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask;

import java.util.regex.Pattern;

import io.github.summerwenlabs.log.mask.strategy.MaskTypeDefinition;

/**
 * Applies one annotation-defined regular-expression replacement.
 *
 * @author SummerWen
 * @since 0.1
 */
final class RegexMaskTypeDefinition implements MaskTypeDefinition {

    private final Pattern pattern;
    private final String replacement;

    RegexMaskTypeDefinition(String pattern, String replacement) {
        this.pattern = Pattern.compile(pattern);
        this.replacement = replacement;
    }

    @Override
    public String getTypeCode() {
        return "CUSTOM";
    }

    @Override
    public String mask(String value) {
        return pattern.matcher(value).replaceAll(replacement);
    }
}
