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
    filters: {
        moment: function (date) {
            return moment(date).startOf("second").fromNow();
        }
    },
    props: ["user"],
    data() {
        return {
            feed: [],
            userFeed: "",
            createDialog: false,
            statusText: "",
            eventSource: null
        };
    },
    mounted: function () {
        this.userFeed = this.user;
        this.setupEventSource();
        this.loadData();
    },
    watch: {
        "$route": function (route) {
            if (!route.params.user || route.params.user === "") {
                this.userFeed = user;
            } else {
                this.userFeed = route.params.user;
            }
            this.loadData();
        },
    },
    methods: {
        loadData: function () {
            console.log("Fetching user feed: " + this.userFeed);
            const url = apiUrl("/api/v1/" + this.userFeed + "/feed");
            axios.get(url).then(resp => {
                (this.feed = resp.data.posts)
            });
        },
        setupEventSource: function () {
            if (this.eventSource != null) {
                this.eventSource.close();
            }
            this.eventSource = new EventSource(apiUrl("/api/v1/" + this.userFeed + "/feed/sse"));
            this.eventSource.onmessage = () => {
                this.loadData();
            };
            this.eventSource.onerror = () => {
                setTimeout(this.setupEventSource, 1000 * 10);
            };
        },
        postStatus: function () {
            const data = {
                content: this.statusText,
                author: user
            };
            this.createDialog = false;
            this.statusText = "";
            console.log("Posting status: " + data.content);
            axios.post(apiUrl("/api/v1/status"), data);
        }
    },
    template: `<div>
<v-list v-for="(item, index) in feed" :key="item.id">
<v-divider v-if="index != 0" class="mb-2"/>
<v-layout row class="pt-1">
<v-avatar class="ma-2"><v-img :src="item.author.avatar"/></v-avatar>
<v-layout justify-start column fill-height>
<v-flex>
<router-link :to="{ name: 'feed', params: { user: item.author.id } }" class="author-name"><span v-html="item.author.name" class="text--primary text-truncate"></span></router-link>
<router-link :to="{ name: 'feed', params: { user: item.author.id } }" class="author-id"><span v-html="item.author.id" class="text--disabled text-truncate"></span></router-link>
<span>·</span>
<span class="post-created">{{ item.created | moment }}</span>
</v-flex>
<v-list-tile-content style="white-space: pre-line;" v-html="item.content"></v-list-tile-content>
</v-layout>
</v-layout>
</v-list>
<v-dialog v-model="createDialog" width="400">
<template v-slot:activator="{ on }">
<v-btn v-on="on" fab right bottom large fixed color="primary" class="ma-2"><v-icon>create</v-icon></v-btn>
</template>
<v-card>
<v-card-text>
<v-textarea label="What's on your mind?" v-model="statusText" autofocus></v-textarea>
</v-card-text>
<v-card-actions>
<v-spacer></v-spacer>
<v-btn flat @click="postStatus()">Post</v-btn>
</v-card-actions>
</v-card>
</v-dialog>
</div>`
}
