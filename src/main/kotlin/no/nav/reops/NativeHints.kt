package no.nav.reops

import no.nav.reops.event.Event
import no.nav.reops.event.Response
import no.nav.reops.exception.InvalidEventException
import no.nav.reops.truncation.SanitizedEvent
import no.nav.reops.truncation.TruncationReport
import no.nav.reops.truncation.TruncationViolation
import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.aot.hint.RuntimeHints
import org.springframework.context.annotation.ImportRuntimeHints

@ImportRuntimeHints(NativeHintsRegistrar::class)
class NativeHints

class NativeHintsRegistrar : RuntimeHintsRegistrar {
    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        val reflection = hints.reflection()

        // Register all data classes used for Jackson serialization/deserialization
        listOf(
            Event::class.java,
            Event.Payload::class.java,
            Response::class.java,
            ErrorResponse::class.java,
            TruncationReport::class.java,
            TruncationViolation::class.java,
            SanitizedEvent::class.java,
            InvalidEventException::class.java,
        ).forEach { clazz ->
            reflection.registerType(
                clazz,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.PUBLIC_FIELDS,
            )
        }
    }
}

