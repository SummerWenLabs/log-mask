/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.samples.boot3;

import io.github.summerwenlabs.log.mask.governance.LogExclude;
import io.github.summerwenlabs.log.mask.governance.Mask;
import io.github.summerwenlabs.log.mask.governance.MaskType;

/**
 * Provides a request and response model for typed JSON body governance.
 *
 * <p>The phone property is masked only in logs and the internal property is
 * excluded only from logs; normal HTTP serialization retains both values.
 *
 * @author SummerWen
 * @since 0.1
 */
public class SamplePayload {

    private String label;
    private String phone;
    private String internal;

    /**
     * Create an empty payload for Jackson deserialization.
     */
    public SamplePayload() {
    }

    SamplePayload(String label, String phone, String internal) {
        this.label = label;
        this.phone = phone;
        this.internal = internal;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    @Mask(type = MaskType.PHONE)
    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    @LogExclude
    public String getInternal() {
        return internal;
    }

    public void setInternal(String internal) {
        this.internal = internal;
    }
}
