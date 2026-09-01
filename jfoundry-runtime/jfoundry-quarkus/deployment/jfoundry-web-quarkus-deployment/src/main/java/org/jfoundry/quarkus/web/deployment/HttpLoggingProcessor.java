package org.jfoundry.quarkus.web.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.resteasy.reactive.spi.ContainerRequestFilterBuildItem;
import io.quarkus.resteasy.reactive.spi.ContainerResponseFilterBuildItem;
import io.quarkus.resteasy.reactive.spi.ReaderInterceptorBuildItem;
import io.quarkus.resteasy.reactive.spi.WriterInterceptorBuildItem;
import jakarta.ws.rs.Priorities;
import org.jfoundry.http.quarkus.HttpLoggingProvider;
import org.jfoundry.http.quarkus.RequestCorrelationProvider;

/// Registers JFoundry HTTP logging providers during Quarkus augmentation.
class HttpLoggingProcessor {

    private static final int PRIORITY = Priorities.USER - 200;

    @BuildStep
    ContainerRequestFilterBuildItem registerRequestCorrelationRequestFilter() {
        return new ContainerRequestFilterBuildItem.Builder(RequestCorrelationProvider.class.getName())
                .setPreMatching(true)
                .setPriority(RequestCorrelationProvider.PRIORITY)
                .setRegisterAsBean(true)
                .build();
    }

    @BuildStep
    ContainerResponseFilterBuildItem registerRequestCorrelationResponseFilter() {
        return new ContainerResponseFilterBuildItem.Builder(RequestCorrelationProvider.class.getName())
                .setPriority(RequestCorrelationProvider.PRIORITY)
                .setRegisterAsBean(true)
                .build();
    }

    @BuildStep
    ContainerRequestFilterBuildItem registerServerRequestFilter() {
        return new ContainerRequestFilterBuildItem.Builder(HttpLoggingProvider.class.getName())
                .setPreMatching(true)
                .setPriority(PRIORITY)
                .setRegisterAsBean(true)
                .build();
    }

    @BuildStep
    ContainerResponseFilterBuildItem registerServerResponseFilter() {
        return new ContainerResponseFilterBuildItem.Builder(HttpLoggingProvider.class.getName())
                .setPriority(PRIORITY)
                .setRegisterAsBean(true)
                .build();
    }

    @BuildStep
    ReaderInterceptorBuildItem registerRequestBodyInterceptor() {
        return new ReaderInterceptorBuildItem.Builder(HttpLoggingProvider.class.getName())
                .setPriority(PRIORITY)
                .setRegisterAsBean(true)
                .build();
    }

    @BuildStep
    WriterInterceptorBuildItem registerResponseBodyInterceptor() {
        return new WriterInterceptorBuildItem.Builder(HttpLoggingProvider.class.getName())
                .setPriority(PRIORITY)
                .setRegisterAsBean(true)
                .build();
    }
}
