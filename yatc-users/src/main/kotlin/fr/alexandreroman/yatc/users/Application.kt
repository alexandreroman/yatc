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

import com.fasterxml.jackson.annotation.JsonInclude
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.cloud.client.discovery.EnableDiscoveryClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.http.CacheControl
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.logout.LogoutFilter
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import org.springframework.web.filter.OncePerRequestFilter
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import javax.servlet.FilterChain
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.validation.constraints.NotBlank

@SpringBootApplication
@EnableDiscoveryClient
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}

@Configuration
@ConfigurationProperties("github")
class AppProperties {
    /**
     * GitHub REST API endpoint URL.
     */
    lateinit var endpoint: String
    /**
     * Set to `true` to enable request/response debugging.
     */
    var debug: Boolean = false
}

@Configuration
class AppConfig {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun restTemplate(httpClient: OkHttpClient): RestTemplate {
        val restTemplate = RestTemplate(OkHttp3ClientHttpRequestFactory(httpClient))

        // Override supported content types for JSON responses, as GitHub sometimes returns
        // JSON responses while using "application/octet-stream" as content type.
        val jsonConverter = MappingJackson2HttpMessageConverter()
        jsonConverter.supportedMediaTypes = listOf(MediaType.APPLICATION_OCTET_STREAM, MediaType.APPLICATION_JSON)
        restTemplate.messageConverters.add(jsonConverter)
        return restTemplate
    }

    @Bean
    fun httpClient(props: AppProperties): OkHttpClient {
        // Use a cache for GitHub responses, to limit the number of sent requests
        // and stay below the hourly quota.
        val cache = Cache(Files.createTempDirectory("httpcache-").toFile(), 10 * 1024 * 1014)
        val builder = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .cache(cache)
        if (props.debug) {
            logger.info("Enabling GitHub client debugging")
            val debugInterceptor = HttpLoggingInterceptor()
            debugInterceptor.level = HttpLoggingInterceptor.Level.BODY
            builder.addInterceptor(debugInterceptor)
        }
        return builder.build()
    }
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
                            val userName = jwtClaims["name"] as String
                            val userAvatar = jwtClaims["avatar"] as String
                            val user = User(userId, userName, userAvatar)
                            SecurityContextHolder.getContext().authentication =
                                    UsernamePasswordAuthenticationToken(user, jwt, emptyList())
                        } else if ("test" in env.activeProfiles) {
                            SecurityContextHolder.getContext().authentication =
                                    UsernamePasswordAuthenticationToken(User(id = "test"), null, listOf())
                        }
                        chain.doFilter(req, resp)
                    }
                }, LogoutFilter::class.java)
    }
}

@RestController
class UsersController(private val usersService: GitHubUsersService) {
    @GetMapping("/api/v1/users/{id}")
    fun getUser(@PathVariable("id") @NotBlank id: String): ResponseEntity<Any> {
        val user = usersService.getUser(id) ?: return ResponseEntity.notFound().build()
        // Tell clients they can safely cache this response.
        // We want to stay under the hourly quota set by GitHub.
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(30, TimeUnit.MINUTES))
                .body(user)
    }

    @GetMapping("/api/v1/users/me")
    fun getMe(@AuthenticationPrincipal user: User) = user
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class User(
        val id: String,
        val name: String? = null,
        val avatar: String? = null
)

@Component
class GitHubUsersService(
        private val restTemplate: RestTemplate,
        private val props: AppProperties,
        private val successfulUserRequestsCounter: Counter,
        private val failedUserRequestsCounter: Counter) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun getUser(id: String): User? {
        val url = "${props.endpoint}/users/$id"
        return try {
            // Send a request to GitHub REST API to fetch user details.
            logger.info("Fetching GitHub user details: {}", url)
            val resp = restTemplate.getForEntity(url, GitHubUserResponse::class.java)
            val githubUser = resp.body!!
            successfulUserRequestsCounter.increment()
            User(id, githubUser.name, githubUser.avatar_url)
        } catch (e: RestClientException) {
            failedUserRequestsCounter.increment()
            logger.warn("Failed to get GitHub user details: $id", e)
            null
        }
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class GitHubUserResponse(
        val avatar_url: String?,
        val name: String?
)

@Configuration
class MetricsConfig {
    // Define custom app metrics to track user requests.

    @Bean
    fun successfulUserRequestsCounter(registry: MeterRegistry) =
            registry.counter("user_successful_requests")

    @Bean
    fun failedUserRequestsCounter(registry: MeterRegistry) =
            registry.counter("user_failed_requests")
}
