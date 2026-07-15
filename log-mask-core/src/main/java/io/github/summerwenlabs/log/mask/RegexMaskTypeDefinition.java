package io.github.summerwenlabs.log.mask;

import java.util.regex.Pattern;

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
