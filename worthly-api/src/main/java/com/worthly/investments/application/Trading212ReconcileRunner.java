package com.worthly.investments.application;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(100)
public class Trading212ReconcileRunner implements ApplicationRunner {

    private final Trading212ConnectionService connections;

    public Trading212ReconcileRunner(Trading212ConnectionService connections) {
        this.connections = connections;
    }

    @Override
    public void run(ApplicationArguments args) {
        connections.reconcile();
    }
}
