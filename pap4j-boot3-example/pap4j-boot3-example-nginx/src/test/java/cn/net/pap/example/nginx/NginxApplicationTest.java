package cn.net.pap.example.nginx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class NginxApplicationTest {

    @Test
    void testInstantiation() {
        NginxApplication application = new NginxApplication();
        assertNotNull(application);
    }

}
