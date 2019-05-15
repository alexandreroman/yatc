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
    data() {
        return {
            user: []
        };
    },
    mounted: function () {
        const url = apiUrl("/api/v1/me");
        axios.get(url).then(resp => {
            this.user = resp.data;
            if (this.user.name == null) {
                this.user.name = this.user.id;
            }
        });
    },
    template: `<div>
<v-list class="pa-0">
    <v-list-tile avatar>
        <v-list-tile-avatar>
            <v-img :src="user.avatar"/>
        </v-list-tile-avatar>
        <v-list-tile-content>
            <v-list-tile-title>{{ user.name }}</v-list-tile-title>
            <v-list-tile-sub-title>{{ user.id }}</v-list-tile-sub-title>
        </v-list-tile-content>
    </v-list-tile>
</v-list>
</div>`
}
