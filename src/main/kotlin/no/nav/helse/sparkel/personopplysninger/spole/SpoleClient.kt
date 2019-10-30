package no.nav.helse.sparkel.personopplysninger.spole

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.github.kittinunf.fuel.httpGet
import org.slf4j.LoggerFactory
import java.time.LocalDate

class SpoleClient(private val baseUrl: String, private val accesstokenScope: String, private val azureClient: AzureClient) {

    companion object {
        private val objectMapper = ObjectMapper()
        private val log = LoggerFactory.getLogger(SpoleClient::class.java)
        private val tjenestekallLog = LoggerFactory.getLogger("tjenestekall")
    }

    fun hentSykepengeperioder(aktørId: String): Sykepengeperioder {
        val (_, _, result) = "$baseUrl/sykepengeperioder/${aktørId}".httpGet()
                .header(mapOf(
                        "Authorization" to "Bearer ${azureClient.getToken(accesstokenScope).accessToken}",
                        "Accept" to "application/json"
                ))
                .responseString()

        val (jsonString, error) = result

        jsonString?.also { response ->
            tjenestekallLog.info(response)
        }?.let { response ->
            objectMapper.readTree(response)
        }?.also { jsonNode ->
            if (jsonNode.has("error")) {
                log.error("${jsonNode["error_description"].textValue()}: $jsonNode")
                throw RuntimeException("error from the azure token endpoint: ${jsonNode["error_description"].textValue()}")
            }

            return Sykepengeperioder(
                    aktørId = jsonNode["aktør_id"].textValue(),
                    perioder = (jsonNode["perioder"] as ArrayNode).map { periodeJson ->
                        Periode(
                                fom = LocalDate.parse(periodeJson["fom"].textValue()),
                                tom = LocalDate.parse(periodeJson["tom"].textValue()),
                                grad = periodeJson["grad"].textValue()
                        )
                    }
            )
        }
        throw error?.exception ?: RuntimeException("unexpected error")
    }
}

data class Sykepengeperioder(val aktørId: String, val perioder: List<Periode>)
data class Periode(val fom: LocalDate, val tom: LocalDate, val grad: String)
