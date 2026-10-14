package com.elias.mismonedas;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "app.messaging.rabbitmq.consumer-auto-startup=false")
class MisMonedasApplicationTests {

    @Test
    void contextLoads() {
    }

}
