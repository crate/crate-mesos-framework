Crate-Mesos-Framework
=====================

This is an integration framework which allows running and managing Crate_ database through Mesos_.

Usage
-----

First, the jar file needs to be built from source::

    ./gradlew fatJar

and then copied to the Mesos master instance.

The framework can be deployed in two ways: (1) executing the jar file from the command line and (2) launching it via Marathon.

In both cases the ``Main`` method requires a ``--crate-version`` argument,
which is the Crate version that should be used.

Current Crate version is ``0.47.7``.

Execute via Command Line
........................

::

    java -Djava.library.path=/usr/local/lib -jar /path/to/crate-mesos.jar --crate-version 0.x.x [OPTIONS]"


Launch via Marathon
....................

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

And submit it to a running Marathon master::

    curl -s -XPOST http://localhost:8080/v2/apps -d@CrateFramework.json -H "Content-Type: application/json"

    
Command Line Options
--------------------

=========================== ============== =================
OPTION                       REQUIRED       DEFAULT
=========================== ============== =================
``--crate-version``         true           
--------------------------- -------------- -----------------
``--crate-cluster-name``    false          crate
--------------------------- -------------- -----------------
``--crate-http-port``       false          4200
--------------------------- -------------- -----------------
``--api-port``              false          4040
--------------------------- -------------- -----------------
``--resource-cpus``         false          0.5
--------------------------- -------------- -----------------
``--resource-memory``       false          512
--------------------------- -------------- -----------------
``--resource-heap``         false          256
--------------------------- -------------- -----------------
``--resource-disk``         false          1024
=========================== ============== =================


API Usage
---------

The API is availble on port ``4040`` (default, but can be set via the ``--api-port`` command line option).

You can get information about the cluster from the ``/cluster`` endpoint::

    curl -X GET http://localhost:4040/cluster

You can resize the cluster by setting the number of desired instances::

    curl -X POST -H "Content-Type: application/json" localhost:4040/cluster/resize -d '{"instances": 5}'


Service Discovery for Applications using DNS
--------------------------------------------

In order for applications to discover the Crate nodes `Mesos-DNS`_ can be used.

If `Mesos-DNS` is running it will automatically retrieve information about the
instances launched by the Crate framework and then the client applications can
connect to the crate cluster using the following URL:
``<cluster-name>.crateframework.<domain>:<http-port>``

Both ``<cluster-name>`` and ``<http-port`` are options that can be specified
when the Mesos Crate Framework is launched. The ``<domain>`` is part of the
Mesos-DNS configuration.

Are you a Developer?
--------------------

You can build Crate-Mesos-Framework on your own with the latest version hosted on GitHub.
To do so, please refer to ``DEVELOP.rst`` for further information.


.. _Crate: https://github.com/crate/crate
.. _Mesos: http://mesos.apache.org
.. _Mesos-DNS: http://mesosphere.github.io/mesos-dns/
