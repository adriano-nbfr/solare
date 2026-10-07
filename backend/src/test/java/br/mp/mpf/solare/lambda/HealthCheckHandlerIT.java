package br.mp.mpf.solare.lambda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Teste de integracao do endpoint GET /api/health: invoca o handler como o
 * API Gateway HTTP API faria e verifica status 200 com corpo "UP".
 */
class HealthCheckHandlerIT {

    private final HealthCheckHandler handler = new HealthCheckHandler();

    @Test
    @DisplayName("GET /api/health retorna 200 com status UP")
    void healthRetorna200() {
        APIGatewayV2HTTPEvent evento = eventoGet("/api/health");

        APIGatewayV2HTTPResponse resposta = handler.handleRequest(evento, null);

        assertNotNull(resposta);
        assertEquals(200, resposta.getStatusCode());
        assertNotNull(resposta.getBody());
        assertTrue(resposta.getBody().contains("\"status\":\"UP\""),
                "Corpo deveria indicar status UP, mas foi: " + resposta.getBody());
        assertEquals("application/json", resposta.getHeaders().get("Content-Type"));
    }

    private static APIGatewayV2HTTPEvent eventoGet(String path) {
        APIGatewayV2HTTPEvent.RequestContext.Http http =
                APIGatewayV2HTTPEvent.RequestContext.Http.builder()
                        .withMethod("GET")
                        .withPath(path)
                        .build();
        APIGatewayV2HTTPEvent.RequestContext ctx =
                APIGatewayV2HTTPEvent.RequestContext.builder()
                        .withHttp(http)
                        .build();
        return APIGatewayV2HTTPEvent.builder()
                .withRawPath(path)
                .withRequestContext(ctx)
                .build();
    }
}
