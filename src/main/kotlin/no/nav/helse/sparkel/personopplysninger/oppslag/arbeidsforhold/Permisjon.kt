package no.nav.helse.sparkel.personopplysninger.oppslag.arbeidsforhold

import java.math.BigDecimal
import java.time.LocalDate

data class Permisjon(val fom: LocalDate,
                     val tom: LocalDate?,
                     val permisjonsprosent: BigDecimal,
                     val årsak: String)
