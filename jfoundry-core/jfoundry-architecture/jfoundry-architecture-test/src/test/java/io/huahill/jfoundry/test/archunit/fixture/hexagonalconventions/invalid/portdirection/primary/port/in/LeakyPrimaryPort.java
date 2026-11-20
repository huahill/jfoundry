package io.huahill.jfoundry.test.archunit.fixture.hexagonalconventions.invalid.portdirection.primary.port.in;

import io.huahill.jfoundry.architecture.hexagonal.PrimaryPort;
import io.huahill.jfoundry.test.archunit.fixture.hexagonalconventions.invalid.portdirection.primary.port.out.OutboundView;

@PrimaryPort
public interface LeakyPrimaryPort {

    OutboundView load();
}
