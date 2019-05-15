/*
 * Copyright (c) 2019 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.alexandreroman.yatc.users

import com.github.tomakehurst.wiremock.client.WireMock.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit4.SpringRunner
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@ActiveProfiles("test")
@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWireMock(port = 9876)
class ApplicationTests {
    @LocalServerPort
    private var webPort: Int = 0
    @Autowired
    private lateinit var restTemplate: TestRestTemplate
    @Value("\${github.endpoint}")
    private lateinit var githubEndpoint: String

    @Test
    fun contextLoads() {
        stubFor(get(urlEqualTo("/greetings")).willReturn(ok("Hello world")))
        assertThat(restTemplate.getForEntity("$githubEndpoint/greetings", String::class.java).body)
                .isEqualTo("Hello world")
    }

    private fun url(path: String) = "http://localhost:$webPort$path"

    @Test
    fun testActuatorHealth() {
        assertThat(restTemplate.getForEntity(url("/actuator/health"), String::class.java).statusCode).isEqualTo(HttpStatus.OK)
    }

    @Test
    fun testGetUser() {
        stubFor(get(urlEqualTo("/users/foo")).willReturn(okJson("""
            { "avatar_url": "http://avatar.com", "name": "Foo" }
            """)))

        val resp = restTemplate.getForEntity(url("/api/v1/users/foo"), User::class.java)
        assertThat(resp.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(resp.hasBody()).isTrue()
        val user = resp.body!!
        assertThat(user.id).isEqualTo("foo")
        assertThat(user.name).isEqualTo("Foo")
        assertThat(user.avatar).isEqualTo("http://avatar.com")
    }

    @Test
    fun testGetUserWithCaching() {
        val cacheDate = Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.RFC_1123_DATE_TIME)
        stubFor(get(urlEqualTo("/users/bar")).willReturn(okJson("""
            { "avatar_url": "http://avatar.com", "name": "Bar" }
            """).withHeader(HttpHeaders.LAST_MODIFIED, cacheDate)
                .withHeader(HttpHeaders.CACHE_CONTROL, "max-age=86400")))
        restTemplate.getForEntity(url("/api/v1/users/bar"), User::class.java)
        restTemplate.getForEntity(url("/api/v1/users/bar"), User::class.java)
        verify(1, getRequestedFor(urlEqualTo("/users/bar")))
    }

    @Test
    fun testUnknownUser() {
        stubFor(get(urlEqualTo("/users/johndoe")).willReturn(notFound()))
        assertThat(restTemplate.getForEntity("/api/v1/users/johndoe", User::class.java).statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun testUserTest() {
        val user = restTemplate.getForObject("/api/v1/users/me", User::class.java)
        assertThat(user.id).isEqualTo("test")
        assertThat(user.name).isNull()
        assertThat(user.avatar).isNull()
    }
}
