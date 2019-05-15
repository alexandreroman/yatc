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

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cloud.client.discovery.EnableDiscoveryClient
import org.springframework.cloud.stream.annotation.EnableBinding
import org.springframework.cloud.stream.annotation.StreamListener
import org.springframework.cloud.stream.messaging.Sink
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.logout.LogoutFilter
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.filter.OncePerRequestFilter
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
class SearchController(private val searchService: SearchService) {
    @GetMapping("/api/v1/search")
    fun search(@RequestParam("q") @NotBlank query: String) =
            SearchResponse(query = query,
                    users = searchService.search(query).users)
}

data class SearchResponse(
        val query: String,
        val users: Set<String> = setOf(),
        val posts: Set<Long> = setOf()
)

@Component
@EnableBinding(Sink::class)
class SearchService(private val userRepository: UserRepository) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    @StreamListener(Sink.INPUT)
    fun onPost(post: Post) {
        logger.info("Received new post from {}", post.author)
        userRepository.put(post.author.toLowerCase())
    }

    @Transactional(readOnly = true)
    fun search(query: String): SearchResults {
        // TODO include posts content in search
        return SearchResults(users = userRepository.search(query.toLowerCase()))
    }
}

data class Post(
        val author: String
)

data class SearchResults(
        val users: Set<String> = setOf(),
        val posts: Set<Long> = setOf()
)

@Component
class UserRepository(private val redisTemplate: StringRedisTemplate) {
    fun put(user: String) = redisTemplate.opsForValue().setIfAbsent(user, "1")
    fun search(user: String) = redisTemplate.keys("*$user*")
}
