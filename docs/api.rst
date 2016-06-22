=============
Framework API
=============


API Usage
=========

The API is availble on port ``4040`` by default, but can be set via the
``--api-port`` command line option.

You can get information about the cluster from the ``/cluster`` endpoint::

    curl -X GET http://localhost:4040/cluster

You can resize the cluster by setting the number of desired instances::

    curl -X POST -H "Content-Type: application/json" localhost:4040/cluster/resize -d '{"instances": 5}'

You can force a shut down the cluster::

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

If you want to ensure green health status (full data + replica availability),
you need to change the ``cluster.graceful_stop.min_availability`` setting to
``full``. This option will cause the Crate node to try and move all shards off
the node before shutting down. If this is not possible, the node will **not**
shut down and timeout (``cluster.graceful_stop.timeout``), but the Crate
Framework will continue trying to shut down the node. Such a state is indicated
by the Framework API if the number of running instances does not approach the
number of desired instances when scaling down. Please keep in mind that the
cluster can not be resized to zero instances.

To shut down the cluster you need to use the ``/cluster/shutdown`` API endpoint.

Cluster Settings
----------------

When resizing a cluster the Crate Framework automatically applies certain
important cluster settings. These settings are:

* ``discovery.zen.minimum_master_nodes``
* ``gateway.recover_after_nodes``
* ``gateway.expected_nodes``

Since only ``minumum_master_nodes`` is a runtime setting, there are limitations
on how these settings are applied.
**These limitations should be carefully considered when running a Crate
cluster!**

1. The ``minimum_master_nodes`` and ``gateway`` settings are applied when a
   node starts using the ``-Des.`` command line argument. This means that, for
   example, when you resize a cluster from 0 to 5 instances, the Crate Framework
   executor will launch Crate using these arguments::

      -Des.discovery.zen.minimum_master_nodes=3
      -Des.gateway.recover_after_nodes=3 -Des.gateway.expected_nodes=5

   This will prevent the cluster from a "split brain" scenario and sets the
   correct gateway settings for recovery.

2. When resizing a cluster from 0 to 1 instance, the same rules apply and the
   executor launches Crate with the following arguments::

       -Des.discovery.zen.minimum_master_nodes=1
       -Des.gateway.recover_after_nodes=1 -Des.gateway.expected_nodes=1

   If you now resize to 5 instances the Framework first tries to update the
   ``minimum_master_nodes`` setting to the new quorum of the
   expected nodes (in this case 5). Since it's not possible to set
   this setting to a value greater than the number of nodes in the (existing)
   cluster, the cluster settings update will fail (silently), and the newly
   added nodes started with the updated arguments::

       -Des.discovery.zen.minimum_master_nodes=3
       -Des.gateway.recover_after_nodes=3 -Des.gateway.expected_nodes=5

   Note, that starting the instances with these settings will not update the
   cluster setting itself, you will have to update them manually using
   ``crash`` or via the Admin UI.

3. When scaling down a cluster, the Crate Framework also updates the
   ``minimum_master_nodes`` setting before shutting down the
   required nodes to reach the new amount of desired instances. This means that
   there's a small window where the setting is "incorrect". If you need to
   scale down your cluster significantly, you should do it in multiple smaller
   iterations.

4. Since the ``gateway.*`` settings are **not** settable at runtime, the value
   applied when first starting the cluster cannot be changed, even when
   scaling down. This means that, for example, the value of ``expected_nodes``
   is still 5 (from the initial cluster start) even though you've already resized the cluster to 3 nodes.


Cluster Upgrade
===============

A zero downtime upgrade of a Crate cluster running on Mesos is currently not
possible, but it's still possible to upgrade the cluster with downtime.

.. warning::

    A cluster upgrade/shutdown requires the ``--crate-data-path`` to be set
    so data is stored persistently outside of the sandboxed executor path.
    **Otherwise data will be lost**

An upgrade requires a few steps:

1. Set graceful stop options
----------------------------

Assuming you've started the Crate Framework with version 0.47.7 and want to
upgrade to version 0.47.8 (or any other newer version), you will first need
to set the minimum availability to ``full`` (see `Resizing a Cluster`_) if
not already done so. Also check the other options for a graceful shutdown. This
will ensure that you are able to resize your cluster to the minimum amount
of nodes.

2. Resize to minimum required nodes
-----------------------------------

The minimum amount of nodes is equal to the highest number of replicas of a
table plus 1::

    min_nodes = max_replicas + 1

E.g. If your cluster has 5 nodes and your table with the highest configured
replicas has 2 replicas, you can resize your cluster down to 3 nodes.

It's highly recommended to shut down Crate nodes one by one. In this way you
are in better control if a node does not shut down gracefully, e.g. it timesout.

3. Restart framework with new Crate version number
--------------------------------------------------

Now you can restart the Crate Framework with the newer Crate version, but the
Crate instances with the old version are still running. If you scale your
cluster now, the new Crate instances will still be using the old version, and
that's not what you want.

4. Shut down remaining instances and scale up again
---------------------------------------------------

To use the new version with the restarted framework, you need to kill the
remaining instances using the ``/cluster/shutdown`` API endpoint.

Once there are no more instances running the old version, you can resize the
cluster and new Crate instances will use the new version from the framework.

Because the framework stores details on which slaves Crate instances with data
were running when you up-scale the cluster again, it will prefer offers from
these slaves.

.. note::

    Please also read the instructions how to perform a `Zero Downtime Upgrade`_!

.. note::

    You can omit step 2, however recovery is faster if there are less instances
    and it's less likely that other frameworks 'capture' resources on slaves
    making it impossible to spawn Crate instances on these slaves again.


.. _Configuration:
https://crate.io/docs/en/stable/configuration.html#graceful-stop
.. _Zero Downtime Upgrade:
https://crate.io/docs/en/stable/best_practice/cluster_upgrade.html
