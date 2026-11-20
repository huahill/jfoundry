package io.huahill.jfoundry.test.archunit.fixture.hexagonalconventions.valid.capability.claim.query.port.out;

import io.huahill.jfoundry.architecture.hexagonal.SecondaryPort;
import io.huahill.jfoundry.test.archunit.fixture.hexagonalconventions.valid.capability.claim.query.view.ClaimView;

@SecondaryPort
public interface ClaimReader {

    ClaimView find();
}
