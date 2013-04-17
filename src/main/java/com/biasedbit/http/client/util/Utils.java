package com.biasedbit.http.client.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Utils {

    public static void ensureValue(boolean condition, String description, Object... args)
            throws IllegalArgumentException {
        if (!condition) throw new IllegalArgumentException(String.format(description, args));
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
