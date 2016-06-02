=====================
Crate Mesos Framework
=====================

.. image:: https://travis-ci.org/crate/crate-mesos-framework.svg?branch=master
    :target: https://travis-ci.org/crate/crate-mesos-framework

.. image:: https://img.shields.io/badge/docs-latest-brightgreen.svg
    :target: https://crate-mesos-framework.readthedocs.io/en/latest/

.. image:: https://img.shields.io/badge/license-Apache%202-blue.svg
    :target: https://raw.githubusercontent.com/crate/crate-mesos-framework/master/LICENSE

This is an integration framework which allows running and managing the Crate_
database through Mesos_.

.. warning::

    **DISCLAIMER:**
    *This is a very early version of the Crate Mesos Framework;
    document, code behavior, and anything else may change without notice
    and/or break older installations!*


Quick Guide
===========

The easiest way to run the Crate Mesos Framework is by scheduling it using
Marathon_, e.g. on a DCOS_ cluster.

The current version of the framework is ``0.2.0``.
Example of a ``Marathon.json`` file:

.. code-block:: json

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
            "CRATE_CLUSTER_NAME": "my-crate-cluster",
            "CRATE_VERSION": "0.54.9",
            "CRATE_HTTP_PORT": "4200",
            "CRATE_TRANSPORT_PORT": "4300",
            "ZOOKEEPER": "master.mesos:2181"
        },
        "fetch": [
            {
                "uri": "https://cdn.crate.io/downloads/crate-mesos-0.2.0.tar.gz",
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
        "cmd": "env && $(pwd)/jre/bin/java $JAVA_OPTS -jar $(pwd)/crate-mesos-0.2.0.jar --crate-cluster-name $CRATE_CLUSTER_NAME --crate-version $CRATE_VERSION --api-port $PORT0 --crate-http-port $CRATE_HTTP_PORT --crate-transport-port $CRATE_TRANSPORT_PORT --zookeeper $ZOOKEEPER",
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

Use ``curl`` to submit the file to Marathon::

    curl -sXPOST "http://master.mesos:8080/v2/apps" -d@crate.json -H "Content-Type: application/json" | jq .

Once deployed you can use the framework API_ to launch a Crate cluster. To do so
find out on which host the framework is scheduled and execute the ``resize``
command::

    curl -sXPOST -H "Content-Type: application/json" $FRAMEWORK_HOST:4040/cluster/resize -d '{"instances": $NUM_INSTANCE}'

Read the documentation_ for further usage instructions!

Build from source
-----------------

However, the ``jar`` file can also be built from source::

    ./gradlew fatJar

The output jar file can be found in ``build/libs/`` and need to be hosted on
your own.


Full Documentation
==================

The documentation for this project has moved into the ``docs/`` folder of this
repository. It is built on ReadTheDocs where it can be found `here`_.


Are you a Developer?
====================

You can build Crate Mesos Framework on your own with the latest version hosted
on GitHub. To do so, please refer to ``DEVELOP.rst`` for further information.


.. _Crate: https://crate.io
.. _Mesos: http://mesos.apache.org
.. _Marathon: https://mesosphere.github.io/marathon/
.. _DCOS: https://dcos.io
.. _API: https://crate-mesos-framework.readthedocs.io/en/latest/api.html
.. _documentation: https://crate-mesos-framework.readthedocs.io/en/latest/index.html
.. _here: https://crate-mesos-framework.readthedocs.io/en/latest/index.html

