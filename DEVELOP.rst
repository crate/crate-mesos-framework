=================================
Crate-Mesos-Framework DEVELOPMENT
=================================

Prerequisites
=============

Crate-mesos-framework is written in Java_ 7, so a JDK needs to be installed. On OS X we
recommend using `Oracle's Java`_ and OpenJDK_ on Linux Systems.

Git checkout
============

Clone the repository::

    $ git clone https://github.com/crate/crate-mesos-framework.git

Gradlew - Building Crate-Mesos-Framework
========================================

This project uses Gradle_ as build tool. It can be invoked by executing
``./gradlew``. The first time this command is executed it is bootstrapped
automatically, therefore there is no need to install gradle on the system.

IntelliJ
--------

Gradle can be used to generate project files that can be opened in IntelliJ::

    ./gradlew idea

Building and running Crate-Mesos-Framework
==========================================

Before the crate-mesos-framework can be launched the ``jar`` file has to be generated::

    ./gradlew fatJar

The jar cannot be run directly as it requires a mesos-master and the mesos
native libraries.  This project includes a Vagrantfile, so ``vagrant`` can be
used to instrument a virtual machine which has mesos installed.

If ``vagrant`` is installed simply run::

    vagrant up

This will create and provision the VM if this is the first time ``vagrant up``
is run, otherwise it will simply boot up the existing VM.

Once the VM is up and running the crate-mesos-framework can be started `inside` the VM.
To do so ``vagrant ssh`` can be used::

    vagrant ssh -c "java -Djava.library.path=/usr/local/lib -jar /vagrant/build/libs/crate-mesos.jar"

.. note::

    Inside the VM /vagrant is mapped to the project root. This way the
    crate-mesos.jar can be accesses from inside the VM.


The Mesos WebUI should be available under http://localhost:5050 immediately
after ``vagrant up`` is finished.

Once the crate-mesos-framework has been launched Crate should become available
(after some time, as docker pull might take some time) under
http://localhost:4200/admin


As a shortcut to ``./gradlew fatJar`` and running ``vagrant ssh ...`` it is
also possible to simply use ``bin/run`` which will invoke both commands.

Running Crate-Mesos-Framework via Marathon
------------------------------------------

``Crate-Mesos-Framework`` instances can be run and controlled through Marathon_ system.
For installing Marathon, please refer to `Mesosphere install guide`_. Marathon WebUI
should be available under http://localhost:8080 after setting up.
To run ``Crate-Mesos-Framework`` instance via ``HTTP`` you need to ``POST`` a JSON file
with configuration environment variables to Marathon.

Example
-------

``crate-mesos.json``

::

    {
      "id": "/crate/dev",
      "instances": 1,
      "cpus": 0.5,
      "mem": 256,
      "ports": [0],
      "uris": [
      ],
      "env": {
        "JAVA_OPTS": "-Xms256m -Xmx256m",
        "CRATE_CLUSTER_NAME": "dev-test",
        "CRATE_NODE_COUNT": "1",
        "CRATE_RESOURCE_CPU_CORES": "1.0",
        "CRATE_RESOURCE_MEMORY": "512",
        "CRATE_RESOURCE_DISK": "1024",
        "MESOS_MASTER": "127.0.0.1:5050",
        "FRAMEWORK_API_PORT": "4040"
      },
      "cmd": "java -Djava.library.path=/usr/local/lib -jar /vagrant/build/libs/crate-mesos.jar"
    }

::

::

    curl -s -XPOST http://localhost:8080/v2/apps -d@crate-mesos.json -H "Content-Type: application/json"

::

Running tests
=============

In order to run the tests simply run them from within intellij or use gradle::

    ./gradlew test

Debugging
=========

It is not really possible to debug the framework from inside intellij. The best
way is to use loggers and then watch all the log files from mesos::

    vagrant ssh -c "tail -f /var/log/mesos/mesos-{slave,master}.{INFO,WARNING,ERROR}"


Zookeeper
=========

If you need to reset the state in Zookeeper you can use the zkCli::

    bin/zk

and then to delete all crate-mesos state run::

    rmr /crate-mesos


.. _Java: http://www.java.com/

.. _`Oracle's Java`: http://www.java.com/en/download/help/mac_install.xml

.. _OpenJDK: http://openjdk.java.net/projects/jdk7/

.. _Gradle: http://www.gradle.org/

.. _Marathon: https://mesosphere.github.io/marathon/

.. _`Mesosphere install guide`: http://mesosphere.com/docs/getting-started/datacenter/install/
