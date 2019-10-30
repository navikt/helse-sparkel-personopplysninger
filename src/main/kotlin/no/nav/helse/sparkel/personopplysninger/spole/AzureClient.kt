package no.nav.helse.sparkel.personopplysninger.spole

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.kittinunf.fuel.httpPost
import org.slf4j.LoggerFactory
import java.time.Instant

class AzureClient(private val tenantUrl: String, private val clientId: String, private val clientSecret: String) {

    companion object {
        private val log = LoggerFactory.getLogger(AzureClient::class.java)
        private val tjenestekallLog = LoggerFactory.getLogger("tjenestekall")
        private val objectMapper = ObjectMapper()
    }

    private val tokencache: MutableMap<String, Token> = mutableMapOf()

    fun getToken(scope: String) =
            tokencache[scope]
                    ?.takeUnless(Token::isExpired)
                    ?: fetchToken(scope)
                            .also { token ->
                                tokencache[scope] = token
                            }


    private fun fetchToken(scope: String): Token {
        val (_, _, result) = "$tenantUrl/oauth2/v2.0/token".httpPost(
                listOf(
                        "client_id" to clientId,
                        "client_secret" to clientSecret,
                        "scope" to scope,
                        "grant_type" to "client_credentials"
                )
        ).responseString()

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


            return Token(
                    tokenType = jsonNode["token_type"].textValue(),
                    expiresIn = jsonNode["expires_in"].longValue(),
                    accessToken = jsonNode["access_token"].textValue()
            )
        }

        throw error?.exception ?: RuntimeException("unexpected error")
    }

    data class Token(val tokenType: String, val expiresIn: Long, val accessToken: String) {
        companion object {
            private const val leewaySeconds = 60
        }

        private val expiresOn = Instant.now().plusSeconds(expiresIn - leewaySeconds)

        fun isExpired(): Boolean = expiresOn.isBefore(Instant.now())
    }
}
