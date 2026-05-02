package com.micro.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.filter.LoadBalancerFilterFunctions.lb;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.path;

@Configuration
public class SpringCloudConfig {
    @Bean
    public RouterFunction<ServerResponse> booksRoute() {
        return route("books").route(path("/book/**"), http()).filter(lb("book-service")).build();
    }

    @Bean
    public RouterFunction<ServerResponse> userRoute() {
        return route("user").route(path("/auth/**"), http()).filter(lb("user-service")).build();
    }
}
