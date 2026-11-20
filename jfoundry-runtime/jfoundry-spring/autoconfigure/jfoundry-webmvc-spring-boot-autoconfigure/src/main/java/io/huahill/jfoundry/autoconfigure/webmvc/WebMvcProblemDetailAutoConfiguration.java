package io.huahill.jfoundry.autoconfigure.webmvc;

import io.huahill.jfoundry.problem.CompositeProblemMapper;
import io.huahill.jfoundry.problem.ProblemMapper;
import io.huahill.jfoundry.problem.ProblemMessageResolver;
import io.huahill.jfoundry.webmvc.spring.MessageSourceProblemMessageResolver;
import io.huahill.jfoundry.webmvc.spring.ProblemDetailsExceptionHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.ArrayList;
import java.util.List;

/// Auto-configuration for JFoundry Spring MVC Problem Details exception responses.
/// Problem messages resolve through Spring's {@link MessageSource} first and the framework catalog second,
/// using the request locale from Spring's locale context.
@AutoConfiguration(beforeName = "org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = {
        "org.springframework.web.servlet.DispatcherServlet",
        "io.huahill.jfoundry.webmvc.spring.ProblemDetailsExceptionHandler"
})
public class WebMvcProblemDetailAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ProblemDetailsExceptionHandler.class)
    public ProblemDetailsExceptionHandler jfoundryProblemDetailsExceptionHandler(
            List<ProblemMapper> problemMappers, ObjectProvider<MessageSource> messageSource) {
        List<ProblemMessageResolver> resolvers = new ArrayList<>();
        MessageSource source = messageSource.getIfAvailable();
        if (source != null) {
            resolvers.add(new MessageSourceProblemMessageResolver(source));
        }
        resolvers.add(ProblemMessageResolver.framework());
        ProblemMessageResolver messages = ProblemMessageResolver.composite(resolvers.toArray(ProblemMessageResolver[]::new));
        return new ProblemDetailsExceptionHandler(
                new CompositeProblemMapper(problemMappers, messages, LocaleContextHolder::getLocale),
                messages);
    }
}
