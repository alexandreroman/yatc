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
            "dark": "true" === localStorage.dark,
        }
    },
    watch: {
        dark(newValue) {
            console.log("Updating dark mode: " + newValue);
            localStorage.dark = newValue;
            app.$data.dark = newValue;
        }
    },
    template: `<div>
<v-layout column class="ma-3">
<v-card>
<v-card-title class="headline"><v-layout column>
Settings<v-divider/></v-layout>
</v-card-title>
<v-card-text>
<v-layout column>
<v-switch v-model="dark" click="updateTheme" label="Dark mode"></v-switch>
</v-layout>
</v-card-text>
</v-card>
</v-layout>
</div>`
};
