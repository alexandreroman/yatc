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

package fr.alexandreroman.yatc.webui

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit4.SpringRunner

@ActiveProfiles("test")
@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationTests {
    @LocalServerPort
    private var webPort: Int = 0
    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Test
    fun contextLoads() {
    }

    @Test
    fun testActuatorHealth() {
        assertThat(restTemplate.getForEntity("http://localhost:$webPort/actuator/health", String::class.java).statusCode).isEqualTo(HttpStatus.OK)
    }

    @Test
    fun testWebjars() {
        arrayOf(
                "/webjars/vue/dist/vue.js",
                "/webjars/vue-router/dist/vue-router.js",
                "/webjars/vuetify/dist/vuetify.js",
                "/webjars/vuetify/dist/vuetify.css",
                "/webjars/moment/moment.js",
                "/webjars/axios/dist/axios.js"
        ).forEach {
            val depUrl = "http://localhost:$webPort/$it"
            val resp = restTemplate.getForEntity(depUrl, String::class.java)
            assertThat(resp.body).isNotEmpty()
            assertThat(resp.statusCode).isEqualTo(HttpStatus.OK)
        }
    }
}
