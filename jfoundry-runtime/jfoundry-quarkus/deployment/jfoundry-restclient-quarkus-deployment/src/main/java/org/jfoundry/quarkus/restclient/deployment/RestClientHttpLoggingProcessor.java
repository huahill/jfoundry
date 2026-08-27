package org.jfoundry.quarkus.restclient.deployment;

import java.util.List;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.nativeimage.ServiceProviderBuildItem;

/// Registers JFoundry REST client logging during Quarkus augmentation.
class RestClientHttpLoggingProcessor {

    static final String REST_CLIENT_BUILDER_LISTENER =
            "org.jfoundry.http.quarkus.HttpLoggingRestClientBuilderListener";

    @BuildStep
    ServiceProviderBuildItem registerRestClientBuilderListener() {
        return new ServiceProviderBuildItem(
                "org.eclipse.microprofile.rest.client.spi.RestClientBuilderListener",
                List.of(REST_CLIENT_BUILDER_LISTENER));
    }
}
