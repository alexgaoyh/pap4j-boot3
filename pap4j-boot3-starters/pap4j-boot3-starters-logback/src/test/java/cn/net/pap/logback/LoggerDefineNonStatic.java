package cn.net.pap.logback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggerDefineNonStatic {

    private final Logger logger = LoggerFactory.getLogger(LoggerDefineNonStatic.class.getName());

    public void someMethod() {
        logger.info("Some info");
    }

}
