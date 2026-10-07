package br.mp.mpf.solare.lambda;

import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;

/**
 * Lambda de health check do BFF Solare, atras do API Gateway HTTP API.
 * Responde {@code GET /api/health} com status 200 e um corpo JSON simples.
 */
public class HealthCheckHandler
        implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private static final String CORPO_OK = "{\"status\":\"UP\",\"servico\":\"solare-bff\"}";

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent evento, Context context) {
        return APIGatewayV2HTTPResponse.builder()
                .withStatusCode(200)
                .withHeaders(Map.of("Content-Type", "application/json"))
                .withBody(CORPO_OK)
                .build();
    }
}
