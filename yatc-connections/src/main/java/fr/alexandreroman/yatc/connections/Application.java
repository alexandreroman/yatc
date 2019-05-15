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

package fr.alexandreroman.yatc.connections;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Cache;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.persistence.*;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.NotBlank;
import java.io.IOException;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.util.Arrays.asList;

@SpringBootApplication
@EnableDiscoveryClient
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}

@Configuration
class AppConfig {
    @Bean
    @LoadBalanced
    RestTemplate restTemplate(OkHttpClient httpClient) {
        // Define a client side load-balanced REST client.
        return new RestTemplate(new OkHttp3ClientHttpRequestFactory(httpClient));
    }

    @Bean
    Interceptor authInterceptor() {
        return chain -> {
            final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getCredentials() != null) {
                final Request newReq = chain.request().newBuilder()
                        .addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + auth.getCredentials())
                        .build();
                return chain.proceed(newReq);
            }
            return chain.proceed(chain.request());
        };
    }

    @Bean
    OkHttpClient httpClient(Interceptor authInterceptor) throws IOException {
        return new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .addInterceptor(authInterceptor)
                // Set up response cache for this HTTP client.
                .cache(new Cache(Files.createTempDirectory("httpcache-").toFile(), 10 * 1024 * 1024))
                .build();
    }
}

@Configuration
@RequiredArgsConstructor
class SecurityConfig extends WebSecurityConfigurerAdapter {
    private final Environment env;

    @Value("${security.tokenSecret}")
    private String tokenSecret;

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.authorizeRequests()
                .antMatchers("/api/v1/connections/**").permitAll()
                .antMatchers("/api/**").authenticated()
                .anyRequest().permitAll().and()
                .httpBasic().disable().formLogin().disable()
                .sessionManagement().disable()
                .csrf().disable()
                .addFilterBefore(new OncePerRequestFilter() {
                    @Override
                    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain) throws ServletException, IOException {
                        final String authToken = req.getHeader(HttpHeaders.AUTHORIZATION);
                        if (authToken != null && authToken.startsWith("Bearer ")) {
                            final String jwt = authToken.replaceFirst("Bearer ", "");
                            final Claims claims = Jwts.parser()
                                    .setSigningKey(Keys.hmacShaKeyFor(tokenSecret.getBytes()))
                                    .parseClaimsJws(jwt).getBody();
                            final String userId = claims.getSubject();
                            SecurityContextHolder.getContext().setAuthentication(
                                    new UsernamePasswordAuthenticationToken(userId, jwt, Collections.emptyList()));
                        } else if (asList(env.getActiveProfiles()).contains("test")) {
                            SecurityContextHolder.getContext().setAuthentication(
                                    new UsernamePasswordAuthenticationToken("test", null, Collections.emptyList()));
                        }
                        chain.doFilter(req, resp);
                    }
                }, LogoutFilter.class);
    }
}

@RestController
@RequiredArgsConstructor
class ConnectionsController {
    private final ConnectionsService connectionsService;

    @GetMapping("/api/v1/connections/{user}")
    public ResponseEntity<?> getFollowers(@PathVariable("user") @NotBlank String user) {
        try {
            return ResponseEntity.ok(new FollowersResponse(connectionsService.getFollowers(user)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/api/v1/connections/{user}/followings")
    public ResponseEntity<?> getFollowings(@PathVariable("user") @NotBlank String user) {
        try {
            return ResponseEntity.ok(new FollowingsResponse(connectionsService.getFollowings(user)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/api/v1/connections/{user}/{follower}")
    public ResponseEntity<?> addConnection(@PathVariable("user") @NotBlank String user,
                                           @PathVariable("follower") @NotBlank String follower) {
        try {
            connectionsService.addConnection(user, follower);
            return ResponseEntity.ok(new FollowersResponse(connectionsService.getFollowers(user)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/api/v1/connections/{user}/{follower}")
    public FollowersResponse deleteConnection(@PathVariable("user") @NotBlank String user,
                                              @PathVariable("follower") @NotBlank String follower) {
        connectionsService.deleteConnection(user, follower);
        return new FollowersResponse(connectionsService.getFollowers(user));
    }
}

@Data
@AllArgsConstructor
@NoArgsConstructor
class FollowersResponse {
    private List<String> followers;
}

@Data
@AllArgsConstructor
@NoArgsConstructor
class FollowingsResponse {
    private List<String> followings;
}

@Component
@RequiredArgsConstructor
@Slf4j
class ConnectionsService {
    private final ConnectionRepository connRepo;
    private final UsersClientService usersClientService;

    @Transactional(readOnly = true)
    public List<String> getFollowers(String user) {
        if (!usersClientService.userExists(user)) {
            throw new IllegalArgumentException("User not found: " + user);
        }
        return connRepo.getFollowers(user);
    }

    @Transactional(readOnly = true)
    public List<String> getFollowings(String user) {
        if (!usersClientService.userExists(user)) {
            throw new IllegalArgumentException("User not found: " + user);
        }
        return connRepo.getFollowings(user);
    }

    @Transactional
    public void addConnection(String user, String follower) {
        if (!usersClientService.userExists(user)) {
            throw new IllegalArgumentException("User not found: " + user);
        }
        if (!usersClientService.userExists(follower)) {
            throw new IllegalArgumentException("User not found: " + follower);
        }

        Connection conn = connRepo.findByUserAndFollower(user, follower);
        if (conn == null) {
            conn = new Connection();
            conn.setUser(user);
            conn.setFollower(follower);
            log.info("Adding connection between {} and his/her follower {}", user, follower);
            connRepo.saveAndFlush(conn);
        }
    }

    @Transactional
    public void deleteConnection(String user, String follower) {
        log.info("Deleting connection between {} and his/her follower {}", user, follower);
        connRepo.deleteByUserAndFollower(user, follower);
    }
}

@Data
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"user", "follower"}))
class Connection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(length = 64, nullable = false)
    private String user;
    @Column(length = 64, nullable = false)
    private String follower;
    @Column(nullable = false)
    private OffsetDateTime created = OffsetDateTime.now();
}

interface ConnectionRepository extends JpaRepository<Connection, Long> {
    @Query("SELECT c.follower FROM Connection c WHERE c.user=:user ORDER BY c.created")
    List<String> getFollowers(String user);

    @Query("SELECT c.user FROM Connection c WHERE c.follower=:user ORDER BY c.created")
    List<String> getFollowings(String user);

    Connection findByUserAndFollower(String user, String follower);

    void deleteByUserAndFollower(String user, String follower);
}

@Component
@Slf4j
@RequiredArgsConstructor
class UsersClientService {
    private final RestTemplate restTemplate;

    public boolean userExists(String user) {
        // Connect to users microservice to check if an user exists.
        // Here we use a RestTemplate instance to connect to this API endpoint:
        // Ribbon is used under the hood to automatically load balance requests
        // across running instances.
        try {
            log.debug("Asking users service if user exists: {}", user);
            // Service name will be resolved by Ribbon, selecting a running instance.
            restTemplate.getForObject("//yatc-users/api/v1/users/" + user, UserResponse.class);
            log.debug("User found: {}", user);
            return true;
        } catch (Exception e) {
            // Users API endpoint may not be available: just return a default value.
            log.warn("Failed to check if user exists: {}", user, e);
            return false;
        }
    }

    @Data
    private static class UserResponse {
        private String id;
    }
}
