package crm.ms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class BookingCarServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(BookingCarServiceApplication.class, args);
    }
}
