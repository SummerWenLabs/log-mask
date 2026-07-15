package io.github.summerwenlabs.log.mask.http;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NameValueCollectionTest {

    @Test
    void groupsValuesByFirstNameOccurrenceAndKeepsTheResultImmutable() {
        NameValueCollection values = NameValueCollection.builder()
                .add("tag", "java")
                .addAll("empty", Collections.<String>emptyList())
                .add("page", "1")
                .addAll("tag", Collections.singletonList("spring"))
                .add("flag", null)
                .build();

        assertEquals(
                Arrays.asList("tag", "empty", "page", "flag"),
                Arrays.asList(
                        values.getEntries().get(0).getName(),
                        values.getEntries().get(1).getName(),
                        values.getEntries().get(2).getName(),
                        values.getEntries().get(3).getName()));
        assertEquals(
                Arrays.asList("java", "spring"),
                values.getEntries().get(0).getValues());
        assertEquals(
                Collections.emptyList(),
                values.getEntries().get(1).getValues());
        assertEquals(
                Collections.singletonList(null),
                values.getEntries().get(3).getValues());
        assertThrows(
                UnsupportedOperationException.class,
                () -> values.getEntries().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> values.getEntries().get(0).getValues().add("changed"));
    }
}
