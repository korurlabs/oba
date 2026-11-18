package com.korur.oba.dojo.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${oba.openapi.url}")
    private String baseUrl;

    @Bean
    public OpenAPI customOpenAPI() {
        Server server = new Server()
                .url(baseUrl)
                .description("Base Server for API Endpoints and Swagger UI");

        Contact contact = new Contact()
                .name("Sensey")
                .email("support@Sensey.io")
                .url(baseUrl);

        Info apiInfo = new Info()
                .title("Village API")
                .version("1.0")
                .contact(contact)
                .description("Sensey Village unified backend API.")
                .termsOfService("https://sensey.io/terms");

        return new OpenAPI()
                .info(apiInfo)
                .servers(List.of(server));
    }
}
