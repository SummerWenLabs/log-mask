/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Carries reference-free startup counts and canonical names after local
 * RestTemplate observation installation is sealed.
 *
 * @author SummerWen
 * @since 0.1
 */
final class RestTemplateObservationSnapshot {

    private static final RestTemplateObservationSnapshot EMPTY =
            new RestTemplateObservationSnapshot(0, Collections.<String>emptyList());

    private final int observedInstanceCountAtStartup;
    private final List<String> observedBeanNamesAtStartup;

    RestTemplateObservationSnapshot(
            int observedInstanceCountAtStartup,
            List<String> observedBeanNamesAtStartup) {
        this.observedInstanceCountAtStartup = observedInstanceCountAtStartup;
        this.observedBeanNamesAtStartup = Collections.unmodifiableList(
                new ArrayList<String>(observedBeanNamesAtStartup));
    }

    static RestTemplateObservationSnapshot empty() {
        return EMPTY;
    }

    int getObservedInstanceCountAtStartup() {
        return observedInstanceCountAtStartup;
    }

    List<String> getObservedBeanNamesAtStartup() {
        return observedBeanNamesAtStartup;
    }
}
