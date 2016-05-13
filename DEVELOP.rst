=================================
Crate-Mesos-Framework DEVELOPMENT
=================================

Prerequisites
=============

Crate-mesos-framework is written in Java_ 7, so a JDK needs to be installed.
On OS X we recommend using `Oracle's Java`_ and OpenJDK_ on Linux Systems.

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

Before the crate-mesos-framework can be launched the ``jar`` file has to be
generated::

    ./gradlew fatJar

The jar cannot be run directly as it requires a mesos-master and the mesos
native libraries.  This project includes a Vagrantfile, so ``vagrant`` can be
used to instrument a virtual machine which has mesos installed.

If ``vagrant`` is installed simply run::

    vagrant up

This will create and provision 4 VMs:

* ``mesos-master``: The Mesos master instance + Zookeeper
* ``mesos-slave-{1..3}``: The Mesos slaves

If this is the first time ``vagrant up`` is run, otherwise it will simply boot
up the existing VMs.

Once the VM is up and running the crate-mesos-framework can be started `inside`
the VM. To do so ``vagrant ssh mesos-master`` can be used::

    vagrant ssh -c "java -Djava.library.path=/usr/local/lib -jar /vagrant/build/libs/crate-mesos-*.jar --crate-version 0.54.9 --zookeeper 192.168.10.100:2181"

.. note::

    Inside the VM /vagrant is mapped to the project root. This way the
    crate-mesos jar file can be accesses from inside the VM.


Hosts Entries
-------------

The static IPs of the Vagrant VMs are ``192.168.10.100`` for the master and
``192.168.10.{101..103}`` for the slaves.

You can add them to your ``/etc/hosts`` file::

    192.168.10.100   mesos-master
    192.168.10.101   mesos-slave-1
    192.168.10.102   mesos-slave-2
    192.168.10.103   mesos-slave-3

The Mesos WebUI should be available under http://mesos-master:5050 immediately
after ``vagrant up`` is finished.

Once the crate-mesos-framework has been launched the framework API becomes
available under http://mesos-master:4040/cluster (if API port not otherwise
specified).

**As a shortcut to ``./gradlew fatJar`` and running ``vagrant ssh ...`` it is
also possible to simply use ``bin/deploy --crate-version 0.47.7 --zookeeper
192.168.10.100:2181`` which will invoke both commands.**

Running Crate-Mesos-Framework via Marathon
------------------------------------------

``Crate-Mesos-Framework`` instances can be run and controlled through Marathon_
system. For installing Marathon, please refer to `Mesosphere install guide`_.
Marathon WebUI should be available under http://mesos-master:8080 after setting up.
To run ``Crate-Mesos-Framework`` instance via ``HTTP`` you need to ``POST`` a
JSON file with configuration environment variables to Marathon.

Example
-------

``marathon/local.json``

::

    {
        "id": "crate-framework",
        "instances": 1,
        "cpus": 0.25,
        "mem": 128,
        "portDefinitions": [
            {
                "port": 4040,
                "protocol": "tcp",
                "name": "api"
            }
        ],
        "requirePorts": true,
        "env": {
            "CRATE_CLUSTER_NAME": "dev-local",
            "CRATE_VERSION": "0.54.9",
            "CRATE_HTTP_PORT": "4200",
            "CRATE_TRANSPORT_PORT": "4300",
            "MESOS_MASTER": "192.168.10.100"
        },
        "fetch": [
            {
                "uri": "file:///vagrant/build/libs/crate-mesos.tar.gz",
                "extract": true,
                "executable": false,
                "cache": false
            },
            {
                "uri": "https://cdn.crate.io/downloads/openjdk/jre-7u80-linux.tar.gz",
                "extract": true,
                "executable": false,
                "cache": false
            }
        ],
        "cmd": "env && $(pwd)/jre/bin/java $JAVA_OPTS -jar $(pwd)/crate-mesos-*.jar --crate-cluster-name $CRATE_CLUSTER_NAME --crate-version $CRATE_VERSION --api-port $PORT0 --crate-http-port $CRATE_HTTP_PORT --crate-transport-port $CRATE_TRANSPORT_PORT --zookeeper $MESOS_MASTER:2181",
        "healthChecks": [
            {
                "protocol": "HTTP",
                "path": "/cluster",
                "gracePeriodSeconds": 3,
                "intervalSeconds": 10,
                "portIndex": 0,
                "timeoutSeconds": 10,
                "maxConsecutiveFailures": 3
            }
        ]
    }

::

    curl -s -XPOST http://mesos-master:8080/v2/apps -d@marathon/local.json -H "Content-Type: application/json"

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


Preparing a new Release
=======================

Before creating a new distribution, a new version and tag should be created:

 - Update the CURRENT version in ``io.crate.frameworks.mesos.Version``.

 - Add a note for the new version at the ``CHANGES.txt`` file.

 - Commit e.g. using message ``'prepare release x.x.x'``.

 - Push to origin

 - Create a tag using the ``create_tag.sh`` script
   (run ``./devtools/create_tag.sh``).

Now everything is ready for building a new distribution, either
manually or let Jenkins do the job as usual :-)

Building a release tarball is done via the ``release`` task. This task
actually only runs the ``fatJar`` task but additionally checks that
the output of ``git describe --tag`` matches the current version of
Crate Mesos Framework::

    ./gradlew release

The resulting ``jar`` file will reside in the folder ``build/libs/``.


.. _Java: http://www.java.com/

.. _`Oracle's Java`: http://www.java.com/en/download/help/mac_install.xml

.. _OpenJDK: http://openjdk.java.net/projects/jdk7/

.. _Gradle: http://www.gradle.org/

.. _Marathon: https://mesosphere.github.io/marathon/

.. _`Mesosphere install guide`: http://mesosphere.com/docs/getting-started/datacenter/install/

