=====================
Crate-Mesos-Framework
=====================

This is an integration framework which allows running and managing Crate_ database through Mesos_.

Usage
=====

First, the jar file needs to be built from source::

    ./gradlew fatJar

The framework can be deployed in two ways: (1) executing the jar file from the
command line and (2) launching it via Marathon.

If the framework is deployed using Marathon the jar needs to be copied to the
mesos-slaves. Otherwise it needs to be copied to the mesos-master from where it
is launched using the command line.

In both cases the ``Main`` method requires a ``--crate-version`` argument,
which is the Crate version that should be used.

Current Crate version is ``0.47.7``.

Execute via Command Line
------------------------

::

    java -Djava.library.path=/usr/local/lib -jar /path/to/crate-mesos.jar --crate-version 0.x.x [OPTIONS]"


Launch via Marathon
--------------------

Create a Marathon configuration file::

    {
      "id": "/crate/dev",
      "instances": 1,
      "cpus": 0.5,
      "mem": 256,
      "ports": [0],
      "uris": [],
      "env": {},
      "cmd": "java -Djava.library.path=/usr/local/lib -jar /path/to/crate-mesos.jar --crate-version 0.x.x [OPTIONS]"
    }

For this to work ``java`` needs to be available on the mesos-slave. If ``java``
isn't available it can be included as dependency in the Marathon configuration
file by listing it in  ``uris`` and by changing the ``cmd``::

    "uris": [
        "https://downloads.mesosphere.io/java/jre-7u76-linux-x64.tar.gz"
    ],
    "cmd": "$(pwd)/jre*/bin/java $JAVA_OPTS -jar /path/to/crate-mesos.jar crate-version 0.47.7",


And submit it to a running Marathon master::

    curl -s -XPOST http://localhost:8080/v2/apps -d@CrateFramework.json -H "Content-Type: application/json"


Command Line Options
====================

Framework specific options
--------------------------

=========================== ============== =======================
OPTION                       REQUIRED       DEFAULT
=========================== ============== =======================
``--crate-version``         true
--------------------------- -------------- -----------------------
``--mesos-master``          false          zk://{zookeeper}/mesos
--------------------------- -------------- -----------------------
``--zookeeper``             false          localhost:2181
--------------------------- -------------- -----------------------
``--crate-cluster-name``    false          crate
--------------------------- -------------- -----------------------
``--crate-http-port``       false          4200
--------------------------- -------------- -----------------------
``--crate-transport-port``  false          4300
--------------------------- -------------- -----------------------
``--crate-data-path``       false          not set
--------------------------- -------------- -----------------------
``--crate-blob-path``       false          not set
--------------------------- -------------- -----------------------
``--api-port``              false          4040
--------------------------- -------------- -----------------------
``--resource-cpus``         false          0.5
--------------------------- -------------- -----------------------
``--resource-memory``       false          512
--------------------------- -------------- -----------------------
``--resource-heap``         false          256
--------------------------- -------------- -----------------------
``--resource-disk``         false          1024
=========================== ============== =======================


Persistent Data Paths
---------------------

Crate has 2 options for persistent data paths: one for data (tables) and one
for blobs.

You can specify both paths (``--crate-data-path``, ``--crate-blob-path``) when
starting the framework.

If the paths are specified, the executor will check if the path exists on the
slave. If the path does not exist, the executor won't start Crate on that slave.


Crate Options
-------------

Configuration options for crate instances can also be passed to the framework.
These options will be passed to the Crate processes which are launched by the
framework.

All options starting with ``-Des.`` are considered crate configuration options.

For example in order to get the framework to launch instances that will have
stats-collecting enabled use the following::

    java ... -jar crate-mesos.jar --crate-version 0.x.x -Des.stats.enabled=true


API Usage
=========

The API is availble on port ``4040`` (default, but can be set via the ``--api-port`` command line option).

You can get information about the cluster from the ``/cluster`` endpoint::

    curl -X GET http://localhost:4040/cluster

You can resize the cluster by setting the number of desired instances::

    curl -X POST -H "Content-Type: application/json" localhost:4040/cluster/resize -d '{"instances": 5}'


Service Discovery for Applications using DNS
============================================

In order for applications to discover the Crate nodes `Mesos-DNS`_ can be used.

If `Mesos-DNS` is running it will automatically retrieve information about the
instances launched by the Crate framework and then the client applications can
connect to the crate cluster using the following URL:
``<cluster-name>.crateframework.<domain>:<http-port>``

Both ``<cluster-name>`` and ``<http-port`` are options that can be specified
when the Mesos Crate Framework is launched. The ``<domain>`` is part of the
Mesos-DNS configuration.


Run Multiple Crate Clusters using Marathon
==========================================

One Crate Framework can only be used to manage one crate cluster.In order to be
able to manage multiple crate clusters it is possible to run the crate
framework multiple times.

The easiest and recommended way to do so is to deploy the Crate Framework using
Marathon. This also has the advantage that the Crate Framework itself will be
HA.


In order to deploy something on Marathon create a json file. For example
``crate-mesos.json`` with the following content::

    {
        "id": "crate-demo",
        "instances": 1,
        "cpus": 0.25,
        "mem": 50,
        "ports": [4040],
        "cmd": "java -Djava.library.path=/usr/local/lib -jar /tmp/crate-mesos.jar --zookeeper mesos-master-1:2181,mesos-master-2:2181,mesos-master-3:2181 --crate-cluster-name crate-demo --crate-version 0.47.7 --api-port $PORT0",
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


In order to instruct marathon to deploy the crate framework curl can then be used::

    curl -s -XPOST http://marathon-url:8080/v2/apps -d@crate-mesos.json -H "Content-Type: application/json"

If `Mesos-DNS`_ is available the launched Crate Framework can then be accessed
using ``crate-demo.marathon.mesos``. Where ``crate-demo`` is the id specified in
the ``crate-mesos.json`` and ``mesos`` is the configured `Mesos-DNS`_ domain.


.. note::

    The defined port (4040) must be available. Either extend the ports
    definitions in `/etc/mesos-slave/resources` or use a dynamic port (setting
    ports to [0]).

    Mesos-DNS also serves SRV records which can also be queried to discover on
    which port the API is listening::

        nslookup -querytype=srv _crate-demo._tcp.marathon.mesos

Now for each additional cluster an additional "crate framework app" can be
deployed using Marathon. Keep in mind that each cluster should have its unique
ports so the port configuration options should be set in each clusters ``cmd``
definition.

.. warning::

    Current limitations:

    - As there is no official crate-mesos release yet the jar file isn't hosted
      but needs to be built locally and somehow copied to the slaves.


Are you a Developer?
====================

You can build Crate-Mesos-Framework on your own with the latest version hosted on GitHub.
To do so, please refer to ``DEVELOP.rst`` for further information.


.. _Crate: https://github.com/crate/crate
.. _Mesos: http://mesos.apache.org
.. _Mesos-DNS: http://mesosphere.github.io/mesos-dns/
