=====================
Crate-Mesos-Framework
=====================

This is an integration framework which allows running and managing Crate_ database through Mesos_.

.. warning::

    **DISCLAIMER**

    *This is a very early version of Crate-Mesos-Framework;
    document, code behavior, and anything else may change
    without notice and/or break older installations!*

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
which is the Crate version that should be used. The version number must be
in the format ``x.y.z``.
Current stable Crate version is ``0.54.9``.

Alternatively you can specify a full download URL for Crate. In that case
the URL needs to be in the format: ``http(s)://<HOST><PATH>/crate-<X>.<Y>.<Z><SUFFIX>.tar.gz``

Open Files
==========

Depending on the size of your installation, Crate can open a lot of files.
You can check the number of open files with ``ulimit -n``, but it can depend
on your host operating system. How to increase the number is dependent for your
operating system. For instance, in RHEL6.x, you can place ``crate.conf`` file
containing ``crate soft nofile 65535`` and ``crate hard nofile 65535``
under ``/etc/security/limit.d``. This will set limit of number of open files
for user ``crate`` to 65535. Furthermore it is recommended
to set the memlock limit (the maximum locked-in-memory address space) to
unlimited. You can do it by adding ``crate hard memlock unlimited`` and
``crate soft memlock unlimited`` lines to the ``crate.conf`` file mentioned
above for RHEL6.x, but please take into account that, it may differ in other
operating systems.

Vagrant Setup
=============

The ``Vagrantfile`` in this repository can be used for easy installation and
testing. If you want to setup a Mesos cluster with 3 nodes, you can simply run
``vagrant up`` and update your ``/etc/hosts`` file with the the IPs/hostnames
of the vagrant boxes. For full setup, see ``DEVELOP.rst``.


Execute via Command Line
------------------------

::

    java -Djava.library.path=/usr/local/lib -jar /path/to/crate-mesos-0.x.x.jar --crate-version 0.x.x [OPTIONS]"


Launch via Marathon
-------------------

Create a Marathon configuration file::

    {
      "id": "/crate/dev",
      "instances": 1,
      "cpus": 0.5,
      "mem": 256,
      "ports": [0],
      "uris": [],
      "env": {},
      "cmd": "java -Djava.library.path=/usr/local/lib -jar /path/to/crate-mesos-0.x.x.jar --crate-version 0.x.x [OPTIONS]"
    }

For this to work ``java`` needs to be available on the mesos-slave. If ``java``
isn't available it can be included as dependency in the Marathon configuration
file by listing it in  ``uris`` and by changing the ``cmd``::

    "uris": [
        "https://downloads.mesosphere.io/java/jre-7u76-linux-x64.tar.gz"
    ],
    "cmd": "$(pwd)/jre*/bin/java $JAVA_OPTS -jar /path/to/crate-mesos-0.x.x.jar --crate-version 0.47.7",


And submit it to a running Marathon master::

    curl -s -XPOST http://localhost:8080/v2/apps -d@CrateFramework.json -H "Content-Type: application/json"

There is a template file for ``marathon.json`` under marathon directory.
You can copy it by ``cp marathon/marathon.json.template marathon/marathon.json``
and modify the necessary parameters


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

    java ... -jar crate-mesos-0.x.x.jar --crate-version 0.x.x -Des.stats.enabled=true


User/Role
---------

**The Crate Framework is run as user ``crate`` and role ``*`` by default.
However, this is configurable using ``--framework-user`` and ``--framework-role``.**

This means, that a user ``crate`` (or other specified user) is required to be present on all instances, both
master and slaves. The user does not need to have any specific permissions. You can
add a user with the simplest configuration::

    useradd crate -s /bin/bash

If you specify a role different to the default ``"*"`` you need to add it the mesos-master
configuration, e.g.::

    echo "crate" > /etc/mesos-master/roles

Resources
=========

Data Path
---------

If you are using `Persistent Data Paths`_ (which is recommended), you need to make sure
that the user ``crate`` has **write** permissions at these locations.
For example::

    chown crate:crate /path/to/persistent/disk

Ports
-----

Crate uses by default a the ports ``4200`` and ``4300``.
In order to get offers you need to add the resource reservation for a port range that includes
these ports, e.g. writing it into the resources file::

    echo 'ports(*)[31000-31099, 31101-32000, 4000-4999]' > /etc/mesos-slave/resources

or starting the slave with the option::

    --resources=ports(*)[31000-31099, 31101-32000, 4000-4999]

Then restart the slave and clean the old slave state if necessary (``rm -f /tmp/mesos/meta/slaves/latest``).

The ports can be configured on startup of the Framework, which means that you need adopt
the resource port range according to your configured ports.

API Usage
=========

The API is availble on port ``4040`` (default, but can be set via the ``--api-port`` command line option).

You can get information about the cluster from the ``/cluster`` endpoint::

    curl -X GET http://localhost:4040/cluster

You can resize the cluster by setting the number of desired instances::

    curl -X POST -H "Content-Type: application/json" localhost:4040/cluster/resize -d '{"instances": 5}'

You can force shut down the cluster::

    curl -X POST http://localhost:4040/cluster/shutdown


Resizing a Cluster
==================

A Crate cluster can be resized by changing the number of instances using the
Framework API (see `API Usage`_).

Increasing the number of instances is always possible, unless the number of
desired instances is greater than the number of slaves. Each instance of the
Crate Framework enforces the contraint that there is only one Crate instance
running on each host.

The Crate Framework shuts down Crate instances gracefully (see `Configuration`_
and `Zero Downtime Upgrade`_) when decreasing the number of instances in a
cluster.

If you want to ensure green health (full data + replica availability), you need
to change the ``cluster.graceful_stop.min_availability`` setting to ``full``.
This option will cause the Crate node to try move all shards off the node
before shutting down. If this is not possible, the node will **not** shut down
and run into the timeout (``cluster.graceful_stop.timeout``). However the Crate
Framework will continue to try to shut down the node again. Such a state is
indicated by the Framework API when the number of running instances does not
approach the number of desired instances when scaling down. Please keep in mind
that the cluster can not be resized to zero instances.

In order to shut down the a cluster you need to use the ``/cluster/shutdown``
API endpoint.

Cluster Settings
----------------

When resizing a cluster the Crate Framework automatically applies certain
important cluster settings. These settings are:

* ``discovery.zen.minimum_master_nodes``
* ``gateway.recover_after_nodes``
* ``gateway.expected_nodes``

Since only the ``minumum_master_nodes`` setting is a runtime settings, there
are various limitations to how theses settings are applied. **These limitations
should be carefully considered when running a Crate cluster!**

1. The three settings are applied on node start using the ``-Des.`` command
   line arguments. This means that when you resize a cluster from 0 instances
   to e.g. 5 instances, the executor of the Crate Framework will launch Crate
   using these arguments among others::

       -Des.discovery.zen.minimum_master_nodes=3 -Des.gateway.recover_after_nodes=3 -Des.gateway.expected_nodes=5

   This will prevent the cluster from a "split brain" and sets the correct
   gateway settings for recovery.

2. However, when resizing a cluster from 0 instances to 1 instance first, the
   same rules apply and the executor launches Crate with the following
   arguments::

       -Des.discovery.zen.minimum_master_nodes=1 -Des.gateway.recover_after_nodes=1 -Des.gateway.expected_nodes=1

   If you now resize to 5 instances the Framework first tries to update the
   ``minimum_master_nodes`` setting of the cluster to the new quorum of the
   expected nodes (in this case 5). However, since it is not possible to set
   this setting to a value greater than the number of nodes in the (existing)
   cluster, the cluster setting update will fail (silently), still, the newly
   added nodes are started with the updated arguments::

       -Des.discovery.zen.minimum_master_nodes=3 -Des.gateway.recover_after_nodes=3 -Des.gateway.expected_nodes=5

   Note, that starting the instances with these parameters will not update the
   cluster setting itself! **You will have to update it manually**, e.g. using
   ``crash`` or via the Admin UI.

3. When scaling down the Crate Framework also updates the
   ``minimum_master_nodes`` setting of the cluster before shutting down the
   required nodes to reach the new amount of desired instances. This means that
   there is a small window where the setting is "incorrect". If you need to
   scale down your cluster big times, you should do it in chunks!

4. Since the ``gateway.*`` settings are **not** settable at runtime, the value
   applied at first start of the cluster cannot be changed any more, even when
   scaling down. This means that e.g. the value of ``expected_nodes`` is still
   5 (from the initial cluster start) even though you've been resizing the
   cluster to 3 nodes already.


Cluster Upgrade
===============

A zero downtime upgrade of a Crate cluster running on Mesos is currently not
possible, however it is still possible to upgrade the cluster with downtime.

.. warning::

    A cluster upgrade/shutdown requires that the ``--crate-data-path`` was set
    so data is stored persistently outside of the sandboxed executor path.
    **Otherwise data will be lost definitely!**

An upgrade requires a few steps:

1. Set graceful stop options
----------------------------

Assuming you've started the Crate Framework with version 0.47.7 and want to
upgrade to version 0.47.8 (or any other greater version), you will first need
to set the minimum availability to ``full`` (see `Resizing a Cluster`_) if
not already done. Also check to other options for graceful shutdown.

This will ensure that you are able to resize your cluster to the minimum amount
of nodes.

2. Resize to minimum required nodes
-----------------------------------

The minimum amount of nodes is equal the highest number of replicas of a table
plus 1::

    min_nodes = max_replicas + 1

E.g. if your cluster has 5 nodes and your table with the most configured replicas
has 2 replicas, you can resize your cluster down to 3 nodes.

It is highly recommended to shut down Crate nodes one by one! In this way you are
in better control if a node does not shut down gracefully, e.g. runs into the
timeout.

3. Restart framework with new Crate version number
--------------------------------------------------

Now you can re-start the Crate Framework with the new Crate version number.
The Crate instances with the old version are still running at this point.
If you'd upscale your cluster, new Crate instances would still use the old version,
but that is not what we want.

4. Shut down remaining instances and scale up again
---------------------------------------------------

In order to be able to use the new version set with the restarted framework, you
need to kill the remaining instances using the ``/cluster/shutdown`` API endpoint.

Once there are no more instances, you can resize the cluster and new Crate instances
will use the new version from the framework.

Because the framework stores the information on which slaves Crate instances with data
were running and when you up-scale the cluster again, it will prefer offers from these
slaves.

.. note::

    Please also read the instructions how to perform a `Zero Downtime Upgrade`_!

.. note::

    You can omit step 2, however recovery is faster if there are less instances
    and it is less likely that other frameworks 'capture' resources on slaves
    making it impossible to spawn Crate instances on these slaves again.


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
                "uri": "https://cdn.crate.io/downloads/openjdk/jre-7u80-linux.tar.gz",
                "extract": true,
                "executable": false,
                "cache": false
            }
        ],
        "cmd": "env && $(pwd)/jre/bin/java $JAVA_OPTS -jar /tmp/crate-mesos-0.1.0.jar --crate-cluster-name $CRATE_CLUSTER_NAME --crate-version $CRATE_VERSION --api-port $PORT0 --crate-http-port $CRATE_HTTP_PORT --crate-transport-port $CRATE_TRANSPORT_PORT",
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

 
As it is shown in the list of parameters above, default value for ``--zookeeper`` parameter is ``localhost:2181``,
but in production cases in must be list of hostnames where you zookeeper cluster installed.
For instance: if your zookeeper nodes are ``mesos-master-1, mesos-master-2, mesos-master-3`` 
then the paramter and the values will look like this : ``mesosmesos-master-1:2181,mesos-master-2:2181,mesos-master-3:2181``.
For DCOS cluster, ``--zookeeper`` parameter has special value, that is, if you scheduling crate mesos framework 
on DCOS using marathon, the value of ``--zookeeper`` must be ``master.mesos:2181``.

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


Mesos Slave Attributes and Crate Node Tags
==========================================

Any attributes that are defined on a Mesos-Slave will be passed to the Crate
processes as node tag with a ``mesos_`` prefix.

For example if a Mesos-Slave is launched with ``--attributes=zone:a`` the Crate
instance will have the ``node.mesos_zone=a`` tag set.

This is can be used to setup a `Multi Zone Crate Cluster`_.

Assuming there are 4 slaves, 2 with the attribute ``zone:a`` and 2 with the
attribute ``zone:b``. In this case the framework would have to be launched with
the following options to have a working multi zone setup::

    java ... -jar crate-mesos-0.1.0.jar --crate-version x.x.x \
        -Des.cluster.routing.allocation.awareness.attributes=mesos_zone \
        -Des.cluster.routing.allocation.awareness.force.mesos_zone.values=a,b


Limitations
===========

* As there is no official crate-mesos release yet the jar file isn't hosted
  but needs to be built locally and somehow copied to the slaves.
* There is no automatic handling of cluster failures.
* The overall cluster health needs to be monitored separately,
  using the Crate Admin UI (running on port ``4200`` at path ``/admin``)
  or other third party tools.
* The cluster does not automatically resize depending on used resources.
* Although Crate requires a minimum disk size to start, the disk usage
  is not monitored inside the framework further more. This can be done using
  the Admin UI or plain SQL.
* A zero-downtime upgrade is not possible at the moment.


Are you a Developer?
====================

You can build Crate-Mesos-Framework on your own with the latest version hosted on GitHub.
To do so, please refer to ``DEVELOP.rst`` for further information.


.. _Crate: https://github.com/crate/crate
.. _Mesos: http://mesos.apache.org
.. _Mesos-DNS: http://mesosphere.github.io/mesos-dns/
.. _Multi Zone Crate Cluster: https://crate.io/docs/en/latest/best_practice/multi_zone_setup.html
.. _Configuration: https://crate.io/docs/en/stable/configuration.html#graceful-stop
.. _Zero Downtime Upgrade: https://crate.io/docs/en/stable/best_practice/cluster_upgrade.html

