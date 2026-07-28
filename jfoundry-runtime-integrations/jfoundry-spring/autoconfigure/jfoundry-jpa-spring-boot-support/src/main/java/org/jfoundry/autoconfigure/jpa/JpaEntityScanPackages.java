package org.jfoundry.autoconfigure.jpa;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.domain.EntityScanPackages;

import java.util.LinkedHashSet;
import java.util.Set;

/// Registers a framework entity package without discarding application entity scan packages.
public final class JpaEntityScanPackages {

    private JpaEntityScanPackages() {
    }

    public static void register(BeanDefinitionRegistry registry, Class<?> entityClass) {
        Set<String> packageNames = new LinkedHashSet<>();
        if (registry instanceof BeanFactory beanFactory && AutoConfigurationPackages.has(beanFactory)) {
            packageNames.addAll(AutoConfigurationPackages.get(beanFactory));
        }
        packageNames.add(entityClass.getPackageName());
        EntityScanPackages.register(registry, packageNames);
    }
}
