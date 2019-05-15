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

package fr.alexandreroman.yatc.search

import org.assertj.core.api.Assertions.assertThat
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.cloud.stream.annotation.EnableBinding
import org.springframework.cloud.stream.messaging.Source
import org.springframework.messaging.support.MessageBuilder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit4.SpringRunner

@ActiveProfiles("test")
@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableBinding(Source::class)
class ApplicationTests {
    @LocalServerPort
    private var webPort = 0
    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var source: Source

    private fun url(path: String) = "http://localhost:$webPort$path"

    @Test
    fun contextLoads() {
    }

    @Test
    @Ignore("Test binder does not work")
    fun testEventListener() {
        source.output().send(MessageBuilder.withPayload(Post(author = "johndoe")).build())
        val resp = restTemplate.getForObject(url("/api/v1/search?q=johndoe"), SearchResponse::class.java)
        assertThat(resp.query).isEqualTo("johndoe")
        assertThat(resp.posts).isEmpty()
        assertThat(resp.users.size).isEqualTo(1)
        assertThat(resp.users).contains("johndoe")
    }
}
