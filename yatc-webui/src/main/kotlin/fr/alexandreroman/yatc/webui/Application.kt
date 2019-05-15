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

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.security.servlet.PathRequest
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.util.*

@SpringBootApplication
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}

@Configuration
@ConfigurationProperties(prefix = "api")
class ApiConfig(
        // API gateway URL.
        var gateway: String = "http://localhost:9000"
)

@EnableWebSecurity
@Configuration
class SecurityConfig : WebSecurityConfigurerAdapter() {
    override fun configure(http: HttpSecurity?) {
        http!!
                .authorizeRequests()
                .requestMatchers(EndpointRequest.toAnyEndpoint()).permitAll()
                .requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()
                .antMatchers("/*.json").permitAll()
                .anyRequest().authenticated()
                .and()
                .oauth2Login()
                .permitAll()
    }
}

@Configuration
@ConfigurationProperties(prefix = "security")
class AuthConfig {
    var tokenLifetime: Long = 604800000
        set(tokenLifetime) {
            field = this.tokenLifetime
        }
    var tokenSecret = "ThisIsMySuperSecretTokenWhichNeedsToBeLongEnough"
        set(tokenSecret) {
            field = this.tokenSecret
        }
}

@RestController
class ConfigController(private val apiConfig: ApiConfig,
                       private val authConfig: AuthConfig) {
    // Frontend needs to know what is the API gateway URL.
    // Here we use a simple trick to generate a Javascript file containing this value.

    @GetMapping("/js/config.js", produces = ["application/javascript"])
    fun getConfig(@AuthenticationPrincipal user: OAuth2User): String {
        val userId = user.attributes.get("login") as String
        val jwt = Jwts.builder()
                .setId(UUID.randomUUID().toString())
                .setSubject(userId)
                .claim("name", user.attributes.get("name"))
                .claim("avatar", user.attributes.get("avatar_url"))
                .setExpiration(Date(System.currentTimeMillis() + authConfig.tokenLifetime))
                .signWith(Keys.hmacShaKeyFor(authConfig.tokenSecret.toByteArray()), SignatureAlgorithm.HS256)
                .compact()

        return """
                const apiGateway = "${apiConfig.gateway}";
                const authToken = "$jwt";
                const user = "$userId";
                function apiUrl(path) {
                    if (!path.startsWith("/")) {
                        path = "/" + path;
                    }
                    return apiGateway + path;
                }
            """.trimIndent()
    }
}

@RestController
class UserController {
    @GetMapping("/user")
    fun user(@AuthenticationPrincipal oauth2User: OAuth2User) = oauth2User
}
