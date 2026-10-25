package client.wexbooks.reader.core

/** Both count and weight are capped; an oversized entry never displaces the working set. */
class BoundedCache<K, V>(
    private val maxEntries: Int,
    private val maxWeight: Int,
    private val weigh: (V) -> Int,
) {
    data class Stats(
        val hits: Long,
        val misses: Long,
        val entries: Int,
        val weight: Int,
        val evictions: Long,
    )

    private val entries = LinkedHashMap<K, V>(16, 0.75f, true)
    private var weight = 0
    private var hits = 0L
    private var misses = 0L
    private var evictions = 0L

    init {
        require(maxEntries > 0 && maxWeight > 0)
    }

    @Synchronized fun get(key: K): V? = entries[key].also { if (it == null) misses++ else hits++ }

    @Synchronized
    fun put(key: K, value: V) {
        val newWeight = weigh(value).also { require(it >= 0) }
        if (newWeight > maxWeight) return
        entries.remove(key)?.let { weight -= weigh(it) }
        entries[key] = value
        weight += newWeight
        while (entries.size > maxEntries || weight > maxWeight) {
            val eldest = entries.entries.iterator()
            weight -= weigh(eldest.next().value)
            eldest.remove()
            evictions++
        }
    }

    @Synchronized
    fun clear() {
        entries.clear()
        weight = 0
    }

    @Synchronized fun stats() = Stats(hits, misses, entries.size, weight, evictions)
}
