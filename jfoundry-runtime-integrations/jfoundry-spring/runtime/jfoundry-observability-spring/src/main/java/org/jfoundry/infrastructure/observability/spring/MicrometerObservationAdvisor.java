package org.jfoundry.infrastructure.observability.spring;

import org.jfoundry.application.inbox.InboxMessageProcessor;
import org.jfoundry.application.lock.LockExecutor;
import org.jfoundry.application.outbox.OutboxDispatcher;
import org.jfoundry.application.outbox.OutboxRecorder;
import org.springframework.aop.support.DefaultBeanFactoryPointcutAdvisor;
import org.springframework.aop.support.StaticMethodMatcherPointcut;

import java.lang.reflect.Method;

/// Spring AOP advisor that resolves the Micrometer interceptor lazily from the BeanFactory.
public final class MicrometerObservationAdvisor extends DefaultBeanFactoryPointcutAdvisor {

    /// Bean name used by this advisor to resolve its interceptor.
    public static final String INTERCEPTOR_BEAN_NAME = "jfoundryMicrometerObservationInterceptor";

    public MicrometerObservationAdvisor() {
        setPointcut(new FrameworkOperationPointcut());
        setAdviceBeanName(INTERCEPTOR_BEAN_NAME);
    }

    private static final class FrameworkOperationPointcut extends StaticMethodMatcherPointcut {

        @Override
        public boolean matches(Method method, Class<?> targetClass) {
            if (MicrometerObservedOperation.class.isAssignableFrom(targetClass)) {
                return false;
            }
            return switch (method.getName()) {
                case "append" -> OutboxRecorder.class.isAssignableFrom(targetClass);
                case "dispatch" -> OutboxDispatcher.class.isAssignableFrom(targetClass);
                case "executeOnce" -> InboxMessageProcessor.class.isAssignableFrom(targetClass);
                case "execute" -> LockExecutor.class.isAssignableFrom(targetClass);
                default -> false;
            };
        }
    }
}
