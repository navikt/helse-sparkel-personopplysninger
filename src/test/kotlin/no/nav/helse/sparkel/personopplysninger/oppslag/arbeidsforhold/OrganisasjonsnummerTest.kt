package no.nav.helse.sparkel.personopplysninger.oppslag.arbeidsforhold

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class OrganisasjonsnummerTest {

    @Test
    fun `ugyldig organisasjonsnummer skal kaste exception`() {
        assertThrows(IllegalArgumentException::class.java) {
            Organisasjonsnummer("889640781")
        }
    }

    @Test
    fun `gyldig organisasjonsnummer`() {
        assertEquals("889640782", Organisasjonsnummer("889640782").value)
    }
}
