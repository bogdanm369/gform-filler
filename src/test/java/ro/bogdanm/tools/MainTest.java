package ro.bogdanm.tools;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "formfiller.autostart=false")
class MainTest {

    @Test
    void contextLoads() {
    }
}

