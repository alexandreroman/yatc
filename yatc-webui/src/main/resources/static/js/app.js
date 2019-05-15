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

// Set default authorization headers for all requests.
axios.defaults.headers.common["Authorization"] = "Bearer " + authToken;

const routes = [
    {
        name: "home", path: "/", component: () => import("./feed.vue.js"),
        meta: {title: "YATC"}, props: {"user": user}
    },
    {
        name: "feed", path: "/feed/:user", component: () => import("./feed.vue.js"),
        meta: {title: "Feed - YATC"}, props: true
    },
    {
        name: "search", path: "/search/:query", component: () => import("./search.vue.js"),
        meta: {title: "Search - YATC"}, props: true
    },
    {
        name: "about", path: "/about", component: () => import("./about.vue.js"),
        meta: {title: "About this app - YATC"}
    },
    {
        name: "settings", path: "/settings", component: () => import("./settings.vue.js"),
        meta: {title: "Settings - YATC"}
    },
    {
        name: "lists", path: "/lists", component: () => import("./todo.vue.js"),
        meta: {title: "Lists - YATC"}
    },
    {
        name: "bookmarks", path: "/bookmarks", component: () => import("./todo.vue.js"),
        meta: {title: "Bookmarks - YATC"}
    },
    {
        name: "notifications", path: "/notifications", component: () => import("./todo.vue.js"),
        meta: {title: "Notifications - YATC"}
    },
    {
        name: "messages", path: "/messages", component: () => import("./todo.vue.js"),
        meta: {title: "Messages - YATC"}
    },
];

const router = new VueRouter({
    routes
});
router.beforeEach((to, from, next) => {
    document.title = to.meta.title;
    next();
});

const app = new Vue({
    router: router,
    data: {
        drawer: null,
        dark: "true" === localStorage.dark,
        searchQuery: "",
        menubar: [
            {title: "Home", icon: "home", name: "home"},
            {title: "Notifications", icon: "notifications", name: "notifications"},
            {title: "Messages", icon: "message", name: "messages"},
            {title: "Bookmarks", icon: "bookmark", name: "bookmarks"},
            {title: "Lists", icon: "list", name: "lists"},
            {title: "Settings", icon: "settings", name: "settings"},
            {title: "About", icon: "question_answer", name: "about"}
        ],
    },
    mounted: function () {
        console.log("API Gateway: " + apiUrl("/"));
        document.getElementById("app").style.display = "block";
    },
    components: {
        Profile: () => import("./profile.vue.js")
    },
    watch: {
        "$route": function (route) {
            if (!("query" in route.params)) {
                this.searchQuery = "";
            }
        }
    },
    methods: {
        onSearch: function () {
            if (this.searchQuery == null || this.searchQuery === "") {
                this.$router.push("/");
            } else {
                const q = this.searchQuery;
                console.log("Searching: " + q);
                this.$router.push({name: "search", params: {query: q}});
            }
        }
    }
}).$mount("#app");
