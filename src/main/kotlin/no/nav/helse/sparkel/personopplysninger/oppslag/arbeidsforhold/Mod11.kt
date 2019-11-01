package no.nav.helse.sparkel.personopplysninger.oppslag.arbeidsforhold

object Mod11 {
    private fun vekttall(i: Int) = 2 + i % 6

    fun kontrollsiffer(number: String) =
            number.reversed().mapIndexed { i, char ->
                if (!Character.isDigit(char)) {
                    throw IllegalArgumentException("$char is not a digit")
                }
                Character.getNumericValue(char) * vekttall(i)
            }.sum().let(Mod11::kontrollsifferFraSum)

    private fun kontrollsifferFraSum(sum: Int) = sum.rem(11).let { rest ->
        when (rest) {
            0 -> '0'
            1 -> '-'
            else -> "${11 - rest}"[0]
        }
    }
}
