package top.egon.mario;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
@Slf4j
public class MarioApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(MarioApplication.class, args);
        log.info("application started, appName={}", context.getEnvironment().getProperty("spring.application.name", "Mario"));
    }

}
