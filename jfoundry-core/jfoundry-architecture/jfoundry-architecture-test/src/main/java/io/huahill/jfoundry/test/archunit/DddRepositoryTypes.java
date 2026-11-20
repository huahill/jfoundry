package io.huahill.jfoundry.test.archunit;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import io.huahill.jfoundry.domain.repository.AggregateRepository;
import org.jmolecules.ddd.types.Repository;

final class DddRepositoryTypes {

    private static final String JFOUNDRY_AGGREGATE_REPOSITORY = AggregateRepository.class.getName();
    private static final String JMOLECULES_REPOSITORY = Repository.class.getName();

    private DddRepositoryTypes() {
    }

    static DescribedPredicate<JavaClass> areRepositoryInterfaces() {
        return new DescribedPredicate<>("DDD repository interfaces") {
            @Override
            public boolean test(JavaClass input) {
                return isRepositoryInterface(input);
            }
        };
    }

    static boolean isRepositoryInterface(JavaClass input) {
        return input.isInterface()
                && (input.isAssignableTo(JMOLECULES_REPOSITORY)
                || input.isAssignableTo(JFOUNDRY_AGGREGATE_REPOSITORY));
    }
}
