package cn.net.pap.logback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggerDefineStatic {

    private static final Logger logger = LoggerFactory.getLogger(LoggerDefineStatic.class.getName());

    public void someMethod() {
        logger.info("Some info");
    }


}
