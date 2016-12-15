# TurboScript Plugin

This plugin integrates Jenkins with the Turbo.net platform. It allows Jenkins to build Turbo.net images and push them to the Turbo.net Hub.

Turbo.net plugin supports following features:
* Build an image with TurboScript
* Push an image to the Turbo.net Hub
* Export an image to the local file system
* Remove an image from the local repository
* Trigger a build job using webhooks

For more details see our plugin at [Jenkins Wiki](https://wiki.jenkins-ci.org/display/JENKINS/SpoonScript+Plugin).

## Build
1. Ensure you have JDK 8.0+ and Maven installed. 
2. Ensure you have the jenkins repos added in your ~/.m2/settings.xml. As described here (https://wiki.jenkins-ci.org/display/JENKINS/Plugin+tutorial)
3. After than `mvn build`
4. To run the plugin locally, use `mvn hpi:run`


## License
|                      |                                          |
|:---------------------|:-----------------------------------------|
| **Author:**          | Turbo.net (<support@turbo.net>)
| **Copyright:**       | Copyright (c) 2015 Turbo.net
| **License:**         | Apache License, Version 2.0

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at 

	http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
