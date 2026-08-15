package com.bitworksmc.headdb.core.util;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

public class Utils {

    private static final AtomicInteger poolNumber = new AtomicInteger(1);
    private static final Pattern INVALID_NAMESPACED_KEY_CHARACTERS = Pattern.compile("[^a-z0-9._/-]");

    /**
     * Splits the given list into sublists of the given chunkSize.
     *
     * @param <T>       the type of elements in the list
     * @param list      the list to split
     * @param chunkSize the maximum size of each chunk
     * @return a list of sublists, each at most chunkSize elements long
     */
    public static <T> List<List<T>> chunk(List<T> list, int chunkSize) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be positive");
        }
        List<List<T>> chunks = new ArrayList<>();
        int size = list.size();
        for (int i = 0; i < size; i += chunkSize) {
            int end = Math.min(size, i + chunkSize);
            // subList returns a view, if you want an independent copy use new ArrayList<>(...)
            chunks.add(new ArrayList<>(list.subList(i, end)));
        }
        return chunks;
    }

    public static boolean matches(@Nullable String text, @Nullable String query) {
        if (text == null || query == null) {
            return false;
        }
        String normalizedQuery = query.trim();
        return !normalizedQuery.isEmpty()
                && text.toLowerCase(Locale.ROOT).contains(normalizedQuery.toLowerCase(Locale.ROOT));
    }

    public static String normalizeNamespacedKey(String key) {
        return INVALID_NAMESPACED_KEY_CHARACTERS
                .matcher(key.toLowerCase(Locale.ROOT))
                .replaceAll("_");
    }


    public static ExecutorService executorService(int nThreads, String namePrefix) {
        ThreadFactory factory = r -> {
            Thread t = new Thread(r);
            t.setName(namePrefix + " #" + poolNumber.getAndIncrement());
            t.setDaemon(true);
            return t;
        };
        return nThreads > 1 ? Executors.newFixedThreadPool(nThreads, factory) : Executors.newSingleThreadExecutor(factory);
    }


}
