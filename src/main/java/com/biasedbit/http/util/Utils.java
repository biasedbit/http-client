package com.biasedbit.http.util;

/**
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
public class Utils {
    public static void ensureValue(boolean condition, String description)
        throws IllegalArgumentException {
        if (!condition) throw new IllegalArgumentException(description);
    }

    public static void ensureState(boolean condition, String description)
        throws IllegalStateException {
        if (!condition) throw new IllegalStateException(description);
    }

    public static void ensureState(boolean condition, String description, Object... args)
            throws IllegalStateException {
        if (!condition) throw new IllegalStateException(String.format(description, args));
    }

    public static String string(Object... args) {
        StringBuilder builder = new StringBuilder();
        for (Object arg : args) builder.append(arg.toString());

        return builder.toString();
    }
}