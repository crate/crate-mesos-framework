============
Installation
============

Build
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


Resources
=========

After setting up a Mesos cluster, your Mesos slaves need to provide various
resources that are required by Crate and the Crate Framework, mainly paths and
ports.

User/Role
---------

**The Crate Framework is run as user ``crate`` and role ``*`` by default.
However, this is configurable using ``--framework-user`` and
``--framework-role``.**

This means, that a user ``crate`` (or other specified user) is required to be
present on all instances, both master and slaves. The user does not need to
have any specific permissions. You can add a user with the simplest
configuration::

    useradd crate -s /bin/bash

If you specify a role different to the default ``"*"`` you need to add it the
mesos-master configuration, e.g.::

    echo "crate" > /etc/mesos-master/roles

Data Path
---------

If you are using :ref:`persistent_data_paths` (which is recommended), you need
to make sure that the user ``crate`` has **write** permissions at these
locations. For example::

    chown crate:crate /path/to/persistent/disk

Ports
-----

Crate uses by default a the ports ``4200`` and ``4300``.
In order to get offers you need to add the resource reservation for a port
range that includes these ports, e.g. writing it into the resources file::

    echo 'ports(*)[31000-31099, 31101-32000, 4000-4999]' > /etc/mesos-slave/resources

or starting the slave with the option::

    --resources=ports(*)[31000-31099, 31101-32000, 4000-4999]

Then restart the slave and clean the old slave state if necessary
(``rm -f /tmp/mesos/meta/slaves/latest``).

The ports can be configured on startup of the Framework, which means that you
need adopt the resource port range according to your configured ports.


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


Local Installation
==================

This repository provides a ``Vagrantfile`` that can be used for a simple
installation of a Mesos cluster and can be used for local deployment and
testing.

The script will launch a 4 node Mesos cluster, 1 master and 3 slaves. For full
instructions how to set up the local cluster (including changes to your
``/etc/hosts`` file), please refer to the ``DEVELOP.rst`` in the root of the
repository_.


Execute via Command Line
------------------------

::

    java -Djava.library.path=/usr/local/lib -jar /path/to/crate-mesos-0.x.x.jar --crate-version 0.x.x [OPTIONS]"


Launch via Marathon
-------------------

Create a Marathon configuration file::

    {
      "id": "crate-dev",
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


.. _repository: https://github.com/crate/crate-mesos-framework

