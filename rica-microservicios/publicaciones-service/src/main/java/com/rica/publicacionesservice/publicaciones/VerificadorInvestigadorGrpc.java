package com.rica.publicacionesservice.publicaciones;

import com.rica.publicacionesservice.grpc.InvestigadorGrpcGrpc.InvestigadorGrpcBlockingStub;
import com.rica.publicacionesservice.grpc.InvestigadorRequest;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class VerificadorInvestigadorGrpc implements VerificadorInvestigador {

    private static final Logger log = LoggerFactory.getLogger(VerificadorInvestigadorGrpc.class);

    private final InvestigadorGrpcBlockingStub stub;
    private final CircuitBreaker circuitBreaker;

    public VerificadorInvestigadorGrpc(InvestigadorGrpcBlockingStub stub) {
        this.stub = stub;
        this.circuitBreaker = CircuitBreaker.of("investigadores-service",
                CircuitBreakerConfig.custom()
                        .slidingWindowSize(10)
                        .minimumNumberOfCalls(5)
                        .failureRateThreshold(50)
                        .waitDurationInOpenState(Duration.ofSeconds(10))
                        .build());
        this.circuitBreaker.getEventPublisher().onStateTransition(
                e -> log.warn("Circuit Breaker investigadores-service: {}", e.getStateTransition()));
    }

    @Override
    public boolean existe(String correoInstitucional) {
        try {
            return circuitBreaker.executeSupplier(() -> {
                var request = InvestigadorRequest.newBuilder()
                        .setCorreoInstitucional(correoInstitucional)
                        .build();
                return stub.withDeadlineAfter(5, TimeUnit.SECONDS)
                        .existeInvestigador(request)
                        .getExiste();
            });
        } catch (CallNotPermittedException e) {
            log.warn("Circuit Breaker ABIERTO: se rechaza la llamada sin intentar la red");
            throw new ServicioNoDisponibleException(
                    "investigadores-service no está disponible (circuito abierto)", e);
        } catch (StatusRuntimeException e) {
            throw new ServicioNoDisponibleException(
                    "No se pudo contactar a investigadores-service: " + e.getStatus().getCode(), e);
        }
    }
}
