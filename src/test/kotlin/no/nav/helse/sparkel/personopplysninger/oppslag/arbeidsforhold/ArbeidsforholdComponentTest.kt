package no.nav.helse.sparkel.personopplysninger.oppslag.arbeidsforhold

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.testing.withTestApplication
import kotlinx.serialization.json.json
import no.nav.common.KafkaEnvironment
import no.nav.helse.sparkel.personopplysninger.createTestApplicationConfig
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.awaitility.Awaitility
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertTrue
import java.sql.Connection
import java.time.Duration
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

class ArbeidsforholdComponentTest {

    private companion object {

        private val objectMapper = jacksonObjectMapper()
                .registerModule(JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

        private const val username = "srvkafkaclient"
        private const val password = "kafkaclient"
        private const val kafkaApplicationId = "sparkel-personopplysninger-v1"
        private const val behovTopic = "privat-helse-sykepenger-behov"

        private val topics = listOf(behovTopic)
        // Use one partition per topic to make message sending more predictable
        private val topicInfos = topics.map { KafkaEnvironment.TopicInfo(it, partitions = 1) }

        private val embeddedKafkaEnvironment = KafkaEnvironment(
                autoStart = false,
                noOfBrokers = 1,
                topicInfos = topicInfos,
                withSchemaRegistry = false,
                withSecurity = false,
                topicNames = listOf(behovTopic)
        )

        private lateinit var adminClient: AdminClient
        private lateinit var kafkaProducer: KafkaProducer<String, String>
        private lateinit var kafkaConsumer: KafkaConsumer<String, String>

        private lateinit var embeddedServer: ApplicationEngine

        private fun applicationConfig(): Map<String, String> {
            return mapOf(
                    "KAFKA_APP_ID" to kafkaApplicationId,
                    "KAFKA_BOOTSTRAP_SERVERS" to embeddedKafkaEnvironment.brokersURL,
                    "KAFKA_USERNAME" to username,
                    "KAFKA_PASSWORD" to password,
                    "KAFKA_COMMIT_INTERVAL_MS_CONFIG" to "100" // Consumer commit interval must be low because we want quick feedback in the [assertMessageIsConsumed] method
            )
        }

        private fun producerProperties() =
                Properties().apply {
                    put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, embeddedKafkaEnvironment.brokersURL)
                    put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "PLAINTEXT")
                    // Make sure our producer waits until the message is received by Kafka before returning. This is to make sure the tests can send messages in a specific order
                    put(ProducerConfig.ACKS_CONFIG, "all")
                    put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "1")
                    put(ProducerConfig.LINGER_MS_CONFIG, "0")
                    put(ProducerConfig.RETRIES_CONFIG, "0")
                    put(SaslConfigs.SASL_MECHANISM, "PLAIN")
                }

        private fun consumerProperties(): MutableMap<String, Any>? {
            return HashMap<String, Any>().apply {
                put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, embeddedKafkaEnvironment.brokersURL)
                put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "PLAINTEXT")
                put(SaslConfigs.SASL_MECHANISM, "PLAIN")
                put(ConsumerConfig.GROUP_ID_CONFIG, "personComponentTest")
                put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
            }
        }

        @BeforeAll
        @JvmStatic
        internal fun `start embedded environment`() {

            embeddedKafkaEnvironment.start()
            adminClient = embeddedKafkaEnvironment.adminClient ?: Assertions.fail("Klarte ikke få tak i adminclient")
            kafkaProducer = KafkaProducer(producerProperties(), StringSerializer(), StringSerializer())
            kafkaConsumer = KafkaConsumer(consumerProperties(), StringDeserializer(), StringDeserializer())
            kafkaConsumer.subscribe(mutableListOf(behovTopic))
            embeddedServer = embeddedServer(Netty, createTestApplicationConfig(applicationConfig()))
                    .start(wait = false)
        }

        @AfterAll
        @JvmStatic
        internal fun `stop embedded environment`() {
            embeddedServer.stop(1, 1, TimeUnit.SECONDS)
            kafkaConsumer.unsubscribe()
            kafkaConsumer.close()
            adminClient.close()
            embeddedKafkaEnvironment.tearDown()
        }

    }

    @BeforeEach
    fun `create test consumer`() {

    }

    @Test
    fun `send behov og svar med løsning`() {

        val aktørId = "1234"
        val jsonObject =
                json {
                    "@behov" to "Personopplysninger"
                    "aktørId" to aktørId
                }
        sendKafkaMessage(behovTopic, aktørId, jsonObject.toString())

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .untilAsserted {
                    val records = kafkaConsumer.poll(1)
                    records.records(behovTopic).forEach{println(it)}
                    //assertTrue(records.records(behovTopic).filter { it.value().contains("@løsning")}.isNotEmpty())
                    assertTrue(false)
                }

    }

    private fun sendKafkaMessage(topic: String, key: String, message: String) =
            kafkaProducer.send(ProducerRecord(topic, key, message))




/*
    @Test
    fun `en liste over arbeidsforhold skal returneres`() {
        val arbeidsforholdV3 = mockk<ArbeidsforholdV3>()

        val aktørId = AktørId("1831212532188")

        every {
            arbeidsforholdV3.finnArbeidsforholdPrArbeidstaker(match {
                it.ident.ident == aktørId.aktor
            })
        } returns FinnArbeidsforholdPrArbeidstakerResponse().apply {
            with (arbeidsforhold) {
                add(Arbeidsforhold().apply {
                    arbeidsgiver = Organisasjon().apply {
                        orgnummer = "889640782"
                    }
                    arbeidsforholdIDnav = 1234L
                    ansettelsesPeriode = AnsettelsesPeriode().apply {
                        periode = Gyldighetsperiode().apply {
                            this.fom = LocalDate.parse("2019-01-01").toXmlGregorianCalendar()
                        }
                    }
                    with (arbeidsavtale) {
                        add(Arbeidsavtale().apply {
                            fomGyldighetsperiode = LocalDate.parse("2019-01-01").toXmlGregorianCalendar()
                            yrke = Yrker().apply {
                                value = "Butikkmedarbeider"
                            }
                            stillingsprosent = BigDecimal.valueOf(100)
                        })
                    }
                })
                add(Arbeidsforhold().apply {
                    arbeidsgiver = Organisasjon().apply {
                        orgnummer = "995298775"
                        navn = "S. VINDEL & SØNN"
                    }
                    arbeidsforholdIDnav = 5678L
                    ansettelsesPeriode = AnsettelsesPeriode().apply {
                        periode = Gyldighetsperiode().apply {
                            this.fom = LocalDate.parse("2015-01-01").toXmlGregorianCalendar()
                            this.tom = LocalDate.parse("2019-01-01").toXmlGregorianCalendar()
                        }
                    }
                    with (arbeidsavtale) {
                        add(Arbeidsavtale().apply {
                            fomGyldighetsperiode = LocalDate.parse("2015-01-01").toXmlGregorianCalendar()
                            yrke = Yrker().apply {
                                value = "Butikkmedarbeider"
                            }
                            stillingsprosent = BigDecimal.valueOf(100)
                        })
                    }
                })
            }
        }

        every {
            arbeidsforholdV3.hentArbeidsforholdHistorikk(match { request ->
                request.arbeidsforholdId == 1234L
            })
        } returns HentArbeidsforholdHistorikkResponse().apply {
            arbeidsforhold = Arbeidsforhold().apply {
                with(arbeidsavtale) {
                    add(Arbeidsavtale().apply {
                        fomGyldighetsperiode = LocalDate.parse("2019-01-01").toXmlGregorianCalendar()
                        yrke = Yrker().apply {
                            value = "Butikkmedarbeider"
                        }
                        stillingsprosent = BigDecimal.valueOf(100)
                    })
                }
            }
        }

        every {
            arbeidsforholdV3.hentArbeidsforholdHistorikk(match { request ->
                request.arbeidsforholdId == 5678L
            })
        } returns HentArbeidsforholdHistorikkResponse().apply {
            arbeidsforhold = Arbeidsforhold().apply {
                with (arbeidsavtale) {
                    add(Arbeidsavtale().apply {
                        fomGyldighetsperiode = LocalDate.parse("2017-01-01").toXmlGregorianCalendar()
                        yrke = Yrker().apply {
                            value = "Butikkmedarbeider"
                        }
                        stillingsprosent = BigDecimal.valueOf(100)
                    })
                    add(Arbeidsavtale().apply {
                        fomGyldighetsperiode = LocalDate.parse("2016-01-01").toXmlGregorianCalendar()
                        tomGyldighetsperiode = LocalDate.parse("2016-12-31").toXmlGregorianCalendar()
                        yrke = Yrker().apply {
                            value = "Butikkmedarbeider"
                        }
                        stillingsprosent = BigDecimal.valueOf(80)
                    })
                    add(Arbeidsavtale().apply {
                        fomGyldighetsperiode = LocalDate.parse("2015-01-01").toXmlGregorianCalendar()
                        tomGyldighetsperiode = LocalDate.parse("2015-12-31").toXmlGregorianCalendar()
                        yrke = Yrker().apply {
                            value = "Butikkmedarbeider"
                        }
                        stillingsprosent = BigDecimal.valueOf(60)
                    })
                }
            }
        }

        val jwkStub = JwtStub("test issuer")
        val token = jwkStub.createTokenFor("srvpleiepengesokna")

        withTestApplication({mockedSparkel(
                jwtIssuer = "test issuer",
                jwkProvider = jwkStub.stubbedJwkProvider(),
                arbeidstakerService = ArbeidstakerService(
                        arbeidsforholdClient = ArbeidsforholdClient(arbeidsforholdV3),
                        datakvalitetProbe = mockk(relaxed = true)
                ))}) {
            handleRequest(HttpMethod.Get, "/api/arbeidsforhold/${aktørId.aktor}?fom=2017-01-01&tom=2019-01-01") {
                addHeader(HttpHeaders.Accept, ContentType.Application.Json.toString())
                addHeader(HttpHeaders.Authorization, "Bearer $token")
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertJsonEquals(JSONObject(expectedJson_arbeidsforhold), JSONObject(response.content))
            }
        }
    }

    @Test
    fun `en liste over arbeidsgivere skal returneres`() {
        val arbeidsforholdV3 = mockk<ArbeidsforholdV3>()
        val organisasjonV5 = mockk<OrganisasjonV5>()

        val aktørId = AktørId("1831212532188")

        every {
            arbeidsforholdV3.finnArbeidsforholdPrArbeidstaker(match {
                it.ident.ident == aktørId.aktor
            })
        } returns FinnArbeidsforholdPrArbeidstakerResponse().apply {
            with (arbeidsforhold) {
                add(Arbeidsforhold().apply {
                    arbeidsgiver = Organisasjon().apply {
                        orgnummer = "913548221"
                    }
                })
                add(Arbeidsforhold().apply {
                    arbeidsgiver = Organisasjon().apply {
                        orgnummer = "984054564"
                    }
                })
            }
        }

        every {
            organisasjonV5.hentOrganisasjon(match {
                it.orgnummer == "913548221"
            })
        } returns HentOrganisasjonResponse().apply {
            organisasjon = no.nav.tjeneste.virksomhet.organisasjon.v5.informasjon.Virksomhet().apply {
                orgnummer = "913548221"
                navn = UstrukturertNavn().apply {
                    with(navnelinje) {
                        add("EQUINOR AS")
                        add("AVD STATOIL SOKKELVIRKSOMHET")
                    }
                }
            }
        }

        every {
            organisasjonV5.hentOrganisasjon(match {
                it.orgnummer == "984054564"
            })
        } returns HentOrganisasjonResponse().apply {
            organisasjon = no.nav.tjeneste.virksomhet.organisasjon.v5.informasjon.Virksomhet().apply {
                orgnummer = "984054564"
                navn = UstrukturertNavn().apply {
                    with(navnelinje) {
                        add("NAV")
                        add("AVD WALDEMAR THRANES GATE")
                    }
                }
            }
        }

        val jwkStub = JwtStub("test issuer")
        val token = jwkStub.createTokenFor("srvpleiepengesokna")

        withTestApplication({mockedSparkel(
                jwtIssuer = "test issuer",
                jwkProvider = jwkStub.stubbedJwkProvider(),
                arbeidsgiverService = ArbeidsgiverService(
                        arbeidsforholdClient = ArbeidsforholdClient(arbeidsforholdV3),
                        organisasjonService = OrganisasjonService(OrganisasjonClient(organisasjonV5))
                ))}) {
            handleRequest(HttpMethod.Get, "/api/arbeidsgivere/${aktørId.aktor}?fom=2017-01-01&tom=2019-01-01") {
                addHeader(HttpHeaders.Accept, ContentType.Application.Json.toString())
                addHeader(HttpHeaders.Authorization, "Bearer $token")
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertJsonEquals(JSONObject(expectedJson_arbeidsgivere), JSONObject(response.content))
            }
        }
    }

    @Test
    fun `en liste over arbeidsgivere skal returneres selv om organisasjonsoppslag gir feil`() {
        val arbeidsforholdV3 = mockk<ArbeidsforholdV3>()
        val organisasjonV5 = mockk<OrganisasjonV5>()

        val aktørId = AktørId("1831212532188")

        every {
            arbeidsforholdV3.finnArbeidsforholdPrArbeidstaker(match {
                it.ident.ident == aktørId.aktor
            })
        } returns FinnArbeidsforholdPrArbeidstakerResponse().apply {
            with (arbeidsforhold) {
                add(Arbeidsforhold().apply {
                    arbeidsgiver = Organisasjon().apply {
                        orgnummer = "913548221"
                    }
                })
                add(Arbeidsforhold().apply {
                    arbeidsgiver = Organisasjon().apply {
                        orgnummer = "984054564"
                    }
                })
            }
        }

        every {
            organisasjonV5.hentOrganisasjon(match {
                it.orgnummer == "913548221"
            })
        } throws(Exception("SOAP fault"))

        every {
            organisasjonV5.hentOrganisasjon(match {
                it.orgnummer == "984054564"
            })
        } throws(Exception("SOAP fault"))

        val jwkStub = JwtStub("test issuer")
        val token = jwkStub.createTokenFor("srvpleiepengesokna")

        withTestApplication({mockedSparkel(
                jwtIssuer = "test issuer",
                jwkProvider = jwkStub.stubbedJwkProvider(),
                arbeidsgiverService = ArbeidsgiverService(
                        arbeidsforholdClient = ArbeidsforholdClient(arbeidsforholdV3),
                        organisasjonService = OrganisasjonService(OrganisasjonClient(organisasjonV5))
                ))}) {
            handleRequest(HttpMethod.Get, "/api/arbeidsgivere/${aktørId.aktor}?fom=2017-01-01&tom=2019-01-01") {
                addHeader(HttpHeaders.Accept, ContentType.Application.Json.toString())
                addHeader(HttpHeaders.Authorization, "Bearer $token")
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertJsonEquals(JSONObject(expectedJson_arbeidsgivere_uten_navn), JSONObject(response.content))
            }
        }
    }

    @Test
    fun `feil returneres når fom ikke er satt for arbeidsgivere`() {
        val aktørId = AktørId("1831212532188")

        val expected = """
            {
                "feilmelding": "you need to supply query parameter fom and tom"
            }
        """.trimIndent()

        val jwkStub = JwtStub("test issuer")
        val token = jwkStub.createTokenFor("srvpleiepengesokna")

        withTestApplication({mockedSparkel(
                jwtIssuer = "test issuer",
                jwkProvider = jwkStub.stubbedJwkProvider()
        )}) {
            handleRequest(HttpMethod.Get, "/api/arbeidsgivere/${aktørId.aktor}") {
                addHeader(HttpHeaders.Accept, ContentType.Application.Json.toString())
                addHeader(HttpHeaders.Authorization, "Bearer $token")
            }.apply {
                assertEquals(HttpStatusCode.BadRequest, response.status())
                assertJsonEquals(JSONObject(expected), JSONObject(response.content))
            }
        }
    }

    @Test
    fun `feil returneres når tom ikke er satt for arbeidsgivere`() {
        val aktørId = AktørId("1831212532188")

        val expected = """
            {
                "feilmelding": "you need to supply query parameter fom and tom"
            }
        """.trimIndent()

        val jwkStub = JwtStub("test issuer")
        val token = jwkStub.createTokenFor("srvpleiepengesokna")

        withTestApplication({mockedSparkel(
                jwtIssuer = "test issuer",
                jwkProvider = jwkStub.stubbedJwkProvider()
        )}) {
            handleRequest(HttpMethod.Get, "/api/arbeidsgivere/${aktørId.aktor}?fom=2019-01-01") {
                addHeader(HttpHeaders.Accept, ContentType.Application.Json.toString())
                addHeader(HttpHeaders.Authorization, "Bearer $token")
            }.apply {
                assertEquals(HttpStatusCode.BadRequest, response.status())
                assertJsonEquals(JSONObject(expected), JSONObject(response.content))
            }
        }
    }

    @Test
    fun `feil returneres når fom er ugyldig for arbeidsgivere`() {
        val aktørId = AktørId("1831212532188")

        val expected = """
            {
                "feilmelding": "fom must be specified as yyyy-mm-dd"
            }
        """.trimIndent()

        val jwkStub = JwtStub("test issuer")
        val token = jwkStub.createTokenFor("srvpleiepengesokna")

        withTestApplication({mockedSparkel(
                jwtIssuer = "test issuer",
                jwkProvider = jwkStub.stubbedJwkProvider()
        )}) {
            handleRequest(HttpMethod.Get, "/api/arbeidsgivere/${aktørId.aktor}?fom=foo&tom=2019-01-01") {
                addHeader(HttpHeaders.Accept, ContentType.Application.Json.toString())
                addHeader(HttpHeaders.Authorization, "Bearer $token")
            }.apply {
                assertEquals(HttpStatusCode.BadRequest, response.status())
                assertJsonEquals(JSONObject(expected), JSONObject(response.content))
            }
        }
    }

    @Test
    fun `feil returneres når tom er ugyldig for arbeidsgivere`() {
        val aktørId = AktørId("1831212532188")

        val expected = """
            {
                "feilmelding": "tom must be specified as yyyy-mm-dd"
            }
        """.trimIndent()

        val jwkStub = JwtStub("test issuer")
        val token = jwkStub.createTokenFor("srvpleiepengesokna")

        withTestApplication({mockedSparkel(
                jwtIssuer = "test issuer",
                jwkProvider = jwkStub.stubbedJwkProvider()
        )}) {
            handleRequest(HttpMethod.Get, "/api/arbeidsgivere/${aktørId.aktor}?fom=2019-01-01&tom=foo") {
                addHeader(HttpHeaders.Accept, ContentType.Application.Json.toString())
                addHeader(HttpHeaders.Authorization, "Bearer $token")
            }.apply {
                assertEquals(HttpStatusCode.BadRequest, response.status())
                assertJsonEquals(JSONObject(expected), JSONObject(response.content))
            }
        }
    }
*/

    private object TestConsumer {
        private val records = mutableListOf<ConsumerRecord<String, String>>()

        private val kafkaConsumer = KafkaConsumer(consumerProperties(), StringDeserializer(), StringDeserializer()).also {
            it.subscribe(topics)
        }

        fun reset() {
            records.clear()
        }

        fun records(topic: String) = records().filter { it.topic() == topic }

        fun records() =
                records.also { it.addAll(kafkaConsumer.poll(Duration.ofMillis(0))) }

        fun close() {
            kafkaConsumer.unsubscribe()
            kafkaConsumer.close()
        }
    }
}

private val expectedJson_arbeidsgivere = """
{
    "arbeidsgivere": [{
        "orgnummer": "913548221",
        "navn": "EQUINOR AS, AVD STATOIL SOKKELVIRKSOMHET",
        "type": "Virksomhet"
    },{
        "orgnummer": "984054564",
        "navn": "NAV, AVD WALDEMAR THRANES GATE",
        "type": "Virksomhet"
    }]
}
""".trimIndent()

private val expectedJson_arbeidsgivere_uten_navn = """
{
    "arbeidsgivere": [{
        "orgnummer": "913548221",
        "type": "Virksomhet"
    },{
        "orgnummer": "984054564",
        "type": "Virksomhet"
    }]
}
""".trimIndent()

private val expectedJson_arbeidsforhold = """
{
    "arbeidsforhold": [{
        "arbeidsgiver": {
            "identifikator": "889640782",
            "type": "Organisasjon"
        },
        "type": "Arbeidstaker",
        "startdato": "2019-01-01",
        "yrke": "Butikkmedarbeider",
        "arbeidsavtaler": [
            {
                "fom":"2019-01-01",
                "yrke":"Butikkmedarbeider",
                "stillingsprosent":100
            }
        ],
        "permisjon": []
    },{
        "arbeidsgiver": {
            "identifikator": "995298775",
            "type": "Organisasjon"
        },
        "type": "Arbeidstaker",
        "startdato": "2015-01-01",
        "sluttdato": "2019-01-01",
        "yrke": "Butikkmedarbeider",
        "arbeidsavtaler": [
            {
                "fom":"2017-01-01",
                "yrke":"Butikkmedarbeider",
                "stillingsprosent":100
            },
            {
                "fom":"2016-01-01",
                "tom":"2016-12-31",
                "yrke":"Butikkmedarbeider",
                "stillingsprosent":80
            },
            {
                "fom":"2015-01-01",
                "tom":"2015-12-31",
                "yrke":"Butikkmedarbeider",
                "stillingsprosent":60
            }
        ],
        "permisjon": []
    }]
}
""".trimIndent()
