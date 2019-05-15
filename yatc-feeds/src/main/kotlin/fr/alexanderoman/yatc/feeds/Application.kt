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

package fr.alexanderoman.yatc.feeds

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
import org.springframework.cloud.client.loadbalancer.LoadBalanced
import org.springframework.cloud.stream.annotation.EnableBinding
import org.springframework.cloud.stream.annotation.StreamListener
import org.springframework.cloud.stream.messaging.Sink
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.logout.LogoutFilter
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestTemplate
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.filter.ShallowEtagHeaderFilter
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.nio.file.Files
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.TimeUnit
import java.util.stream.Stream
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.servlet.FilterChain
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.validation.constraints.NotBlank
import kotlin.streams.toList

@SpringBootApplication
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
                .antMatchers("/api/v1/feeds/**/sse").permitAll()
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
class FeedController(private val feedService: FeedService) {
    @Transactional(readOnly = true)
    @GetMapping("/api/v1/feeds/{user}")
    fun getUserFeed(@PathVariable("user") @NotBlank user: String,
                    @RequestParam("page", defaultValue = "0") page: Int): ResponseEntity<FeedResponse> {
        val posts = feedService.getFeed(user, page).toList()
        return ResponseEntity.ok(FeedResponse(posts))
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class FeedEvent(
        val id: Long
)

data class PostSummary(
        val id: Long,
        val author: String,
        val created: OffsetDateTime,
        val content: String
)

data class Post(
        val id: Long,
        val author: User,
        val created: OffsetDateTime,
        val content: String
)

@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class User(
        val id: String,
        val name: String?,
        val avatar: String?
)

data class FeedResponse(
        val posts: List<Post>
)

@Entity
data class FeedItem(
        @Id
        var post: Long,
        @Column(nullable = false)
        var author: String,
        @Column(nullable = false)
        var created: OffsetDateTime
)

interface FeedItemRepository : JpaRepository<FeedItem, Long> {
    @Query("SELECT f FROM FeedItem f WHERE f.author in (:authors) ORDER BY f.created DESC")
    fun findFromAuthors(authors: List<String>, page: Pageable): Stream<FeedItem>
}

@Component
@EnableBinding(Sink::class)
@RestController
class PostListener(private val feedService: FeedService,
                   private val connectionsClientService: ConnectionsClientService) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val emittersByUser: MutableMap<String, CopyOnWriteArraySet<SseEmitter>> = ConcurrentHashMap()

    @StreamListener(Sink.INPUT)
    fun onNewPost(post: PostSummary) {
        logger.info("Received post {} from {}", post.id, post.author)
        feedService.savePost(post)

        // Notify followers there's a new post.
        // We also include post author.
        val followers = mutableListOf(post.author)
        followers.addAll(connectionsClientService.getFollowers(post.author))
        val event = SseEmitter.event().id(post.id.toString()).data(FeedEvent(id = post.id))
        val deadEmitters = mutableSetOf<SseEmitter>()
        followers.forEach { follower ->
            val emitters = emittersByUser[follower]
            if (emitters != null) {
                emitters.forEach { emitter ->
                    try {
                        emitter.send(event)
                    } catch (e: Exception) {
                        deadEmitters.add(emitter)
                    }
                }
                emitters.removeAll(deadEmitters)
            }
        }
    }

    @GetMapping("/api/v1/feeds/{user}/sse")
    fun getFeedEvents(@PathVariable("user") @NotBlank user: String): SseEmitter {
        val emitter = SseEmitter(1000 * 60 * 5L)

        var emitters = emittersByUser[user]
        if (emitters == null) {
            emitters = CopyOnWriteArraySet()
        }
        emitters.add(emitter)
        emittersByUser.put(user, emitters)
        emitter.onCompletion { emitters.remove(emitter) }
        emitter.onTimeout { emitters.remove(emitter) }
        return emitter
    }
}

@Component
class FeedService(private val feedItemRepo: FeedItemRepository,
                  private val postsClientService: PostsClientService,
                  private val connectionsClientService: ConnectionsClientService) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun savePost(post: PostSummary) {
        var item = feedItemRepo.findByIdOrNull(post.id)
        if (item == null) {
            logger.info("Saving post {} to feed: {}", post.id, post.author)
            item = FeedItem(post.id, post.author, post.created)
            feedItemRepo.save(item)
        } else {
            logger.warn("Post already received: {}", post.id)
        }
    }

    @Transactional(readOnly = true)
    fun getFeed(user: String, page: Int): Stream<Post> {
        val authors = connectionsClientService.getFollowings(user).toMutableList()
        logger.debug("User {} is followed by: {}", user, authors)

        // Also include user's post in feed.
        authors.add(user)
        return feedItemRepo.findFromAuthors(authors, PageRequest.of(page, 10))
                .map { postsClientService.getPost(it.post) }
                .filter(Objects::nonNull) as Stream<Post>
    }
}

@Component
class PostsClientService(private val restTemplate: RestTemplate,
                         private val usersClientService: UsersClientService) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun getPost(postId: Long): Post? =
            try {
                logger.debug("Fetching post: {}", postId)
                val url = "//yatc-posts/api/v1/posts/$postId"
                val summary = restTemplate.getForObject(url, PostSummary::class.java)!!
                val author = usersClientService.getUser(summary.author)
                Post(summary.id, author, summary.created, summary.content)
            } catch (e: Exception) {
                logger.warn("Failed to get post: {}", postId, e)
                null
            }
}

@Component
class ConnectionsClientService(private val restTemplate: RestTemplate) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun getFollowings(user: String) =
            try {
                val url = "//yatc-connections/api/v1/connections/$user/followings"
                val ret = restTemplate.getForObject(url, UserFollowings::class.java) ?: UserFollowings()
                ret.followings
            } catch (e: Exception) {
                logger.warn("Failed to get user followings: {}", user, e)
                listOf<String>()
            }

    private data class UserFollowings(
            val followings: List<String> = listOf()
    )

    fun getFollowers(user: String) =
            try {
                val url = "//yatc-connections/api/v1/connections/$user"
                val ret = restTemplate.getForObject(url, UserFollowers::class.java) ?: UserFollowers()
                ret.followers
            } catch (e: Exception) {
                logger.warn("Failed to get user followers: {}", user, e)
                listOf<String>()
            }

    private data class UserFollowers(
            val followers: List<String> = listOf()
    )
}

@Component
class UsersClientService(private val restTemplate: RestTemplate) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun getUser(user: String): User {
        logger.debug("Fetch user details: $user")
        return restTemplate.getForObject("//yatc-users/api/v1/users/$user", User::class.java)
                ?: throw IllegalArgumentException("User not found: $user")
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
