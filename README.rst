=======================
CrateDB Mesos Framework
=======================

.. image:: https://travis-ci.org/crate/crate-mesos-framework.svg?branch=master
    :target: https://travis-ci.org/crate/crate-mesos-framework

.. image:: https://img.shields.io/badge/license-Apache%202-blue.svg
    :target: https://raw.githubusercontent.com/crate/crate-mesos-framework/master/LICENSE

|

An integration framework that allows you to run and manage CrateDB_ via `Apache
Mesos`_.

**This project is currently unmaintained. Contributions welcome. See below.**

*Note: this framework is experimental and not suitable for production use.
Future changes in the API might break older installations!*

Prerequisites
=============

A JDK needs to be installed.

On OS X, we recommend using `Oracle's Java`_. If you're using Linux, we
recommend OpenJDK_.

We recommend you use a recent Java 8 version.

Vagrant_ is also used.

Setup and Building
==================

Clone the repository::

    $ git clone https://github.com/crate/crate-mesos-framework.git

Build the JAR file::

    $ ./gradlew fatJar

The JAR file can then be found in the ``build/libs/`` directory.

This JAR file cannot be run directly as it requires a Mesos master instance and
the Mesos native libraries.

Launching
=========

This project ships with a Vagrantfile that can be used with Vagrant to launch
*virtual machines* (VM) with Mesos installed.

Launch the VMs like so::

    $ vagrant up

This will create and provision four VMs:

- ``mesos-master``
    The Mesos master instance along with Zookeeper
- ``mesos-slave-{1..3}``
    The Mesos slaves

If you have run ``vagrant up`` before, Vagrant boots the existing VMs.

Once the VMs are up-and-running, the CrateDB Mesos framework can be started
inside the master VM. You can do that like so::

    $ vagrant ssh -c "java -Djava.library.path=/usr/local/lib -jar /vagrant/build/libs/crate-mesos-*.jar --crate-version 0.54.9 --zookeeper 192.168.10.100:2181"

Inside the VM, ``/vagrant`` is mapped to the project root. This way, the JAR
file can be accessed.

Hosts Entries
-------------

The static IPs of the Vagrant VMs are ``192.168.10.100`` for the master and
``192.168.10.{101..103}`` for the slaves.

You can add them to your ``/etc/hosts`` file, like so::

    192.168.10.100 mesos-master
    192.168.10.101 mesos-slave-1
    192.168.10.102 mesos-slave-2
    192.168.10.103 mesos-slave-3

The Mesos WebUI should be available under http://mesos-master:5050 immediately
after ``vagrant up`` is finished.

Once the CrateDB Mesos framework has been launched, the framework API becomes
available at http://mesos-master:4040/cluster (if an API port is not otherwise
specified).

Shortcut
--------

You can re-build the JAR file and re-start the framework with this shortcut
command::

    $ bin/deploy --crate-version 0.47.7 --zookeeper 192.168.10.100:2181

Running via Marathon
====================

One of the easiest ways to run CrateDB Mesos framework is via Marathon_, on
something like a DCOS_ cluster.

For installing Marathon, please refer to `Mesosphere install guide`_. The
Marathon WebUI should be available under http://mesos-master:8080 after setting
things up.

Modify the template `marathon/local.json`_ file to suit your purposes and then
submit the file to Marathon, like so::

    $ curl -s -XPOST http://mesos-master:8080/v2/apps -d@marathon/local.json -H "Content-Type: application/json"

Once deployed, you can use the framework API_ to launch a CrateDB cluster. To do
so, execute the ``resize`` command::

    $ curl -sXPOST -H "Content-Type: application/json" <FRAMEWORK_HOST>:4040/cluster/resize -d '{"instances": <NUM_INSTANCES>}'

Here, ``<FRAMEWORK_HOST>`` is the hostname or IP of the host the framework is
scheduled on, and ``<NUM_INSTANCES>`` is the desired number of CrateDB nodes.

Contributing
============

This project is primarily maintained by Crate.io_, but we welcome community
contributions!

See the `developer docs`_ and the `contribution docs`_ for more information.

Help
====

Looking for more help?

- Read `the project documentation`_
- Check `StackOverflow`_ for common problems
- Chat with us on `Slack`_
- Get `paid support`_

.. _`Mesosphere install guide`: http://mesosphere.com/docs/getting-started/datacenter/install/
.. _Apache Mesos: http://mesos.apache.org
.. _API: https://crate.io/docs/reference/mesos-framework/en/latest/api.html
.. _contribution docs: CONTRIBUTING.rst
.. _Crate.io: http://crate.io/
.. _CrateDB: https://crate.io
.. _DCOS: https://dcos.io
.. _developer docs: DEVELOP.rst
.. _Gradle: http://www.gradle.org/
.. _Marathon: https://mesosphere.github.io/marathon/
.. _marathon/local.json: marathon/local.json
.. _OpenJDK: http://openjdk.java.net/projects/jdk8/
.. _Oracle's Java: http://www.java.com/en/download/help/mac_install.xml
.. _paid support: https://crate.io/pricing/
.. _Slack: https://crate.io/docs/support/slackin/
.. _StackOverflow: https://stackoverflow.com/tags/crate
.. _the project documentation: https://crate.io/docs/reference/mesos-framework/
.. _Vagrant: https://www.vagrantup.com/
