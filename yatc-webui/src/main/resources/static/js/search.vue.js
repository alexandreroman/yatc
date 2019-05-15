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

export default {
    props: ["query"],
    data() {
        return {
            users: [],
            refresh: 1
        };
    },
    watch: {
        "$route": function (route) {
            this.query = route.params.query;
        },
        "query": function () {
            this.doSearch();
        }
    },
    mounted: function () {
        this.doSearch();
    },
    methods: {
        doSearch: function () {
            console.log("Showing search results: " + this.query);
            this.users = [];
            const that = this;

            axios.get(apiUrl("/api/v1/search?q=" + this.query)).then(resp3 => {
                resp3.data.users.forEach(userName => {
                    axios.get(apiUrl("/api/v1/" + userName)).then(resp4 => {
                        if (resp4.status === 200) {
                            const u = resp4.data;
                            if (u.name == null) {
                                u.name = u.id;
                            }
                            u.vueId = u.id;
                            that.users.push(u);
                        }
                        this.updateFollowings();
                    });
                });
            });
        },
        follow: function (follower) {
            console.log("Following: " + follower);
            axios.post(apiUrl("/api/v1/" + follower + "/followers/" + user, {})).then(resp => {
                this.updateFollowings();
            });
        },
        unfollow: function (follower) {
            console.log("Unfollowing: " + follower);
            axios.delete(apiUrl("/api/v1/" + follower + "/followers/" + user)).then(resp => {
                this.updateFollowings();
            });
        },
        updateFollowings: function () {
            console.log("Updating followings");
            axios.get(apiUrl("/api/v1/" + user + "/followings")).then(resp => {
                const followings = resp.data.followings;
                console.log("Followings for " + user + ": " + followings);

                this.users.forEach(function (u) {
                    u.followable = user !== u.id;
                    u.following = u.followable && followings.indexOf(u.id) !== -1;
                    u.vueId = u.id + "-" + u.following;
                });
            });
        }
    },
    template: `<div>
<v-list v-for="(user, index) in users" :key="user.vueId">
    <v-divider v-if="index != 0" class="mb-1"/>
    <v-layout row align-center>
        <v-avatar class="ma-2"><v-img :src="user.avatar"/></v-avatar>
        <v-layout column>
            <router-link :to="{ name: 'feed', params: { user: user.id } }" class="author-name"><span v-html="user.name" class="text--primary text-truncate"></span></router-link>
            <router-link :to="{ name: 'feed', params: { user: user.id } }" class="author-id"><span v-html="user.id" class="text--disabled text-truncate"></span></router-link>
        </v-layout>
        <v-layout row justify-end ali v-if="user.followable">
            <v-btn v-if="!user.following" @click.stop="follow(user.id)" flat>Follow</v-btn>
            <v-btn v-if="user.following" @click.stop="unfollow(user.id)" flat>Unfollow</v-btn>
        </v-layout>
    </v-layout>
</v-list>
</div>`
}
