package io.github.summerwenlabs.log.mask.samples;

import io.github.summerwenlabs.log.mask.LogExclude;
import io.github.summerwenlabs.log.mask.Mask;
import io.github.summerwenlabs.log.mask.MaskType;

/** Request and response model used to demonstrate typed JSON body governance. */
public class SamplePayload {

    private String label;
    private String phone;
    private String internal;

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
