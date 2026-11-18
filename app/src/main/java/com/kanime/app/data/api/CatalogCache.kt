package com.kanime.app.data.api

import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe in-memory TTL cache matching Anivault-API's 3-tier cache buckets:
 * - MAPPING: 24h (ID mappings, anime details, art, cast, themes, pictures, recommendations)
 * - EPISODES: 1h (episode lists, episode stills, seasonal catalog, AniList search)
 * - STREAM: 5m (transient search queries like MAL text search)
 *
 * Supports negative caching (storing explicit null hits) so missing TMDB/Kitsu entries
 * are not repeatedly re-fetched during the TTL window.
 */
object CatalogCache {

    enum class Bucket(val ttlMs: Long) {
        MAPPING(24L * 60L * 60L * 1000L),
        EPISODES(60L * 60L * 1000L),
        STREAM(5L * 60L * 1000L)
    }

    private data class Entry(
        val value: Any?,
        val expiresAt: Long
    )

    sealed interface CacheLookup<out T> {
        data object Miss : CacheLookup<Nothing>
        data class Hit<T>(val value: T) : CacheLookup<T>
    }

    private val store = ConcurrentHashMap<String, Entry>()

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): T? {
        val entry = store[key] ?: return null
        if (System.currentTimeMillis() > entry.expiresAt) {
            store.remove(key)
            return null
        }
        return entry.value as? T
    }

    /**
     * Distinguishes between a cache miss and a cached null value (negative cache).
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> lookup(key: String): CacheLookup<T?> {
        val entry = store[key] ?: return CacheLookup.Miss
        if (System.currentTimeMillis() > entry.expiresAt) {
            store.remove(key)
            return CacheLookup.Miss
        }
        return CacheLookup.Hit(entry.value as? T)
    }

    fun set(key: String, value: Any?, bucket: Bucket = Bucket.MAPPING) {
        if (store.size > 2000) {
            pruneExpired()
        }
        store[key] = Entry(value, System.currentTimeMillis() + bucket.ttlMs)
    }

    fun remove(key: String) {
        store.remove(key)
    }

    fun clear() {
        store.clear()
    }

    fun size(): Int = store.size

    private fun pruneExpired() {
        val now = System.currentTimeMillis()
        val iter = store.entries.iterator()
        while (iter.hasNext()) {
            if (iter.next().value.expiresAt < now) {
                iter.remove()
            }
        }
    }
}
