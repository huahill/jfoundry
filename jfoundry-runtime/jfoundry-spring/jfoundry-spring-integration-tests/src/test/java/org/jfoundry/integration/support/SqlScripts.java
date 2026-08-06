package org.jfoundry.integration.support;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;

public final class SqlScripts {

    private SqlScripts() {
    }

    public static void run(DataSource dataSource, String... classpathScripts) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        for (String classpathScript : classpathScripts) {
            populator.addScript(new ClassPathResource(classpathScript));
        }
        populator.execute(dataSource);
    }
}
