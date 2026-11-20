package io.huahill.jfoundry.test.archunit.fixture.hexagonalconventions.valid.capability.claim.query.port.in;

import io.huahill.jfoundry.architecture.hexagonal.PrimaryPort;
import io.huahill.jfoundry.test.archunit.fixture.hexagonalconventions.valid.capability.claim.query.view.ClaimView;

@PrimaryPort
public interface FindClaimUseCase {

    ClaimView find();
}
