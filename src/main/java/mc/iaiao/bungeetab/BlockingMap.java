package mc.iaiao.bungeetab;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

public class BlockingMap<K, V> {
    private final Map<K, BlockingQueue<V>> map = new ConcurrentHashMap<>();

    private synchronized BlockingQueue<V> ensureQueueExists(K key) {
        return map.computeIfAbsent(key, k -> new ArrayBlockingQueue<>(1));
    }

    public boolean put(K key, V value) {
        BlockingQueue<V> queue = ensureQueueExists(key);
        return queue.offer(value);
    }

    public V get(K key) {
        BlockingQueue<V> queue = ensureQueueExists(key);
        return queue.poll();
    }
}