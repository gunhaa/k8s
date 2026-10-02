package dev.k8slab.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BalanceWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(BalanceWorkerApplication.class, args);
    }
}
