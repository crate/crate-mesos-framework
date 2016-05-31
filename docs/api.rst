=============
Framework API
=============


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
This option will cause the Crate node to try to move all shards off the node
before shutting down. If this is not possible, the node will **not** shut down
and run into the timeout (``cluster.graceful_stop.timeout``). However the Crate
Framework will continue trying to shut down the node again. Such a state is
indicated by the Framework API when the number of running instances does not
approach the number of desired instances when scaling down. Please keep in mind
that the cluster can not be resized to zero instances.

In order to shut down the the cluster you need to use the ``/cluster/shutdown``
API endpoint.

Cluster Settings
----------------

When resizing a cluster the Crate Framework automatically applies certain
important cluster settings. These settings are:

* ``discovery.zen.minimum_master_nodes``
* ``gateway.recover_after_nodes``
* ``gateway.expected_nodes``

Since only the ``minumum_master_nodes`` setting is a runtime settings, there
are various limitations to how these settings are applied. **These limitations
should be carefully considered when running a Crate cluster!**

1. The ``minimum_master_nodes`` and ``gateway`` settings are applied on node
   start using the ``-Des.`` command line arguments.
   This means that when you resize a cluster from 0 instances
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
   scale down your cluster big times, you should do it in multiple smaller
   iterations!

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

The minimum amount of nodes is equal to the highest number of replicas of a
table plus 1::

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


.. _Configuration: https://crate.io/docs/en/stable/configuration.html#graceful-stop
.. _Zero Downtime Upgrade: https://crate.io/docs/en/stable/best_practice/cluster_upgrade.html

