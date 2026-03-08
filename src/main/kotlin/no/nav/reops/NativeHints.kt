package no.nav.reops

import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.aot.hint.RuntimeHints

class NativeHintsRegistrar : RuntimeHintsRegistrar {
    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        val reflection = hints.reflection()

        // Register LZ4 / XXHash classes used by Kafka producer (loaded via reflection).
        // These are NOT discovered by Spring AOT processing because Kafka resolves them
        // reflectively at runtime. We also register the static INSTANCE field that
        // lz4-java's factory lookup accesses.
        listOf(
            "net.jpountz.lz4.LZ4Factory",
            "net.jpountz.lz4.LZ4HCJavaSafeCompressor",
            "net.jpountz.xxhash.XXHashFactory",
            "net.jpountz.xxhash.StreamingXXHash32\$Factory",
            "net.jpountz.xxhash.StreamingXXHash64\$Factory",
            "net.jpountz.xxhash.StreamingXXHash32JavaSafe\$Factory",
            "net.jpountz.xxhash.StreamingXXHash64JavaSafe\$Factory",
            "net.jpountz.xxhash.StreamingXXHash32JavaUnsafe\$Factory",
            "net.jpountz.xxhash.StreamingXXHash64JavaUnsafe\$Factory",
            "net.jpountz.lz4.LZ4JavaSafeCompressor",
            "net.jpountz.lz4.LZ4JavaSafeFastDecompressor",
            "net.jpountz.lz4.LZ4JavaSafeSafeDecompressor",
            "net.jpountz.xxhash.XXHash32JavaSafe",
            "net.jpountz.xxhash.XXHash64JavaSafe",
            "net.jpountz.xxhash.StreamingXXHash32JavaSafe",
            "net.jpountz.xxhash.StreamingXXHash64JavaSafe",
        ).forEach { className ->
            val resolvedClass = classLoader?.let { runCatching { Class.forName(className, false, it) }.getOrNull() }
            if (resolvedClass != null) {
                reflection.registerType(
                    resolvedClass,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                    MemberCategory.ACCESS_DECLARED_FIELDS,
                    MemberCategory.ACCESS_PUBLIC_FIELDS,
                )
                runCatching { resolvedClass.getDeclaredField("INSTANCE") }.getOrNull()?.let { field ->
                    reflection.registerField(field)
                }
            }
        }
    }
}
