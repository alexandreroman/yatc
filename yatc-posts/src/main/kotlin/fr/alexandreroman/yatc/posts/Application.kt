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

import com.fasterxml.jackson.annotation.JsonInclude
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.cloud.client.discovery.EnableDiscoveryClient
import org.springframework.cloud.client.loadbalancer.LoadBalanced
import org.springframework.cloud.stream.annotation.EnableBinding
import org.springframework.cloud.stream.messaging.Source
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.CrudRepository
import org.springframework.http.CacheControl
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory
import org.springframework.messaging.support.MessageBuilder
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.logout.LogoutFilter
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.client.RestTemplate
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.filter.ShallowEtagHeaderFilter
import java.nio.file.Files
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit
import java.util.stream.Stream
import javax.persistence.*
import javax.servlet.FilterChain
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.validation.constraints.Positive

@SpringBootApplication
@EnableDiscoveryClient
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}

@Configuration
class SecurityConfig(private val env: Environment) : WebSecurityConfigurerAdapter() {
    @Value("\${security.tokenSecret}")
    private lateinit var tokenSecret: String

    override fun configure(http: HttpSecurity?) {
        http!!.authorizeRequests()
                .antMatchers("/api/**").authenticated()
                .anyRequest().permitAll().and()
                .httpBasic().disable().formLogin().disable()
                .sessionManagement().disable()
                .csrf().disable()
                .addFilterBefore(object : OncePerRequestFilter() {
                    override fun doFilterInternal(req: HttpServletRequest, resp: HttpServletResponse, chain: FilterChain) {
                        val authToken = req.getHeader(HttpHeaders.AUTHORIZATION)
                        if (authToken != null && authToken.startsWith("Bearer ")) {
                            val jwt = authToken.replaceFirst("Bearer ".toRegex(), "")
                            val jwtClaims = Jwts.parser()
                                    .setSigningKey(Keys.hmacShaKeyFor(tokenSecret.toByteArray()))
                                    .parseClaimsJws(jwt).body
                            val userId = jwtClaims.subject
                            SecurityContextHolder.getContext().authentication =
                                    UsernamePasswordAuthenticationToken(userId, jwt, emptyList())
                        } else if ("test" in env.activeProfiles) {
                            SecurityContextHolder.getContext().authentication =
                                    UsernamePasswordAuthenticationToken("test", null, listOf())
                        }
                        chain.doFilter(req, resp)
                    }
                }, LogoutFilter::class.java)
    }
}

@RestController
class PostsController(private val postsService: PostsService) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @PostMapping("/api/v1/posts")
    fun newPost(@RequestBody req: NewPostRequest) =
            try {
                if (req.content.isBlank()) {
                    throw IllegalArgumentException("Empty content")
                }
                val post = postsService.createPost(req.author, req.content.trim())
                ResponseEntity.ok(post)
            } catch (e: IllegalArgumentException) {
                logger.warn("Cannot create post from user: {}", req.author, e)
                ResponseEntity.badRequest().body("Cannot create post")
            }

    @GetMapping("/api/v1/posts/{post}")
    fun getPost(@PathVariable("post") @Positive postId: Long) =
            try {
                val post = postsService.getPost(postId)
                ResponseEntity.ok()
                        .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                        .lastModified(post.created.toInstant().toEpochMilli())
                        .body(post)
            } catch (e: IllegalArgumentException) {
                ResponseEntity.notFound().build<Any>()
            }
}

data class NewPostRequest(
        val author: String,
        val content: String
)

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
data class PagedPostsResponse(
        val page: Int = 0,
        val posts: List<PostResponse> = emptyList()
)

@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class PostResponse(
        val id: Long,
        val author: User,
        val created: OffsetDateTime,
        val content: String
)

@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class User(
        val id: String,
        val name: String? = null,
        val avatar: String? = null
)

@Entity
data class Post(
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        var id: Long? = null,
        @Column(nullable = false, length = 64)
        var author: String,
        @Column(nullable = false)
        var created: OffsetDateTime = OffsetDateTime.now(),
        @Column(nullable = false, length = 256)
        var content: String
)

interface PostRepository : CrudRepository<Post, Long> {
    fun findByAuthorOrderByCreatedDesc(author: String, pageable: Pageable): Stream<Post>
}

@Component
@EnableBinding(Source::class)
class PostsService(
        private val postRepo: PostRepository,
        private val usersClientService: UsersClientService,
        private val source: Source) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun createPost(author: String, content: String): Post {
        if (!usersClientService.userExists(author)) {
            throw IllegalArgumentException("User not found: $author")
        }
        var post = Post(author = author, content = content)
        post = postRepo.save(post)
        logger.info("Created post from {}: {}", author, post.id)

        source.output().send(MessageBuilder.withPayload(post).build())

        return post
    }

    @Transactional(readOnly = true)
    fun getPost(post: Long) = postRepo.findById(post).orElseThrow { IllegalArgumentException("Unknown post: $post") }
}

@Component
class UsersClientService(private val restTemplate: RestTemplate) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun userExists(user: String) =
            try {
                getUser(user)
                true
            } catch (e: Exception) {
                false
            }

    fun getUser(user: String) =
            try {
                restTemplate.getForObject("//yatc-users/api/v1/users/$user", User::class.java)
            } catch (e: Exception) {
                logger.warn("Failed to get user: {}", user, e)
                null
            }
}

@Configuration
class AppConfig {
    @Bean
    @LoadBalanced
    fun restTemplate(httpClient: OkHttpClient) =
            RestTemplate(OkHttp3ClientHttpRequestFactory(httpClient))

    @Bean
    fun authInterceptor() = Interceptor { chain ->
        val auth = SecurityContextHolder.getContext().authentication
        if (auth != null && auth.isAuthenticated && auth.credentials != null) {
            // Forward authentication token to HTTP calls.
            val newReq = chain.request()
                    .newBuilder().addHeader(HttpHeaders.AUTHORIZATION, "Bearer ${auth.credentials}")
                    .build()
            chain.proceed(newReq)
        } else {
            chain.proceed(chain.request())
        }
    }

    @Bean
    fun httpClient(authInterceptor: Interceptor): OkHttpClient =
            // Create an HTTP client with caching support.
            OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .addInterceptor(authInterceptor)
                    .cache(Cache(Files.createTempDirectory("httpcache-").toFile(), 10 * 1024 * 1024))
                    .build()

    @Bean
    fun filterRegistrationBean(): FilterRegistrationBean<ShallowEtagHeaderFilter> {
        val eTagFilter = ShallowEtagHeaderFilter()
        val registration = FilterRegistrationBean(eTagFilter)
        registration.addUrlPatterns("/*")
        return registration
    }
}
