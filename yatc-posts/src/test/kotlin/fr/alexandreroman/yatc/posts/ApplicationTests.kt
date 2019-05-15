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

package fr.alexandreroman.yatc.posts

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.cloud.stream.messaging.Source
import org.springframework.cloud.stream.test.binder.MessageCollector
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit4.SpringRunner

@ActiveProfiles("test")
@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationTests {
    @LocalServerPort
    private var webPort = 0
    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @MockBean
    private lateinit var usersClientService: UsersClientService
    @Autowired
    private lateinit var messageCollector: MessageCollector
    @Autowired
    private lateinit var source: Source

    private fun url(path: String) = "http://localhost:$webPort$path"

    @Test
    fun contextLoads() {
    }

    @Test
    fun testCreatePostUnknownAuthor() {
        given(usersClientService.userExists(anyString())).willReturn(false)

        val req = NewPostRequest("johndoe", "Hello")
        val resp = restTemplate.postForEntity(url("/api/v1/posts"), req, String::class.java)
        assertThat(resp.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun testCreatePost() {
        given(usersClientService.userExists(anyString())).willReturn(true)
        given(usersClientService.getUser("joe")).willReturn(User("joe"))

        val req = NewPostRequest("joe", "Hello")
        val resp = restTemplate.postForEntity(url("/api/v1/posts"), req, Post::class.java)
        assertThat(resp.statusCode).isEqualTo(HttpStatus.OK)
        val post = resp.body!!
        assertThat(post.content).isEqualTo("Hello")
        assertThat(post.created).isNotNull()
        assertThat(post.id).isEqualTo(1)

        val msg = messageCollector.forChannel(source.output()).take()
        val om = jacksonObjectMapper().registerModule(JavaTimeModule())
        val receivedPost = om.readValue(msg.payload.toString(), Post::class.java)
        assertThat(receivedPost.id).isEqualTo(1)
        assertThat(receivedPost.author).isEqualTo("joe")
        assertThat(receivedPost.created).isNotNull()
        assertThat(receivedPost.content).isEqualTo("Hello")

        val resp2 = restTemplate.getForEntity(url("/api/v1/posts/1"), Post::class.java)
        assertThat(resp2.statusCode).isEqualTo(HttpStatus.OK)
        val post2 = resp2.body!!
        assertThat(post2).isEqualTo(post)
    }

    @Test
    fun testGetPostUnknown() {
        val resp = restTemplate.getForEntity(url("/api/v1/posts/123"), String::class.java)
        assertThat(resp.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }
}
