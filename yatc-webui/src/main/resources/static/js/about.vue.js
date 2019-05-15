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
    template: `<div>
<v-layout column class="ma-3">
<v-card>
<v-card-title class="headline"><v-layout column>
About this app<v-divider/></v-layout>
</v-card-title>
<v-card-text>
<v-layout column>
<span>This app is a naive implementation of a Twitter clone, using cloud-native technologies.</span>
<v-layout>
<a href="https://spring.io/projects/spring-boot"><v-img class="ma-1" alt="Spring Boot" width="50" height="50" src="/images/spring-boot.logo.png"/></a>
<a href="https://spring.io/projects/spring-cloud"><v-img class="ma-1" alt="Spring Cloud" width="50" height="50" src="/images/spring-cloud.logo.png"/></a>
</v-layout>
<v-layout align-start column class="pt-3 pb-4">
<v-btn href="https://github.com/alexandreroman/yatc">Get source code</v-btn>
</v-layout>
<v-layout align-end column>
<span>Copyright &copy; 2019 <a href="https://pivotal.io">Pivotal Software Inc.</a></span>
</v-layout>
</v-layout>
</v-card-text>
</v-card>
</v-layout>
</div>`
};
