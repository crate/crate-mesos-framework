===============
Developer Guide
===============

IDE
---

We recommend that you use `IntelliJ IDEA`_ to develop this project.

Gradle can be used to generate project files that can be opened in IntelliJ::

    $ ./gradlew idea

Running tests
=============

You can run tests directly from within IntelliJ.

You can also run them using Gradle::

    $ ./gradlew test

Integrations Tests
------------------

The integration tests can be run like so::

    $ ./gradlew itest

The integration tests use the Minimesos_ testing framework which requires a
working local Docker_ environment.

You can set up a local Docker environment like so::

    $ docker-machine create -d virtualbox \
        --virtualbox-memory 8192 \
        --virtualbox-cpu-count 1 \
        minimesos
    $ eval $(docker-machine env minimesos)
    $ sudo route delete 172.17.0.0/16
    $ sudo route -n add 172.17.0.0/16 $(docker-machine ip minimesos)

Debugging
=========

It is not easy to debug the framework from within IntelliJ.

The best way to debug is to use loggers and then watch the log files from
Mesos::

    $ vagrant ssh -c "tail -f /var/log/mesos/mesos-{slave,master}.{INFO,WARNING,ERROR}"

Zookeeper
=========

If you need to reset the state in Zookeeper you can use the CLI client::

    $ bin/zk

To delete all CrateDB Mesos state run::

    $ rmr /crate-mesos

Preparing a Release
===================

To create a new release, you must:

- Update the ``CURRENT`` version in ``io.crate.frameworks.mesos.Version``

- Add a section for the new version in the ``CHANGES.txt`` file

- Commit your changes with a message like "prepare release x.y.z"

- Push to origin

- Create a tag by running ``./devtools/create_tag.sh``

At this point, Jenkins will take care of the rest.

However, if you'd like to do things manually, you can run::

    $ ./gradlew release

This Gradle task runs the ``fatJar`` task, but additionally checks that the
output of ``git describe --tag`` matches the current version.

The resulting JAR file will reside in the ``build/libs`` directory.

.. _Docker: https://www.docker.com/
.. _Gradle: http://www.gradle.org/
.. _IntelliJ IDEA: https://www.jetbrains.com/idea/
.. _Minimesos: https://minimesos.org/
