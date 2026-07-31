package org.jfoundry.autoconfigure.persistence.mybatis;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ImportRuntimeHints;

/// Registers reflection hints required by JFoundry aggregate persistence data objects in a Native Image.
@AutoConfiguration
@ImportRuntimeHints(MybatisPlusNativeRuntimeHints.class)
public class MybatisPlusNativeRuntimeHintsAutoConfiguration {
}
