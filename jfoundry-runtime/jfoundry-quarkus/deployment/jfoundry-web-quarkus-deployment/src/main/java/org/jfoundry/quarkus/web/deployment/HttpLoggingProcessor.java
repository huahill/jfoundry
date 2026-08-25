package org.jfoundry.quarkus.web.deployment;

import java.util.List;
import java.util.Optional;

import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.nativeimage.ServiceProviderBuildItem;
import io.quarkus.resteasy.reactive.spi.ContainerRequestFilterBuildItem;
import io.quarkus.resteasy.reactive.spi.ContainerResponseFilterBuildItem;
import io.quarkus.resteasy.reactive.spi.ReaderInterceptorBuildItem;
import io.quarkus.resteasy.reactive.spi.WriterInterceptorBuildItem;
import jakarta.ws.rs.Priorities;
import org.jfoundry.http.quarkus.HttpLoggingProvider;

/// Registers JFoundry HTTP logging providers during Quarkus augmentation.
class HttpLoggingProcessor {

    private static final int PRIORITY = Priorities.USER - 200;

    static final String REST_CLIENT_BUILDER_LISTENER =
            "org.jfoundry.http.quarkus.HttpLoggingRestClientBuilderListener";

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

    @BuildStep
    Optional<ServiceProviderBuildItem> registerRestClientBuilderListener(Capabilities capabilities) {
        if (capabilities.isMissing(Capability.REST_CLIENT)
                && capabilities.isMissing(Capability.REST_CLIENT_REACTIVE)) {
            return Optional.empty();
        }
        return Optional.of(new ServiceProviderBuildItem(
                "org.eclipse.microprofile.rest.client.spi.RestClientBuilderListener",
                List.of(REST_CLIENT_BUILDER_LISTENER)));
    }
}
