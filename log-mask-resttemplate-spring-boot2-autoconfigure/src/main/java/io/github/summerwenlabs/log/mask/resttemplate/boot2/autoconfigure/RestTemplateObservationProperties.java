package io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration for the RestTemplate observation adapter. */
@ConfigurationProperties(prefix = "log-mask.logging.rest-template")
public class RestTemplateObservationProperties {

    private boolean enabled = true;
    private List<String> observedBeanNames = new ArrayList<String>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getObservedBeanNames() {
        return observedBeanNames;
    }

    public void setObservedBeanNames(List<String> observedBeanNames) {
        this.observedBeanNames = observedBeanNames == null
                ? new ArrayList<String>()
                : new ArrayList<String>(observedBeanNames);
    }
}
