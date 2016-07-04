.. highlight:: sh

.. _usage:

=====
Usage
=====

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
--------------------------- -------------- -----------------------
``--framework-name``        false          crate-mesos
--------------------------- -------------- -----------------------
``--framework-user``        false          crate
--------------------------- -------------- -----------------------
``--framework-role``        false          *
=========================== ============== =======================


Zookeeper
---------

The default value for the ``--zookeeper`` parameter is ``localhost:2181``, but
in highly available production cases it must be a list of hostnames in your
Zookeeper cluster.

For example, if your Zookeeper nodes are ``mesos-master-1``, ``mesos-master-2``,
and ``mesos-master-3``, then the values of the parameter will look like::

    mesos-master-1:2181,mesos-master-2:2181,mesos-master-3:2181

For DCOS_ clusters the ``--zookeeper`` parameter is ``master.mesos:2181``.


.. _persistent_data_paths:

Persistent Data Paths
---------------------

Crate has 2 options for persistent data paths. One for data (tables), and one
for blobs.

You can specify both paths (``--crate-data-path``, ``--crate-blob-path``) when
starting the framework.

If the paths are specified, the executor will check if the path exists on the
slave. If the path does not exist, the executor won't start Crate on that slave.


Crate Options
-------------

Configuration options for Crate instances can also be passed to the framework.
These options will be passed to the Crate processes launched by the framework.

All options starting with ``-Des.`` are considered Crate configuration options.

For example to get the framework to launch instances that will have statistic
collection enabled use the following::

    java ... -jar crate-mesos-0.x.x.jar --crate-version 0.x.x
    -Des.stats.enabled=true


Crate Node Tags
---------------

Any attributes defined on a Mesos-Slave will be passed to the Crate processes as
node tag with a ``mesos_`` prefix.

For example if a Mesos-Slave is launched with ``--attributes=zone:a`` the Crate
instance will have the ``node.mesos_zone=a`` tag set.

This can be used to setup a `Multi Zone Crate Cluster`_.

Assuming there are 4 slaves, 2 with the attribute ``zone:a`` and 2 with the
attribute ``zone:b``, you would launch the framework with the following options
for a working multi zone setup::

    java ... -jar crate-mesos-0.1.0.jar --crate-version x.x.x \
        -Des.cluster.routing.allocation.awareness.attributes=mesos_zone \
        -Des.cluster.routing.allocation.awareness.force.mesos_zone.values=a,b


Service Discovery
=================

Service discovery for applications using DNS
--------------------------------------------

For applications to discover the Crate nodes, `Mesos-DNS`_ can be used.

If `Mesos-DNS` is running it will automatically retrieve information about the
instances launched by the Crate framework and then the client applications can
connect to the crate cluster using the following URL:

``<cluster-name>.crateframework.<domain>:<http-port>``

Both ``<cluster-name>`` and ``<http-port>`` can be specified when the Mesos
Crate Framework is launched. The ``<domain>`` is part of the Mesos-DNS
configuration.


Run Multiple Crate Clusters using Marathon
==========================================

One Crate Framework can only manage one Crate cluster. To manage multiple Crate
clusters it's possible to run the Crate Framework multiple times.

The easiest and recommended method is to deploy the Crate Framework using
Marathon. This has the advantage that the Crate Framework itself will be
highly available.

To deploy something on Marathon, create a json file. For example,
``crate-mesos.json`` with the following content::

    {
        "id": "crate-demo",
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
            "CRATE_VERSION": "0.54.8",
            "CRATE_HTTP_PORT": "4200",
            "CRATE_TRANSPORT_PORT": "4300"
        },
        "fetch": [
            {
                "uri":
                "https://cdn.crate.io/downloads/openjdk/jre-7u80-linux.tar.gz",
                "extract": true,
                "executable": false,
                "cache": false
            }
        ],
        "cmd": "env && $(pwd)/jre/bin/java $JAVA_OPTS -jar
        /tmp/crate-mesos-0.1.0.jar --crate-cluster-name $CRATE_CLUSTER_NAME
        --crate-version $CRATE_VERSION --api-port $PORT0 --crate-http-port
        $CRATE_HTTP_PORT --crate-transport-port $CRATE_TRANSPORT_PORT",
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



You can use curl to instruct Marathon to deploy the Crate framework::

    curl -s -XPOST http://marathon-url:8080/v2/apps -d@crate-mesos.json -H
    "Content-Type: application/json"

If `Mesos-DNS`_ is available the launched Crate Framework can then be accessed
using ``crate-demo.marathon.mesos``. Where ``crate-demo`` is the id specified in
the ``crate-mesos.json`` and ``mesos`` is the configured `Mesos-DNS`_ domain.


.. note::

    The defined port (4040) must be available. Either extend the ports
    definitions in `/etc/mesos-slave/resources` or use a dynamic port (setting
    ports to [0]).

    Mesos-DNS also serves SRV records which can be queried to discover on which
    port the API is listening::

        nslookup -querytype=srv _crate-demo._tcp.marathon.mesos

For each additional cluster another "crate framework app" can be deployed using
Marathon. Keep in mind that each cluster should have unique ports so the port
configuration options should be set in each clusters' ``cmd`` definition.


.. _Mesos-DNS: http://mesosphere.github.io/mesos-dns
.. _`Multi Zone Crate Cluster`: https://crate.io/docs/en/latest/best_practice/multi_zone_setup.html
.. _DCOS: https://docs.mesosphere.com/usage/services/crate
