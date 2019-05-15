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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlGroup;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ActiveProfiles("test")
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ApplicationTests {
    @LocalServerPort
    private int webPort;
    @Autowired
    private TestRestTemplate restTemplate;

    @MockBean
    private UsersClientService usersClientService;

    @Test
    public void contextLoads() {
    }

    private final String url(String path) {
        return "http://localhost:" + webPort + path;
    }

    @Test
    public void testGetFollowersUnknown() {
        given(usersClientService.userExists(anyString())).willReturn(false);

        final ResponseEntity<FollowersResponse> resp = restTemplate.getForEntity(url("/api/v1/connections/unknown"), FollowersResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    public void testAddConnectionFromScratch() {
        given(usersClientService.userExists(anyString())).willReturn(true);

        final ResponseEntity<FollowersResponse> resp = restTemplate.postForEntity(url("/api/v1/connections/newuser/randomfollower"), null, FollowersResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getFollowers().size()).isEqualTo(1);
        assertThat(resp.getBody().getFollowers().get(0)).isEqualTo("randomfollower");

        final ResponseEntity<FollowersResponse> resp2 = restTemplate.postForEntity(url("/api/v1/connections/newuser/randomfollower2"), null, FollowersResponse.class);
        assertThat(resp2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp2.getBody().getFollowers().size()).isEqualTo(2);
        assertThat(resp2.getBody().getFollowers()).contains("randomfollower", "randomfollower2");
    }

    @Test
    @SqlGroup({@Sql(value = "classpath:populatedb.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)})
    public void testGetFollowers() {
        given(usersClientService.userExists(anyString())).willReturn(true);

        final ResponseEntity<FollowersResponse> resp = restTemplate.getForEntity(url("/api/v1/connections/johndoe"), FollowersResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getFollowers().size()).isEqualTo(2);
        assertThat(resp.getBody().getFollowers()).contains("jojobizarre", "laracroft");

        final ResponseEntity<FollowersResponse> resp2 = restTemplate.getForEntity(url("/api/v1/connections/lonelyguy"), FollowersResponse.class);
        assertThat(resp2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp2.getBody().getFollowers().size()).isEqualTo(1);
        assertThat(resp2.getBody().getFollowers()).contains("lonelyguy");

        final ResponseEntity<FollowingsResponse> resp3 = restTemplate.getForEntity(url("/api/v1/connections/laracroft/followings"), FollowingsResponse.class);
        assertThat(resp3.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp3.getBody().getFollowings().size()).isEqualTo(1);
        assertThat(resp3.getBody().getFollowings()).contains("johndoe");
    }

    @Test
    @SqlGroup({@Sql(value = "classpath:populatedb.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)})
    public void testAddConnection() {
        given(usersClientService.userExists(anyString())).willReturn(true);

        final ResponseEntity<FollowersResponse> resp = restTemplate.postForEntity(url("/api/v1/connections/lonelyguy/shadowman"), null, FollowersResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getFollowers().size()).isEqualTo(2);
        assertThat(resp.getBody().getFollowers()).contains("lonelyguy", "shadowman");
    }

    @Test
    public void testAddConnectionUnknownUser() {
        given(usersClientService.userExists("randomuser")).willReturn(false);
        given(usersClientService.userExists("randomfollower")).willReturn(true);

        final ResponseEntity<FollowersResponse> resp = restTemplate.postForEntity(url("/api/v1/connections/randomuser/randomfollower"), null, FollowersResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    public void testAddConnectionUnknownFollower() {
        given(usersClientService.userExists("randomuser")).willReturn(true);
        given(usersClientService.userExists("randomfollower")).willReturn(false);

        final ResponseEntity<FollowersResponse> resp = restTemplate.postForEntity(url("/api/v1/connections/randomuser/randomfollower"), null, FollowersResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @SqlGroup({@Sql(value = "classpath:populatedb.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)})
    public void testRemoveFollowers() {
        given(usersClientService.userExists(anyString())).willReturn(true);

        restTemplate.delete(url("/api/v1/connections/johndoe/laracroft"));
        final ResponseEntity<FollowersResponse> resp = restTemplate.getForEntity(url("/api/v1/connections/johndoe"), FollowersResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getFollowers()).containsExactlyInAnyOrder("jojobizarre");
    }
}
