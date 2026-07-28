package org.jfoundry.application.event.externalization;

import java.lang.reflect.Method;

final class PropertyPathReader {

    private PropertyPathReader() {
    }

    static String normalize(String path) {
        if (path.startsWith("#this.")) {
            return path.substring("#this.".length());
        }
        if (path.startsWith("this.")) {
            return path.substring("this.".length());
        }
        return path;
    }

    static Object read(Object root, String path) throws ReflectiveOperationException {
        Object current = root;
        for (String segment : normalize(path).split("\\.")) {
            if (segment.isBlank()) {
                throw new NoSuchMethodException("empty property segment in path " + path);
            }
            if (current == null) {
                return null;
            }
            current = readProperty(current, segment);
        }
        return current;
    }

    private static Object readProperty(Object target, String property) throws ReflectiveOperationException {
        Class<?> type = target.getClass();
        String suffix = Character.toUpperCase(property.charAt(0)) + property.substring(1);
        try {
            return invoke(type.getMethod("get" + suffix), target);
        } catch (NoSuchMethodException ignored) {
            try {
                return invoke(type.getMethod("is" + suffix), target);
            } catch (NoSuchMethodException ignoredAgain) {
                return invoke(type.getMethod(property), target);
            }
        }
    }

    private static Object invoke(Method method, Object target) throws ReflectiveOperationException {
        if (!method.canAccess(target)) {
            method.setAccessible(true);
        }
        return method.invoke(target);
    }
}
