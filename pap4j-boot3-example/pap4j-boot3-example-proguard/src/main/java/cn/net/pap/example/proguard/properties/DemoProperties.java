package cn.net.pap.example.proguard.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * priority using @ConfigurationProperties, not @Value
 */
@Component
@ConfigurationProperties(prefix = "demo")
public class DemoProperties {

    private Intercept intercept;

    public static class Intercept {

        private String readPrefixes;

        private String writePrefixes;

        private String enableDetailLog;

        private String markDemoData;

        private String successMessage;

        public String getReadPrefixes() {
            return readPrefixes;
        }

        public void setReadPrefixes(String readPrefixes) {
            this.readPrefixes = readPrefixes;
        }

        public String getWritePrefixes() {
            return writePrefixes;
        }

        public void setWritePrefixes(String writePrefixes) {
            this.writePrefixes = writePrefixes;
        }

        public String getEnableDetailLog() {
            return enableDetailLog;
        }

        public void setEnableDetailLog(String enableDetailLog) {
            this.enableDetailLog = enableDetailLog;
        }

        public String getMarkDemoData() {
            return markDemoData;
        }

        public void setMarkDemoData(String markDemoData) {
            this.markDemoData = markDemoData;
        }

        public String getSuccessMessage() {
            return successMessage;
        }

        public void setSuccessMessage(String successMessage) {
            this.successMessage = successMessage;
        }
    }

    public Intercept getIntercept() {
        return intercept;
    }

    public void setIntercept(Intercept intercept) {
        this.intercept = intercept;
    }
}
