# Nimrod/G


## Usage
The CLI is huge, consider use the `-h` flag.
```
usage: nimrod [-h] [-c CONFIG] [-d] command ...

Invoke Nimrod/G CLI commands

optional arguments:
  -h, --help             show this help message and exit
  -c CONFIG, --config CONFIG
                         Path to configuration file. (default: /home/user/.config/nimrod/nimrod.ini)
  -d, --debug            Enable debug output. (default: false)

valid commands:
  command
    setprop              Set a configuration property.
    getprop              Get a configuration property.
    addexp               Add a new, empty experiment from a planfile.
    delexp               Delete an experiment and all associated data from the database.
    addjobs              Add a list of jobs to the given run.
    master               Start the experiment master.
    resource             Compute resource operations.
    setup                Nimrod/G setup functionality.
    portalapi            Portal API
    rest                 Start the REST service and DAV server.
    compile              Compile a planfile.
    staging              Execute staging commands.
```

## Build Instructions

Use the `nimw.sh` wrapper script to invoke the CLI via Gradle.

To generate a tarball, use `gradle nimrod:assembleDist`.

### Requirements
* Java 10+
* Gradle 4.7+

## Installation

* Create a `nimrod.ini` configuration file in `~/.config/nimrod`
  - A sample is provided in `nimrodg-cli/src/main/resources`
* Create a setup configuration file. This can be placed anywhere.
  - A sample is provided in `nimrodg-cli/src/main/resources`
* Run `nimrod setup init /path/to/setup-config.ini`
* You're ready to go.

## License
This project is licensed under the [Apache License, Version 2.0](https://opensource.org/licenses/Apache-2.0):

Copyright &copy; 2019 [The University of Queensland](http://uq.edu.au/)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

### 3rd-party Licenses


| Project | License | License URL |
| ------- | ------- | ----------- |
| [Antlr4](http://www.antlr.org) | The BSD License | http://www.antlr.org/license.html |
| [icu4j](http://site.icu-project.org) | Unicode/ICU License | http://source.icu-project.org/repos/icu/trunk/icu4j/main/shared/licenses/LICENSE |
| [PgJDBC](https://jdbc.postgresql.org/about/about.html) | BSD-2-Clause License | https://jdbc.postgresql.org/about/license.html
| [\[ini4j\]](http://ini4j.sourceforge.net/) | Apache 2.0 | http://www.apache.org/licenses/LICENSE-2.0.txt |
| [Bouncy Castle Crypto APIs](https://www.bouncycastle.org/) | Bouncy Castle License | https://www.bouncycastle.org/license.html |
| [Jersey](https://jersey.github.io/) | CDDL 1.1 | https://jersey.github.io/license.html |
| [sqlite-jdbc](https://github.com/xerial/sqlite-jdbc) | Apache 2.0 | http://www.apache.org/licenses/LICENSE-2.0.txt |
| [RabbitMQ Java Client Library](https://www.rabbitmq.com/java-client.html) | Apache 2.0 | http://www.apache.org/licenses/LICENSE-2.0.txt |
| [Apache log4j2](https://logging.apache.org/log4j/) | Apache 2.0 | http://www.apache.org/licenses/LICENSE-2.0.txt |
| [Apache Commons CSV](https://commons.apache.org/csv/) | Apache 2.0 | http://www.apache.org/licenses/LICENSE-2.0.txt |
| [Apache Commons IO](https://commons.apache.org/io/) | Apache 2.0 | http://www.apache.org/licenses/LICENSE-2.0.txt |
| [Apache Commons Collections](https://commons.apache.org/proper/commons-collections/) | Apache 2.0 | http://www.apache.org/licenses/LICENSE-2.0.txt |
| [Apache Tomcat](http://tomcat.apache.org/) | Apache 2.0 | http://www.apache.org/licenses/LICENSE-2.0.txt |
| [Apache Mina SSHD](https://mina.apache.org/sshd-project/index.html) | Apache 2.0 | http://www.apache.org/licenses/LICENSE-2.0.txt |


