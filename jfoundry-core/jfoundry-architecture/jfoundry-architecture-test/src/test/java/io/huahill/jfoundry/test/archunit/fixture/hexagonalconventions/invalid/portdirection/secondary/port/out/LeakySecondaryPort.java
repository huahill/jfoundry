package io.huahill.jfoundry.test.archunit.fixture.hexagonalconventions.invalid.portdirection.secondary.port.out;

import io.huahill.jfoundry.architecture.hexagonal.SecondaryPort;
import io.huahill.jfoundry.test.archunit.fixture.hexagonalconventions.invalid.portdirection.secondary.port.in.InboundRequest;

@SecondaryPort
public interface LeakySecondaryPort {

    void store(InboundRequest request);
}
